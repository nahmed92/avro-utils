/*
 * #region
 * avro-utils
 * %%
 * Copyright (C) 2018 Etilize
 * %%
 * NOTICE: All information contained herein is, and remains the property of ETILIZE.
 * The intellectual and technical concepts contained herein are proprietary to
 * ETILIZE and may be covered by U.S. and Foreign Patents, patents in process, and
 * are protected by trade secret or copyright law. Dissemination of this information
 * or reproduction of this material is strictly forbidden unless prior written
 * permission is obtained from ETILIZE. Access to the source code contained herein
 * is hereby forbidden to anyone except current ETILIZE employees, managers or
 * contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure of this source code, which includes information that is confidential
 * and/or proprietary, and is a trade secret, of ETILIZE. ANY REPRODUCTION, MODIFICATION,
 * DISTRIBUTION, PUBLIC PERFORMANCE, OR PUBLIC DISPLAY OF OR THROUGH USE OF THIS
 * SOURCE CODE WITHOUT THE EXPRESS WRITTEN CONSENT OF ETILIZE IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES. THE RECEIPT
 * OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION DOES NOT CONVEY OR
 * IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS, OR TO
 * MANUFACTURE, USE, OR SELL ANYTHING THAT IT MAY DESCRIBE, IN WHOLE OR IN PART.
 * #endregion
 */

package com.etilize.avro.spring;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericContainer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cloud.stream.schema.ParsedSchema;
import org.springframework.cloud.stream.schema.SchemaNotFoundException;
import org.springframework.cloud.stream.schema.SchemaReference;
import org.springframework.cloud.stream.schema.SchemaRegistrationResponse;
import org.springframework.cloud.stream.schema.avro.AvroSchemaRegistryClientMessageConverter;
import org.springframework.cloud.stream.schema.client.SchemaRegistryClient;
import org.springframework.integration.support.MutableMessageHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.MimeType;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;

/**
 * A {@link org.springframework.messaging.converter.MessageConverter} for Apache Avro,
 * with the ability to publish and retrieve schemas stored in a schema server, allowing
 * for schema evolution in applications. The supported content types are in the form
 * `application/*+avro`.
 *
 * During the conversion to a message, the converter will set the 'contentType' header to
 * 'application/[prefix].[subject].v[version]+avro', where:
 *
 * <li>
 * <ul>
 * <i>prefix</i> is a configurable prefix (default 'vnd');
 * </ul>
 * <ul>
 * <i>subject</i> is a subject derived from the type of the outgoing object - typically
 * the class name;
 * </ul>
 * <ul>
 * <i>version</i> is the schema version for the given subject;
 * </ul>
 * </li>
 *
 * When converting from a message, the converter will parse the content-type and use it to
 * fetch and cache the writer schema using the provided {@link SchemaRegistryClient}.
 *
 * @author Faisal Feroz
 */
public class AvroJsonSchemaRegistryClientMessageConverter
        extends AvroSchemaRegistryClientMessageConverter {

    private final AvroMapper mapper;

    private final CacheManager cacheManager;

    private final SchemaRegistryClient schemaRegistryClient;

    /**
     * Creates a new instance, configuring it with {@link SchemaRegistryClient} and
     * {@link CacheManager}.
     *
     * @param mapper the {@link AvroMapper} used to generate the schema
     * @param schemaRegistryClient the {@link SchemaRegistryClient} used to interact with
     *        the schema registry server.
     * @param cacheManager instance of {@link CacheManager} to cache parsed schemas. If
     *        caching is not required use {@link NoOpCacheManager}
     */
    public AvroJsonSchemaRegistryClientMessageConverter(final AvroMapper mapper,
            final SchemaRegistryClient schemaRegistryClient,
            final CacheManager cacheManager) {
        super(schemaRegistryClient, cacheManager);
        this.mapper = mapper;
        this.schemaRegistryClient = schemaRegistryClient;
        this.cacheManager = cacheManager;
    }

    @Override
    protected Schema resolveSchemaForWriting(final Object payload,
            final MessageHeaders headers, final MimeType hintedContentType) {

        Schema schema;
        schema = extractSchemaForWriting(payload);
        ParsedSchema parsedSchema = this.cacheManager.getCache(REFERENCE_CACHE_NAME).get(
                schema, ParsedSchema.class);

        if (parsedSchema == null) {
            parsedSchema = new ParsedSchema(schema);
            this.cacheManager.getCache(REFERENCE_CACHE_NAME).putIfAbsent(schema,
                    parsedSchema);
        }

        if (parsedSchema.getRegistration() == null) {
            final SchemaRegistrationResponse response = this.schemaRegistryClient.register(
                    toSubject(schema), AVRO_FORMAT, parsedSchema.getRepresentation());
            parsedSchema.setRegistration(response);

        }

        final SchemaReference schemaReference = parsedSchema.getRegistration().getSchemaReference();

        if (headers instanceof MutableMessageHeaders) {
            headers.put(MessageHeaders.CONTENT_TYPE,
                    "application/vnd." + schemaReference.getSubject() + ".v"
                            + schemaReference.getVersion() + "+avro");
        }

        return schema;
    }

    @Override
    protected Object convertFromInternal(final Message<?> message,
            final Class<?> targetClass, final Object conversionHint) {
        Object result = null;
        try {
            final byte[] payload = (byte[]) message.getPayload();
            final ByteBuffer buf = ByteBuffer.wrap(payload);
            MimeType mimeType = getContentTypeResolver().resolve(message.getHeaders());
            if (mimeType == null) {
                if (conversionHint instanceof MimeType) {
                    mimeType = (MimeType) conversionHint;
                } else {
                    return null;
                }
            }
            buf.get(payload);
            AvroSchema schema = new AvroSchema(
                    resolveWriterSchemaForDeserialization(mimeType));
            final Schema readerSchema = resolveReaderSchemaForDeserialization(
                    targetClass);
            if (readerSchema != null) {
                schema = schema.withReaderSchema(new AvroSchema(readerSchema));
            }
            result = mapper.readerFor(targetClass).with(schema).readValue(payload);
        } catch (final IOException e) {
            throw new MessageConversionException(message, "Failed to read payload", e);
        }
        return result;
    }

    private Schema extractSchemaForWriting(final Object payload) {
        Schema schema = null;
        this.logger.debug("Obtaining schema for class " + payload.getClass());

        if (GenericContainer.class.isAssignableFrom(payload.getClass())) {
            schema = ((GenericContainer) payload).getSchema();
            this.logger.debug("Avro type detected, using schema from object");
        } else {
            schema = this.cacheManager.getCache(REFLECTION_CACHE_NAME).get(
                    payload.getClass().getName(), Schema.class);
            if (schema == null) {
                if (!isDynamicSchemaGenerationEnabled()) {
                    throw new SchemaNotFoundException(String.format(
                            "No schema found in the local cache for %s, and dynamic schema generation "
                                    + "is not enabled",
                            payload.getClass()));
                } else {
                    schema = generateSchema(payload);
                }
                this.cacheManager.getCache(REFLECTION_CACHE_NAME).put(
                        payload.getClass().getName(), schema);
            }
        }
        return schema;
    }

    private Schema generateSchema(final Object payload) {
        try {
            return mapper.schemaFor(payload.getClass()).getAvroSchema();
        } catch (final JsonMappingException e) {
            throw new SchemaGenerationException(e);
        }
    }

}

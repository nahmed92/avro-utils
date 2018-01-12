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

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cloud.stream.schema.SchemaNotFoundException;
import org.springframework.cloud.stream.schema.SchemaReference;
import org.springframework.cloud.stream.schema.client.ConfluentSchemaRegistryClient;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.MimeType;

import com.etilize.avro.test.AbstractIntegrationTest;
import com.etilize.avro.test.TestMessage;
import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.google.common.collect.Maps;

public class AvroJsonSchemaRegistryClientMessageConverterTest
        extends AbstractIntegrationTest {

    private AvroJsonSchemaRegistryClientMessageConverter converter;

    @Mock
    private AvroMapper mapper;

    @Mock
    private ConfluentSchemaRegistryClient client;

    @Override
    public void before() throws Exception {
        super.before();
        converter = new AvroJsonSchemaRegistryClientMessageConverter(mapper, client,
                new ConcurrentMapCacheManager());
        converter.afterPropertiesSet();
    }

    @Test(expected = MessageConversionException.class)
    public void shouldThrowExceptionWhenErrorOccursInMessageDeserialization()
            throws Exception {
        final byte[] payload = new byte[0];
        final Map<String, Object> map = new HashMap<>();
        map.put(MessageHeaders.CONTENT_TYPE,
                MimeType.valueOf("application/vnd.testmessage.v1+avro"));
        final MessageHeaders headers = new MessageHeaders(map);

        ObjectReader reader = Mockito.mock(ObjectReader.class);
        Mockito.when(mapper.readerFor(TestMessage.class)).thenReturn(reader);
        Mockito.when(reader.with(Mockito.any(FormatSchema.class))).thenReturn(reader);
        Mockito.when(reader.readValue(payload)).thenThrow(
                new IOException("Mock Exception"));

        final SchemaReference schemaReference = new SchemaReference("testmessage", 1,
                AvroJsonSchemaRegistryClientMessageConverter.AVRO_FORMAT);
        Mockito.when(client.fetch(schemaReference)).thenReturn(
                "{\"type\":\"record\",\"name\":\"TestMessage\",\"namespace\":\"com.etilize.avro.spring.test.TestMessage\",\"doc\":\"Message for testing\",\"fields\":[{\"name\":\"field1\",\"type\":\"string\",\"doc\":\"Required string field\"},{\"name\":\"field2\",\"type\":[{\"type\":\"long\",\"java-class\":\"java.lang.Long\"},\"null\"],\"doc\":\"Nullable long field wit default value of -1\",\"default\":-1}]}");

        final TestMessage message = (TestMessage) converter.fromMessage(
                new GenericMessage<>(payload, headers), TestMessage.class);

        assertThat(message, is(notNullValue()));
        assertThat(message.getField1(), is("Mock Field 1"));
        assertThat(message.getField2(), is(nullValue()));
    }

    @Test(expected = SchemaNotFoundException.class)
    public void shouldThrowExceptionWhenDynamicGenerationIsFalseAndNoSchemaExistsWhileSerializing() {
        final TestMessage testMessage = new TestMessage("Mock Field 1");

        converter.toMessage(testMessage,
                new MessageHeaders(Maps.<String, Object> newHashMap()));
    }

    @Test(expected = SchemaGenerationException.class)
    public void shouldThrowExceptionWhenUnableToGenerateDynamicSchema() throws Exception {
        Mockito.when(mapper.schemaFor(TestMessage.class)).thenThrow(
                JsonMappingException.fromUnexpectedIOE(new IOException("Mock Message")));

        final TestMessage testMessage = new TestMessage("Mock Field 1");
        converter.setDynamicSchemaGenerationEnabled(true);

        converter.toMessage(testMessage,
                new MessageHeaders(Maps.<String, Object> newHashMap()));
    }
}

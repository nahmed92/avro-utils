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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.junit.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cloud.stream.schema.client.ConfluentSchemaRegistryClient;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.MimeType;

import com.etilize.avro.test.AbstractIntegrationTest;
import com.etilize.avro.test.TestMessage;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.google.common.collect.Maps;

public class AvroJsonSchemaRegistryClientMessageConverterIntegrationTest
        extends AbstractIntegrationTest {

    private AvroJsonSchemaRegistryClientMessageConverter converter;

    private AvroMapper mapper = new AvroMapper();

    private final TestMessage testMessage = new TestMessage("Mock Field 1");

    private byte[] payload;

    @Override
    public void before() throws Exception {
        super.before();

        final ConfluentSchemaRegistryClient client = new ConfluentSchemaRegistryClient();
        client.setEndpoint(String.format("http://localhost:%d/", instanceRule.port()));

        converter = new AvroJsonSchemaRegistryClientMessageConverter(mapper, client,
                new ConcurrentMapCacheManager());
        converter.setDynamicSchemaGenerationEnabled(true);

        converter.afterPropertiesSet();
        final DatumWriter<Object> writer = new ReflectDatumWriter<>(
                mapper.schemaFor(TestMessage.class).getAvroSchema());
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final Encoder encoder = EncoderFactory.get().binaryEncoder(baos, null);
        writer.write(testMessage, encoder);
        encoder.flush();

        payload = baos.toByteArray();
    }

    @Test
    public void shouldConvertFromMessage() {
        stubFor(get(urlPathEqualTo("/subjects/testmessage/versions/1")) //
                .willReturn(aResponse() //
                        .withStatus(200) //
                        .withHeader(HttpHeaders.CONTENT_TYPE,
                                "application/vnd.schemaregistry.v1+json") //
                        .withBodyFile("testmessage_get.json")));

        final Map<String, Object> map = new HashMap<>();
        map.put(MessageHeaders.CONTENT_TYPE,
                MimeType.valueOf("application/vnd.testmessage.v1+avro"));
        final MessageHeaders headers = new MessageHeaders(map);

        final TestMessage message = (TestMessage) converter.fromMessage(
                new GenericMessage<>(payload, headers), TestMessage.class);

        assertThat(message, is(notNullValue()));
        assertThat(message.getField1(), is("Mock Field 1"));
        assertThat(message.getField2(), is(nullValue()));
    }

    @Test
    public void shouldConvertToMessage() {
        stubFor(post(urlPathEqualTo("/subjects/testmessage/versions")) //
                .willReturn(aResponse() //
                        .withStatus(200) //
                        .withHeader(HttpHeaders.CONTENT_TYPE,
                                "application/vnd.schemaregistry.v1+json") //
                        .withBody("{\"id\":1}")));

        stubFor(post(urlPathEqualTo("/subjects/testmessage")) //
                .willReturn(aResponse() //
                        .withStatus(200) //
                        .withHeader(HttpHeaders.CONTENT_TYPE,
                                "application/vnd.schemaregistry.v1+json") //
                        .withBodyFile("testmessage_get.json")));

        final Message<?> message = converter.toMessage(testMessage,
                new MessageHeaders(Maps.<String, Object> newHashMap()));
        assertThat(message, is(notNullValue()));
        assertThat((byte[]) message.getPayload(), is(payload));
    }
}

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

package com.etilize.avro.utils;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.junit.Test;

import com.etilize.avro.test.AbstractTest;
import com.etilize.avro.test.TestMessage;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;

public class AvroInteropUtilsTest extends AbstractTest {

    private AvroInteropUtils avro;

    private byte[] payload;

    private final TestMessage testMessage = new TestMessage("Mock Field 1");

    private final AvroMapper mapper = new AvroMapper();

    @Override
    public void before() throws Exception {
        super.before();
        avro = new AvroInteropUtils(mapper);

        final DatumWriter<Object> writer = new ReflectDatumWriter<>(
                mapper.schemaFor(TestMessage.class).getAvroSchema());
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final Encoder encoder = EncoderFactory.get().binaryEncoder(baos, null);
        writer.write(testMessage, encoder);
        encoder.flush();

        payload = baos.toByteArray();
    }

    @Test
    public void shouldDecode() throws Exception {

        avro.decode(payload, TestMessage.class);
    }

    @Test
    public void shouldDecodeUsingSchema() throws Exception {
        final String schema = "{\"type\":\"record\",\"name\":\"TestMessage\",\"namespace\":"
                + "\"com.etilize.avro.test\",\"doc\":\"Message for testing\",\"fields\":"
                + "[{\"name\":\"field1\",\"type\":\"string\",\"doc\":\"Required string field\"},"
                + "{\"name\":\"field2\",\"type\":[{\"type\":\"long\",\"java-class\":"
                + "\"java.lang.Long\"},\"null\"],\"doc\":\"Nullable long field wit default value"
                + " of -1\",\"default\":-1}]}";

        final TestMessage testMessage = avro.decode(payload, schema, TestMessage.class);
        assertThat(testMessage.getField1(), is("Mock Field 1"));
        assertThat(testMessage.getField2(), nullValue());
    }
}

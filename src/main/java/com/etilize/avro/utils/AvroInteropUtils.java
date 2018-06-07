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

import java.io.IOException;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;

/**
 * Testing Utility functions related to Apache Avro
 *
 * @author Faisal Feroz
 *
 */
public class AvroInteropUtils {

    private final AvroMapper mapper;

    /**
     * Constructor for Avro Test Utils
     *
     * @param mapper {@link AvroMapper} instance to use for decoding the messages
     */
    public AvroInteropUtils(final AvroMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Decodes the given payload as an instance of the given clazz
     *
     * @param payload the payload to decode
     * @param clazz the java type/class the message should be decoded as
     * @return decoded payload
     * @throws IOException is thrown when there is an error in decoding payload
     */
    public <T> T decode(final byte[] payload, final Class<T> clazz) throws IOException {
        final AvroSchema schema = mapper.schemaFor(clazz);
        return mapper.readerFor(clazz).with(schema).readValue(payload);
    }

    /**
     * Decodes the given payload using provided avro schema as an instance of the given clazz
     *
     * @param payload the payload to decode
     * @param schema avro schmea
     * @param clazz the java type/class the message should be decoded as
     * @return decoded payload
     * @throws IOException is thrown when there is an error in decoding payload
     */
    public <T> T decode(final byte[] payload, final String schema, final Class<T> clazz)
            throws IOException {
        final AvroSchema avroschema = mapper.schemaFrom(schema);
        return mapper.readerFor(clazz).with(avroschema).readValue(payload);
    }
}

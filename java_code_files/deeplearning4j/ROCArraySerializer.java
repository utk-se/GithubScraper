/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.nd4j.evaluation.serde;

import org.nd4j.evaluation.classification.ROC;
import org.nd4j.shade.jackson.core.JsonGenerator;
import org.nd4j.shade.jackson.core.JsonProcessingException;
import org.nd4j.shade.jackson.databind.JsonSerializer;
import org.nd4j.shade.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Custom Jackson serializer for ROC[]. Simply delegates to {@link ROCSerializer} internally.
 *
 * @author Alex Black
 */
public class ROCArraySerializer extends JsonSerializer<ROC[]> {
    private static final ROCSerializer serializer = new ROCSerializer();

    @Override
    public void serialize(ROC[] rocs, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
                    throws IOException, JsonProcessingException {
        jsonGenerator.writeStartArray();
        for (ROC r : rocs) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("@class", ROC.class.getName());
            serializer.serialize(r, jsonGenerator, serializerProvider);
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndArray();
    }
}

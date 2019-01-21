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

package org.nd4j.serde.jackson;

import org.junit.BeforeClass;
import org.junit.Test;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.shade.jackson.databind.ObjectMapper;
import org.nd4j.shade.jackson.databind.module.SimpleModule;
import org.nd4j.shade.serde.jackson.shaded.NDArrayDeSerializer;
import org.nd4j.shade.serde.jackson.shaded.NDArraySerializer;

import static org.junit.Assert.assertEquals;

/**
 * Created by agibsonccc on 6/23/16.
 */
public class NdArraySerializerTest {
    private static ObjectMapper objectMapper;

    @BeforeClass
    public static void before() {
        objectMapper = objectMapper();

    }


    @Test
    public void testSerde() throws Exception {
        String json = objectMapper.writeValueAsString(Nd4j.create(2, 2));
        INDArray assertion = Nd4j.create(2, 2);
        INDArray test = objectMapper.readValue(json, INDArray.class);
        assertEquals(assertion, test);
    }

    private static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule nd4j = new SimpleModule("nd4j");
        nd4j.addDeserializer(INDArray.class, new NDArrayDeSerializer());
        nd4j.addSerializer(INDArray.class, new NDArraySerializer());
        mapper.registerModule(nd4j);
        return mapper;

    }
}

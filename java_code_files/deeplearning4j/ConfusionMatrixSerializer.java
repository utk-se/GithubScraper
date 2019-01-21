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

import com.google.common.collect.Multiset;
import org.nd4j.evaluation.classification.ConfusionMatrix;
import org.nd4j.shade.jackson.core.JsonGenerator;
import org.nd4j.shade.jackson.core.JsonProcessingException;
import org.nd4j.shade.jackson.databind.JsonSerializer;
import org.nd4j.shade.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A JSON serializer for {@code ConfusionMatrix<Integer>} instances, used in {@link org.deeplearning4j.eval.Evaluation}
 *
 * @author Alex Black
 */
public class ConfusionMatrixSerializer extends JsonSerializer<ConfusionMatrix<Integer>> {
    @Override
    public void serialize(ConfusionMatrix<Integer> cm, JsonGenerator gen, SerializerProvider provider)
                    throws IOException, JsonProcessingException {
        List<Integer> classes = cm.getClasses();
        Map<Integer, Multiset<Integer>> matrix = cm.getMatrix();

        Map<Integer, int[][]> m2 = new LinkedHashMap<>();
        for (Integer i : matrix.keySet()) { //i = Actual class
            Multiset<Integer> ms = matrix.get(i);
            int[][] arr = new int[2][ms.size()];
            int used = 0;
            for (Integer j : ms.elementSet()) {
                int count = ms.count(j);
                arr[0][used] = j; //j = Predicted class
                arr[1][used] = count; //prediction count
                used++;
            }
            m2.put(i, arr);
        }

        gen.writeStartObject();
        gen.writeObjectField("classes", classes);
        gen.writeObjectField("matrix", m2);
        gen.writeEndObject();
    }
}

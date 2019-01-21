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

package org.nd4j.serde.gson;

import org.junit.Test;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import static org.junit.Assert.assertEquals;

public class GsonDeserializationUtilsTest {
    @Test
    public void deserializeRawJson_PassInInRank3Array_ExpectCorrectDeserialization() {
        String serializedRawArray = "[[[1.00, 11.00, 3.00],\n" + "[13.00, 5.00, 15.00],\n" + "[7.00, 17.00, 9.00]]]";
        INDArray expectedArray = buildExpectedArray(1, 1, 3, 3);

        INDArray indArray = GsonDeserializationUtils.deserializeRawJson(serializedRawArray);

        assertEquals(expectedArray, indArray);
    }

    @Test
    public void deserializeRawJson_ArrayHasOnlyOneRowWithColumns_ExpectCorrectDeserialization() {
        String serializedRawArray = "[1.00, 11.00, 3.00]";
        INDArray expectedArray = Nd4j.create(new double[] {1, 11, 3});

        INDArray indArray = GsonDeserializationUtils.deserializeRawJson(serializedRawArray);

        assertEquals(expectedArray, indArray);
    }

    @Test
    public void deserializeRawJson_ArrayIsRankFive_ExpectCorrectDeserialization() {
        String serializedRawArray = "[[[[[1.00, 11.00],\n" + "    [3.00, 13.00]],\n" + "   [[5.00, 15.00],\n"
                        + "    [7.00, 17.00]]],\n" + "  [[[9.00, 1.00],\n" + "    [11.00, 3.00]],\n"
                        + "   [[13.00, 5.00],\n" + "    [15.00, 7.00]]],\n" + "  [[[17.00, 9.00],\n"
                        + "    [1.00, 11.00]],\n" + "   [[3.00, 13.00],\n" + "    [5.00, 15.00]]]],\n"
                        + " [[[[7.00, 17.00],\n" + "    [9.00, 1.00]],\n" + "   [[11.00, 3.00],\n"
                        + "    [13.00, 5.00]]],\n" + "  [[[15.00, 7.00],\n" + "    [17.00, 9.00]],\n"
                        + "   [[1.00, 11.00],\n" + "    [3.00, 13.00]]],\n" + "  [[[5.00, 15.00],\n"
                        + "    [7.00, 17.00]],\n" + "   [[9.00, 1.00],\n" + "    [11.00, 3.00]]]],\n"
                        + " [[[[13.00, 5.00],\n" + "    [15.00, 7.00]],\n" + "   [[17.00, 9.00],\n"
                        + "    [1.00, 11.00]]],\n" + "  [[[3.00, 13.00],\n" + "    [5.00, 15.00]],\n"
                        + "   [[7.00, 17.00],\n" + "    [9.00, 1.00]]],\n" + "  [[[11.00, 3.00],\n"
                        + "    [13.00, 5.00]],\n" + "   [[15.00, 7.00],\n" + "    [17.00, 9.00]]]]]";
        INDArray expectedArray = buildExpectedArray(8, 3, 3, 2, 2, 2);

        INDArray array = GsonDeserializationUtils.deserializeRawJson(serializedRawArray);

        assertEquals(expectedArray, array);
    }


    @Test
    public void testSimpleVector() {
        INDArray arr = Nd4j.linspace(1, 4, 4, DataType.FLOAT);
        assertEquals(GsonDeserializationUtils.deserializeRawJson(arr.toString()), arr);
    }



    @Test
    public void deserializeRawJson_HaveCommaInsideNumbers_ExpectCorrectDeserialization() {
        String serializedRawArray =
                        "[[1.00, 1100.00, 3.00],\n" + "[13.00, 5.00, 15591.00],\n" + "[7000.00, 17.00, 9.00]]";
        INDArray expectedArray = Nd4j.create(new double[] {1, 1100, 3, 13, 5, 15591, 7000, 17, 9}, new int[] {3, 3});

        INDArray indArray = GsonDeserializationUtils.deserializeRawJson(serializedRawArray);

        assertEquals(expectedArray, indArray);
    }

    private INDArray buildExpectedArray(int numberOfTripletRows, int... shape) {
        INDArray expectedArray = Nd4j.create(3 * numberOfTripletRows, 3);
        for (int i = 0; i < numberOfTripletRows; i++) {
            int index = 3 * i;
            expectedArray.putRow(index, Nd4j.create(new double[] {1, 11, 3}));
            expectedArray.putRow(index + 1, Nd4j.create(new double[] {13, 5, 15}));
            expectedArray.putRow(index + 2, Nd4j.create(new double[] {7, 17, 9}));
        }

        return expectedArray.reshape(shape);
    }
}

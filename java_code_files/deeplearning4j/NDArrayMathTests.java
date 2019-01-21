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

package org.nd4j.linalg.shape;

import lombok.val;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.nd4j.linalg.BaseNd4jTest;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.util.NDArrayMath;

import static org.junit.Assert.assertEquals;


/**
 * @author Adam Gibson
 */
@RunWith(Parameterized.class)
public class NDArrayMathTests extends BaseNd4jTest {

    public NDArrayMathTests(Nd4jBackend backend) {
        super(backend);
    }


    @Test
    public void testVectorPerSlice() {
        INDArray arr = Nd4j.create(2, 2, 2, 2);
        assertEquals(4, NDArrayMath.vectorsPerSlice(arr));

        INDArray matrix = Nd4j.create(2, 2);
        assertEquals(2, NDArrayMath.vectorsPerSlice(matrix));

        INDArray arrSliceZero = arr.slice(0);
        assertEquals(4, NDArrayMath.vectorsPerSlice(arrSliceZero));

    }

    @Test
    public void testMatricesPerSlice() {
        INDArray arr = Nd4j.create(2, 2, 2, 2);
        assertEquals(2, NDArrayMath.matricesPerSlice(arr));
    }

    @Test
    public void testLengthPerSlice() {
        INDArray arr = Nd4j.create(2, 2, 2, 2);
        val lengthPerSlice = NDArrayMath.lengthPerSlice(arr);
        assertEquals(8, lengthPerSlice);
    }

    @Test
    public void toffsetForSlice() {
        INDArray arr = Nd4j.create(3, 2, 2);
        int slice = 1;
        assertEquals(4, NDArrayMath.offsetForSlice(arr, slice));
    }


    @Test
    public void testMapOntoVector() {
        INDArray arr = Nd4j.create(3, 2, 2);
        assertEquals(NDArrayMath.mapIndexOntoVector(2, arr), 4);
    }

    @Test
    public void testNumVectors() {
        INDArray arr = Nd4j.create(3, 2, 2);
        assertEquals(4, NDArrayMath.vectorsPerSlice(arr));
        INDArray matrix = Nd4j.create(2, 2);
        assertEquals(2, NDArrayMath.vectorsPerSlice(matrix));

    }

    @Test
    public void testOffsetForSlice() {
        INDArray arr = Nd4j.linspace(1, 16, 16, DataType.DOUBLE).reshape(2, 2, 2, 2);
        int[] dimensions = {0, 1};
        INDArray permuted = arr.permute(2, 3, 0, 1);
        int[] test = {0, 0, 1, 1};
        for (int i = 0; i < permuted.tensorsAlongDimension(dimensions); i++) {
            assertEquals(test[i], NDArrayMath.sliceOffsetForTensor(i, permuted, new int[] {2, 2}));
        }

        val arrTensorsPerSlice = NDArrayMath.tensorsPerSlice(arr, new int[] {2, 2});
        assertEquals(2, arrTensorsPerSlice);

        INDArray arr2 = Nd4j.linspace(1, 12, 12, DataType.DOUBLE).reshape(3, 2, 2);
        int[] assertions = {0, 1, 2};
        for (int i = 0; i < assertions.length; i++) {
            assertEquals(assertions[i], NDArrayMath.sliceOffsetForTensor(i, arr2, new int[] {2, 2}));
        }



        val tensorsPerSlice = NDArrayMath.tensorsPerSlice(arr2, new int[] {2, 2});
        assertEquals(1, tensorsPerSlice);


        INDArray otherTest = Nd4j.linspace(1, 144, 144, DataType.DOUBLE).reshape(6, 3, 2, 2, 2);
        System.out.println(otherTest);
        INDArray baseArr = Nd4j.linspace(1, 8, 8, DataType.DOUBLE).reshape(2, 2, 2);
        for (int i = 0; i < baseArr.tensorsAlongDimension(0, 1); i++) {
            System.out.println(NDArrayMath.sliceOffsetForTensor(i, baseArr, new int[] {2, 2}));
        }


    }

    @Test
    public void testOddDimensions() {
        INDArray arr = Nd4j.create(3, 2, 2);
        val numMatrices = NDArrayMath.matricesPerSlice(arr);
        assertEquals(1, numMatrices);
    }

    @Test
    public void testTotalVectors() {
        INDArray arr2 = Nd4j.create(2, 2, 2, 2);
        assertEquals(8, NDArrayMath.numVectors(arr2));
    }


    @Override
    public char ordering() {
        return 'f';
    }
}

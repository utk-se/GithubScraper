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

package org.nd4j.linalg.api.tad;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.nd4j.linalg.BaseNd4jTest;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.checkutil.NDArrayCreationUtil;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.primitives.Pair;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Alex Black
 */
@Slf4j
@RunWith(Parameterized.class)
public class TestTensorAlongDimension extends BaseNd4jTest {


    public TestTensorAlongDimension(Nd4jBackend backend) {
        super(backend);
    }


    @Test
    public void testJavaVsNative() {
        long totalJavaTime = 0;
        long totalCTime = 0;
        long n = 10;
        INDArray row = Nd4j.create(1, 100);

        for (int i = 0; i < n; i++) {
            StopWatch javaTiming = new StopWatch();
            javaTiming.start();
            row.javaTensorAlongDimension(0, 0);
            javaTiming.stop();
            StopWatch cTiming = new StopWatch();
            cTiming.start();
            row.tensorAlongDimension(0, 0);
            cTiming.stop();
            totalJavaTime += javaTiming.getNanoTime();
            totalCTime += cTiming.getNanoTime();
        }

        System.out.println("Java timing " + (totalJavaTime / n) + " C time " + (totalCTime / n));

    }

    @Test
    public void testTadShapesEdgeCases() {
        INDArray row = Nd4j.create(DataType.DOUBLE, 1, 5);
        INDArray col = Nd4j.create(DataType.DOUBLE, 5, 1);

        assertArrayEquals(new int[] {1, 5}, row.tensorAlongDimension(0, 1).shape());
        assertArrayEquals(new int[] {1, 5}, col.tensorAlongDimension(0, 0).shape());
    }

    @Test
    public void testTadShapes1d() {
        //Ensure TAD returns the correct/expected shapes, and values don't depend on underlying array layout/order etc
        /**
         * NEED TO WORK ON ELEMENT WISE STRIDE NOW.
         */
        //From a 2d array:
        int rows = 3;
        int cols = 4;
        INDArray testValues = Nd4j.linspace(1, rows * cols, rows * cols, DataType.DOUBLE).reshape('c', rows, cols);
        List<Pair<INDArray, String>> list = NDArrayCreationUtil.getAllTestMatricesWithShape('c', rows, cols, 12345, DataType.DOUBLE);
        for (Pair<INDArray, String> p : list) {
            INDArray arr = p.getFirst().assign(testValues);

            //Along dimension 0: expect row vector with length 'rows'
            assertEquals(cols, arr.tensorsAlongDimension(0));
            for (int i = 0; i < cols; i++) {
                INDArray tad = arr.tensorAlongDimension(i, 0);
                INDArray javaTad = arr.javaTensorAlongDimension(i, 0);
                assertEquals(javaTad, tad);
                assertArrayEquals(new int[] {1, rows}, tad.shape());
                assertEquals(testValues.javaTensorAlongDimension(i, 0), tad);
            }

            //Along dimension 1: expect row vector with length 'cols'
            assertEquals(rows, arr.tensorsAlongDimension(1));
            for (int i = 0; i < rows; i++) {
                INDArray tad = arr.tensorAlongDimension(i, 1);
                assertArrayEquals(new int[] {1, cols}, tad.shape());
                assertEquals(testValues.javaTensorAlongDimension(i, 1), tad);
            }
        }

        //From a 3d array:
        int dim2 = 5;
        log.info("AF");
        testValues = Nd4j.linspace(1, rows * cols * dim2, rows * cols * dim2, DataType.DOUBLE).reshape('c', rows, cols, dim2);
        list = NDArrayCreationUtil.getAll3dTestArraysWithShape(12345, new int[]{rows, cols, dim2}, DataType.DOUBLE);
        for (Pair<INDArray, String> p : list) {
            INDArray arr = p.getFirst().assign(testValues);
            INDArray javaTad = arr.javaTensorAlongDimension(0, 0);
            INDArray tadTest = arr.tensorAlongDimension(0, 0);
            assertEquals(javaTad, tadTest);
            //Along dimension 0: expect row vector with length 'rows'
            assertEquals("Failed on " + p.getValue(), cols * dim2, arr.tensorsAlongDimension(0));
            for (int i = 0; i < cols * dim2; i++) {
                INDArray tad = arr.tensorAlongDimension(i, 0);
                assertArrayEquals(new int[] {1, rows}, tad.shape());
                assertEquals(testValues.javaTensorAlongDimension(i, 0), tad);
            }

            //Along dimension 1: expect row vector with length 'cols'
            assertEquals(rows * dim2, arr.tensorsAlongDimension(1));
            for (int i = 0; i < rows * dim2; i++) {
                INDArray tad = arr.tensorAlongDimension(i, 1);
                assertArrayEquals(new int[] {1, cols}, tad.shape());
                assertEquals(testValues.javaTensorAlongDimension(i, 1), tad);
            }

            //Along dimension 2: expect row vector with length 'dim2'
            assertEquals(rows * cols, arr.tensorsAlongDimension(2));
            for (int i = 0; i < rows * cols; i++) {
                INDArray tad = arr.tensorAlongDimension(i, 2);
                assertArrayEquals(new int[] {1, dim2}, tad.shape());
                assertEquals(testValues.javaTensorAlongDimension(i, 2), tad);
            }
        }
    }

    @Test
    public void testTadShapes2d() {
        //Ensure TAD returns the correct/expected shapes, and values don't depend on underlying array layout/order etc

        //From a 3d array:
        int rows = 3;
        int cols = 4;
        int dim2 = 5;
        INDArray testValues = Nd4j.linspace(1, rows * cols * dim2, rows * cols * dim2, DataType.DOUBLE).reshape('c', rows, cols, dim2);
        List<Pair<INDArray, String>> list = NDArrayCreationUtil.getAll3dTestArraysWithShape(12345, new int[]{rows, cols, dim2}, DataType.DOUBLE);
        for (Pair<INDArray, String> p : list) {
            INDArray arr = p.getFirst().assign(testValues);

            //Along dimension 0,1: expect matrix with shape [rows,cols]
            assertEquals(dim2, arr.tensorsAlongDimension(0, 1));
            for (int i = 0; i < dim2; i++) {
                INDArray javaTad = arr.javaTensorAlongDimension(i, 0, 1);
                INDArray tad = arr.tensorAlongDimension(i, 0, 1);
                int javaEleStride = javaTad.elementWiseStride();
                int testTad = tad.elementWiseStride();
                assertEquals(javaEleStride, testTad);
                assertEquals(javaTad, tad);
                assertArrayEquals(new long[] {rows, cols}, tad.shape());
                assertEquals(testValues.tensorAlongDimension(i, 0, 1), tad);
            }

            //Along dimension 0,2: expect matrix with shape [rows,dim2]
            assertEquals(cols, arr.tensorsAlongDimension(0, 2));
            for (int i = 0; i < cols; i++) {
                INDArray javaTad = arr.javaTensorAlongDimension(i, 0, 2);
                INDArray tad = arr.tensorAlongDimension(i, 0, 2);
                assertEquals(javaTad, tad);
                assertArrayEquals(new long[] {rows, dim2}, tad.shape());
                assertEquals(testValues.javaTensorAlongDimension(i, 0, 2), tad);
            }

            //Along dimension 1,2: expect matrix with shape [cols,dim2]
            assertEquals(rows, arr.tensorsAlongDimension(1, 2));
            for (int i = 0; i < rows; i++) {
                INDArray tad = arr.tensorAlongDimension(i, 1, 2);
                assertArrayEquals(new long[] {cols, dim2}, tad.shape());
                assertEquals(testValues.javaTensorAlongDimension(i, 1, 2), tad);
            }
        }

        //From a 4d array:
        int dim3 = 6;
        testValues = Nd4j.linspace(1, rows * cols * dim2 * dim3, rows * cols * dim2 * dim3, DataType.DOUBLE).reshape('c', rows, cols,
                        dim2, dim3);
        list = NDArrayCreationUtil.getAll4dTestArraysWithShape(12345, new int[]{rows, cols, dim2, dim3}, DataType.DOUBLE);
        for (Pair<INDArray, String> p : list) {
            INDArray arr = p.getFirst().assign(testValues);

            //Along dimension 0,1: expect matrix with shape [rows,cols]
            assertEquals(dim2 * dim3, arr.tensorsAlongDimension(0, 1));
            for (int i = 0; i < dim2 * dim3; i++) {
                INDArray tad = arr.tensorAlongDimension(i, 0, 1);
                assertArrayEquals(new long[] {rows, cols}, tad.shape());
                assertEquals(testValues.javaTensorAlongDimension(i, 0, 1), tad);
            }

            //Along dimension 0,2: expect matrix with shape [rows,dim2]
            assertEquals(cols * dim3, arr.tensorsAlongDimension(0, 2));
            for (int i = 0; i < cols * dim3; i++) {
                INDArray tad = arr.tensorAlongDimension(i, 0, 2);
                assertArrayEquals(new long[] {rows, dim2}, tad.shape());
                assertEquals(testValues.javaTensorAlongDimension(i, 0, 2), tad);
            }

            //Along dimension 0,3: expect matrix with shape [rows,dim3]
            assertEquals(cols * dim2, arr.tensorsAlongDimension(0, 3));
            for (int i = 0; i < cols * dim2; i++) {
                INDArray tad = arr.tensorAlongDimension(i, 0, 3);
                assertArrayEquals(new long[] {rows, dim3}, tad.shape());
                assertEquals(testValues.javaTensorAlongDimension(i, 0, 3), tad);
            }


            //Along dimension 1,2: expect matrix with shape [cols,dim2]
            assertEquals(rows * dim3, arr.tensorsAlongDimension(1, 2));
            for (int i = 0; i < rows * dim3; i++) {
                INDArray tad = arr.tensorAlongDimension(i, 1, 2);
                assertArrayEquals(new long[] {cols, dim2}, tad.shape());
                assertEquals(testValues.javaTensorAlongDimension(i, 1, 2), tad);
            }

            //Along dimension 1,3: expect matrix with shape [cols,dim3]
            assertEquals(rows * dim2, arr.tensorsAlongDimension(1, 3));
            for (int i = 0; i < rows * dim2; i++) {
                INDArray tad = arr.tensorAlongDimension(i, 1, 3);
                assertArrayEquals(new long[] {cols, dim3}, tad.shape());
                assertEquals(testValues.javaTensorAlongDimension(i, 1, 3), tad);
            }

            //Along dimension 2,3: expect matrix with shape [dim2,dim3]
            assertEquals(rows * cols, arr.tensorsAlongDimension(2, 3));
            for (int i = 0; i < rows * cols; i++) {
                INDArray tad = arr.tensorAlongDimension(i, 2, 3);
                assertArrayEquals(new long[] {dim2, dim3}, tad.shape());
                assertEquals(testValues.javaTensorAlongDimension(i, 2, 3), tad);
            }
        }
    }

    @Test
    public void testTadKnownValues() {
        long[] shape = {2, 3, 4};

        INDArray arr = Nd4j.create(DataType.DOUBLE, shape);
        for (int i = 0; i < shape[0]; i++) {
            for (int j = 0; j < shape[1]; j++) {
                for (int k = 0; k < shape[2]; k++) {
                    double d = 100 * i + 10 * j + k;
                    arr.putScalar(i, j, k, d);
                }
            }
        }

        INDArray exp01_0 = Nd4j.create(new double[][] {{0, 10, 20}, {100, 110, 120}});
        INDArray exp01_1 = Nd4j.create(new double[][] {{1, 11, 21}, {101, 111, 121}});

        INDArray exp02_0 = Nd4j.create(new double[][] {{0, 1, 2, 3}, {100, 101, 102, 103}});
        INDArray exp02_1 = Nd4j.create(new double[][] {{10, 11, 12, 13}, {110, 111, 112, 113}});

        INDArray exp12_0 = Nd4j.create(new double[][] {{0, 1, 2, 3}, {10, 11, 12, 13}, {20, 21, 22, 23}});
        INDArray exp12_1 =
                        Nd4j.create(new double[][] {{100, 101, 102, 103}, {110, 111, 112, 113}, {120, 121, 122, 123}});

        assertEquals(exp01_0, arr.tensorAlongDimension(0, 0, 1));
        assertEquals(exp01_0, arr.tensorAlongDimension(0, 1, 0));
        assertEquals(exp01_1, arr.tensorAlongDimension(1, 0, 1));
        assertEquals(exp01_1, arr.tensorAlongDimension(1, 1, 0));

        assertEquals(exp02_0, arr.tensorAlongDimension(0, 0, 2));
        assertEquals(exp02_0, arr.tensorAlongDimension(0, 2, 0));
        assertEquals(exp02_1, arr.tensorAlongDimension(1, 0, 2));
        assertEquals(exp02_1, arr.tensorAlongDimension(1, 2, 0));

        assertEquals(exp12_0, arr.tensorAlongDimension(0, 1, 2));
        assertEquals(exp12_0, arr.tensorAlongDimension(0, 2, 1));
        assertEquals(exp12_1, arr.tensorAlongDimension(1, 1, 2));
        assertEquals(exp12_1, arr.tensorAlongDimension(1, 2, 1));
    }

    @Test
    public void testStalled() {
        int shape[] = new int[] {3, 3, 4, 5};
        INDArray orig2 = Nd4j.create(shape, 'c');
        System.out.println("Shape: " + Arrays.toString(orig2.shapeInfoDataBuffer().asInt()));
        INDArray tad2 = orig2.tensorAlongDimension(1, 1, 2, 3);

        log.info("You'll never see this message");
    }

    @Override
    public char ordering() {
        return 'c';
    }
}

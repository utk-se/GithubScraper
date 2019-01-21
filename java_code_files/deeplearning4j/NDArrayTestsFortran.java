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

package org.nd4j.linalg;


import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.comparison.Eps;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.checkutil.NDArrayCreationUtil;
import org.nd4j.linalg.executors.ExecutorServiceProvider;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * NDArrayTests for fortran ordering
 *
 * @author Adam Gibson
 */
@RunWith(Parameterized.class)
@Slf4j
public class NDArrayTestsFortran extends BaseNd4jTest {


    public NDArrayTestsFortran(Nd4jBackend backend) {
        super(backend);
    }

    @Before
    public void before() throws Exception {
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }



    @Test
    public void testScalarOps() {
        INDArray n = Nd4j.create(Nd4j.ones(27).data(), new long[] {3, 3, 3});
        assertEquals(27d, n.length(), 1e-1);
        n.addi(Nd4j.scalar(1d));
        n.subi(Nd4j.scalar(1.0d));
        n.muli(Nd4j.scalar(1.0d));
        n.divi(Nd4j.scalar(1.0d));

        n = Nd4j.create(Nd4j.ones(27).data(), new long[] {3, 3, 3});
        assertEquals(27, n.sumNumber().doubleValue(), 1e-1);
        INDArray a = n.slice(2);
        assertEquals(true, Arrays.equals(new long[] {3, 3}, a.shape()));

    }



    @Test
    public void testColumnMmul() {
        DataBuffer data = Nd4j.linspace(1, 10, 18, DataType.FLOAT).data();
        INDArray x2 = Nd4j.create(data, new long[] {2, 3, 3});
        data = Nd4j.linspace(1, 12, 9, DataType.FLOAT).data();
        INDArray y2 = Nd4j.create(data, new long[] {3, 3});
        INDArray z2 = Nd4j.create(DataType.FLOAT, new long[] {3, 2}, 'f');
        z2.putColumn(0, y2.getColumn(0));
        z2.putColumn(1, y2.getColumn(1));
        INDArray nofOffset = Nd4j.create(DataType.FLOAT, new long[] {3, 3}, 'f');
        nofOffset.assign(x2.slice(0));
        assertEquals(nofOffset, x2.slice(0));

        INDArray slice = x2.slice(0);
        INDArray zeroOffsetResult = slice.mmul(z2);
        INDArray offsetResult = nofOffset.mmul(z2);
        assertEquals(zeroOffsetResult, offsetResult);


        INDArray slice1 = x2.slice(1);
        INDArray noOffset2 = Nd4j.create(DataType.FLOAT, slice1.shape());
        noOffset2.assign(slice1);
        assertEquals(slice1, noOffset2);

        INDArray noOffsetResult = noOffset2.mmul(z2);
        INDArray slice1OffsetResult = slice1.mmul(z2);

        assertEquals(noOffsetResult, slice1OffsetResult);
    }


    @Test
    public void testRowVectorGemm() {
        INDArray linspace = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(1, -1).castTo(DataType.DOUBLE);
        INDArray other = Nd4j.linspace(1, 16, 16, DataType.DOUBLE).reshape(4, 4).castTo(DataType.DOUBLE);
        INDArray result = linspace.mmul(other);
        INDArray assertion = Nd4j.create(new double[] {30., 70., 110., 150.});
        assertEquals(assertion, result);
    }



    @Test
    public void testRepmat() {
        INDArray rowVector = Nd4j.create(1, 4);
        INDArray repmat = rowVector.repmat(4, 4);
        assertTrue(Arrays.equals(new long[] {4, 16}, repmat.shape()));
    }

    @Test
    public void testReadWrite() throws Exception {
        INDArray write = Nd4j.linspace(1, 4, 4, DataType.DOUBLE);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        Nd4j.write(write, dos);

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        DataInputStream dis = new DataInputStream(bis);
        INDArray read = Nd4j.read(dis);
        assertEquals(write, read);

    }


    @Test
    public void testReadWriteDouble() throws Exception {
        INDArray write = Nd4j.linspace(1, 4, 4, DataType.FLOAT);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        Nd4j.write(write, dos);

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        DataInputStream dis = new DataInputStream(bis);
        INDArray read = Nd4j.read(dis);
        assertEquals(write, read);

    }



    @Test
    public void testMultiThreading() throws Exception {
        ExecutorService ex = ExecutorServiceProvider.getExecutorService();

        List<Future<?>> list = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            Future<?> future = ex.submit(new Runnable() {
                @Override
                public void run() {
                    INDArray dot = Nd4j.linspace(1, 8, 8, DataType.DOUBLE);
                    System.out.println(Transforms.sigmoid(dot));
                }
            });
            list.add(future);
        }
        for (Future<?> future : list) {
            future.get(1, TimeUnit.MINUTES);
        }

    }


    @Test
    public void testBroadcastingGenerated() {
        int[][] broadcastShape = NDArrayCreationUtil.getRandomBroadCastShape(7, 6, 10);
        List<List<Pair<INDArray, String>>> broadCastList = new ArrayList<>(broadcastShape.length);
        for (int[] shape : broadcastShape) {
            List<Pair<INDArray, String>> arrShape = NDArrayCreationUtil.get6dPermutedWithShape(7, shape, DataType.DOUBLE);
            broadCastList.add(arrShape);
            broadCastList.add(NDArrayCreationUtil.get6dReshapedWithShape(7, shape, DataType.DOUBLE));
            broadCastList.add(NDArrayCreationUtil.getAll6dTestArraysWithShape(7, shape, DataType.DOUBLE));
        }

        for (List<Pair<INDArray, String>> b : broadCastList) {
            for (Pair<INDArray, String> val : b) {
                INDArray inputArrBroadcast = val.getFirst();
                val destShape = NDArrayCreationUtil.broadcastToShape(inputArrBroadcast.shape(), 7);
                INDArray output = inputArrBroadcast
                                .broadcast(NDArrayCreationUtil.broadcastToShape(inputArrBroadcast.shape(), 7));
                assertArrayEquals(destShape, output.shape());
            }
        }



    }

    @Test
    public void testBroadCasting() {
        INDArray first = Nd4j.arange(0, 3).reshape(3, 1).castTo(DataType.DOUBLE);
        INDArray ret = first.broadcast(3, 4);
        INDArray testRet = Nd4j.create(new double[][] {{0, 0, 0, 0}, {1, 1, 1, 1}, {2, 2, 2, 2}});
        assertEquals(testRet, ret);
        INDArray r = Nd4j.arange(0, 4).reshape(1, 4).castTo(DataType.DOUBLE);
        INDArray r2 = r.broadcast(4, 4);
        INDArray testR2 = Nd4j.create(new double[][] {{0, 1, 2, 3}, {0, 1, 2, 3}, {0, 1, 2, 3}, {0, 1, 2, 3}});
        assertEquals(testR2, r2);

    }

    @Test
    public void testOneTensor() {
        INDArray arr = Nd4j.ones(1, 1, 1, 1, 1, 1, 1);
        INDArray matrixToBroadcast = Nd4j.ones(1, 1);
        assertEquals(matrixToBroadcast.broadcast(arr.shape()), arr);
    }

    @Test
    public void testSortWithIndicesDescending() {
        INDArray toSort = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(2, 2);
        //indices,data
        INDArray[] sorted = Nd4j.sortWithIndices(toSort.dup(), 1, false);
        INDArray sorted2 = Nd4j.sort(toSort.dup(), 1, false);
        assertEquals(sorted[1], sorted2);
        INDArray shouldIndex = Nd4j.create(new double[] {1, 1, 0, 0}, new long[] {2, 2});
        assertEquals(getFailureMessage(), shouldIndex, sorted[0]);


    }


    @Test
    public void testSortWithIndices() {
        INDArray toSort = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(2, 2);
        //indices,data
        INDArray[] sorted = Nd4j.sortWithIndices(toSort.dup(), 1, true);
        INDArray sorted2 = Nd4j.sort(toSort.dup(), 1, true);
        assertEquals(sorted[1], sorted2);
        INDArray shouldIndex = Nd4j.create(new double[] {0, 0, 1, 1}, new long[] {2, 2});
        assertEquals(getFailureMessage(), shouldIndex, sorted[0]);


    }

    @Test
    public void testNd4jSortScalar() {
        INDArray linspace = Nd4j.linspace(1, 8, 8, DataType.DOUBLE).reshape(1, -1);
        INDArray sorted = Nd4j.sort(linspace, 1, false);
        System.out.println(sorted);
    }

    @Test
    public void testSwapAxesFortranOrder() {
        INDArray n = Nd4j.create(Nd4j.linspace(1, 30, 30, DataType.DOUBLE).data(), new long[] {3, 5, 2}).castTo(DataType.DOUBLE);
        for (int i = 0; i < n.slices(); i++) {
            INDArray nSlice = n.slice(i);
            for (int j = 0; j < nSlice.slices(); j++) {
                INDArray sliceJ = nSlice.slice(j);
                System.out.println(sliceJ);
            }
            System.out.println(nSlice);
        }
        INDArray slice = n.swapAxes(2, 1);
        INDArray assertion = Nd4j.create(new double[] {1, 4, 7, 10, 13});
        INDArray test = slice.slice(0).slice(0);
        assertEquals(assertion, test);
    }



    @Test
    public void testDimShuffle() {
        INDArray n = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(2, 2);
        INDArray twoOneTwo = n.dimShuffle(new Object[] {0, 'x', 1}, new int[] {0, 1}, new boolean[] {false, false});
        assertTrue(Arrays.equals(new long[] {2, 1, 2}, twoOneTwo.shape()));

        INDArray reverse = n.dimShuffle(new Object[] {1, 'x', 0}, new int[] {1, 0}, new boolean[] {false, false});
        assertTrue(Arrays.equals(new long[] {2, 1, 2}, reverse.shape()));

    }

    @Test
    public void testGetVsGetScalar() {
        INDArray a = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(2, 2);
        float element = a.getFloat(0, 1);
        double element2 = a.getDouble(0, 1);
        assertEquals(element, element2, 1e-1);
        INDArray a2 = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(2, 2);
        float element23 = a2.getFloat(0, 1);
        double element22 = a2.getDouble(0, 1);
        assertEquals(element23, element22, 1e-1);

    }

    @Test
    public void testDivide() {
        INDArray two = Nd4j.create(new float[] {2, 2, 2, 2});
        INDArray div = two.div(two);
        assertEquals(getFailureMessage(), Nd4j.ones(DataType.FLOAT, 4), div);

        INDArray half = Nd4j.create(new float[] {0.5f, 0.5f, 0.5f, 0.5f}, new long[] {2, 2});
        INDArray divi = Nd4j.create(new float[] {0.3f, 0.6f, 0.9f, 0.1f}, new long[] {2, 2});
        INDArray assertion = Nd4j.create(new float[] {1.6666666f, 0.8333333f, 0.5555556f, 5}, new long[] {2, 2});
        INDArray result = half.div(divi);
        assertEquals(getFailureMessage(), assertion, result);
    }


    @Test
    public void testSigmoid() {
        INDArray n = Nd4j.create(new float[] {1, 2, 3, 4});
        INDArray assertion = Nd4j.create(new float[] {0.73105858f, 0.88079708f, 0.95257413f, 0.98201379f});
        INDArray sigmoid = Transforms.sigmoid(n, false);
        assertEquals(getFailureMessage(), assertion, sigmoid);

    }

    @Test
    public void testNeg() {
        INDArray n = Nd4j.create(new float[] {1, 2, 3, 4});
        INDArray assertion = Nd4j.create(new float[] {-1, -2, -3, -4});
        INDArray neg = Transforms.neg(n);
        assertEquals(getFailureMessage(), assertion, neg);

    }


    @Test
    public void testCosineSim() {
        INDArray vec1 = Nd4j.create(new double[] {1, 2, 3, 4});
        INDArray vec2 = Nd4j.create(new double[] {1, 2, 3, 4});
        double sim = Transforms.cosineSim(vec1, vec2);
        assertEquals(getFailureMessage(), 1, sim, 1e-1);

        INDArray vec3 = Nd4j.create(new float[] {0.2f, 0.3f, 0.4f, 0.5f});
        INDArray vec4 = Nd4j.create(new float[] {0.6f, 0.7f, 0.8f, 0.9f});
        sim = Transforms.cosineSim(vec3, vec4);
        assertEquals(getFailureMessage(), 0.98, sim, 1e-1);

    }


    @Test
    public void testExp() {
        INDArray n = Nd4j.create(new double[] {1, 2, 3, 4});
        INDArray assertion = Nd4j.create(new double[] {2.71828183f, 7.3890561f, 20.08553692f, 54.59815003f});
        INDArray exped = Transforms.exp(n);
        assertEquals(assertion, exped);
    }



    @Test
    public void testScalar() {
        INDArray a = Nd4j.scalar(1.0f);
        assertEquals(true, a.isScalar());

        INDArray n = Nd4j.create(new float[] {1.0f}, new long[] {1, 1});
        assertEquals(n, a);
        assertTrue(n.isScalar());
    }



    @Test
    public void testWrap() {
        int[] shape = {2, 4};
        INDArray d = Nd4j.linspace(1, 8, 8, DataType.DOUBLE).reshape(shape[0], shape[1]);
        INDArray n = d;
        assertEquals(d.rows(), n.rows());
        assertEquals(d.columns(), n.columns());

        INDArray vector = Nd4j.linspace(1, 3, 3, DataType.DOUBLE);
        INDArray testVector = vector;
        for (int i = 0; i < vector.length(); i++)
            assertEquals(vector.getDouble(i), testVector.getDouble(i), 1e-1);
        assertEquals(3, testVector.length());
        assertEquals(true, testVector.isVector());
        assertEquals(true, Shape.shapeEquals(new long[] {3}, testVector.shape()));

        INDArray row12 = Nd4j.linspace(1, 2, 2, DataType.DOUBLE).reshape(2, 1);
        INDArray row22 = Nd4j.linspace(3, 4, 2, DataType.DOUBLE).reshape(1, 2);

        assertEquals(row12.rows(), 2);
        assertEquals(row12.columns(), 1);
        assertEquals(row22.rows(), 1);
        assertEquals(row22.columns(), 2);
    }

    @Test
    public void testGetRowFortran() {
        INDArray n = Nd4j.create(Nd4j.linspace(1, 4, 4, DataType.FLOAT).data(), new long[] {2, 2});
        INDArray column = Nd4j.create(new float[] {1, 3});
        INDArray column2 = Nd4j.create(new float[] {2, 4});
        INDArray testColumn = n.getRow(0);
        INDArray testColumn1 = n.getRow(1);
        assertEquals(column, testColumn);
        assertEquals(column2, testColumn1);


    }

    @Test
    public void testGetColumnFortran() {
        INDArray n = Nd4j.create(Nd4j.linspace(1, 4, 4, DataType.DOUBLE).data(), new long[] {2, 2});
        INDArray column = Nd4j.create(new double[] {1, 2});
        INDArray column2 = Nd4j.create(new double[] {3, 4});
        INDArray testColumn = n.getColumn(0);
        INDArray testColumn1 = n.getColumn(1);
        log.info("testColumn shape: {}", Arrays.toString(testColumn.shapeInfoDataBuffer().asInt()));
        assertEquals(column, testColumn);
        assertEquals(column2, testColumn1);

    }


    @Test
    public void testGetColumns() {
        INDArray matrix = Nd4j.linspace(1, 6, 6, DataType.DOUBLE).reshape(2, 3).castTo(DataType.DOUBLE);
        log.info("Original: {}", matrix);
        INDArray matrixGet = matrix.getColumns(1, 2);
        INDArray matrixAssertion = Nd4j.create(new double[][] {{3, 5}, {4, 6}});
        log.info("order A: {}", Arrays.toString(matrixAssertion.shapeInfoDataBuffer().asInt()));
        log.info("order B: {}", Arrays.toString(matrixGet.shapeInfoDataBuffer().asInt()));
        log.info("data A: {}", Arrays.toString(matrixAssertion.data().asFloat()));
        log.info("data B: {}", Arrays.toString(matrixGet.data().asFloat()));
        assertEquals(matrixAssertion, matrixGet);
    }


    @Test
    public void testVectorInit() {
        DataBuffer data = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).data();
        INDArray arr = Nd4j.create(data, new long[] {1, 4});
        assertEquals(true, arr.isRowVector());
        INDArray arr2 = Nd4j.create(data, new long[] {1, 4});
        assertEquals(true, arr2.isRowVector());

        INDArray columnVector = Nd4j.create(data, new long[] {4, 1});
        assertEquals(true, columnVector.isColumnVector());
    }


    @Test
    public void testAssignOffset() {
        INDArray arr = Nd4j.ones(5, 5);
        INDArray row = arr.slice(1);
        row.assign(1);
        assertEquals(Nd4j.ones(5), row);
    }

    @Test
    public void testColumns() {
        INDArray arr = Nd4j.create(new long[] {3, 2}).castTo(DataType.DOUBLE);
        INDArray column2 = arr.getColumn(0);
        //assertEquals(true, Shape.shapeEquals(new long[]{3, 1}, column2.shape()));
        INDArray column = Nd4j.create(new double[] {1, 2, 3}, new long[] {1, 3});
        arr.putColumn(0, column);

        INDArray firstColumn = arr.getColumn(0);

        assertEquals(column, firstColumn);


        INDArray column1 = Nd4j.create(new double[] {4, 5, 6}, new long[] {1, 3});
        arr.putColumn(1, column1);
        INDArray testRow1 = arr.getColumn(1);
        assertEquals(column1, testRow1);


        INDArray evenArr = Nd4j.create(new double[] {1, 2, 3, 4}, new long[] {2, 2});
        INDArray put = Nd4j.create(new double[] {5, 6}, new long[] {1, 2});
        evenArr.putColumn(1, put);
        INDArray testColumn = evenArr.getColumn(1);
        assertEquals(put, testColumn);


        INDArray n = Nd4j.create(Nd4j.linspace(1, 4, 4, DataType.DOUBLE).data(), new long[] {2, 2}).castTo(DataType.DOUBLE);
        INDArray column23 = n.getColumn(0);
        INDArray column12 = Nd4j.create(new double[] {1, 2}, new long[] {1, 2});
        assertEquals(column23, column12);


        INDArray column0 = n.getColumn(1);
        INDArray column01 = Nd4j.create(new double[] {3, 4}, new long[] {1, 2});
        assertEquals(column0, column01);


    }


    @Test
    public void testPutRow() {
        INDArray d = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(2, 2);
        INDArray n = d.dup();

        //works fine according to matlab, let's go with it..
        //reproduce with:  A = newShapeNoCopy(linspace(1,4,4),[2 2 ]);
        //A(1,2) % 1 index based
        float nFirst = 3;
        float dFirst = d.getFloat(0, 1);
        assertEquals(nFirst, dFirst, 1e-1);
        assertEquals(d, n);
        assertEquals(true, Arrays.equals(new long[] {2, 2}, n.shape()));

        INDArray newRow = Nd4j.linspace(5, 6, 2, DataType.DOUBLE);
        n.putRow(0, newRow);
        d.putRow(0, newRow);


        INDArray testRow = n.getRow(0);
        assertEquals(newRow.length(), testRow.length());
        assertEquals(true, Shape.shapeEquals(new long[] {1, 2}, testRow.shape()));


        INDArray nLast = Nd4j.create(Nd4j.linspace(1, 4, 4, DataType.DOUBLE).data(), new long[] {2, 2}).castTo(DataType.DOUBLE);
        INDArray row = nLast.getRow(1);
        INDArray row1 = Nd4j.create(new double[] {2, 4}, new long[] {1, 2});
        assertEquals(row, row1);


        INDArray arr = Nd4j.create(new long[] {3, 2}).castTo(DataType.DOUBLE);
        INDArray evenRow = Nd4j.create(new double[] {1, 2}, new long[] {1, 2});
        arr.putRow(0, evenRow);
        INDArray firstRow = arr.getRow(0);
        assertEquals(true, Shape.shapeEquals(new long[] {1, 2}, firstRow.shape()));
        INDArray testRowEven = arr.getRow(0);
        assertEquals(evenRow, testRowEven);


        INDArray row12 = Nd4j.create(new double[] {5, 6}, new long[] {1, 2});
        arr.putRow(1, row12);
        assertEquals(true, Shape.shapeEquals(new long[] {1, 2}, arr.getRow(0).shape()));
        INDArray testRow1 = arr.getRow(1);
        assertEquals(row12, testRow1);


        INDArray multiSliceTest = Nd4j.create(Nd4j.linspace(1, 16, 16, DataType.DOUBLE).data(), new long[] {4, 2, 2}).castTo(DataType.DOUBLE);
        INDArray test = Nd4j.create(new double[] {2, 10}, new long[] {1, 2});
        INDArray test2 = Nd4j.create(new double[] {6, 14}, new long[] {1, 2});

        INDArray multiSliceRow1 = multiSliceTest.slice(1).getRow(0);
        INDArray multiSliceRow2 = multiSliceTest.slice(1).getRow(1);

        assertEquals(test, multiSliceRow1);
        assertEquals(test2, multiSliceRow2);

    }



    @Test
    public void testInplaceTranspose() {
        INDArray test = Nd4j.rand(3, 4);
        INDArray orig = test.dup();
        INDArray transposei = test.transposei();

        for (int i = 0; i < orig.rows(); i++) {
            for (int j = 0; j < orig.columns(); j++) {
                assertEquals(orig.getDouble(i, j), transposei.getDouble(j, i), 1e-1);
            }
        }
    }



    @Test
    public void testMmulF() {

        DataBuffer data = Nd4j.linspace(1, 10, 10, DataType.DOUBLE).data();
        INDArray n = Nd4j.create(data, new long[] {1, 10});
        INDArray transposed = n.transpose();
        assertEquals(true, n.isRowVector());
        assertEquals(true, transposed.isColumnVector());


        INDArray innerProduct = n.mmul(transposed);

        INDArray scalar = Nd4j.scalar(385.0);
        assertEquals(getFailureMessage(), scalar, innerProduct);


    }



    @Test
    public void testRowsColumns() {
        DataBuffer data = Nd4j.linspace(1, 6, 6, DataType.DOUBLE).data();
        INDArray rows = Nd4j.create(data, new long[] {2, 3});
        assertEquals(2, rows.rows());
        assertEquals(3, rows.columns());

        INDArray columnVector = Nd4j.create(data, new long[] {6, 1});
        assertEquals(6, columnVector.rows());
        assertEquals(1, columnVector.columns());
        INDArray rowVector = Nd4j.create(data, new long[] {1, 6});
        assertEquals(1, rowVector.rows());
        assertEquals(6, rowVector.columns());
    }


    @Test
    public void testTranspose() {
        INDArray n = Nd4j.create(Nd4j.ones(100).castTo(DataType.DOUBLE).data(), new long[] {5, 5, 4});
        INDArray transpose = n.transpose();
        assertEquals(n.length(), transpose.length());
        assertEquals(true, Arrays.equals(new long[] {4, 5, 5}, transpose.shape()));

        INDArray rowVector = Nd4j.linspace(1, 10, 10, DataType.DOUBLE).reshape(1, -1);
        assertTrue(rowVector.isRowVector());
        INDArray columnVector = rowVector.transpose();
        assertTrue(columnVector.isColumnVector());


        INDArray linspaced = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(2, 2);
        INDArray transposed = Nd4j.create(new double[] {1, 3, 2, 4}, new long[] {2, 2});
        assertEquals(transposed, linspaced.transpose());

        linspaced = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(2, 2);
        //fortran ordered
        INDArray transposed2 = Nd4j.create(new double[] {1, 3, 2, 4}, new long[] {2, 2});
        transposed = linspaced.transpose();
        assertEquals(transposed, transposed2);


    }



    @Test
    public void testAddMatrix() {
        INDArray five = Nd4j.ones(5);
        five.addi(five.dup());
        INDArray twos = Nd4j.valueArrayOf(5, 2);
        assertEquals(getFailureMessage(), twos, five);

    }



    @Test
    public void testMMul() {
        INDArray arr = Nd4j.create(new double[][] {{1, 2, 3}, {4, 5, 6}});

        INDArray assertion = Nd4j.create(new double[][] {{14, 32}, {32, 77}});

        INDArray test = arr.mmul(arr.transpose());
        assertEquals(getFailureMessage(), assertion, test);

    }

    @Test
    public void testPutSlice() {
        INDArray n = Nd4j.linspace(1, 27, 27, DataType.DOUBLE).reshape(3, 3, 3);
        INDArray newSlice = Nd4j.create(DataType.DOUBLE, 3, 3);
        n.putSlice(0, newSlice);
        assertEquals(getFailureMessage(), newSlice, n.slice(0));

    }

    @Test
    public void testRowVectorMultipleIndices() {
        INDArray linear = Nd4j.create(DataType.DOUBLE, 1, 4);
        linear.putScalar(new long[] {0, 1}, 1);
        assertEquals(getFailureMessage(), linear.getDouble(0, 1), 1, 1e-1);
    }



    @Test
    public void testDim1() {
        INDArray sum = Nd4j.linspace(1, 2, 2, DataType.DOUBLE).reshape(2, 1);
        INDArray same = sum.dup();
        assertEquals(same.sum(1), sum);
    }


    @Test
    public void testEps() {
        val ones = Nd4j.ones(5);
        val res = Nd4j.createUninitialized(DataType.BOOL, 5);
        assertTrue(Nd4j.getExecutioner().exec(new Eps(ones, ones, res)).all());
    }


    @Test
    public void testLogDouble() {
        INDArray linspace = Nd4j.linspace(1, 6, 6, DataType.DOUBLE).castTo(DataType.DOUBLE);
        INDArray log = Transforms.log(linspace);
        INDArray assertion = Nd4j.create(new double[] {0, 0.6931471805599453, 1.0986122886681098, 1.3862943611198906, 1.6094379124341005, 1.791759469228055});
        assertEquals(assertion, log);
    }

    @Test
    public void testVectorSum() {
        INDArray lin = Nd4j.linspace(1, 4, 4, DataType.DOUBLE);
        assertEquals(10.0, lin.sumNumber().doubleValue(), 1e-1);

    }

    @Test
    public void testVectorSum2() {
        INDArray lin = Nd4j.create(new double[] {1, 2, 3, 4});
        assertEquals(10.0, lin.sumNumber().doubleValue(), 1e-1);

    }

    @Test
    public void testVectorSum3() {
        INDArray lin = Nd4j.create(new double[] {1, 2, 3, 4});
        INDArray lin2 = Nd4j.create(new double[] {1, 2, 3, 4});
        assertEquals(lin, lin2);
    }

    @Test
    public void testSmallSum() {
        INDArray base = Nd4j.create(new double[] {5.843333333333335, 3.0540000000000007});
        base.addi(1e-12);
        INDArray assertion = Nd4j.create(new double[] {5.84333433, 3.054001});
        assertEquals(assertion, base);

    }



    @Test
    public void testPermute() {
        INDArray n = Nd4j.create(Nd4j.linspace(1, 20, 20, DataType.DOUBLE).data(), new long[] {5, 4});
        INDArray transpose = n.transpose();
        INDArray permute = n.permute(1, 0);
        assertEquals(permute, transpose);
        assertEquals(transpose.length(), permute.length(), 1e-1);


        INDArray toPermute = Nd4j.create(Nd4j.linspace(0, 7, 8, DataType.DOUBLE).data(), new long[] {2, 2, 2});
        INDArray permuted = toPermute.permute(2, 1, 0);
        assertNotEquals(toPermute, permuted);

        INDArray permuteOther = toPermute.permute(1, 2, 0);
        for (int i = 0; i < permuteOther.slices(); i++) {
            INDArray toPermutesliceI = toPermute.slice(i);
            INDArray permuteOtherSliceI = permuteOther.slice(i);
            permuteOtherSliceI.toString();
            assertNotEquals(toPermutesliceI, permuteOtherSliceI);
        }
        assertArrayEquals(permuteOther.shape(), toPermute.shape());
        assertNotEquals(toPermute, permuteOther);


    }



    @Test
    public void testAppendBias() {
        INDArray rand = Nd4j.linspace(1, 25, 25, DataType.DOUBLE).reshape(1, -1).transpose();
        INDArray test = Nd4j.appendBias(rand);
        INDArray assertion = Nd4j.toFlattened(rand, Nd4j.scalar(DataType.DOUBLE, 1.0)).reshape(-1, 1);
        assertEquals(assertion, test);
    }

    @Test
    public void testRand() {
        INDArray rand = Nd4j.randn(5, 5);
        Nd4j.getDistributions().createUniform(0.4, 4).sample(5);
        Nd4j.getDistributions().createNormal(1, 5).sample(10);
        //Nd4j.getDistributions().createBinomial(5, 1.0).sample(new long[]{5, 5});
        //Nd4j.getDistributions().createBinomial(1, Nd4j.ones(5, 5)).sample(rand.shape());
        Nd4j.getDistributions().createNormal(rand, 1).sample(rand.shape());
    }



    @Test
    public void testIdentity() {
        INDArray eye = Nd4j.eye(5);
        assertTrue(Arrays.equals(new long[] {5, 5}, eye.shape()));
        eye = Nd4j.eye(5);
        assertTrue(Arrays.equals(new long[] {5, 5}, eye.shape()));


    }


    @Test
    public void testColumnVectorOpsFortran() {
        INDArray twoByTwo = Nd4j.create(new float[] {1, 2, 3, 4}, new long[] {2, 2});
        INDArray toAdd = Nd4j.create(new float[] {1, 2}, new long[] {2, 1});
        twoByTwo.addiColumnVector(toAdd);
        INDArray assertion = Nd4j.create(new float[] {2, 4, 4, 6}, new long[] {2, 2});
        assertEquals(assertion, twoByTwo);
    }



    @Test
    public void testRSubi() {
        INDArray n2 = Nd4j.ones(2);
        INDArray n2Assertion = Nd4j.zeros(2);
        INDArray nRsubi = n2.rsubi(1);
        assertEquals(n2Assertion, nRsubi);
    }



    @Test
    public void testAssign() {
        INDArray vector = Nd4j.linspace(1, 5, 5, DataType.DOUBLE);
        vector.assign(1);
        assertEquals(Nd4j.ones(5).castTo(DataType.DOUBLE), vector);
        INDArray twos = Nd4j.ones(2, 2);
        INDArray rand = Nd4j.rand(2, 2);
        twos.assign(rand);
        assertEquals(rand, twos);

        INDArray tensor = Nd4j.rand(DataType.DOUBLE, 3, 3, 3);
        INDArray ones = Nd4j.ones(3, 3, 3).castTo(DataType.DOUBLE);
        assertTrue(Arrays.equals(tensor.shape(), ones.shape()));
        ones.assign(tensor);
        assertEquals(tensor, ones);
    }

    @Test
    public void testAddScalar() {
        INDArray div = Nd4j.valueArrayOf(new long[] {1, 4}, 4.0);
        INDArray rdiv = div.add(1);
        INDArray answer = Nd4j.valueArrayOf(new long[] {1, 4}, 5.0);
        assertEquals(answer, rdiv);
    }

    @Test
    public void testRdivScalar() {
        INDArray div = Nd4j.valueArrayOf(new long[] {1, 4}, 4.0);
        INDArray rdiv = div.rdiv(1);
        INDArray answer = Nd4j.valueArrayOf(new long[] {1, 4}, 0.25);
        assertEquals(rdiv, answer);
    }

    @Test
    public void testRDivi() {
        INDArray n2 = Nd4j.valueArrayOf(new long[] {1, 2}, 4.0);
        INDArray n2Assertion = Nd4j.valueArrayOf(new long[] {1, 2}, 0.5);
        INDArray nRsubi = n2.rdivi(2);
        assertEquals(n2Assertion, nRsubi);
    }



    @Test
    public void testNumVectorsAlongDimension() {
        INDArray arr = Nd4j.linspace(1, 24, 24, DataType.DOUBLE).reshape(4, 3, 2);
        assertEquals(12, arr.vectorsAlongDimension(2));
    }



    @Test
    public void testBroadCast() {
        INDArray n = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(1, -1);
        INDArray broadCasted = n.broadcast(5, 4);
        for (int i = 0; i < broadCasted.rows(); i++) {
            assertEquals(n, broadCasted.getRow(i));
        }

        INDArray broadCast2 = broadCasted.getRow(0).broadcast(5, 4);
        assertEquals(broadCasted, broadCast2);


        INDArray columnBroadcast = n.transpose().broadcast(4, 5);
        for (int i = 0; i < columnBroadcast.columns(); i++) {
            assertEquals(columnBroadcast.getColumn(i), n.transpose());
        }

        INDArray fourD = Nd4j.create(1, 2, 1, 1);
        INDArray broadCasted3 = fourD.broadcast(1, 2, 36, 36);
        assertTrue(Arrays.equals(new long[] {1, 2, 36, 36}, broadCasted3.shape()));
    }

    @Test
    public void testMatrix() {
        INDArray arr = Nd4j.create(new double[] {1, 2, 3, 4}, new long[] {2, 2});
        INDArray brr = Nd4j.create(new double[] {5, 6}, new long[] {1, 2});
        INDArray row = arr.getRow(0);
        row.subi(brr);
        assertEquals(Nd4j.create(new double[] {-4, -3}), arr.getRow(0));

    }

    @Test
    public void testPutRowGetRowOrdering() {
        INDArray row1 = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(2, 2);
        INDArray put = Nd4j.create(new double[] {5, 6});
        row1.putRow(1, put);

        System.out.println(row1);

        INDArray row1Fortran = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(2, 2);
        INDArray putFortran = Nd4j.create(new double[] {5, 6});
        row1Fortran.putRow(1, putFortran);
        assertEquals(row1, row1Fortran);
        INDArray row1CTest = row1.getRow(1);
        INDArray row1FortranTest = row1Fortran.getRow(1);
        assertEquals(row1CTest, row1FortranTest);



    }


    @Test
    public void testSumWithRow1() {
        //Works:
        INDArray array2d = Nd4j.ones(1, 10);
        array2d.sum(0); //OK
        array2d.sum(1); //OK

        INDArray array3d = Nd4j.ones(1, 10, 10);
        array3d.sum(0); //OK
        array3d.sum(1); //OK
        array3d.sum(2); //java.lang.IllegalArgumentException: Illegal index 100 derived from 9 with offset of 10 and stride of 10

        INDArray array4d = Nd4j.ones(1, 10, 10, 10);
        INDArray sum40 = array4d.sum(0); //OK
        INDArray sum41 = array4d.sum(1); //OK
        INDArray sum42 = array4d.sum(2); //java.lang.IllegalArgumentException: Illegal index 1000 derived from 9 with offset of 910 and stride of 10
        INDArray sum43 = array4d.sum(3); //java.lang.IllegalArgumentException: Illegal index 1000 derived from 9 with offset of 100 and stride of 100

        System.out.println("40: " + sum40.length());
        System.out.println("41: " + sum41.length());
        System.out.println("42: " + sum42.length());
        System.out.println("43: " + sum43.length());

        INDArray array5d = Nd4j.ones(1, 10, 10, 10, 10);
        array5d.sum(0); //OK
        array5d.sum(1); //OK
        array5d.sum(2); //java.lang.IllegalArgumentException: Illegal index 10000 derived from 9 with offset of 9910 and stride of 10
        array5d.sum(3); //java.lang.IllegalArgumentException: Illegal index 10000 derived from 9 with offset of 9100 and stride of 100
        array5d.sum(4); //java.lang.IllegalArgumentException: Illegal index 10000 derived from 9 with offset of 1000 and stride of 1000
    }

    @Test
    public void testSumWithRow2() {
        //All sums in this method execute without exceptions.
        INDArray array3d = Nd4j.ones(2, 10, 10);
        array3d.sum(0);
        array3d.sum(1);
        array3d.sum(2);

        INDArray array4d = Nd4j.ones(2, 10, 10, 10);
        array4d.sum(0);
        array4d.sum(1);
        array4d.sum(2);
        array4d.sum(3);

        INDArray array5d = Nd4j.ones(2, 10, 10, 10, 10);
        array5d.sum(0);
        array5d.sum(1);
        array5d.sum(2);
        array5d.sum(3);
        array5d.sum(4);
    }


    @Test
    public void testPutRowFortran() {
        INDArray row1 = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(2, 2).castTo(DataType.DOUBLE);
        INDArray put = Nd4j.create(new double[] {5, 6});
        row1.putRow(1, put);


        INDArray row1Fortran = Nd4j.create(new double[][] {{1, 3}, {2, 4}});
        INDArray putFortran = Nd4j.create(new double[] {5, 6});
        row1Fortran.putRow(1, putFortran);
        assertEquals(row1, row1Fortran);



    }


    @Test
    public void testElementWiseOps() {
        INDArray n1 = Nd4j.scalar(1);
        INDArray n2 = Nd4j.scalar(2);
        INDArray nClone = n1.add(n2);
        assertEquals(Nd4j.scalar(3), nClone);
        INDArray n1PlusN2 = n1.add(n2);
        assertFalse(getFailureMessage(), n1PlusN2.equals(n1));

        INDArray n3 = Nd4j.scalar(3.0);
        INDArray n4 = Nd4j.scalar(4.0);
        INDArray subbed = n4.sub(n3);
        INDArray mulled = n4.mul(n3);
        INDArray div = n4.div(n3);

        assertFalse(subbed.equals(n4));
        assertFalse(mulled.equals(n4));
        assertEquals(Nd4j.scalar(1.0), subbed);
        assertEquals(Nd4j.scalar(12.0), mulled);
        assertEquals(Nd4j.scalar(1.333333333333333333333), div);
    }


    @Test
    public void testRollAxis() {
        INDArray toRoll = Nd4j.ones(3, 4, 5, 6);
        assertArrayEquals(new long[] {3, 6, 4, 5}, Nd4j.rollAxis(toRoll, 3, 1).shape());
        val shape = Nd4j.rollAxis(toRoll, 3).shape();
        assertArrayEquals(new long[] {6, 3, 4, 5}, shape);
    }

    @Test
    public void testTensorDot() {
        INDArray oneThroughSixty = Nd4j.arange(60).reshape('f', 3, 4, 5);
        INDArray oneThroughTwentyFour = Nd4j.arange(24).reshape('f', 4, 3, 2);
        INDArray result = Nd4j.tensorMmul(oneThroughSixty, oneThroughTwentyFour, new int[][] {{1, 0}, {0, 1}});
        assertArrayEquals(new long[] {5, 2}, result.shape());
        INDArray assertion = Nd4j.create(new double[][] {{440., 1232.}, {1232., 3752.}, {2024., 6272.}, {2816., 8792.},
                        {3608., 11312.}});
        assertEquals(assertion, result);

    }


    @Test
    public void testNegativeShape() {
        INDArray linspace = Nd4j.linspace(1, 4, 4, DataType.DOUBLE);
        INDArray reshaped = linspace.reshape(-1, 2);
        assertArrayEquals(new long[] {2, 2}, reshaped.shape());

        INDArray linspace6 = Nd4j.linspace(1, 6, 6, DataType.DOUBLE);
        INDArray reshaped2 = linspace6.reshape(-1, 3);
        assertArrayEquals(new long[] {2, 3}, reshaped2.shape());

    }

    @Test
    public void testGetColumnGetRow() {
        INDArray row = Nd4j.ones(5).reshape(1, -1);
        for (int i = 0; i < 5; i++) {
            INDArray col = row.getColumn(i);
            assertArrayEquals(col.shape(), new long[] {});
        }

        INDArray col = Nd4j.ones(5, 1);
        for (int i = 0; i < 5; i++) {
            INDArray row2 = col.getRow(i);
            assertArrayEquals(row2.shape(), new long[] {1, 1});
        }
    }

    @Test
    public void testDupAndDupWithOrder() {
        List<Pair<INDArray, String>> testInputs = NDArrayCreationUtil.getAllTestMatricesWithShape(4, 5, 123, DataType.DOUBLE);
        int count = 0;
        for (Pair<INDArray, String> pair : testInputs) {
            String msg = pair.getSecond();
            INDArray in = pair.getFirst();
            System.out.println("Count " + count);
            INDArray dup = in.dup();
            INDArray dupc = in.dup('c');
            INDArray dupf = in.dup('f');

            assertEquals(msg, in, dup);
            assertEquals(msg, dup.ordering(), (char) Nd4j.order());
            assertEquals(msg, dupc.ordering(), 'c');
            assertEquals(msg, dupf.ordering(), 'f');
            assertEquals(msg, in, dupc);
            assertEquals(msg, in, dupf);
            count++;
        }
    }

    @Test
    public void testToOffsetZeroCopy() {
        List<Pair<INDArray, String>> testInputs = NDArrayCreationUtil.getAllTestMatricesWithShape(4, 5, 123, DataType.DOUBLE);

        int cnt = 0;
        for (Pair<INDArray, String> pair : testInputs) {
            String msg = pair.getSecond();
            INDArray in = pair.getFirst();
            INDArray dup = Shape.toOffsetZeroCopy(in);
            INDArray dupc = Shape.toOffsetZeroCopy(in, 'c');
            INDArray dupf = Shape.toOffsetZeroCopy(in, 'f');
            INDArray dupany = Shape.toOffsetZeroCopyAnyOrder(in);

            assertEquals(msg + ": " + cnt, in, dup);
            assertEquals(msg, in, dupc);
            assertEquals(msg, in, dupf);
            assertEquals(msg, dupc.ordering(), 'c');
            assertEquals(msg, dupf.ordering(), 'f');
            assertEquals(msg, in, dupany);

            assertEquals(dup.offset(), 0);
            assertEquals(dupc.offset(), 0);
            assertEquals(dupf.offset(), 0);
            assertEquals(dupany.offset(), 0);
            assertEquals(dup.length(), dup.data().length());
            assertEquals(dupc.length(), dupc.data().length());
            assertEquals(dupf.length(), dupf.data().length());
            assertEquals(dupany.length(), dupany.data().length());
            cnt++;
        }
    }


    @Override
    public char ordering() {
        return 'f';
    }
}

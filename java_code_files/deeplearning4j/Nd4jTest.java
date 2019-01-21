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

package org.nd4j.linalg.factory;

import lombok.val;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.Pointer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.nd4j.linalg.BaseNd4jTest;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.checkutil.NDArrayCreationUtil;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.linalg.util.ArrayUtil;
import org.nd4j.nativeblas.NativeOpsHolder;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 */
@RunWith(Parameterized.class)
public class Nd4jTest extends BaseNd4jTest {
    public Nd4jTest(Nd4jBackend backend) {
        super(backend);
    }

    @Test
    public void testRandShapeAndRNG() {
        INDArray ret = Nd4j.rand(new int[] {4, 2}, Nd4j.getRandomFactory().getNewRandomInstance(123));
        INDArray ret2 = Nd4j.rand(new int[] {4, 2}, Nd4j.getRandomFactory().getNewRandomInstance(123));

        assertEquals(ret, ret2);
    }

    @Test
    public void testRandShapeAndMinMax() {
        INDArray ret = Nd4j.rand(new int[] {4, 2}, -0.125f, 0.125f, Nd4j.getRandomFactory().getNewRandomInstance(123));
        INDArray ret2 = Nd4j.rand(new int[] {4, 2}, -0.125f, 0.125f, Nd4j.getRandomFactory().getNewRandomInstance(123));
        assertEquals(ret, ret2);
    }

    @Test
    public void testCreateShape() {
        INDArray ret = Nd4j.create(new int[] {4, 2});

        assertEquals(ret.length(), 8);
    }

    @Test
    public void testCreateFromList() {
        List<Double> doubles = Arrays.asList(1.0, 2.0);
        INDArray NdarrayDobules = Nd4j.create(doubles);

        assertEquals((Double)NdarrayDobules.getDouble(0),doubles.get(0));
        assertEquals((Double)NdarrayDobules.getDouble(1),doubles.get(1));

        List<Float> floats = Arrays.asList(3.0f, 4.0f);
        INDArray NdarrayFloats = Nd4j.create(floats);
        assertEquals((Float)NdarrayFloats.getFloat(0),floats.get(0));
        assertEquals((Float)NdarrayFloats.getFloat(1),floats.get(1));
    }

    @Test
    public void testGetRandom() {
        Random r = Nd4j.getRandom();
        Random t = Nd4j.getRandom();

        assertEquals(r, t);
    }

    @Test
    public void testGetRandomSetSeed() {
        Random r = Nd4j.getRandom();
        Random t = Nd4j.getRandom();

        assertEquals(r, t);
        r.setSeed(123);
        assertEquals(r, t);
    }

    @Test
    public void testOrdering() {
        INDArray fNDArray = Nd4j.create(new float[] {1f}, NDArrayFactory.FORTRAN);
        assertEquals(NDArrayFactory.FORTRAN, fNDArray.ordering());
        INDArray cNDArray = Nd4j.create(new float[] {1f}, NDArrayFactory.C);
        assertEquals(NDArrayFactory.C, cNDArray.ordering());
    }

    @Override
    public char ordering() {
        return 'c';
    }


    @Test
    public void testMean() {
        INDArray data = Nd4j.create(new double[] {4., 4., 4., 4., 8., 8., 8., 8., 4., 4., 4., 4., 8., 8., 8., 8., 4.,
                        4., 4., 4., 8., 8., 8., 8., 4., 4., 4., 4., 8., 8., 8., 8, 2., 2., 2., 2., 4., 4., 4., 4., 2.,
                        2., 2., 2., 4., 4., 4., 4., 2., 2., 2., 2., 4., 4., 4., 4., 2., 2., 2., 2., 4., 4., 4., 4.},
                new int[] {2, 2, 4, 4});

        INDArray actualResult = data.mean(0);
        INDArray expectedResult = Nd4j.create(new double[] {3., 3., 3., 3., 6., 6., 6., 6., 3., 3., 3., 3., 6., 6., 6.,
                6., 3., 3., 3., 3., 6., 6., 6., 6., 3., 3., 3., 3., 6., 6., 6., 6.}, new int[] {2, 4, 4});
        assertEquals(getFailureMessage(), expectedResult, actualResult);
    }


    @Test
    public void testVar() {
        INDArray data = Nd4j.create(new double[] {4., 4., 4., 4., 8., 8., 8., 8., 4., 4., 4., 4., 8., 8., 8., 8., 4.,
                        4., 4., 4., 8., 8., 8., 8., 4., 4., 4., 4., 8., 8., 8., 8, 2., 2., 2., 2., 4., 4., 4., 4., 2.,
                        2., 2., 2., 4., 4., 4., 4., 2., 2., 2., 2., 4., 4., 4., 4., 2., 2., 2., 2., 4., 4., 4., 4.},
                new long[] {2, 2, 4, 4});

        INDArray actualResult = data.var(false, 0);
        INDArray expectedResult = Nd4j.create(new double[] {1., 1., 1., 1., 4., 4., 4., 4., 1., 1., 1., 1., 4., 4., 4.,
                4., 1., 1., 1., 1., 4., 4., 4., 4., 1., 1., 1., 1., 4., 4., 4., 4.}, new long[] {2, 4, 4});
        assertEquals(getFailureMessage(), expectedResult, actualResult);
    }

    @Test
    public void testVar2() {
        INDArray arr = Nd4j.linspace(1, 6, 6, DataType.DOUBLE).reshape(2, 3);
        INDArray var = arr.var(false, 0);
        assertEquals(Nd4j.create(new double[] {2.25, 2.25, 2.25}), var);
    }

    @Test
    public void testExpandDims(){
        final List<Pair<INDArray, String>> testMatricesC = NDArrayCreationUtil.getAllTestMatricesWithShape('c', 3, 5, 0xDEAD, DataType.DOUBLE);
        final List<Pair<INDArray, String>> testMatricesF = NDArrayCreationUtil.getAllTestMatricesWithShape('f', 7, 11, 0xBEEF, DataType.DOUBLE);

        final ArrayList<Pair<INDArray, String>> testMatrices = new ArrayList<>(testMatricesC);
        testMatrices.addAll(testMatricesF);

        for (Pair<INDArray, String> testMatrixPair : testMatrices) {
            final String recreation = testMatrixPair.getSecond();
            final INDArray testMatrix = testMatrixPair.getFirst();
            final char ordering = testMatrix.ordering();
            val shape = testMatrix.shape();
            final int rank = testMatrix.rank();
            for (int i = -rank; i <= rank; i++) {
                final INDArray expanded = Nd4j.expandDims(testMatrix, i);

                final String message = "Expanding in Dimension " + i + "; Shape before expanding: " + Arrays.toString(shape) + " "+ordering+" Order; Shape after expanding: " + Arrays.toString(expanded.shape()) +  " "+expanded.ordering()+"; Input Created via: " + recreation;

                val tmR = testMatrix.ravel();
                val expR = expanded.ravel();
                assertEquals(message, 1, expanded.shape()[i < 0 ? i + rank : i]);
                assertEquals(message, tmR, expR);
                assertEquals(message, ordering,  expanded.ordering());

                testMatrix.assign(Nd4j.rand(DataType.DOUBLE, shape));
                assertEquals(message, testMatrix.ravel(), expanded.ravel());
            }
        }
    }
    @Test
    public void testSqueeze(){
        final List<Pair<INDArray, String>> testMatricesC = NDArrayCreationUtil.getAllTestMatricesWithShape('c', 3, 1, 0xDEAD, DataType.DOUBLE);
        final List<Pair<INDArray, String>> testMatricesF = NDArrayCreationUtil.getAllTestMatricesWithShape('f', 7, 1, 0xBEEF, DataType.DOUBLE);

        final ArrayList<Pair<INDArray, String>> testMatrices = new ArrayList<>(testMatricesC);
        testMatrices.addAll(testMatricesF);

        for (Pair<INDArray, String> testMatrixPair : testMatrices) {
            final String recreation = testMatrixPair.getSecond();
            final INDArray testMatrix = testMatrixPair.getFirst();
            final char ordering = testMatrix.ordering();
            val shape = testMatrix.shape();
            final INDArray squeezed = Nd4j.squeeze(testMatrix, 1);
            final long[] expShape = ArrayUtil.removeIndex(shape, 1);
            final String message = "Squeezing in dimension 1; Shape before squeezing: " + Arrays.toString(shape) + " " + ordering + " Order; Shape after expanding: " + Arrays.toString(squeezed.shape()) +  " "+squeezed.ordering()+"; Input Created via: " + recreation;

            assertArrayEquals(message, expShape, squeezed.shape());
            assertEquals(message, ordering, squeezed.ordering());
            assertEquals(message, testMatrix.ravel(), squeezed.ravel());

            testMatrix.assign(Nd4j.rand(shape));
            assertEquals(message, testMatrix.ravel(), squeezed.ravel());

        }
    }


    @Test
    public void testNumpyConversion() throws Exception {
        INDArray linspace = Nd4j.linspace(1,4,4, DataType.FLOAT);
        Pointer convert = Nd4j.getNDArrayFactory().convertToNumpy(linspace);
        convert.position(0);

        Pointer pointer = NativeOpsHolder.getInstance().getDeviceNativeOps().loadNpyFromHeader(convert);
        Pointer pointer1 = NativeOpsHolder.getInstance().getDeviceNativeOps().dataPointForNumpyStruct(pointer);
        pointer1.capacity(linspace.data().getElementSize() * linspace.data().length());
        ByteBuffer byteBuffer = linspace.data().pointer().asByteBuffer();
        byte[] originalData = new byte[byteBuffer.capacity()];
        byteBuffer.get(originalData);


        ByteBuffer floatBuffer = pointer1.asByteBuffer();
        byte[] dataTwo = new byte[floatBuffer.capacity()];
        floatBuffer.get(dataTwo);
        Assert.assertArrayEquals(originalData,dataTwo);
        floatBuffer.position(0);

        DataBuffer dataBuffer = Nd4j.createBuffer(new FloatPointer(floatBuffer.asFloatBuffer()),linspace.length(), DataType.FLOAT);
        assertArrayEquals(new float[]{1,2,3,4}, dataBuffer.asFloat(), 1e-5f);

        INDArray convertedFrom = Nd4j.getNDArrayFactory().createFromNpyHeaderPointer(convert);
        assertEquals(linspace,convertedFrom);

        File tmpFile = new File(System.getProperty("java.io.tmpdir"),"nd4j-numpy-tmp-" + UUID.randomUUID().toString() + ".bin");
        tmpFile.deleteOnExit();
        Nd4j.writeAsNumpy(linspace,tmpFile);

        INDArray numpyFromFile = Nd4j.createFromNpyFile(tmpFile);
        assertEquals(linspace,numpyFromFile);

    }



    @Test
    public void testNumpyWrite() throws Exception {
        INDArray linspace = Nd4j.linspace(1,4,4);
        File tmpFile = new File(System.getProperty("java.io.tmpdir"),"nd4j-numpy-tmp-" + UUID.randomUUID().toString() + ".bin");
        tmpFile.deleteOnExit();
        Nd4j.writeAsNumpy(linspace,tmpFile);

        INDArray numpyFromFile = Nd4j.createFromNpyFile(tmpFile);
        assertEquals(linspace,numpyFromFile);

    }

    @Test
    public void testNpyByteArray() throws Exception {
        INDArray linspace = Nd4j.linspace(1,4,4);
        byte[] bytes = Nd4j.toNpyByteArray(linspace);
        INDArray fromNpy = Nd4j.createNpyFromByteArray(bytes);
        assertEquals(linspace,fromNpy);

    }



}


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

package jcuda.jcublas.ops;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.nd4j.jita.allocator.enums.AllocationStatus;
import org.nd4j.jita.conf.Configuration;
import org.nd4j.jita.conf.CudaEnvironment;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.*;
import org.nd4j.linalg.convolution.Convolution;
import org.nd4j.linalg.convolution.OldConvolution;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @author raver119@gmail.com
 */
@Ignore
public class CudaTransformsTests {

    @Before
    public void setUp() {
        CudaEnvironment.getInstance().getConfiguration()
                .setFirstMemory(AllocationStatus.DEVICE)
                .setExecutionModel(Configuration.ExecutionModel.ASYNCHRONOUS)
                .setAllocationModel(Configuration.AllocationModel.CACHE_ALL)
                .setMaximumSingleDeviceAllocation(1024 * 1024 * 1024L)
                .setMaximumBlockSize(128)
                .setMaximumGridSize(256)
                .enableDebug(false)
                .setVerbose(false);



        System.out.println("Init called");
    }

    @Test
    public void testPinnedCosine() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new Cos(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(0.53f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedAbs() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new Abs(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(1.01f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedCeil() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new Ceil(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(2.0f, array2.getFloat(0), 0.01);

    }

    @Test
    public void testPinnedExp() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new Exp(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(2.75f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedExp2() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{0.9f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f});


        Nd4j.getExecutioner().exec(new Exp(array1));

        System.out.println("Array1: " + array1);

        assertEquals(2.45f, array1.getFloat(0), 0.01);
        assertEquals(1.0f, array1.getFloat(1), 0.01);
    }

    @Test
    public void testPinnedFloor() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new Floor(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(1.0f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedLog() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

         INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new Log(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(0.01f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedPow() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        Nd4j.getExecutioner().exec(new Pow(array1, 3));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(1.03f, array1.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedSetRange() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new SetRange(array1, 0.1f, 1.0f));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(0.1f, array1.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedSigmoid() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new Sigmoid(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(0.73f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedSign() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{-1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new Sign(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(1.0f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedSin() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new Sin(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(0.85f, array2.getFloat(0), 0.01);

    }

    @Test
    public void testPinnedSoftplus() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new SoftPlus(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(1.32f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedSqrt() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{2.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new Sqrt(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(1.42f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedTanh() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new Tanh(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(0.77f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedAcos() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{0.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new ACos(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(1.56f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedASin() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        //INDArray array1 = Nd4j.create(new float[]{0.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        //INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});

        INDArray array1 = Nd4j.create(new float[]{0.01f, 1.01f, });
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, });

        Nd4j.getExecutioner().exec(new ASin(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(0.01f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedATan() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new ATan(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(0.79f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedNegative() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new Negative(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(-1.01f, array2.getFloat(0), 0.01);
    }

    @Test
    public void testPinnedCosineF() throws Exception {
        // simple way to stop test if we're not on CUDA backend here
        assertEquals("JcublasLevel1", Nd4j.getBlasWrapper().level1().getClass().getSimpleName());

        INDArray array1 = Nd4j.create(new float[]{1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f, 1.01f});//.dup('f');
        INDArray array2 = Nd4j.create(new float[]{1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f});


        Nd4j.getExecutioner().exec(new Cos(array1, array2));

        System.out.println("Array1: " + array1);
        System.out.println("Array2: " + array2);

        assertEquals(0.53f, array2.getFloat(0), 0.01);
    }

     @Test
    public void testSoftmaxFC()  throws Exception {
        INDArray array1 = Nd4j.ones(2048).dup('f');
        INDArray array2 = Nd4j.zeros(2048);

        Nd4j.getExecutioner().exec(new OldSoftMax(array1));

        Nd4j.getExecutioner().exec(new OldSoftMax(array2));

        System.out.println("Array1: " + Arrays.toString(array1.data().asFloat()));
        System.out.println("Array2: " + Arrays.toString(array2.data().asFloat()));

        //assertEquals(array1, array2);

        assertEquals(1.0, array1.sumNumber().doubleValue(), 0.0001);
        assertEquals(1.0, array2.sumNumber().doubleValue(), 0.0001);

    }

    @Test
    public void testLogSoftmaxFC()  throws Exception {
        INDArray array1 = Nd4j.ones(2048).dup('f');
        INDArray array2 = Nd4j.zeros(2048);

        Nd4j.getExecutioner().exec(new LogSoftMax(array1));

        Nd4j.getExecutioner().exec(new LogSoftMax(array2));

        System.out.println("Array1: " + Arrays.toString(array1.data().asFloat()));
        System.out.println("Array2: " + Arrays.toString(array2.data().asFloat()));

        assertEquals(array1, array2);

        //assertEquals(1.0, array1.sumNumber().doubleValue(), 0.0001);
        //assertEquals(1.0, array2.sumNumber().doubleValue(), 0.0001);

    }

    @Test
    public void testSoftmaxDerivativeFC()  throws Exception {
        INDArray array1 = Nd4j.ones(2048).dup('f');
        INDArray array2 = Nd4j.zeros(2048);

        Nd4j.getExecutioner().exec(new SoftMaxDerivative(array1));

        Nd4j.getExecutioner().exec(new SoftMaxDerivative(array2));

        System.out.println("Array1: " + Arrays.toString(array1.data().asFloat()));
        System.out.println("Array2: " + Arrays.toString(array2.data().asFloat()));

        assertEquals(array1, array2);

        assertEquals(1.0, array1.sumNumber().doubleValue(), 0.001);
        assertEquals(1.0, array2.sumNumber().doubleValue(), 0.001);

    }

    @Test
    public void testIsMaxEqualValues(){
        //Nd4j.dtype = DataType.DOUBLE;
        //Assumption here: should only have a 1 for *first* maximum value, if multiple values are exactly equal


        //[1 1 1] -> [1 0 0]
        //Loop to double check against any threading weirdness...
        for( int i=0; i<10; i++ ) {
            assertEquals(Nd4j.create(new double[]{1, 0, 0}), Nd4j.getExecutioner().execAndReturn(new IsMax(Nd4j.ones(3))));
        }

        //[0 0 0 2 2 0] -> [0 0 0 1 0 0]
        assertEquals(Nd4j.create(new double[]{0, 0, 0, 1, 0, 0}), Nd4j.getExecutioner().execAndReturn(new IsMax(Nd4j.create(new double[]{0, 0, 0, 2, 2, 0}))));

        //[0 2]    [0 1]
        //[2 1] -> [0 0]
        INDArray orig = Nd4j.create(new double[][]{{0, 2}, {2, 1}});
        INDArray exp = Nd4j.create(new double[][]{{0, 1}, {0, 0}});
        INDArray outc = Nd4j.getExecutioner().execAndReturn(new IsMax(orig.dup('c')));
        INDArray outf = Nd4j.getExecutioner().execAndReturn(new IsMax(orig.dup('f')));


        System.out.println("Exp: " + Arrays.toString(exp.data().asFloat()));
        System.out.println("OutC: " + Arrays.toString(outc.data().asFloat()));
        System.out.println("OutF: " + Arrays.toString(outf.data().asFloat()));
        System.out.println("outC shapeInfo: " + outc.shapeInfoDataBuffer());
        System.out.println("outF shapeInfo: " + outf.shapeInfoDataBuffer());
        assertEquals(exp, outc);
        assertEquals(exp, outf);
    }

    @Test
    public void testIsMaxMinimized() throws Exception {
        System.out.println("A------------------------------");
        INDArray orig = Nd4j.create(new double[][]{{0, 2}, {2, 1}});
        System.out.println("AA------------------------------");
        INDArray origf = orig.dup('f');
        System.out.println("AB------------------------------");
        INDArray outf = Nd4j.getExecutioner().execAndReturn(new IsMax(origf));
        System.out.println("AC------------------------------");
        INDArray exp = Nd4j.create(new double[][]{{0, 1}, {0, 0}});

        System.out.println("A0------------------------------");
        System.out.println("exp data: " +Arrays.toString(exp.data().asFloat()));
        System.out.println("A1------------------------------");
        System.out.println("OutF data: " +Arrays.toString(outf.data().asFloat()));
        System.out.println("A2------------------------------");
        System.out.println("exp shape: " + exp.shapeInfoDataBuffer());
        System.out.println("A3------------------------------");
        System.out.println("OutF shape: " + outf.shapeInfoDataBuffer());
        System.out.println("A4------------------------------");
        System.out.println("exp: " + exp);
        System.out.println("A5------------------------------");
        System.out.println("OutF: " + outf);
        System.out.println("A6------------------------------");

        assertEquals(exp, outf);
    }

    @Test
    public void testSoftmaxSmall()  throws Exception {
        INDArray array1 = Nd4j.zeros(15);
        array1.putScalar(0, 0.9f);

        Nd4j.getExecutioner().exec(new OldSoftMax(array1));

        System.out.println("Array1: " + Arrays.toString(array1.data().asFloat()));

        assertEquals(1.0, array1.sumNumber().doubleValue(), 0.0001);
        assertEquals(0.14f, array1.getFloat(0), 0.01f);
     }

    @Test
    public void testClassificationSoftmax() throws Exception {
        INDArray input = Nd4j.zeros(256, 3000);
        System.out.println("A0: --------------------------------");
        for (int i = 0; i < 256; i++) {
            input.putScalar(3000 * i, (i * 2) + 0.5);
        }
        System.out.println("AF: --------------------------------");
   //     AtomicAllocator.getInstance().getPointer(input);
      //  AtomicAllocator.getInstance().getPointer(input.shapeInfoDataBuffer());

        System.out.println("AX: --------------------------------");
    //    AtomicAllocator.getInstance().getPointer(input);
     //   AtomicAllocator.getInstance().getPointer(input.shapeInfoDataBuffer());

        System.out.println("AA: --------------------------------");
        float sumAll = input.sumNumber().floatValue();
        System.out.println("A1: --------------------------------");
        System.out.println("Data:" + input.data().length() + " Sum: " + sumAll);
        assertEquals(65408.0f, sumAll, 0.01f);

        System.out.println("A2: --------------------------------");
        OldSoftMax softMax = new OldSoftMax(input);
        long time1 = System.currentTimeMillis();
        Nd4j.getExecutioner().exec(softMax);
        long time2 = System.currentTimeMillis();
        System.out.println("A3: --------------------------------");
        System.out.println("Execution time: " + (time2 - time1));
/*
        assertEquals(0.036710344f,input.getFloat(0), 0.01f);
        assertEquals(0.023549506f,input.getFloat(152), 0.01f);
        assertEquals(0.005180763f,input.getFloat(310), 0.01f);
        assertEquals(4.5634616E-7f,input.getFloat(879), 0.01f);
*/
        for (int i = 0; i < 256; i++) {
            INDArray slice = input.slice(i);
            System.out.println("Position [0]: " + input.getDouble(3000 * i) + ", [1]: " + input.getDouble(3000 * i + 1));

            assertTrue(input.getDouble(3000 * i) > input.getDouble(3000 * i + 1));

            float sum = slice.sumNumber().floatValue();
            assertEquals("Failed on iteration ["+i+"]", 1.0f, sum, 0.01f);
        }
    }

    @Test
    public void testTanhXZ(){
        INDArray arrC = Nd4j.linspace(-6,6,12).reshape('c',4,3);
        INDArray arrF = Nd4j.create(new int[]{4,3},'f').assign(arrC);
        double[] d = arrC.data().asDouble();
        double[] e = new double[d.length];
        for(int i=0; i<e.length; i++ ) e[i] = Math.tanh(d[i]);

        INDArray exp = Nd4j.create(e, new int[]{4,3}, 'c');

        //First: do op with just x (inplace)
        INDArray arrFCopy = arrF.dup('f');
        INDArray arrCCopy = arrF.dup('c');
        Nd4j.getExecutioner().exec(new Tanh(arrFCopy));
        Nd4j.getExecutioner().exec(new Tanh(arrCCopy));

        System.out.println("ArrF shape: " + arrFCopy.shapeInfoDataBuffer());
        System.out.println("ArrC shape: " + arrCCopy.shapeInfoDataBuffer());

        assertEquals(exp, arrCCopy);
        assertEquals(exp, arrFCopy);

        //Second: do op with both x and z:



        INDArray zOutFC = Nd4j.create(new int[]{4,3},'c');
        INDArray zOutFF = Nd4j.create(new int[]{4,3},'f');
        INDArray zOutCC = Nd4j.create(new int[]{4,3},'c');
        INDArray zOutCF = Nd4j.create(new int[]{4,3},'f');

        System.out.println("arrF order: " + arrF.ordering());
        System.out.println("zOutFC order: " + zOutFC.ordering());

        Nd4j.getExecutioner().exec(new Tanh(arrF, zOutFC));
//        Nd4j.getExecutioner().exec(new Tanh(arrF, zOutFF));
//        Nd4j.getExecutioner().exec(new Tanh(arrC, zOutCC));
//        Nd4j.getExecutioner().exec(new Tanh(arrC, zOutCF));

        assertEquals(exp, zOutFC);  //fails
        //assertEquals(exp, zOutFF);  //pass
        //assertEquals(exp, zOutCC);  //pass
        //assertEquals(exp, zOutCF);  //fails
    }

    @Test
    public void testCol2Im2() {
        int kh = 1;
        int kw = 1;
        int sy = 1;
        int sx = 1;
        int ph = 1;
        int pw = 1;
        INDArray linspaced = Nd4j.linspace(1,64,64).reshape(2,2,2,2,2,2);
        INDArray newTest = Convolution.col2im(linspaced,sy,sx,ph,pw,2,2);
        INDArray assertion = OldConvolution.col2im(linspaced,sy,sx,ph,pw,2,2);

        System.out.println("Assertion dimensions: " + Arrays.toString(assertion.shape()));
        System.out.println("Assertion data: " + Arrays.toString(assertion.data().asFloat()));
        System.out.println("Result data: " + Arrays.toString(newTest.data().asFloat()));
        assertEquals(assertion,newTest);
    }

    @Test
    public void testTransformExp() throws Exception {
        INDArray array1 = Nd4j.zeros(1500,150);
        //System.out.println("ShapeBuffer: " + array1.shapeInfoDataBuffer());

        Exp exp = new Exp(array1);
        long time1 = System.currentTimeMillis();
        Nd4j.getExecutioner().exec(exp);
        long time2 = System.currentTimeMillis();

        System.out.println("Execution time: ["+ (time2 - time1)+"]");


        for (int x = 0; x < 1500 * 150; x++) {
            assertEquals("Failed on iteration ["+ x+"]",1f, array1.getFloat(x), 0.0001f);
        }

        //Thread.sleep(100000000000L);
    }

    @Test
    public void testIsMaxDimensional() throws Exception {

        INDArray arr = Nd4j.create(new int[]{51200,4},'c');
        INDArray epsilon = Nd4j.create(new int[]{51200,1},'c');
/*
        int[] kernel = {2,2};
        int[] strides = {2,2};
        int[] pad = {0,0};
        INDArray col6d = Nd4j.create(new int[]{64,50,4,4,2,2},'c');
        INDArray col6dPermuted = col6d.permute(0,1,4,5,2,3);


        Convolution.im2col(input, kernel[0], kernel[1], strides[0], strides[1], pad[0], pad[1], false, col6dPermuted);

        int miniBatch = 64;
        int depth = 50;
        int outH = 4;
        int outW = 4;
        INDArray col2d = col6d.reshape('c',miniBatch*depth*outH*outW,kernel[0]*kernel[1]);

        for( int i=0; i<1000;i ++ ){
            System.out.println(i);
            Convolution.im2col(col2d, kernel[0], kernel[1], strides[0], strides[1], pad[0], pad[1], false, col6dPermuted);
            INDArray isMax = Nd4j.getExecutioner().execAndReturn(new IsMax(col2d,1));
            isMax.muliColumnVector(epsilon);
        }
*/

        INDArray input = Nd4j.create(new int[]{64,50,8,8},'c');
        for( int i=0; i<10;i ++ ){
            INDArray isMax = Nd4j.getExecutioner().execAndReturn(new IsMax(arr,1));


        }
    }

    @Test
    public void testSignXZ(){
        double[] d =   {1.0, -1.1,  1.2,  1.3, -1.4, -1.5, 1.6, -1.7, -1.8, -1.9, -1.01, -1.011};
        double[] e =   {1.0, -1.0,  1.0,  1.0, -1.0, -1.0, 1.0, -1.0, -1.0, -1.0, -1.0, -1.0};

        INDArray arrF = Nd4j.create(d,new int[]{4,3},'f');
        INDArray arrC = Nd4j.create(new int[]{4,3},'c').assign(arrF);

        INDArray exp = Nd4j.create(e, new int[]{4,3}, 'f');

        //First: do op with just x (inplace)
        INDArray arrFCopy = arrF.dup('f');
        INDArray arrCCopy = arrC.dup('c');
        Nd4j.getExecutioner().exec(new Sign(arrFCopy));
        Nd4j.getExecutioner().exec(new Sign(arrCCopy));
        assertEquals(exp, arrFCopy);
        assertEquals(exp, arrCCopy);

        //Second: do op with both x and z:
        INDArray zOutFC = Nd4j.create(new int[]{4,3},'c');
        INDArray zOutFF = Nd4j.create(new int[]{4,3},'f');
        INDArray zOutCC = Nd4j.create(new int[]{4,3},'c');
        INDArray zOutCF = Nd4j.create(new int[]{4,3},'f');
        Nd4j.getExecutioner().exec(new Sign(arrF, zOutFC));
        Nd4j.getExecutioner().exec(new Sign(arrF, zOutFF));
        Nd4j.getExecutioner().exec(new Sign(arrC, zOutCC));
        Nd4j.getExecutioner().exec(new Sign(arrC, zOutCF));

        assertEquals(exp, zOutFC);  //fails
        assertEquals(exp, zOutFF);  //pass
        assertEquals(exp, zOutCC);  //pass
        assertEquals(exp, zOutCF);  //fails
    }


    @Test
    public void testSoftmax1D_1() throws Exception {
        INDArray input1T = Nd4j.create(new double[]{ -0.75, 0.58, 0.42, 1.03, -0.61, 0.19, -0.37, -0.40, -1.42, -0.04}).transpose();
        INDArray input1 = Nd4j.create(new double[]{ -0.75, 0.58, 0.42, 1.03, -0.61, 0.19, -0.37, -0.40, -1.42, -0.04});
        INDArray input2 = Nd4j.zerosLike(input1);
        Nd4j.copy(input1, input2);
        INDArray output1 = Nd4j.create(1, 10);
        INDArray output1T = Nd4j.create(1, 10);

        System.out.println("FA --------------------");
        Nd4j.getExecutioner().exec(new OldSoftMax(input1, output1));
        Nd4j.getExecutioner().exec(new OldSoftMax(input1T, output1T));
        System.out.println("FB --------------------");

        System.out.println("Softmax = " + output1);
        INDArray output2 = Nd4j.create(1,10);
        Nd4j.getExecutioner().exec(new SoftMaxDerivative(input2, output2));
        System.out.println("Softmax Derivative = " + output2);

        INDArray assertion1 = Nd4j.create(new double[]{0.04, 0.16, 0.14, 0.26, 0.05, 0.11, 0.06, 0.06, 0.02, 0.09});

        assertArrayEquals(assertion1.data().asFloat(), output1.data().asFloat(), 0.01f);
        assertArrayEquals(assertion1.data().asFloat(), output1T.data().asFloat(), 0.01f);

    }

    @Test
    public void testSoftmax2D_T() {
        INDArray input1 = Nd4j.create(1000).transpose();
        INDArray input2 = Nd4j.zerosLike(input1);
        Nd4j.copy(input1, input2);
        INDArray output1 = Nd4j.create(1, 1000);
        Nd4j.getExecutioner().exec(new OldSoftMax(input1, output1));
        System.out.println("Softmax = " + output1);
        INDArray output2 = Nd4j.create(1,1000);
        Nd4j.getExecutioner().exec(new SoftMaxDerivative(input2, output2));
        System.out.println("Softmax Derivative = " + output2);

        assertEquals(1.0f, output1.sumNumber().floatValue(), 0.01f);
        assertEquals(1.0f, output2.sumNumber().floatValue(), 0.01f);
    }
}

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

package org.nd4j.linalg.nativ;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.nd4j.linalg.BaseNd4jTest;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;

import static org.junit.Assert.assertEquals;

/**
 * This class contains tests related to javacpp presets and their libnd4j integration
 * @author raver119@gmail.com
 */
@Slf4j
public class NativeBlasTests extends BaseNd4jTest {

    public NativeBlasTests(Nd4jBackend backend) {
        super(backend);
    }

    @Before
    public void setUp() {
        Nd4j.getExecutioner().enableDebugMode(true);
        Nd4j.getExecutioner().enableVerboseMode(true);
    }

    @After
    public void setDown() {
        Nd4j.getExecutioner().enableDebugMode(false);
        Nd4j.getExecutioner().enableVerboseMode(false);
    }

    @Test
    public void testBlasGemm1() {

        // we're skipping blas here
        if (Nd4j.getExecutioner().getClass().getSimpleName().toLowerCase().contains("cuda"))
            return;

        val A = Nd4j.linspace(1, 9, 9, DataType.DOUBLE).reshape('c', 3, 3);
        val B = Nd4j.linspace(1, 9, 9, DataType.DOUBLE).reshape('c', 3, 3);

        val exp = A.mmul(B);

        val res = Nd4j.create(DataType.DOUBLE, new long[] {3, 3}, 'c');

        val matmul = DynamicCustomOp.builder("matmul")
                .addInputs(A, B)
                .addOutputs(res)
                .build();

        Nd4j.getExecutioner().exec(matmul);

        // ?
        assertEquals(exp, res);
    }


    @Test
    public void testBlasGemm2() {

        // we're skipping blas here
        if (Nd4j.getExecutioner().getClass().getSimpleName().toLowerCase().contains("cuda"))
            return;

        val A = Nd4j.linspace(1, 9, 9, DataType.DOUBLE).reshape('c', 3, 3).dup('f');
        val B = Nd4j.linspace(1, 9, 9, DataType.DOUBLE).reshape('c', 3, 3).dup('f');

        val exp = A.mmul(B);

        val res = Nd4j.create(DataType.DOUBLE, new long[] {3, 3}, 'c');

        val matmul = DynamicCustomOp.builder("matmul")
                .addInputs(A, B)
                .addOutputs(res)
                .build();

        Nd4j.getExecutioner().exec(matmul);

        // ?
        assertEquals(exp, res);
    }


    @Test
    public void testBlasGemm3() {

        // we're skipping blas here
        if (Nd4j.getExecutioner().getClass().getSimpleName().toLowerCase().contains("cuda"))
            return;

        val A = Nd4j.linspace(1, 9, 9, DataType.DOUBLE).reshape('c', 3, 3).dup('f');
        val B = Nd4j.linspace(1, 9, 9, DataType.DOUBLE).reshape('c', 3, 3);

        val exp = A.mmul(B);

        val res = Nd4j.create(DataType.DOUBLE, new long[] {3, 3}, 'c');

        val matmul = DynamicCustomOp.builder("matmul")
                .addInputs(A, B)
                .addOutputs(res)
                .build();

        Nd4j.getExecutioner().exec(matmul);

        // ?
        assertEquals(exp, res);
    }


    @Test
    public void testBlasGemm4() {

        // we're skipping blas here
        if (Nd4j.getExecutioner().getClass().getSimpleName().toLowerCase().contains("cuda"))
            return;

        val A = Nd4j.linspace(1, 12, 12, DataType.DOUBLE).reshape('c', 4, 3);
        val B = Nd4j.linspace(1, 12, 12, DataType.DOUBLE).reshape('c', 3, 4);

        val exp = A.mmul(B);

        val res = Nd4j.create(DataType.DOUBLE, new long[] {4, 4}, 'c');

        val matmul = DynamicCustomOp.builder("matmul")
                .addInputs(A, B)
                .addOutputs(res)
                .build();

        Nd4j.getExecutioner().exec(matmul);

        // ?
        assertEquals(exp, res);
    }


    @Test
    public void testBlasGemm5() {

        // we're skipping blas here
        if (Nd4j.getExecutioner().getClass().getSimpleName().toLowerCase().contains("cuda"))
            return;

        val A = Nd4j.linspace(1, 12, 12, DataType.DOUBLE).reshape('c', 4, 3).dup('f');
        val B = Nd4j.linspace(1, 12, 12, DataType.DOUBLE).reshape('c', 3, 4);

        val exp = A.mmul(B);

        val res = Nd4j.create(DataType.DOUBLE, new long[] {4, 4}, 'c');

        val matmul = DynamicCustomOp.builder("matmul")
                .addInputs(A, B)
                .addOutputs(res)
                .build();

        Nd4j.getExecutioner().exec(matmul);

        // ?
        assertEquals(exp, res);
    }

    @Test
    public void testBlasGemm6() {

        // we're skipping blas here
        if (Nd4j.getExecutioner().getClass().getSimpleName().toLowerCase().contains("cuda"))
            return;

        val A = Nd4j.linspace(1, 12, 12, DataType.DOUBLE).reshape('c', 4, 3).dup('f');
        val B = Nd4j.linspace(1, 12, 12, DataType.DOUBLE).reshape('c', 3, 4).dup('f');

        val exp = A.mmul(B);

        val res = Nd4j.createUninitialized(DataType.DOUBLE, new long[] {4, 4}, 'c');

        val matmul = DynamicCustomOp.builder("matmul")
                .addInputs(A, B)
                .addOutputs(res)
                .build();

        Nd4j.getExecutioner().exec(matmul);

        // ?
        assertEquals(exp, res);
    }


    @Test
    public void testBlasGemm7() {

        // we're skipping blas here
        if (Nd4j.getExecutioner().getClass().getSimpleName().toLowerCase().contains("cuda"))
            return;

        val A = Nd4j.linspace(1, 12, 12, DataType.DOUBLE).reshape('c', 4, 3);
        val B = Nd4j.linspace(1, 12, 12, DataType.DOUBLE).reshape('c', 3, 4).dup('f');

        val exp = A.mmul(B);

        val res = Nd4j.createUninitialized(DataType.DOUBLE, new long[] {4, 4}, 'c');

        val matmul = DynamicCustomOp.builder("matmul")
                .addInputs(A, B)
                .addOutputs(res)
                .build();

        Nd4j.getExecutioner().exec(matmul);

        // ?
        assertEquals(exp, res);
    }




    @Test
    public void testBlasGemv1() {

        // we're skipping blas here
        if (Nd4j.getExecutioner().getClass().getSimpleName().toLowerCase().contains("cuda"))
            return;

        val A = Nd4j.linspace(1, 9, 9, DataType.DOUBLE).reshape('c', 3, 3);
        val B = Nd4j.linspace(1, 3, 3, DataType.DOUBLE).reshape('c', 3, 1);

        val res = Nd4j.create(DataType.DOUBLE, new long[] {3, 1}, 'c');

        val matmul = DynamicCustomOp.builder("matmul")
                .addInputs(A, B)
                .addOutputs(res)
                .build();

        Nd4j.getExecutioner().exec(matmul);


        val exp = A.mmul(B);
        log.info("exp: {}", exp);

        // ?
        assertEquals(exp, res);
    }


    @Test
    public void testBlasGemv2() {

        // we're skipping blas here
        if (Nd4j.getExecutioner().getClass().getSimpleName().toLowerCase().contains("cuda"))
            return;

        val A = Nd4j.linspace(1, 9, 9, DataType.DOUBLE).reshape('c', 3, 3).dup('f');
        val B = Nd4j.linspace(1, 3, 3, DataType.DOUBLE).reshape('c', 3, 1).dup('f');

        val res = Nd4j.createUninitialized(DataType.DOUBLE, new long[] {3, 1}, 'f');

        val matmul = DynamicCustomOp.builder("matmul")
                .addInputs(A, B)
                .addOutputs(res)
                .build();

        Nd4j.getExecutioner().exec(matmul);


        val exp = A.mmul(B);
        log.info("exp mean: {}", exp.meanNumber());

        // ?
        assertEquals(exp, res);
    }


    @Test
    public void testBlasGemv3() {

        // we're skipping blas here
        if (Nd4j.getExecutioner().getClass().getSimpleName().toLowerCase().contains("cuda"))
            return;

        val A = Nd4j.linspace(1, 20, 20, DataType.FLOAT).reshape('c', 4, 5);
        val B = Nd4j.linspace(1, 5, 5, DataType.FLOAT).reshape('c', 5, 1);

        val exp = A.mmul(B);

        val res = Nd4j.createUninitialized(DataType.FLOAT, new long[] {4, 1}, 'c');

        val matmul = DynamicCustomOp.builder("matmul")
                .addInputs(A, B)
                .addOutputs(res)
                .build();

        Nd4j.getExecutioner().exec(matmul);




        // ?
        assertEquals(exp, res);
    }

    @Override
    public char ordering() {
        return 'c';
    }
}

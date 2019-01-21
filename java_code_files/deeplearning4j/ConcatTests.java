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

package org.nd4j.linalg.shape.concat;

import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.nd4j.linalg.BaseNd4jTest;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.checkutil.NDArrayCreationUtil;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.primitives.Pair;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Adam Gibson
 */
@Slf4j
@RunWith(Parameterized.class)
public class ConcatTests extends BaseNd4jTest {

    public ConcatTests(Nd4jBackend backend) {
        super(backend);
    }


    @Test
    public void testConcat() {
        INDArray A = Nd4j.linspace(1, 8, 8, DataType.DOUBLE).reshape(2, 2, 2);
        INDArray B = Nd4j.linspace(1, 12, 12, DataType.DOUBLE).reshape(3, 2, 2);
        INDArray concat = Nd4j.concat(0, A, B);
        assertTrue(Arrays.equals(new long[] {5, 2, 2}, concat.shape()));

    }

    @Test
    public void testConcatHorizontally() {
        INDArray rowVector = Nd4j.ones(1, 5);
        INDArray other = Nd4j.ones(1, 5);
        INDArray concat = Nd4j.hstack(other, rowVector);
        assertEquals(rowVector.rows(), concat.rows());
        assertEquals(rowVector.columns() * 2, concat.columns());

    }


    @Test
    public void testVStackColumn() {
        INDArray linspaced = Nd4j.linspace(1, 3, 3, DataType.DOUBLE).reshape(3, 1);
        INDArray stacked = linspaced.dup();
        INDArray assertion = Nd4j.create(new double[] {1, 2, 3, 1, 2, 3}, new int[] {6, 1});
        INDArray test = Nd4j.vstack(linspaced, stacked);
        assertEquals(assertion, test);
    }


    @Test
    public void testConcatScalars() {
        INDArray first = Nd4j.arange(0, 1).reshape(1, 1);
        INDArray second = Nd4j.arange(0, 1).reshape(1, 1);
        INDArray firstRet = Nd4j.concat(0, first, second);
        assertTrue(firstRet.isColumnVector());
        INDArray secondRet = Nd4j.concat(1, first, second);
        assertTrue(secondRet.isRowVector());


    }


    @Test
    public void testConcatMatrices() {
        INDArray a = Nd4j.linspace(1, 4, 4, DataType.DOUBLE).reshape(2, 2);
        INDArray b = a.dup();


        INDArray concat1 = Nd4j.concat(1, a, b);
        INDArray oneAssertion = Nd4j.create(new double[][] {{1, 3, 1, 3}, {2, 4, 2, 4}});
        assertEquals(oneAssertion, concat1);

        INDArray concat = Nd4j.concat(0, a, b);
        INDArray zeroAssertion = Nd4j.create(new double[][] {{1, 3}, {2, 4}, {1, 3}, {2, 4}});
        assertEquals(zeroAssertion, concat);
    }

    @Test
    public void testConcatRowVectors() {
        INDArray rowVector = Nd4j.create(new double[] {1, 2, 3, 4, 5, 6}, new int[] {1, 6});
        INDArray matrix = Nd4j.create(new double[] {7, 8, 9, 10, 11, 12}, new int[] {1, 6});

        INDArray assertion1 = Nd4j.create(new double[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, new int[] {1, 12});
        INDArray assertion0 = Nd4j.create(new double[][] {{1, 2, 3, 4, 5, 6}, {7, 8, 9, 10, 11, 12}});

        //      INDArray concat1 = Nd4j.hstack(rowVector, matrix);
        INDArray concat0 = Nd4j.vstack(rowVector, matrix);
        //        assertEquals(assertion1, concat1);
        assertEquals(assertion0, concat0);
    }


    @Test
    public void testConcat3d() {
        INDArray first = Nd4j.linspace(1, 24, 24, DataType.DOUBLE).reshape('c', 2, 3, 4);
        INDArray second = Nd4j.linspace(24, 36, 12, DataType.DOUBLE).reshape('c', 1, 3, 4);
        INDArray third = Nd4j.linspace(36, 48, 12, DataType.DOUBLE).reshape('c', 1, 3, 4);

        //ConcatV2, dim 0
        INDArray exp = Nd4j.create(DataType.DOUBLE, 2 + 1 + 1, 3, 4);
        exp.put(new INDArrayIndex[] {NDArrayIndex.interval(0, 2), NDArrayIndex.all(), NDArrayIndex.all()}, first);
        exp.put(new INDArrayIndex[] {NDArrayIndex.point(2), NDArrayIndex.all(), NDArrayIndex.all()}, second);
        exp.put(new INDArrayIndex[] {NDArrayIndex.point(3), NDArrayIndex.all(), NDArrayIndex.all()}, third);

        INDArray concat0 = Nd4j.concat(0, first, second, third);

        assertEquals(exp, concat0);

        System.out.println("1------------------------");

        //ConcatV2, dim 1
        second = Nd4j.linspace(24, 32, 8, DataType.DOUBLE).reshape('c', 2, 1, 4);
        third = Nd4j.linspace(32, 48, 16, DataType.DOUBLE).reshape('c', 2, 2, 4);
        exp = Nd4j.create(DataType.DOUBLE, 2, 3 + 1 + 2, 4);
        exp.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.interval(0, 3), NDArrayIndex.all()}, first);
        exp.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.point(3), NDArrayIndex.all()}, second);
        exp.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.interval(4, 6), NDArrayIndex.all()}, third);

        System.out.println("2------------------------");

        INDArray concat1 = Nd4j.concat(1, first, second, third);

        assertEquals(exp, concat1);

        //ConcatV2, dim 2
        second = Nd4j.linspace(24, 36, 12, DataType.DOUBLE).reshape('c', 2, 3, 2);
        third = Nd4j.linspace(36, 42, 6, DataType.DOUBLE).reshape('c', 2, 3, 1);
        exp = Nd4j.create(DataType.DOUBLE, 2, 3, 4 + 2 + 1);

        exp.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.all(), NDArrayIndex.interval(0, 4)}, first);
        exp.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.all(), NDArrayIndex.interval(4, 6)}, second);
        exp.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.all(), NDArrayIndex.point(6)}, third);

        INDArray concat2 = Nd4j.concat(2, first, second, third);

        assertEquals(exp, concat2);
    }

    @Test
    @Ignore
    public void testConcat3dv2() {

        INDArray first = Nd4j.linspace(1, 24, 24, DataType.DOUBLE).reshape('c', 2, 3, 4);
        INDArray second = Nd4j.linspace(24, 35, 12, DataType.DOUBLE).reshape('c', 1, 3, 4);
        INDArray third = Nd4j.linspace(36, 47, 12, DataType.DOUBLE).reshape('c', 1, 3, 4);

        //ConcatV2, dim 0
        INDArray exp = Nd4j.create(2 + 1 + 1, 3, 4);
        exp.put(new INDArrayIndex[] {NDArrayIndex.interval(0, 2), NDArrayIndex.all(), NDArrayIndex.all()}, first);
        exp.put(new INDArrayIndex[] {NDArrayIndex.point(2), NDArrayIndex.all(), NDArrayIndex.all()}, second);
        exp.put(new INDArrayIndex[] {NDArrayIndex.point(3), NDArrayIndex.all(), NDArrayIndex.all()}, third);

        List<Pair<INDArray, String>> firsts = NDArrayCreationUtil.getAll3dTestArraysWithShape(12345, new long[]{2, 3, 4}, DataType.DOUBLE);
        List<Pair<INDArray, String>> seconds = NDArrayCreationUtil.getAll3dTestArraysWithShape(12345, new long[]{1, 3, 4}, DataType.DOUBLE);
        List<Pair<INDArray, String>> thirds = NDArrayCreationUtil.getAll3dTestArraysWithShape(12345, new long[]{1, 3, 4}, DataType.DOUBLE);
        for (Pair<INDArray, String> f : firsts) {
            for (Pair<INDArray, String> s : seconds) {
                for (Pair<INDArray, String> t : thirds) {
                    INDArray f2 = f.getFirst().assign(first);
                    INDArray s2 = s.getFirst().assign(second);
                    INDArray t2 = t.getFirst().assign(third);

                    System.out.println("-------------------------------------------");
                    INDArray concat0 = Nd4j.concat(0, f2, s2, t2);

                    assertEquals(exp, concat0);
                }
            }
        }

        //ConcatV2, dim 1
        second = Nd4j.linspace(24, 31, 8, DataType.DOUBLE).reshape('c', 2, 1, 4);
        third = Nd4j.linspace(32, 47, 16, DataType.DOUBLE).reshape('c', 2, 2, 4);
        exp = Nd4j.create(2, 3 + 1 + 2, 4);
        exp.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.interval(0, 3), NDArrayIndex.all()}, first);
        exp.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.point(3), NDArrayIndex.all()}, second);
        exp.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.interval(4, 6), NDArrayIndex.all()}, third);

        firsts = NDArrayCreationUtil.getAll3dTestArraysWithShape(12345, new long[]{2, 3, 4}, DataType.DOUBLE);
        seconds = NDArrayCreationUtil.getAll3dTestArraysWithShape(12345, new long[]{2, 1, 4}, DataType.DOUBLE);
        thirds = NDArrayCreationUtil.getAll3dTestArraysWithShape(12345, new long[]{2, 2, 4}, DataType.DOUBLE);
        for (Pair<INDArray, String> f : firsts) {
            for (Pair<INDArray, String> s : seconds) {
                for (Pair<INDArray, String> t : thirds) {
                    INDArray f2 = f.getFirst().assign(first);
                    INDArray s2 = s.getFirst().assign(second);
                    INDArray t2 = t.getFirst().assign(third);

                    INDArray concat1 = Nd4j.concat(1, f2, s2, t2);

                    assertEquals(exp, concat1);
                }
            }
        }

        //ConcatV2, dim 2
        second = Nd4j.linspace(24, 35, 12, DataType.DOUBLE).reshape('c', 2, 3, 2);
        third = Nd4j.linspace(36, 41, 6, DataType.DOUBLE).reshape('c', 2, 3, 1);
        exp = Nd4j.create(2, 3, 4 + 2 + 1);
        exp.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.all(), NDArrayIndex.interval(0, 4)}, first);
        exp.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.all(), NDArrayIndex.interval(4, 6)}, second);
        exp.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.all(), NDArrayIndex.point(6)}, third);

        firsts = NDArrayCreationUtil.getAll3dTestArraysWithShape(12345, new long[]{2, 3, 4}, DataType.DOUBLE);
        seconds = NDArrayCreationUtil.getAll3dTestArraysWithShape(12345, new long[]{2, 3, 2}, DataType.DOUBLE);
        thirds = NDArrayCreationUtil.getAll3dTestArraysWithShape(12345, new long[]{2, 3, 1}, DataType.DOUBLE);
        for (Pair<INDArray, String> f : firsts) {
            for (Pair<INDArray, String> s : seconds) {
                for (Pair<INDArray, String> t : thirds) {
                    INDArray f2 = f.getFirst().assign(first);
                    INDArray s2 = s.getFirst().assign(second);
                    INDArray t2 = t.getFirst().assign(third);

                    INDArray concat2 = Nd4j.concat(2, f2, s2, t2);

                    assertEquals(exp, concat2);
                }
            }
        }
    }


    @Override
    public char ordering() {
        return 'f';
    }
}

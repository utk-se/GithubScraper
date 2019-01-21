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

package org.nd4j.linalg.jcublas;

import com.google.flatbuffers.FlatBufferBuilder;
import org.nd4j.linalg.api.blas.params.MMulTranspose;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.BaseSparseNDArrayCSR;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;

/**
 * @author Audrey Loeffel
 */
public class JcusparseNDArrayCSR extends BaseSparseNDArrayCSR {
    /**
     * The length of the values and columns arrays is equal to the number of non-zero elements in A.
     * The length of the pointerB and pointerE arrays is equal to the number of rows in A.
     *
     * @param data            a double array that contains the non-zero element of the sparse matrix A
     * @param columnsPointers Element i of the integer array columns is the number of the column in A that contains the i-th value
     *                        in the values array.
     * @param pointerB        Element j of this integer array gives the index of the element in the values array that is first
     *                        non-zero element in a row j of A. Note that this index is equal to pointerB(j) - pointerB(1)+1 .
     * @param pointerE        An integer array that contains row indices, such that pointerE(j)-pointerB(1) is the index of the
     *                        element in the values array that is last non-zero element in a row j of A.
     * @param shape           Shape of the matrix A
     */
    public JcusparseNDArrayCSR(double[] data, int[] columnsPointers, int[] pointerB, int[] pointerE, long[] shape) {
        super(data, columnsPointers, pointerB, pointerE, shape);
    }

    public JcusparseNDArrayCSR(float[] data, int[] columnsPointers, int[] pointerB, int[] pointerE, long[] shape) {
        super(data, columnsPointers, pointerB, pointerE, shape);
    }

    public JcusparseNDArrayCSR(DataBuffer data, int[] columnsPointers, int[] pointerB, int[] pointerE, long[] shape) {
        super(data, columnsPointers, pointerB, pointerE, shape);
    }

    @Override
    public String getStringUnsafe(long index) {
        return null;
    }

    @Override
    public INDArray repeat(int dimension, long... repeats) {
        return null;
    }

    @Override
    public INDArray mmul(INDArray other, MMulTranspose mMulTranspose) {
        return null;
    }

    /**
     * Perform an copy matrix multiplication
     *
     * @param other         the other matrix to perform matrix multiply with
     * @param result        the result ndarray
     * @param mMulTranspose the transpose status of each array
     * @return the result of the matrix multiplication
     */
    @Override
    public INDArray mmul(INDArray other, INDArray result, MMulTranspose mMulTranspose) {
        return null;
    }

    @Override
    public INDArray mmuli(INDArray other, MMulTranspose transpose) {
        return null;
    }

    @Override
    public INDArray mmuli(INDArray other, INDArray result, MMulTranspose transpose) {
        return null;
    }

    @Override
    public INDArray reshape(char order, int... newShape) {
        return null;
    }

    @Override
    public INDArray reshape(int[] shape) {
        return null;
    }

    @Override
    public LongShapeDescriptor shapeDescriptor() {
        return null;
    }


    @Override
    public int toFlatArray(FlatBufferBuilder builder) {
        throw new UnsupportedOperationException();
    }

    /**
     * This method returns true if this INDArray is special case: no-value INDArray
     *
     * @return
     */
    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isR() {
        return false;
    }

    @Override
    public boolean isZ() {
        return false;
    }

    @Override
    public boolean isB() {
        return false;
    }

    @Override
    public boolean isS() {
        return false;
    }

    @Override
    public INDArray castTo(DataType dataType) {
        return null;
    }

    @Override
    public boolean all() {
        return false;
    }

    @Override
    public boolean any() {
        return false;
    }

    @Override
    public boolean none() {
        return false;
    }

    @Override
    public boolean closeable() {
        return false;
    }

    @Override
    public void close() {

    }
}

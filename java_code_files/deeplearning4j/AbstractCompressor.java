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

package org.nd4j.compression.impl;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.Pointer;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.buffer.DataTypeEx;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.compression.CompressedDataBuffer;
import org.nd4j.linalg.compression.NDArrayCompressor;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.util.ArrayUtil;

/**
 * @author raver119@gmail.com
 */
@Slf4j
public abstract class AbstractCompressor implements NDArrayCompressor {

    @Override
    public INDArray compress(INDArray array) {
        INDArray dup = array.dup(array.ordering());

        Nd4j.getExecutioner().commit();

        dup.setData(compress(dup.data()));
        dup.markAsCompressed(true);

        return dup;
    }

    /**
     * This method has no effect in this compressor
     *
     * @param vars
     */
    @Override
    public void configure(Object... vars) {
        // no-op
    }

    /**
     * Inplace compression of INDArray
     *
     * @param array
     */
    @Override
    public void compressi(INDArray array) {
        // TODO: lift this restriction
        if (array.isView())
            throw new UnsupportedOperationException("Impossible to apply inplace compression on View");

        array.setData(compress(array.data()));
        array.markAsCompressed(true);
    }

    @Override
    public void decompressi(INDArray array) {
        if (!array.isCompressed())
            return;

        array.markAsCompressed(false);
        array.setData(decompress(array.data(), ((CompressedDataBuffer)array.data()).getCompressionDescriptor().getOriginalDataType()));
    }

    @Override
    public INDArray decompress(INDArray array) {
        if (!array.isCompressed())
            return array;

        val descriptor = ((CompressedDataBuffer)array.data()).getCompressionDescriptor();
        val buffer = decompress(array.data(), descriptor.getOriginalDataType());
        val shapeInfo = array.shapeInfoDataBuffer();
        INDArray rest = Nd4j.createArrayFromShapeBuffer(buffer, shapeInfo);

        return rest;
    }

    public abstract DataBuffer decompress(DataBuffer buffer, DataType dataType);

    public abstract DataBuffer compress(DataBuffer buffer);

    protected static DataTypeEx convertType(DataType type) {
        if (type == DataType.HALF) {
            return DataTypeEx.FLOAT16;
        } else if (type == DataType.FLOAT) {
            return DataTypeEx.FLOAT;
        } else if (type == DataType.DOUBLE) {
            return DataTypeEx.DOUBLE;
        } else
            throw new IllegalStateException("Unknown dataType: [" + type + "]");
    }

    protected DataTypeEx getGlobalTypeEx() {
        DataType type = Nd4j.dataType();

        return convertType(type);
    }

    public static DataTypeEx getBufferTypeEx(DataBuffer buffer) {
        DataType type = buffer.dataType();

        return convertType(type);
    }

    /**
     * This method creates compressed INDArray from Java float array, skipping usual INDArray instantiation routines
     * Please note: This method compresses input data as vector
     *
     * @param data
     * @return
     */
    @Override
    public INDArray compress(float[] data) {
        return compress(data, new int[] {1, data.length}, Nd4j.order());
    }

    /**
     * This method creates compressed INDArray from Java double array, skipping usual INDArray instantiation routines
     * Please note: This method compresses input data as vector
     *
     * @param data
     * @return
     */
    @Override
    public INDArray compress(double[] data) {
        return compress(data, new int[] {1, data.length}, Nd4j.order());
    }

    /**
     * This method creates compressed INDArray from Java float array, skipping usual INDArray instantiation routines
     *
     * @param data
     * @param shape
     * @param order
     * @return
     */
    @Override
    public INDArray compress(float[] data, int[] shape, char order) {
        FloatPointer pointer = new FloatPointer(data);

        DataBuffer shapeInfo = Nd4j.getShapeInfoProvider().createShapeInformation(ArrayUtil.toLongArray(shape), order, DataType.FLOAT).getFirst();
        DataBuffer buffer = compressPointer(DataTypeEx.FLOAT, pointer, data.length, 4);

        return Nd4j.createArrayFromShapeBuffer(buffer, shapeInfo);
    }

    /**
     * This method creates compressed INDArray from Java double array, skipping usual INDArray instantiation routines
     *
     * @param data
     * @param shape
     * @param order
     * @return
     */
    @Override
    public INDArray compress(double[] data, int[] shape, char order) {
        DoublePointer pointer = new DoublePointer(data);

        DataBuffer shapeInfo = Nd4j.getShapeInfoProvider().createShapeInformation(ArrayUtil.toLongArray(shape), order, DataType.DOUBLE).getFirst();
        DataBuffer buffer = compressPointer(DataTypeEx.DOUBLE, pointer, data.length, 8);

        return Nd4j.createArrayFromShapeBuffer(buffer, shapeInfo);
    }

    protected abstract CompressedDataBuffer compressPointer(DataTypeEx srcType, Pointer srcPointer, int length,
                    int elementSize);
}

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

package org.nd4j.linalg.cpu.nativecpu.compression;

import org.bytedeco.javacpp.IntPointer;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.buffer.DataTypeEx;
import org.nd4j.linalg.api.concurrency.AffinityManager;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.compression.CompressedDataBuffer;
import org.nd4j.linalg.compression.CompressionDescriptor;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.conditions.Conditions;

/**
 * This compression is very special case, and shouldn't be ever used outside of ParallelWrapper/ParameterServer implementation.
 * It encodes data as delta between zero and abs threshold.
 *
 * Unlike CpuThreshold codec, CpuFlexibleThreshold tries to target specified sparsity/density updates ratio via topN approach
 *
 * PLEASE NOTE: DO NOT USE THIS COMPRESSOR UNLESS YOU'RE 100% SURE WHAT YOU DO!
 *
 * @author raver119@gmail.com
 */
public class CpuFlexibleThreshold extends CpuThreshold {

    public CpuFlexibleThreshold() {
        super();
        this.threshold = 0.1f;
    }

    /**
     * This method returns compression descriptor. It should be unique for any compressor implementation
     *
     * @return
     */
    @Override
    public String getDescriptor() {
        return "FTHRESHOLD";
    }

    /**
     * This method allows you to configure desired sparsity/density ratio for updates. Pass it as float/double value
     *
     * Default value: 0.1
     * @param vars
     */
    @Override
    public void configure(Object... vars) {
        super.configure(vars);
    }


    @Override
    public DataBuffer compress(DataBuffer buffer) {
        INDArray temp = Nd4j.createArrayFromShapeBuffer(buffer, Nd4j.getShapeInfoProvider().createShapeInformation(new long[]{1, buffer.length()}, DataType.INT).getFirst());
        double max = temp.amaxNumber().doubleValue();

        int cntAbs = temp.scan(Conditions.absGreaterThanOrEqual(max - (max * threshold))).intValue();

        long originalLength = buffer.length() * Nd4j.sizeOfDataType(buffer.dataType());
        int compressedLength = cntAbs + 4;
        // first 3 elements contain header
        IntPointer pointer = new IntPointer(compressedLength);
        pointer.put(0, cntAbs);
        pointer.put(1, (int) buffer.length());
        pointer.put(2, Float.floatToIntBits(threshold)); // please note, this value will be ovewritten anyway
        pointer.put(3, 0);

        CompressionDescriptor descriptor = new CompressionDescriptor();
        descriptor.setCompressedLength(compressedLength * 4); // sizeOf(INT)
        descriptor.setOriginalLength(originalLength);
        descriptor.setOriginalElementSize(Nd4j.sizeOfDataType(buffer.dataType()));
        descriptor.setNumberOfElements(buffer.length());

        descriptor.setCompressionAlgorithm(getDescriptor());
        descriptor.setCompressionType(getCompressionType());

        CompressedDataBuffer cbuff = new CompressedDataBuffer(pointer, descriptor);

        Nd4j.getNDArrayFactory().convertDataEx(getBufferTypeEx(buffer), buffer.addressPointer(), DataTypeEx.FTHRESHOLD, pointer, buffer.length());

        Nd4j.getAffinityManager().tagLocation(buffer, AffinityManager.Location.HOST);

        return cbuff;
    }
}

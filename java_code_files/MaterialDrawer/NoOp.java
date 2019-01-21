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

import lombok.val;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.buffer.DataTypeEx;
import org.nd4j.linalg.api.ops.performance.PerformanceTracker;
import org.nd4j.linalg.compression.CompressedDataBuffer;
import org.nd4j.linalg.compression.CompressionDescriptor;
import org.nd4j.linalg.compression.CompressionType;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.memory.MemcpyDirection;

/**
 * Dummy NoOp compressor, that actually does no compression.
 *
 * @author raver119@gmail.com
 */
public class NoOp extends AbstractCompressor {
    /**
     * This method returns compression descriptor. It should be unique for any compressor implementation
     *
     * @return
     */
    @Override
    public String getDescriptor() {
        return "NOOP";
    }

    /**
     * This method returns compression opType provided by specific NDArrayCompressor implementation
     *
     * @return
     */
    @Override
    public CompressionType getCompressionType() {
        return CompressionType.LOSSLESS;
    }

    @Override
    public DataBuffer decompress(DataBuffer buffer, DataType type) {

        CompressedDataBuffer comp = (CompressedDataBuffer) buffer;

        DataBuffer result = Nd4j.createBuffer(comp.length(), false);
        Nd4j.getMemoryManager().memcpy(result, buffer);

        return result;
    }

    @Override
    public DataBuffer compress(DataBuffer buffer) {

        CompressionDescriptor descriptor = new CompressionDescriptor(buffer, this);

        BytePointer ptr = new BytePointer(buffer.length() * buffer.getElementSize());
        CompressedDataBuffer result = new CompressedDataBuffer(ptr, descriptor);

        Nd4j.getMemoryManager().memcpy(result, buffer);

        return result;
    }

    @Override
    protected CompressedDataBuffer compressPointer(DataTypeEx srcType, Pointer srcPointer, int length,
                                                   int elementSize) {

        CompressionDescriptor descriptor = new CompressionDescriptor();
        descriptor.setCompressionType(getCompressionType());
        descriptor.setOriginalLength(length * elementSize);
        descriptor.setCompressionAlgorithm(getDescriptor());
        descriptor.setOriginalElementSize(elementSize);
        descriptor.setCompressedLength(length * elementSize);
        descriptor.setNumberOfElements(length);

        BytePointer ptr = new BytePointer(length * elementSize);

        val perfD = PerformanceTracker.getInstance().helperStartTransaction();

        // this Pointer.memcpy is used intentionally. This method operates on host memory ALWAYS
        Pointer.memcpy(ptr, srcPointer, length * elementSize);

        PerformanceTracker.getInstance().helperRegisterTransaction(0, perfD, length * elementSize, MemcpyDirection.HOST_TO_HOST);

        CompressedDataBuffer buffer = new CompressedDataBuffer(ptr, descriptor);

        return buffer;
    }
}

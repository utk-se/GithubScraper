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

package org.nd4j.linalg.compression;

import lombok.Data;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A compression descriptor containing the
 * compression opType, compression algorithm,
 * original length, compressed length,
 * number of elements, and the original
 * element size
 *
 * @author raver119@gmail.com
 */
@Data
public class CompressionDescriptor implements Cloneable, Serializable {
    private CompressionType compressionType;
    private String compressionAlgorithm;
    private long originalLength;
    private long compressedLength;
    private long numberOfElements;
    private long originalElementSize;
    private DataType originalDataType;

    //40 bytes for the compression descriptor bytebuffer
    public final static int COMPRESSION_BYTE_BUFFER_LENGTH = 40;

    public CompressionDescriptor() {

    }

    /**
     * Create a  compression descriptor from the given
     * data buffer elements
     * @param buffer the databuffer to create
     *               the compression descriptor from
     */
    public CompressionDescriptor(DataBuffer buffer) {
        this.originalLength = buffer.length() * buffer.getElementSize();
        this.numberOfElements = buffer.length();
        this.originalElementSize = buffer.getElementSize();
        this.originalDataType = buffer.dataType();
    }

    /**
     * Initialize a compression descriptor
     * based on the given algorithm and data buffer
     * @param buffer the data buffer to base the sizes off of
     * @param algorithm the algorithm used
     *                  in the descriptor
     */
    public CompressionDescriptor(DataBuffer buffer, String algorithm) {
        this(buffer);
        this.compressionAlgorithm = algorithm;
    }

    /**
     * Initialize a compression descriptor
     * based on the given data buffer (for the sizes)
     * and the compressor to get the opType
     * @param buffer
     * @param compressor
     */
    public CompressionDescriptor(DataBuffer buffer, NDArrayCompressor compressor) {
        this(buffer, compressor.getDescriptor());
        this.compressionType = compressor.getCompressionType();
    }


    /**
     * Instantiate a compression descriptor from
     * the given bytebuffer
     * @param byteBuffer the bytebuffer to instantiate
     *                   the descriptor from
     * @return the instantiated descriptor based on the given
     * bytebuffer
     */
    public static CompressionDescriptor fromByteBuffer(ByteBuffer byteBuffer) {
        CompressionDescriptor compressionDescriptor = new CompressionDescriptor();
        //compression opType
        int compressionTypeOrdinal = byteBuffer.getInt();
        CompressionType compressionType = CompressionType.values()[compressionTypeOrdinal];
        compressionDescriptor.setCompressionType(compressionType);

        //compression algo
        int compressionAlgoOrdinal = byteBuffer.getInt();
        CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.values()[compressionAlgoOrdinal];
        compressionDescriptor.setCompressionAlgorithm(compressionAlgorithm.name());
        //from here everything is longs
        compressionDescriptor.setOriginalLength(byteBuffer.getLong());
        compressionDescriptor.setCompressedLength(byteBuffer.getLong());
        compressionDescriptor.setNumberOfElements(byteBuffer.getLong());
        compressionDescriptor.setOriginalElementSize(byteBuffer.getLong());
        compressionDescriptor.setOriginalDataType(DataType.values()[byteBuffer.getInt()]);
        return compressionDescriptor;
    }

    /**
     * Return a direct allocated
     * bytebuffer from the compression codec.
     * The size of the bytebuffer is calculated to be:
     * 40: 8 + 32
     * two ints representing their enum values
     * for the compression algorithm and opType
     *
     * and 4 longs for the compressed and
     * original sizes
     * @return the bytebuffer described above
     */
    public ByteBuffer toByteBuffer() {
        //3 ints  at 4 bytes a piece, this includes the compression algorithm
        //that we convert to enum
        int enumSize = 3 * 4;
        //4 longs at 8 bytes a piece
        int sizesLength = 4 * 8;
        ByteBuffer directAlloc = ByteBuffer.allocateDirect(enumSize + sizesLength).order(ByteOrder.nativeOrder());
        directAlloc.putInt(compressionType.ordinal());
        directAlloc.putInt(CompressionAlgorithm.valueOf(compressionAlgorithm).ordinal());
        directAlloc.putLong(originalLength);
        directAlloc.putLong(compressedLength);
        directAlloc.putLong(numberOfElements);
        directAlloc.putLong(originalElementSize);
        directAlloc.putInt(originalDataType.ordinal());
        directAlloc.rewind();
        return directAlloc;
    }

    @Override
    public CompressionDescriptor clone() {
        CompressionDescriptor descriptor = new CompressionDescriptor();
        descriptor.compressionType = this.compressionType;
        descriptor.compressionAlgorithm = this.compressionAlgorithm;
        descriptor.originalLength = this.originalLength;
        descriptor.compressedLength = this.compressedLength;
        descriptor.numberOfElements = this.numberOfElements;
        descriptor.originalElementSize = this.originalElementSize;
        descriptor.originalDataType = this.originalDataType;

        return descriptor;
    }
}

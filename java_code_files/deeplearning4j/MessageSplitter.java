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

package org.nd4j.parameterserver.distributed.v2.util;

import lombok.NonNull;
import lombok.val;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.primitives.AtomicBoolean;
import org.nd4j.linalg.util.ND4JFileUtils;
import org.nd4j.linalg.util.SerializationUtils;
import org.nd4j.parameterserver.distributed.v2.chunks.ChunksTracker;
import org.nd4j.parameterserver.distributed.v2.chunks.impl.FileChunksTracker;
import org.nd4j.parameterserver.distributed.v2.chunks.VoidChunk;
import org.nd4j.parameterserver.distributed.v2.chunks.impl.InmemoryChunksTracker;
import org.nd4j.parameterserver.distributed.v2.messages.VoidMessage;
import org.nd4j.linalg.primitives.Optional;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class provides methods for splitting VoidMessages into chunks, and merging them back again
 *
 * @author raver119@gmail.com
 */
public class MessageSplitter {
    private static final MessageSplitter INSTANCE = new MessageSplitter();

    protected Map<String, ChunksTracker> trackers = new ConcurrentHashMap<>();

    // simple counter for memory used by all in-memory trackers
    protected final AtomicLong memoryUse = new AtomicLong(0);

    public MessageSplitter() {
        //
    }

    /**
     * This method returns shared instance of MessageSplitter
     *
     * @return
     */
    public static MessageSplitter getInstance() {
        return INSTANCE;
    }

    /**
     * This method splits VoidMessage into chunks, and returns them as Collection
     * @param message
     * @return
     */
    public Collection<VoidChunk> split(@NonNull VoidMessage message, int maxBytes) throws IOException {
        if (maxBytes <= 0)
            throw new ND4JIllegalStateException("MaxBytes must be > 0");

        val tempFile = ND4JFileUtils.createTempFile("messageSplitter","temp");
        val result = new ArrayList<VoidChunk>();

        try (val fos = new FileOutputStream(tempFile); val bos = new BufferedOutputStream(fos)) {
            // serializing original message to disc
            SerializationUtils.serialize(message, fos);

            val length = tempFile.length();
            int numChunks = (int) (length /  maxBytes + (length % maxBytes > 0 ? 1 : 0));
            try (val fis = new FileInputStream(tempFile); val bis = new BufferedInputStream(fis)) {
                // now we'll be reading serialized message into
                val bytes = new byte[maxBytes];
                int cnt = 0;
                int id = 0;

                while (cnt < length) {
                    val c = bis.read(bytes);

                    val tmp = Arrays.copyOf(bytes, c);

                    // FIXME: we don't really want UUID used here, it's just a placeholder for now
                    val msg = VoidChunk.builder()
                            .messageId(java.util.UUID.randomUUID().toString())
                            .originalId(message.getMessageId())
                            .chunkId(id++)
                            .numberOfChunks(numChunks)
                            .splitSize(maxBytes)
                            .payload(tmp)
                            .totalSize(length)
                            .build();

                    result.add(msg);
                    cnt += c;
                }
            }
        }

        tempFile.delete();
        return result;
    }


    /**
     * This method checks, if specified message Id is being tracked
     * @param messageId
     * @return true if tracked, and false otherwise
     */
    boolean isTrackedMessage(@NonNull String messageId) {
        return trackers.containsKey(messageId);
    }

    /**
     * This method checks, if specified message is being tracked
     * @param chunk
     * @return true if tracked, and false otherwise
     */
    boolean isTrackedMessage(@NonNull VoidChunk chunk) {
        return isTrackedMessage(chunk.getOriginalId());
    }


    /**
     * This method tries to merge using files tracker
     *
     * @param chunk
     * @param <T>
     * @return
     */
    public <T extends VoidMessage> Optional<T> merge(@NonNull VoidChunk chunk) {
        return merge(chunk, -1L);
    }

    /**
     * This method removes specified messageId from tracking
     *
     * @param messageId
     */
    public void release(String messageId) {
        //
    }

    /**
     *
     * @param chunk
     * @param memoryLimit
     * @param <T>
     * @return
     */
    public <T extends VoidMessage> Optional<T> merge(@NonNull VoidChunk chunk, long memoryLimit) {
        val originalId= chunk.getOriginalId();
        val checker = new AtomicBoolean(false);
        ChunksTracker tracker = null;

        if (memoryUse.get() + chunk.getTotalSize() < memoryLimit) {
            tracker = new InmemoryChunksTracker(chunk);
            tracker = trackers.putIfAbsent(originalId, tracker);
            if (tracker == null) {
                memoryUse.addAndGet(chunk.getTotalSize());
            }
        } else {
            tracker = new FileChunksTracker(chunk);
            tracker = trackers.putIfAbsent(originalId, tracker);
        }

        if (tracker == null)
            tracker = trackers.get(chunk.getOriginalId());

        if (tracker.append(chunk)) {
            try {
                return Optional.of((T) tracker.getMessage());
            } finally {
                // we should decrease  memory amouint
                if (tracker instanceof InmemoryChunksTracker)
                    memoryUse.addAndGet(-chunk.getTotalSize());

                tracker.release();

                trackers.remove(chunk.getOriginalId());
            }
        } else
            return Optional.empty();
    }

    public void reset() {
        memoryUse.set(0);
        trackers.clear();
    }
}

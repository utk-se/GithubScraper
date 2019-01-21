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

package org.nd4j.jita.memory.impl;

import org.bytedeco.javacpp.Pointer;
import org.nd4j.jita.allocator.enums.AllocationStatus;
import org.nd4j.jita.allocator.impl.AllocationPoint;
import org.nd4j.jita.allocator.impl.AllocationShape;
import org.nd4j.jita.allocator.pointers.CudaPointer;
import org.nd4j.jita.allocator.pointers.PointersPair;
import org.nd4j.jita.allocator.utils.AllocationUtils;
import org.nd4j.jita.conf.Configuration;
import org.nd4j.jita.conf.CudaEnvironment;
import org.nd4j.jita.memory.MemoryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * This is MemoryProvider implementation, that adds cache for memory reuse purposes. Only host memory is cached for future reuse.
 *
 * If some memory chunk gets released via allocator, it'll be probably saved for future reused within same JVM process.
 *
 * @author raver119@gmail.com
 */
public class CudaCachingZeroProvider extends CudaDirectProvider implements MemoryProvider {
    private static Logger log = LoggerFactory.getLogger(CudaCachingZeroProvider.class);

    protected volatile ConcurrentHashMap<AllocationShape, CacheHolder> zeroCache = new ConcurrentHashMap<>();

    protected final AtomicLong cacheZeroHit = new AtomicLong(0);
    protected final AtomicLong cacheZeroMiss = new AtomicLong(0);

    protected final AtomicLong cacheDeviceHit = new AtomicLong(0);
    protected final AtomicLong cacheDeviceMiss = new AtomicLong(0);



    private final AtomicLong allocRequests = new AtomicLong(0);

    protected final AtomicLong zeroCachedAmount = new AtomicLong(0);
    protected List<AtomicLong> deviceCachedAmount = new ArrayList<>();


    protected final Semaphore singleLock = new Semaphore(1);

    // we don't cache allocations greater then this value
    //protected final long MAX_SINGLE_ALLOCATION = configuration.getMaximumHostCacheableLength();

    // maximum cached size of memory
    //protected final long MAX_CACHED_MEMORY = configuration.getMaximumHostCache();

    // memory chunks below this threshold will be guaranteed regardless of number of cache entries
    // that especially covers all possible variations of shapeInfoDataBuffers in all possible cases
    protected final long FORCED_CACHE_THRESHOLD = 96;

    //  number of preallocation entries for each yet-unknown shape
    //protected final int PREALLOCATION_LIMIT = configuration.getPreallocationCalls();

    public CudaCachingZeroProvider() {

    }

    /**
     * This method provides PointersPair to memory chunk specified by AllocationShape
     *
     * PLEASE NOTE: This method can actually ignore malloc request, and give out previously cached free memory chunk with equal shape.
     *
     * @param shape shape of desired memory chunk
     * @param point target AllocationPoint structure
     * @param location either HOST or DEVICE
     * @return
     */
    @Override
    public PointersPair malloc(AllocationShape shape, AllocationPoint point, AllocationStatus location) {
        long reqMemory = AllocationUtils.getRequiredMemory(shape);

        if (location == AllocationStatus.HOST && reqMemory < CudaEnvironment.getInstance().getConfiguration().getMaximumHostCacheableLength()) {

            CacheHolder cache = zeroCache.get(shape);
            if (cache != null) {
                Pointer pointer = cache.poll();
                if (pointer != null) {
                    cacheZeroHit.incrementAndGet();

                    // since this memory chunk is going to be used now, remove it's amount from
                    zeroCachedAmount.addAndGet(-1 * reqMemory);

                    PointersPair pair = new PointersPair();
                    pair.setDevicePointer(new CudaPointer(pointer.address()));
                    pair.setHostPointer(new CudaPointer(pointer.address()));

                    point.setAllocationStatus(AllocationStatus.HOST);
                    return pair;
                }
            }
            cacheZeroMiss.incrementAndGet();

            if (CudaEnvironment.getInstance().getConfiguration().isUsePreallocation() && zeroCachedAmount.get() < CudaEnvironment.getInstance().getConfiguration().getMaximumHostCache() / 10
                            && reqMemory < 16 * 1024 * 1024L) {
                CachePreallocator preallocator = new CachePreallocator(shape, location, CudaEnvironment.getInstance().getConfiguration().getPreallocationCalls());
                preallocator.start();
            }

            cacheZeroMiss.incrementAndGet();
            return super.malloc(shape, point, location);
        }

        return super.malloc(shape, point, location);
    }



    protected void ensureCacheHolder(AllocationShape shape) {
        if (!zeroCache.containsKey(shape)) {
            try {
                singleLock.acquire();
                if (!zeroCache.containsKey(shape)) {
                    zeroCache.put(shape, new CacheHolder(shape, zeroCachedAmount));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                singleLock.release();
            }
        }

    }

    /**
     * This method frees specific chunk of memory, described by AllocationPoint passed in.
     *
     * PLEASE NOTE: This method can actually ignore free, and keep released memory chunk for future reuse.
     *
     * @param point
     */
    @Override
    public void free(AllocationPoint point) {
        if (point.getAllocationStatus() == AllocationStatus.DEVICE) {
            super.free(point);
        } else {
            AllocationShape shape = point.getShape();
            long reqMemory = AllocationUtils.getRequiredMemory(shape);

            // we don't cache too big objects
            if (reqMemory > CudaEnvironment.getInstance().getConfiguration().getMaximumHostCacheableLength() || zeroCachedAmount.get() >= CudaEnvironment.getInstance().getConfiguration().getMaximumHostCache()) {
                //log.info("HOST memory purging: {} bytes; MS: {}; MT: {}", reqMemory, MAX_SINGLE_ALLOCATION, MAX_CACHED_MEMORY);
                super.free(point);
                return;
            }

            ensureCacheHolder(shape);

            //log.info("Saving DEVICE memory into cache...");

            /*
                Now we should decide if this object can be cached or not
             */
            CacheHolder cache = zeroCache.get(shape);

            // memory chunks < threshold will be cached no matter what
            if (reqMemory <= FORCED_CACHE_THRESHOLD) {
                Pointer.memset(point.getHostPointer(), 0, reqMemory);
                cache.put(new CudaPointer(point.getHostPointer().address()));
            } else {
                long cacheEntries = cache.size();
                long cacheHeight = zeroCache.size();

                // total memory allocated within this bucket
                long cacheDepth = cacheEntries * reqMemory;

                //   if (cacheDepth < MAX_CACHED_MEMORY / cacheHeight) {
                Pointer.memset(point.getHostPointer(), 0, reqMemory);
                cache.put(new CudaPointer(point.getHostPointer().address()));
                //    } else {
                //       super.free(point);
                //    }
            }
        }
    }

    private float getZeroCacheHitRatio() {
        long totalHits = cacheZeroHit.get() + cacheZeroMiss.get();
        float cacheRatio = cacheZeroHit.get() * 100 / (float) totalHits;
        return cacheRatio;
    }

    private float getDeviceCacheHitRatio() {
        long totalHits = cacheDeviceHit.get() + cacheDeviceMiss.get();
        float cacheRatio = cacheDeviceHit.get() * 100 / (float) totalHits;
        return cacheRatio;
    }

    @Deprecated
    public void printCacheStats() {
        log.debug("Cached host amount: " + zeroCachedAmount.get());
        log.debug("Cached device amount: " + deviceCachedAmount.get(0).get());
        log.debug("Total shapes in cache: " + zeroCache.size());
        log.debug("Current host hit ratio: " + getZeroCacheHitRatio());
        log.debug("Current device hit ratio: " + getDeviceCacheHitRatio());
    }

    protected class CacheHolder {
        private Queue<Pointer> queue = new ConcurrentLinkedQueue<>();
        private AtomicInteger counter = new AtomicInteger(0);
        private long reqMem = 0;
        private final AtomicLong allocCounter;

        public CacheHolder(AllocationShape shape, AtomicLong counter) {
            this.reqMem = AllocationUtils.getRequiredMemory(shape);
            this.allocCounter = counter;
        }

        public int size() {
            return counter.get();
        }

        public Pointer poll() {
            Pointer pointer = queue.poll();
            if (pointer != null)
                counter.decrementAndGet();

            return pointer;
        }

        public void put(Pointer pointer) {
            allocCounter.addAndGet(reqMem);
            counter.incrementAndGet();
            queue.add(pointer);
        }
    }

    protected class CachePreallocator extends Thread implements Runnable {

        private AllocationShape shape;
        private AllocationStatus location;
        private int target;

        public CachePreallocator(AllocationShape shape, AllocationStatus location, int numberOfEntries) {
            this.shape = shape;
            this.target = numberOfEntries;
            this.location = location;
        }

        @Override
        public void run() {
            //            log.info("Precaching ["+target+"] chunks for shape: " + shape);

            ensureCacheHolder(shape);

            for (int i = 0; i < target; i++) {
                AllocationPoint point = new AllocationPoint();

                PointersPair pair = CudaCachingZeroProvider.super.malloc(shape, point, this.location);
                if (this.location == AllocationStatus.HOST) {
                    Pointer pointer = new CudaPointer(pair.getHostPointer().address());
                    CudaCachingZeroProvider.this.zeroCache.get(shape).put(pointer);
                }
            }
        }
    }

    @Override
    public void purgeCache() {
        for (AllocationShape shape : zeroCache.keySet()) {
            Pointer ptr = null;
            while ((ptr = zeroCache.get(shape).poll()) != null) {
                freeHost(ptr);
            }
        }

        zeroCachedAmount.set(0);
    }
}

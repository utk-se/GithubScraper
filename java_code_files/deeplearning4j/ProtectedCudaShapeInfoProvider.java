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

package org.nd4j.jita.constant;

import lombok.extern.slf4j.Slf4j;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.api.shape.options.ArrayOptionsHelper;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.jita.allocator.impl.AtomicAllocator;
import org.nd4j.jita.conf.Configuration;
import org.nd4j.jita.conf.CudaEnvironment;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.BaseShapeInfoProvider;
import org.nd4j.linalg.api.shape.ShapeDescriptor;
import org.nd4j.linalg.factory.Nd4j;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author raver119@gmail.com
 */
@Slf4j
public class ProtectedCudaShapeInfoProvider extends BaseShapeInfoProvider {

    private AtomicAllocator allocator;

    private AtomicLong cacheHit = new AtomicLong(1);
    private AtomicLong cacheMiss = new AtomicLong(1);

    private Semaphore lock = new Semaphore(1);

    protected static final ConstantProtector protector = ConstantProtector.getInstance();

    private static ProtectedCudaShapeInfoProvider ourInstance = new ProtectedCudaShapeInfoProvider();


    private ProtectedCudaShapeInfoProvider() {

    }

    /**
     * This method forces cache purge, if cache is available for specific implementation
     */
    @Override
    public void purgeCache() {
        protector.purgeProtector();
    }

    public static ProtectedCudaShapeInfoProvider getInstance() {
        return ourInstance;
    }


    @Override
    public Pair<DataBuffer, long[]> createShapeInformation(long[] shape, long[] stride, long elementWiseStride, char order, DataType type) {
        long extras = ArrayOptionsHelper.setOptionBit(0L, type);
        return createShapeInformation(shape, stride, elementWiseStride, order, extras);
    }

    @Override
    public Pair<DataBuffer, long[]> createShapeInformation(long[] shape, long[] stride, long elementWiseStride, char order, long extras) {
        // We enforce offset to 0 in shapeBuffer, since we need it for cache efficiency + we don't actually use offset value @ native side
        long offset = 0;

        Integer deviceId = AtomicAllocator.getInstance().getDeviceId();

        LongShapeDescriptor descriptor = new LongShapeDescriptor(shape, stride, offset, elementWiseStride, order, extras);

        if (!protector.containsDataBuffer(deviceId, descriptor)) {
            Pair<DataBuffer, long[]> buffer = null;
            synchronized (this) {
                if (!protector.containsDataBuffer(deviceId, descriptor)) {
                    //log.info("Cache miss: {}", descriptor);
                    buffer = super.createShapeInformation(shape, stride, elementWiseStride, order, extras);
                    buffer.getFirst().setConstant(true);

                    if (CudaEnvironment.getInstance().getConfiguration().getMemoryModel() == Configuration.MemoryModel.IMMEDIATE) {
                        Nd4j.getConstantHandler().moveToConstantSpace(buffer.getFirst());
                    }

                    //deviceCache.get(deviceId).put(descriptor, buffer);
                    protector.persistDataBuffer(deviceId, descriptor, buffer);

                    bytes.addAndGet(buffer.getFirst().length() * 8 * 2);

                    cacheMiss.incrementAndGet();
                } else {
                    buffer = protector.getDataBuffer(deviceId, descriptor);
                }
            }
            return buffer;
        } else {
            //       log.info("Cache hit: {}", descriptor);
            cacheHit.incrementAndGet();
        }

        return protector.getDataBuffer(deviceId, descriptor); //deviceCache.get(deviceId).get(descriptor);
    }
}

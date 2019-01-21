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

package org.nd4j.jita.flow.impl;

import lombok.Getter;
import org.nd4j.jita.allocator.Allocator;
import org.nd4j.jita.allocator.context.ContextPack;
import org.nd4j.jita.allocator.enums.AllocationStatus;
import org.nd4j.jita.allocator.enums.CudaConstants;
import org.nd4j.jita.allocator.impl.AllocationPoint;
import org.nd4j.jita.allocator.pointers.cuda.cudaEvent_t;
import org.nd4j.jita.allocator.pointers.cuda.cudaStream_t;
import org.nd4j.jita.allocator.time.TimeProvider;
import org.nd4j.jita.allocator.time.providers.OperativeProvider;
import org.nd4j.jita.allocator.utils.AllocationUtils;
import org.nd4j.jita.concurrency.EventsProvider;
import org.nd4j.jita.conf.Configuration;
import org.nd4j.jita.conf.CudaEnvironment;
import org.nd4j.jita.flow.FlowController;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.jcublas.context.CudaContext;
import org.nd4j.nativeblas.NativeOps;
import org.nd4j.nativeblas.NativeOpsHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Experimental code, do not use please.
 *
 * @author raver119@gmail.com
 */
@Deprecated
public class AsynchronousFlowController implements FlowController {
    private volatile Allocator allocator;

    private static final Configuration configuration = CudaEnvironment.getInstance().getConfiguration();

    private static Logger log = LoggerFactory.getLogger(AsynchronousFlowController.class);

    protected NativeOps nativeOps = NativeOpsHolder.getInstance().getDeviceNativeOps();

    @Getter
    protected EventsProvider eventsProvider = new EventsProvider();

    private transient TimeProvider timeProvider = new OperativeProvider();

    protected AtomicLong asyncHit = new AtomicLong(0);
    protected AtomicLong asyncMiss = new AtomicLong(0);

    protected Map<Integer, AtomicLong> lanesCounter = new ConcurrentHashMap<>();

    private AtomicLong totalHits = new AtomicLong(0);

    protected static final int MAX_EXECUTION_QUEUE = configuration.getCommandQueueLength();

    protected static final AtomicLong eventCounts = new AtomicLong(0);

    protected ArrayList<ArrayList<Queue<cudaEvent_t>>> eventsBarrier = new ArrayList<>();
    protected ArrayList<ArrayList<AtomicLong>> laneClocks = new ArrayList<>();
    protected ArrayList<AtomicLong> deviceClocks = new ArrayList<>();

    public AsynchronousFlowController() {
        int numLanes = configuration.getCommandLanesNumber();
        int numDevices = Nd4j.getAffinityManager().getNumberOfDevices();

        for (int d = 0; d < numDevices; d++) {
            eventsBarrier.add(d, new ArrayList<Queue<cudaEvent_t>>());
            laneClocks.add(d, new ArrayList<AtomicLong>());
            deviceClocks.add(d, new AtomicLong(0));
            for (int l = 0; l < numLanes; l++) {
                eventsBarrier.get(d).add(l, new ConcurrentLinkedQueue<cudaEvent_t>());
                laneClocks.get(d).add(l, new AtomicLong(0));
            }
        }

    }

    @Override
    public void synchronizeToDevice(AllocationPoint point) {

    }

    @Override
    public void init(Allocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public void synchronizeToHost(AllocationPoint point) {
        if (!point.isActualOnHostSide()) {

            if (!point.isConstant())
                waitTillFinished(point);

            //  log.info("Synchronization started... " + point.getShape());

            // if this piece of memory is device-dependant, we'll also issue copyback once
            if (point.getAllocationStatus() == AllocationStatus.DEVICE && !point.isActualOnHostSide()) {
                CudaContext context = (CudaContext) allocator.getDeviceContext().getContext();

                if (nativeOps.memcpyAsync(point.getHostPointer(), point.getDevicePointer(),
                                AllocationUtils.getRequiredMemory(point.getShape()),
                                CudaConstants.cudaMemcpyDeviceToHost, context.getSpecialStream()) == 0)
                    throw new IllegalStateException("MemcpyAsync D2H failed: [" + point.getDevicePointer().address()
                                    + "] -> [" + point.getHostPointer().address() + "]");

                commitTransfer(context.getSpecialStream());
            } // else log.info("Not [DEVICE] memory, skipping...");

            // updating host read timer
            point.tickHostRead();
            //log.info("After sync... isActualOnHostSide: {}", point.isActualOnHostSide());
        }
    }

    @Override
    public void waitTillFinished(AllocationPoint point) {
        cudaEvent_t event = point.getWriteLane();
        if (event != null) {
            event.synchronize();
            event.destroy();
        }
    }

    public void waitTillReleased(AllocationPoint point) {
        waitTillFinished(point);

        cudaEvent_t event;
        while ((event = point.getReadLane().poll()) != null) {
            event.synchronize();
            event.destroy();
        }
    }

    @Override
    public void registerAction(CudaContext context, INDArray result, INDArray... operands) {
        // TODO: this should be lane-dependant context

        if (totalHits.incrementAndGet() % 25000 == 0) {
            log.debug("AsyncHit ratio: [{}]", getAsyncHitRatio());
            /*
            for (int lane = 0; lane < allocator.getContextPool().acquireContextPackForDevice(0).getAvailableLanes(); lane++) {
                log.debug("Lane [{}]: {} ", lane, lanesCounter.get(lane).get());
            }
            */
        }

        cudaEvent_t event = new cudaEvent_t(nativeOps.createEvent());
        event.setLaneId(context.getLaneId());
        nativeOps.registerEvent(event, context.getOldStream());

        if (result != null) {
            setWriteLane(result, event);
            allocator.tickDeviceWrite(result);
        }

        for (INDArray operand : operands) {
            if (operand == null)
                continue;

            setReadLane(operand, event);
        }

        Integer deviceId = allocator.getDeviceId();
        fillTail(deviceId, event.getLaneId(), event);
    }

    @Override
    public void registerActionAllWrite(CudaContext context, INDArray... operands) {

    }

    protected void setWriteLane(INDArray array, cudaEvent_t event) {
        AllocationPoint point = allocator.getAllocationPoint(array);

        point.setWriteLane(event);
    }

    protected void setReadLane(INDArray array, cudaEvent_t event) {
        AllocationPoint point = allocator.getAllocationPoint(array);

        point.addReadLane(event);
    }

    protected Queue<cudaEvent_t> getReadLanes(INDArray array) {
        AllocationPoint point = allocator.getAllocationPoint(array);

        return point.getReadLane();
    }

    protected cudaEvent_t getWriteLane(INDArray array) {
        AllocationPoint point = allocator.getAllocationPoint(array);

        return point.getWriteLane();
    }

    protected int hasActiveWrite(INDArray array) {
        if (array == null)
            return -1;

        cudaEvent_t event = getWriteLane(array);
        if (event == null || event.isDestroyed())
            return -1;

        return event.getLaneId();
    }

    protected int hasActiveWrite(AllocationPoint point) {

        cudaEvent_t event = point.getWriteLane();
        if (event == null || event.isDestroyed())
            return -1;

        return event.getLaneId();
    }

    protected boolean hasActiveReads(AllocationPoint point) {
        Queue<cudaEvent_t> events = point.getReadLane();

        if (events.size() == 0)
            return false;

        AtomicBoolean result = new AtomicBoolean(false);
        List<cudaEvent_t> asList = new ArrayList<>(events);
        for (cudaEvent_t event : asList) {
            if (event == null)
                continue;

            // we mark this AllocationPoint is pending read, if at least one event isn't destroyed yet
            result.compareAndSet(false, !event.isDestroyed());
        }

        return result.get();
    }

    protected boolean hasActiveReads(INDArray array) {
        if (array == null)
            return false;

        AllocationPoint point = allocator.getAllocationPoint(array);

        return hasActiveReads(point);
    }

    protected boolean isMatchingLanes(int[] lanes) {
        if (lanes[0] == lanes[1] || lanes[1] == -1 || lanes[0] == -1)
            return true;
        return false;
    }

    protected boolean isMatchingLanes(int zLane, int[] lanes) {
        if ((zLane == lanes[0] || zLane == lanes[1]) && isMatchingLanes(lanes))
            return true;
        return false;
    }

    protected void synchronizeReadLanes(AllocationPoint point) {
        cudaEvent_t event;
        int cnt = 0;
        while ((event = point.getReadLane().poll()) != null) {
            event.synchronize();
            event.destroy();
            cnt++;
        }
        //  log.info("Events synchronized: [{}]", cnt);
    }

    protected void synchronizeReadLanes(INDArray array) {
        if (array == null)
            return;

        AllocationPoint point = allocator.getAllocationPoint(array);
        synchronizeReadLanes(point);
    }

    @Override
    public void registerAction(CudaContext context, AllocationPoint result, AllocationPoint... operands) {
        cudaEvent_t event = new cudaEvent_t(nativeOps.createEvent());
        event.setLaneId(context.getLaneId());
        nativeOps.registerEvent(event, context.getOldStream());

        result.setWriteLane(event);

        Integer deviceId = allocator.getDeviceId();
        fillTail(deviceId, event.getLaneId(), event);
    }

    @Override
    public CudaContext prepareAction(AllocationPoint result, AllocationPoint... operands) {
        if (hasActiveReads(result))
            synchronizeReadLanes(result);

        ContextPack pack = allocator.getContextPool().acquireContextPackForDevice(allocator.getDeviceId());

        return pack.getContextForLane(pack.nextRandomLane());
    }


    protected int pickFirstLane(int[] lanes) {
        if (lanes[0] >= 0)
            return lanes[0];
        else if (lanes[1] >= 0)
            return lanes[1];

        return 0;
    }

    @Override
    public CudaContext prepareAction(INDArray result, INDArray... operands) {

        /**
         * This method should decide, which CUDA stream should be used for execution, based on data affinity
         * Decision is made based on data affinity, at INDArray level solely.
         */

        ContextPack pack = allocator.getContextPool().acquireContextPackForDevice(allocator.getDeviceId());

        // for result holding lane do not really matters, only depending lanes to matter, because they are used to read
        // default lane is lane_0
        int newLane = 0;
        int zLane = hasActiveWrite(result);
        boolean zReads = hasActiveReads(result);

        if (result != null && (zReads || zLane >= 0)) {
            // we send this op to the same lane as active read/write event


            // but we still have to check, if op.X and op.Y has pending writes on other lanes
            //  log.info("Busy Z dep: [{}], hasReads: [{}]", zLane, zReads);

            AtomicInteger cnt = new AtomicInteger(0);
            AtomicInteger holdersCount = new AtomicInteger(0);
            int lastLane = -1;
            //int pendingLanes[] = new int[]{-1, -1};

            // FIXME: this is wrong.
            int pendingLanes[] = new int[operands.length + 1];
            Arrays.fill(pendingLanes, -1);

            for (INDArray operand : operands) {
                if (operand == null)
                    continue;

                int lane = hasActiveWrite(operand);
                if (lane >= 0) {
                    // at least one operand has pendingWrite. And we don't care about pending reads.
                    pendingLanes[cnt.get()] = lane;
                    holdersCount.incrementAndGet();
                    lastLane = lane;
                }
                cnt.incrementAndGet();
            }

            if (zReads) {
                //      log.info("Synchronizing zReads");
                synchronizeReadLanes(result);
            }

            if (holdersCount.get() > 0) {
                asyncMiss.incrementAndGet();

                if (isMatchingLanes(zLane, pendingLanes)) {
                    //   log.info("All matching lanes additional deps in [{}] -> [{}, {}]", zLane, pendingLanes[0], pendingLanes[1]);
                    if (zLane >= 0)
                        newLane = zLane;
                    else
                        newLane = pickFirstLane(pendingLanes);
                } else {
                    //   log.info("Mismatching lanes additional deps in [{}] -> [{}, {}]", zLane, pendingLanes[0], pendingLanes[1]);
                    // now we must sync on both pendingLanes and pass data to zLane
                    if (zLane >= 0)
                        newLane = zLane;
                    else
                        newLane = pickFirstLane(pendingLanes);

                    for (INDArray operand : operands) {
                        if (operand == null)
                            continue;

                        waitTillFinished(allocator.getAllocationPoint(operand));
                    }
                }
            } else {
                //      log.info("Only Z is holder: [{}]", zLane);

                asyncHit.incrementAndGet();

                if (zLane < 0)
                    zLane = pack.nextRandomLane();

                newLane = zLane;
            }
        } else {
            // we go and check op.X and op.Y
            AtomicInteger cnt = new AtomicInteger(0);
            AtomicInteger holdersCount = new AtomicInteger(0);
            int lastLane = -1;

            // FIXME: this is wrong.
            //int pendingLanes[] = new int[]{-1, -1, -1, -1};
            int pendingLanes[] = new int[operands.length + 1];
            Arrays.fill(pendingLanes, -1);

            for (INDArray operand : operands) {
                if (operand == null)
                    continue;

                int lane = hasActiveWrite(operand);
                if (lane >= 0) {
                    // at least one operand has pendingWrite. And we don't care about pending reads.
                    pendingLanes[cnt.get()] = lane;
                    holdersCount.incrementAndGet();
                    lastLane = lane;
                }
                cnt.incrementAndGet();
            }

            if (holdersCount.get() > 0) {
                // we have some holders here
                asyncMiss.incrementAndGet();
                if (isMatchingLanes(pendingLanes)) {
                    // if op.X and/or op.Y has pending write in same lane - just throw op to that lane, and enjoy
                    newLane = lastLane;
                    //     log.info("Paired dependencies: [{}]", newLane);
                } else {
                    // we have different lanes for op.X and op.Y with pending write. We need to synchronize somewhere to become free.
                    // basically - synchronize on one lane, and throw task to another one
                    //log.info("Unpaired dependencies: [{}, {}]", pendingLanes[0], pendingLanes[1]);
                    if (pendingLanes[0] >= 0) {
                        waitTillFinished(allocator.getAllocationPoint(operands[0]));
                        newLane = pendingLanes[1];
                    } else if (pendingLanes[1] >= 0) {
                        waitTillFinished(allocator.getAllocationPoint(operands[1]));
                        newLane = pendingLanes[0];
                    }
                }
            } else {
                // we don't have any holders here. Totally free execution here
                asyncHit.incrementAndGet();

                newLane = pack.nextRandomLane();

                //   log.info("Free pass here: [{}]", newLane);
            }
        }



        CudaContext context = pack.getContextForLane(newLane);

        if (result != null)
            allocator.getAllocationPoint(result).setCurrentContext(context);

        for (INDArray operand : operands) {
            if (operand == null)
                continue;

            allocator.getAllocationPoint(operand).setCurrentContext(context);
        }

        if (!lanesCounter.containsKey(newLane)) {
            lanesCounter.put(newLane, new AtomicLong(0));
        }


        lanesCounter.get(newLane).incrementAndGet();

        if (context == null)
            throw new IllegalStateException("Context shouldn't be null: " + newLane);


        return context;
    }

    @Override
    public CudaContext prepareActionAllWrite(INDArray... operands) {
        return null;
    }

    private float getAsyncHitRatio() {
        long totalHits = asyncHit.get() + asyncMiss.get();
        float cacheRatio = asyncHit.get() * 100 / (float) totalHits;
        return cacheRatio;
    }

    protected void fillTail(int deviceId, int lane, cudaEvent_t event) {
        eventsBarrier.get(deviceId).get(lane).add(event);
        long tick = deviceClocks.get(deviceId).incrementAndGet();
        laneClocks.get(deviceId).get(lane).set(tick);
    }

    /**
     * This method ensures the events in the beginning of FIFO queues are finished
     */
    protected void sweepTail() {
        Integer deviceId = allocator.getDeviceId();
        int cnt = 0;

        // we get number of issued commands for specific device
        long lastCommandId = deviceClocks.get(deviceId).get();

        for (int l = 0; l < configuration.getCommandLanesNumber(); l++) {
            Queue<cudaEvent_t> queue = eventsBarrier.get(deviceId).get(l);

            if (queue.size() >= MAX_EXECUTION_QUEUE
                            || laneClocks.get(deviceId).get(l).get() < lastCommandId - MAX_EXECUTION_QUEUE) {
                cudaEvent_t event = queue.poll();
                if (event != null && !event.isDestroyed()) {
                    event.synchronize();
                    event.destroy();
                    cnt++;
                }
            }

        }

        deviceClocks.get(deviceId).incrementAndGet();

        //  log.info("Events sweeped: [{}]", cnt);
    }


    protected void cutTail() {
        Integer deviceId = allocator.getDeviceId();

        for (int l = 0; l < configuration.getCommandLanesNumber(); l++) {
            Queue<cudaEvent_t> queue = eventsBarrier.get(deviceId).get(l);
            cudaEvent_t event;
            while ((event = queue.poll()) != null) {
                event.synchronize();
                event.destroy();
            }
        }
    }

    @Override
    public void commitTransfer(cudaStream_t streamUsed) {
        sweepTail();
        streamUsed.synchronize();
    }
}

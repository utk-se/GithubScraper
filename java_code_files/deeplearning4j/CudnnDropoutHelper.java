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

package org.deeplearning4j.nn.layers.dropout;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.*;
import org.deeplearning4j.nn.conf.dropout.DropoutHelper;
import org.deeplearning4j.nn.layers.BaseCudnnHelper;
import org.nd4j.jita.allocator.Allocator;
import org.nd4j.jita.allocator.impl.AtomicAllocator;
import org.nd4j.jita.conf.CudaEnvironment;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.jcublas.context.CudaContext;
import org.nd4j.linalg.util.ArrayUtil;
import org.nd4j.util.StringUtils;

import static org.bytedeco.javacpp.cudnn.*;
import static org.bytedeco.javacpp.cudnn.cudnnDestroyTensorDescriptor;

/**
 * CuDNN dropout helper
 *
 * Note that for repeatability between calls (for example, for gradient checks), we need to do two things:
 * (a) set the ND4J RNG seed
 * (b) clear the rngStates field
 *
 * @author Alex Black
 */
@Data
@Slf4j
public class CudnnDropoutHelper extends BaseCudnnHelper implements DropoutHelper {

    private static class CudnnDropoutContext extends CudnnContext {

        private static class Deallocator extends CudnnDropoutContext implements Pointer.Deallocator {
            Deallocator(CudnnDropoutContext c) {
                super(c);
            }

            @Override
            public void deallocate() {
                destroyHandles();
            }
        }

        private cudnn.cudnnTensorStruct xTensorDesc = new cudnn.cudnnTensorStruct();    //Input
        private cudnn.cudnnTensorStruct dxTensorDesc = new cudnn.cudnnTensorStruct();   //Grad at input
        private cudnn.cudnnTensorStruct yTensorDesc = new cudnn.cudnnTensorStruct();    //Output
        private cudnn.cudnnTensorStruct dyTensorDesc = new cudnn.cudnnTensorStruct();   //Grad at output
        private cudnn.cudnnDropoutStruct dropoutDesc = new cudnn.cudnnDropoutStruct();

        public CudnnDropoutContext() {
            createHandles();
            deallocator(new Deallocator(this));
        }

        public CudnnDropoutContext(CudnnDropoutContext c) {
            super(c);
            xTensorDesc = new cudnn.cudnnTensorStruct(c.xTensorDesc);
            dxTensorDesc = new cudnn.cudnnTensorStruct(c.dxTensorDesc);
            yTensorDesc = new cudnn.cudnnTensorStruct(c.yTensorDesc);
            dyTensorDesc = new cudnn.cudnnTensorStruct(c.dyTensorDesc);
            dropoutDesc = new cudnn.cudnnDropoutStruct(c.dropoutDesc);
        }

        @Override
        protected void createHandles() {
            super.createHandles();
            checkCudnn(cudnnCreateTensorDescriptor(xTensorDesc));
            checkCudnn(cudnnCreateTensorDescriptor(dxTensorDesc));
            checkCudnn(cudnnCreateTensorDescriptor(yTensorDesc));
            checkCudnn(cudnnCreateTensorDescriptor(dyTensorDesc));
            checkCudnn(cudnnCreateDropoutDescriptor(dropoutDesc));
        }

        @Override
        protected void destroyHandles() {
            checkCudnn(cudnnDestroyTensorDescriptor(xTensorDesc));
            checkCudnn(cudnnDestroyTensorDescriptor(dxTensorDesc));
            checkCudnn(cudnnDestroyTensorDescriptor(yTensorDesc));
            checkCudnn(cudnnDestroyTensorDescriptor(dyTensorDesc));
            checkCudnn(cudnnDestroyDropoutDescriptor(dropoutDesc));
            super.destroyHandles();
        }
    }

    private CudnnDropoutContext cudnnContext = new CudnnDropoutContext();
    private boolean initializedDescriptor = false;
    private DataCache rngStates;    //"Pointer to user-allocated GPU memory that will hold random number generator states."
    private DataCache mask;         //Mask: persistence between forward and backward
    private SizeTPointer stateSizeBytesPtr;
    private SizeTPointer reserveSizeBytesPtr;
    private float lastInitializedP;

    @Override
    public void applyDropout(INDArray input, INDArray resultArray, double dropoutInputRetainProb) {
        float p = (float)(1.0 - dropoutInputRetainProb);    //CuDNN uses p = probability of setting to 0. We use p = probability of retaining

        //TODO int cast
        int[] inShape = adaptForTensorDescr(ArrayUtil.toInts(input.shape()));
        int[] inStride = adaptForTensorDescr(ArrayUtil.toInts(input.stride()));
        checkCudnn(cudnnSetTensorNdDescriptor(cudnnContext.xTensorDesc, dataType, inShape.length, inShape, inStride));

        int[] outShape = adaptForTensorDescr(ArrayUtil.toInts(resultArray.shape()));
        int[] outStride = adaptForTensorDescr(ArrayUtil.toInts(resultArray.stride()));
        checkCudnn(cudnnSetTensorNdDescriptor(cudnnContext.yTensorDesc, dataType, outShape.length, outShape, outStride));


        if(stateSizeBytesPtr == null){
            stateSizeBytesPtr = new SizeTPointer(1);
            reserveSizeBytesPtr = new SizeTPointer(1);
        }
        checkCudnn(cudnnDropoutGetStatesSize(cudnnContext, stateSizeBytesPtr));
        long rngStateSizeBytes = stateSizeBytesPtr.get();
        checkCudnn(cudnnDropoutGetReserveSpaceSize(cudnnContext.xTensorDesc, reserveSizeBytesPtr));
        long maskReserveSizeBytes = reserveSizeBytesPtr.get();

        if(rngStates == null || rngStates.capacity() < rngStateSizeBytes){
            if(log.isTraceEnabled()){
                if(rngStates == null){
                    log.trace("CudnnDropoutHelper: Allocating intial RNG states workspace of size {} ({})", rngStateSizeBytes,
                            StringUtils.TraditionalBinaryPrefix.long2String(rngStateSizeBytes, "B", 2));
                } else {
                    log.trace("CudnnDropoutHelper: Deallocating RNG states of size {} ({}), allocating new workspace of size {} ({})",
                            rngStates.capacity(), StringUtils.TraditionalBinaryPrefix.long2String(rngStates.capacity(), "B", 2),
                            rngStateSizeBytes, StringUtils.TraditionalBinaryPrefix.long2String(rngStateSizeBytes, "B", 2));
                }
            }

            if(rngStates != null)
                rngStates.deallocate();
            //states = "Pointer to user-allocated GPU memory that will hold random number generator states."
            rngStates = new DataCache(rngStateSizeBytes);
            initializedDescriptor = false;
        }
        if(mask == null || mask.capacity() < maskReserveSizeBytes){
            if(log.isTraceEnabled()){
                if(mask == null){
                    log.trace("CudnnDropoutHelper: Allocating intial mask array of size {} ({})", maskReserveSizeBytes,
                            StringUtils.TraditionalBinaryPrefix.long2String(maskReserveSizeBytes, "B", 2));
                } else {
                    log.trace("CudnnDropoutHelper: Deallocating mask array of size {} ({}), allocating new mask array of size {} ({})",
                            mask.capacity(), StringUtils.TraditionalBinaryPrefix.long2String(mask.capacity(), "B", 2),
                            maskReserveSizeBytes, StringUtils.TraditionalBinaryPrefix.long2String(maskReserveSizeBytes, "B", 2));
                }
            }

            if(mask != null)
                mask.deallocate();
            //mask = "Pointer to user-allocated GPU memory used by this function. It is expected
            //that contents of reserveSpace doe not change between cudnnDropoutForward and
            //cudnnDropoutBackward calls."
            mask = new DataCache(maskReserveSizeBytes);
        }

        //Dropout descriptor: (re)initialize if required
        if(!initializedDescriptor || p != lastInitializedP) {
            if(log.isTraceEnabled()){
                log.trace("CudnnDropoutHelper: (re)initializing dropout descriptor");
            }
            //NOTE: cudnnSetDropoutDescriptor has some internal computation/initialization, and hence is expensive to
            // call - so we want to call this as infrequently as possible, and cache the result
            long seed = Nd4j.getRandom().nextLong();
            lastInitializedP = p;
            checkCudnn(cudnnSetDropoutDescriptor(cudnnContext.dropoutDesc, cudnnContext, p, rngStates, rngStates.capacity(), seed));
            initializedDescriptor = true;
        }

        Allocator allocator = AtomicAllocator.getInstance();
        CudaContext context = allocator.getFlowController().prepareAction(input, resultArray);
        Pointer xPtr = allocator.getPointer(input, context);
        Pointer yPtr = allocator.getPointer(resultArray, context);

        checkCudnn(cudnnSetStream(cudnnContext, new cuda.CUstream_st(context.getOldStream())));
        checkCudnn(cudnnDropoutForward(cudnnContext, cudnnContext.dropoutDesc, cudnnContext.xTensorDesc, xPtr,
                cudnnContext.yTensorDesc, yPtr, mask, mask.capacity()));

        allocator.registerAction(context, input, resultArray);
        if (CudaEnvironment.getInstance().getConfiguration().isDebug())
            context.syncOldStream();
    }

    @Override
    public void backprop(INDArray gradAtOutput, INDArray gradAtInput) {
        int[] gradAtOutShape = adaptForTensorDescr(ArrayUtil.toInts(gradAtOutput.shape()));
        int[] gradAtOutStride = adaptForTensorDescr(ArrayUtil.toInts(gradAtOutput.stride()));
        checkCudnn(cudnnSetTensorNdDescriptor(cudnnContext.dyTensorDesc, dataType, gradAtOutShape.length, gradAtOutShape, gradAtOutStride));

        int[] gradAtInShape = adaptForTensorDescr(ArrayUtil.toInts(gradAtInput.shape()));
        int[] gradAtInStride = adaptForTensorDescr(ArrayUtil.toInts(gradAtInput.stride()));
        checkCudnn(cudnnSetTensorNdDescriptor(cudnnContext.dxTensorDesc, dataType, gradAtInShape.length, gradAtInShape, gradAtInStride));

        Allocator allocator = AtomicAllocator.getInstance();
        CudaContext context = allocator.getFlowController().prepareAction(gradAtOutput, gradAtInput);
        Pointer dyPtr = allocator.getPointer(gradAtOutput, context);
        Pointer dxPtr = allocator.getPointer(gradAtInput, context);

        checkCudnn(cudnnDropoutBackward(cudnnContext, cudnnContext.dropoutDesc, cudnnContext.dyTensorDesc, dyPtr,
                cudnnContext.dxTensorDesc, dxPtr, mask, mask.capacity()));

        allocator.registerAction(context, gradAtOutput, gradAtInput);
        if (CudaEnvironment.getInstance().getConfiguration().isDebug())
            context.syncOldStream();
    }
}

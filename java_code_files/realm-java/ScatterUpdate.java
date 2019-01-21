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

package org.nd4j.linalg.api.ops.custom;

import lombok.NonNull;
import lombok.val;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.CustomOp;
import org.nd4j.linalg.api.ops.CustomOpDescriptor;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.List;

public class ScatterUpdate implements CustomOp {
    protected CustomOp op;

    // update operation: 0 - add; 1 - sub; 2 - mul; 3 - div; 4 - rsub; 5 - rdiv; 6 - assign
    public enum UpdateOp {
        ADD,
        SUBTRACT,
        MILTIPLY,
        DIVIDE,
        RSUBTRACT,
        RDIVIDE,
        ASSIGN,
    }

    public ScatterUpdate(@NonNull INDArray original, @NonNull INDArray updates, @NonNull int[] indices, int[] dimension, @NonNull UpdateOp op) {
        this(original, updates, null, indices, dimension, op);
    }

    public ScatterUpdate(@NonNull INDArray original, @NonNull INDArray updates, INDArray result, @NonNull int[] indices, int[] dimension, @NonNull UpdateOp op) {

        List<Integer> iargs = new ArrayList<>();
        iargs.add(op.ordinal());
        iargs.add(dimension.length);
        for (val v: dimension)
            iargs.add(v);

        iargs.add(indices.length);
        for (val v: indices)
            iargs.add(v);

        if (updates.tensorAlongDimension(0, dimension).lengthLong() != original.tensorAlongDimension(0, dimension).lengthLong())
            throw new ND4JIllegalStateException("ScatterUpdate requires equal shaped tensors for operation along given dimension(s)");

        long numTensors = original.tensorsAlongDimension(dimension);
        for (val idx: indices)
            if (idx >= numTensors)
                throw new ND4JIllegalStateException("Can't update index higher then num tensors");

        this.op = DynamicCustomOp.builder("scatter_update")
                .addInputs(original, updates)
                .callInplace(true)
                .addIntegerArguments(iargs)
                .build();
    }

    /**
     * This method returns op opName as string
     *
     * @return
     */
    @Override
    public String opName() {
        return op.opName();
    }

    /**
     * This method returns LongHash of the opName()
     *
     * @return
     */
    @Override
    public long opHash() {
        return op.opHash();
    }

    /**
     * This method returns true if op is supposed to be executed inplace
     *
     * @return
     */
    @Override
    public boolean isInplaceCall() {
        return op.isInplaceCall();
    }

    @Override
    public INDArray[] outputArguments() {
        return op.outputArguments();
    }

    @Override
    public INDArray[] inputArguments() {
        return op.inputArguments();
    }

    @Override
    public long[] iArgs() {
        return op.iArgs();
    }

    @Override
    public double[] tArgs() {
        return op.tArgs();
    }

    @Override
    public boolean[] bArgs() {
        return op.bArgs();
    }

    @Override
    public void addIArgument(int... arg) {
        op.addIArgument(arg);
    }

    @Override
    public void addIArgument(long... arg) {
        op.addIArgument(arg);
    }

    @Override
    public void addBArgument(boolean... arg) {
        op.addBArgument(arg);
    }

    @Override
    public void removeIArgument(Integer arg) {
        op.removeIArgument(arg);
    }

    @Override
    public Boolean getBArgument(int index) {
        return op.getBArgument(index);
    }

    @Override
    public Long getIArgument(int index) {
        return op.getIArgument(index);
    }

    @Override
    public int numIArguments() {
        return op.numIArguments();
    }

    @Override
    public void addTArgument(double... arg) {
        op.addTArgument(arg);
    }

    @Override
    public void removeTArgument(Double arg) {
        op.removeTArgument(arg);
    }

    @Override
    public Double getTArgument(int index) {
        return op.getTArgument(index);
    }

    @Override
    public int numTArguments() {
        return op.numTArguments();
    }

    @Override
    public int numBArguments() {
        return 0;
    }

    @Override
    public void addInputArgument(INDArray... arg) {
        op.addInputArgument(arg);
    }

    @Override
    public void removeInputArgument(INDArray arg) {
        op.removeInputArgument(arg);
    }

    @Override
    public INDArray getInputArgument(int index) {
        return op.getInputArgument(index);
    }

    @Override
    public int numInputArguments() {
        return op.numInputArguments();
    }

    @Override
    public void addOutputArgument(INDArray... arg) {
        op.addOutputArgument(arg);
    }

    @Override
    public void removeOutputArgument(INDArray arg) {

    }

    @Override
    public INDArray getOutputArgument(int index) {
        return op.getOutputArgument(index);
    }

    @Override
    public int numOutputArguments() {
        return op.numOutputArguments();
    }

    @Override
    public List<LongShapeDescriptor> calculateOutputShape() {
        return Nd4j.getExecutioner().calculateOutputShape(this);
    }

    @Override
    public CustomOpDescriptor getDescriptor() {
        return op.getDescriptor();
    }

    @Override
    public void assertValidForExecution() {

    }
}

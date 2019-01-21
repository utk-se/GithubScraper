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

package org.nd4j.linalg.api.ops;

import lombok.val;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class BaseReduceFloatOp extends BaseReduceOp implements ReduceFloatOp {

    public BaseReduceFloatOp(INDArray x, INDArray y, INDArray z, boolean newFormat, boolean keepDims, int... dimensions){
        super(x, y, z, newFormat, keepDims, dimensions);
    }

    protected BaseReduceFloatOp(SameDiff sameDiff, SDVariable i_v, boolean keepDims, int[] dimensions) {
        super(sameDiff, i_v, dimensions, keepDims);
    }

    protected BaseReduceFloatOp(SameDiff sameDiff, SDVariable i_v, SDVariable i_v2, int[] dimensions) {
        super(sameDiff, i_v, i_v2, dimensions);
    }

    protected BaseReduceFloatOp(SameDiff sameDiff, SDVariable input, int[] dimensions, boolean keepDims) {
        super(sameDiff, input, dimensions, keepDims);
    }

    protected BaseReduceFloatOp(SameDiff sameDiff, SDVariable input, int... dimensions) {
        super(sameDiff, input, dimensions);
    }

    public BaseReduceFloatOp(INDArray x, INDArray z, boolean newFormat, boolean keepDims, int[] dimensions) {
        super(x, null, z, newFormat, keepDims, dimensions);
    }

    public BaseReduceFloatOp(INDArray input, INDArray output, boolean keepDims, int... dimensions){
        super(input, null, output, dimensions);
        this.keepDims = keepDims;
        this.dimensions = dimensions;
    }


    public BaseReduceFloatOp(INDArray x, INDArray y, INDArray z, int... dimensions) {
        super(x, y, z, dimensions);
    }
    public BaseReduceFloatOp(INDArray x, INDArray z, int... dimensions) {
        super(x, null, z, dimensions);
    }


    public BaseReduceFloatOp(INDArray x, int... dimensions) {
        super(x, dimensions);
    }

    protected BaseReduceFloatOp() {
        super();
    }

    @Override
    public Type opType() {
        return Type.REDUCE_FLOAT;
    }

    @Override
    public Type getOpType() {
        return opType();
    }

    @Override
    public DataType resultType() {
        if (this.x() != null && this.x().isR())
            return this.x().dataType();

        return Nd4j.defaultFloatingPointType();
    }

    @Override
    public boolean validateDataTypes() {
        if (y() != null)
            Preconditions.checkArgument(x().dataType() == y().dataType(),
                    "Op.X [%s] type must be the same as Op.Y [%s] for op %s: x.shape=%ndShape, y.shape=%ndShape", x().dataType(),
                    y().dataType(), getClass().getName(), x(), y() );

        if (z() != null)
            Preconditions.checkArgument(z().isR(),"Op.Z (result array) must be one of floating types: z datatype = %s", z().dataType());

        return true;
    }

    @Override
    public List<LongShapeDescriptor> calculateOutputShape() {
        if(x == null)
            return Collections.emptyList();

        //Calculate reduction shape. Note that reduction on scalar - returns a scalar
        long[] reducedShape = x.length() == 0 ? x.shape() : Shape.getReducedShape(x.shape(),dimensions, isKeepDims(), newFormat);
        DataType retType = arg().dataType();
        if(!retType.isFPType())
            retType = Nd4j.defaultFloatingPointType();
        return Collections.singletonList(LongShapeDescriptor.fromShape(reducedShape, retType));
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        //Second input is dynamic axis arg
        Preconditions.checkState(dataTypes != null && (dataTypes.size() == 1 || dataTypes.size() == 2),
                "Expected 1 or 2 input datatype for %s, got input %s", getClass(), dataTypes);
        Preconditions.checkState(dataTypes.size() == 1 || dataTypes.get(1).isIntType(), "When executing reductions" +
                "with 2 inputs, second input (axis) must be an integer datatype for %s, got %s", getClass(), dataTypes);
        //Output data type: always float. TODO let's allow configuration...
        return Collections.singletonList(Nd4j.defaultFloatingPointType());
    }
}

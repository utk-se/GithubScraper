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

package org.nd4j.linalg.api.ops.random;

import lombok.NoArgsConstructor;
import lombok.val;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseOp;
import org.nd4j.linalg.api.ops.RandomOp;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author raver119@gmail.com
 */
@NoArgsConstructor
public abstract class BaseRandomOp extends BaseOp implements RandomOp {
    protected long[] shape;

    public BaseRandomOp(SameDiff sameDiff, SDVariable i_v) {
        Preconditions.checkNotNull(i_v, "Input variable can't be null with this constructor");
        this.sameDiff = sameDiff;
        this.xVertexId = i_v.getVarName();
        sameDiff.addArgsFor(new String[]{xVertexId},this);
        if(Shape.isPlaceholderShape(i_v.getShape())) {
            sameDiff.addPropertyToResolve(this,i_v.getVarName());
        }
    }

    public BaseRandomOp(SameDiff sd, long[] shape){
        super(sd, null);
        Preconditions.checkArgument(shape != null && shape.length > 0, "Shape must be non-null, length > 0. Got: %s", shape);
        this.sameDiff = sd;
        this.shape = shape;
        setInstanceId();
        sameDiff.addArgsFor(new String[0], this);
    }

    public BaseRandomOp(INDArray x, INDArray y, INDArray z){
        super(x,y,z);
    }

    @Override
    public Type opType() {
        return Type.RANDOM;
    }

    @Override
    public List<LongShapeDescriptor> calculateOutputShape() {
        if(shape != null){
            return Collections.singletonList(LongShapeDescriptor.fromShape(shape, Nd4j.defaultFloatingPointType()));
        } else {
            List<LongShapeDescriptor> ret = new ArrayList<>(1);
            val shape = sameDiff.getShapeForVarName(args()[0].getVarName());
            if (shape != null)
                ret.add(LongShapeDescriptor.fromShape(shape, Shape.pickPairwiseDataType(args()[0].dataType(), Nd4j.dataType())));
            return ret;
        }
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataTypes){
        Preconditions.checkState(inputDataTypes == null || inputDataTypes.isEmpty(), "Expected no input data types for %s, got %s", getClass().getName(), inputDataTypes);
        //TODO MAKE CONFIGUREABLE - https://github.com/deeplearning4j/deeplearning4j/issues/6854
        return Collections.singletonList(DataType.FLOAT);
    }
}

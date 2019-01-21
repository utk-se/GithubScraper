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

package org.nd4j.linalg.api.ops.impl.transforms.same;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ops.impl.transforms.BaseDynamicTransformOp;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.api.shape.Shape;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Identity function
 *
 * @author Adam Gibson
 */
public class Identity extends BaseDynamicTransformOp {

    public Identity(SameDiff sd, SDVariable input){
        super(sd, new SDVariable[]{input}, false);
    }

    public Identity(){ }

    @Override
    public String opName() {
        return "identity";
    }

    @Override
    public String onnxName() {
        return "Constant";
    }

    @Override
    public String tensorflowName() {
        return "Identity";
    }

    @Override
    public String[] tensorflowNames() {
        return new String[]{"Identity"};
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        return i_v;
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        Preconditions.checkState(dataTypes != null && dataTypes.size() == 1, "Expected exactly 1 input datatype for %s, got input %s", getClass(), dataTypes);
        return dataTypes;
    }

    @Override
    public List<LongShapeDescriptor> calculateOutputShape() {
        if(inputArguments == null || inputArguments.isEmpty())
            return Collections.emptyList();
        return Collections.singletonList(inputArguments.get(0).shapeDescriptor());
    }

}

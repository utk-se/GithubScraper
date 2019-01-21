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

package org.nd4j.linalg.api.ops.random.compat;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.util.ArrayUtil;

import java.util.Collections;
import java.util.List;

/**
 * This op is a wrapper for RandomNormal Op
 * @author raver119@gmail.com
 */
public class RandomStandardNormal extends DynamicCustomOp {

    public RandomStandardNormal() {
        // values are just hardcoded for this op
        addTArgument(0.0, 1.0);
    }

    public RandomStandardNormal(SameDiff sameDiff, SDVariable[] args) {
        super(null, sameDiff, args);

        // values are just hardcoded for this op
        addTArgument(0.0, 1.0);
    }

    public RandomStandardNormal(INDArray shape) {
        super(null, new INDArray[]{shape},new INDArray[0]);

        // values are just hardcoded for this op
        addTArgument(0.0, 1.0);
    }

    public RandomStandardNormal(INDArray shape, INDArray output) {
        super(null, new INDArray[]{shape},new INDArray[]{output});

        // values are just hardcoded for this op
        addTArgument(0.0, 1.0);
    }

    public RandomStandardNormal(long shape[]) {
        this(Nd4j.create(ArrayUtil.toDouble(shape)), Nd4j.create(shape));
    }

    @Override
    public String opName() {
        return "random_normal";
    }

    @Override
    public String tensorflowName() {
        return "RandomStandardNormal";
    }

    @Override
    public Object[] getExtraArgs() {
        // FIXME: why the hell we need this?
        return new Object[] {new Double(0.0), new Double(1.0)};
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataTypes){
        Preconditions.checkState(inputDataTypes != null && inputDataTypes.size() == 1, "Expected exactly 1 input datatype for %s, got %s", getClass(), inputDataTypes);
        //Input data type specifies the shape; output data type should be any float
        //TODO MAKE CONFIGUREABLE - https://github.com/deeplearning4j/deeplearning4j/issues/6854
        return Collections.singletonList(DataType.FLOAT);
    }
}

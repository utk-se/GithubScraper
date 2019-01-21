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

package org.nd4j.linalg.api.ops.impl.transforms.comparison;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformOp;
import org.nd4j.linalg.api.ops.BaseTransformSameOp;
import org.nd4j.linalg.indexing.conditions.Condition;

import java.util.*;

/**
 * Element-wise Compare-and-Replace implementation as Op
 * Basically this op does the same as Compare-and-Set, but op.X is checked against Condition instead
 *
 * @author raver119@gmail.com
 */
public class CompareAndReplace extends BaseTransformSameOp {

    private Condition condition;
    private double compare;
    private double set;
    private double eps;
    private int mode;

    public CompareAndReplace(SameDiff sameDiff, SDVariable to, SDVariable from, Condition condition) {
        super(sameDiff, to, from, false);
        this.condition = condition;
        this.compare = condition.getValue();
        this.set = 0;
        this.mode = condition.condtionNum();
        this.eps = condition.epsThreshold();
        this.extraArgs = new Object[] {compare, set, eps, (double) mode};
    }

    public CompareAndReplace() {

    }


    /**
     * With this constructor, op will check each X element against given Condition, and if condition met, element Z will be set to Y value, and X otherwise
     *
     * PLEASE NOTE: X will be modified inplace.
     *
     * Pseudocode:
     * z[i] = condition(x[i]) ? y[i] : x[i];
     *
     * @param x
     * @param y
     * @param condition
     */
    public CompareAndReplace(INDArray x, INDArray y, Condition condition) {
        this(x, y, x, condition);
    }

    /**
     * With this constructor, op will check each X element against given Condition, and if condition met, element Z will be set to Y value, and X otherwise
     *
     * Pseudocode:
     * z[i] = condition(x[i]) ? y[i] : x[i];
     *
     * @param x
     * @param y
     * @param z
     * @param condition
     */
    public CompareAndReplace(INDArray x, INDArray y, INDArray z, Condition condition) {
        super(x, y, z);
        this.compare = condition.getValue();
        this.set = 0;
        this.mode = condition.condtionNum();
        this.eps = condition.epsThreshold();
        this.extraArgs = new Object[] {compare, set, eps, (double) mode};
    }



    @Override
    public Map<String, Object> propertiesForFunction() {
        Map<String,Object> ret = new LinkedHashMap<>();
        ret.put("compare",compare);
        ret.put("set",set);
        ret.put("eps",eps);
        ret.put("mode",mode);
        return ret;
    }



    @Override
    public int opNum() {
        return 13;
    }

    @Override
    public String opName() {
        return "car";
    }

    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx op opName found for " +  opName());
    }

    @Override
    public String tensorflowName() {
        throw new NoOpNameFoundException("No tensorflow op opName found for " +  opName());
    }


    @Override
    public List<SDVariable> doDiff(List<SDVariable> grad) {
        //2 inputs: 'to' and 'from'
        //Pass through gradient for 'to' where condition is NOT satisfied
        //Pass through gradient for 'from' where condition IS satisfied
        SDVariable maskMatched = sameDiff.matchCondition(arg(0), condition).castTo(arg().dataType());
        SDVariable maskNotMatched = maskMatched.rsub(1.0);

        return Arrays.asList(grad.get(0).mul(maskNotMatched), grad.get(0).mul(maskMatched));
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        Preconditions.checkState(dataTypes != null && dataTypes.size() == 2, "Expected exactly 2 input datatypes for %s, got input %s", getClass(), dataTypes);
        Preconditions.checkState(dataTypes.get(0) == dataTypes.get(1), "Input data types must be the same: got %s", dataTypes);
        return Collections.singletonList(dataTypes.get(0));
    }
}


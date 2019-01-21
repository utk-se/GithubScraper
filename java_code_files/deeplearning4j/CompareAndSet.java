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
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformOp;
import org.nd4j.linalg.api.ops.BaseTransformSameOp;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.conditions.Condition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Element-wise Compare-and-set implementation as Op
 *
 * Please check javadoc to specific constructors, for detail information.
 *
 * @author raver119@gmail.com
 */
public class CompareAndSet extends BaseTransformSameOp {

    private Condition condition;
    private double compare;
    private double set;
    private double eps;
    private int mode;

    public CompareAndSet(SameDiff sameDiff, SDVariable to, Number set, Condition condition) {
        super(sameDiff, to, false);
        this.condition = condition;
        this.compare = condition.getValue();
        this.set = set.doubleValue();
        this.mode = condition.condtionNum();
        this.eps = condition.epsThreshold();
        this.extraArgs = new Object[] {compare, set, eps, (double) mode};
    }

    public CompareAndSet() {

    }

    public CompareAndSet(INDArray x, double compare, double set, double eps) {
        this(x, compare, set, eps, null);
    }

    public CompareAndSet(INDArray x, double compare, double set, double eps, Condition condition) {
        super(x);
        this.compare = compare;
        this.set = set;
        this.eps = eps;
        if (condition == null)
            this.mode = 0;
        else
            this.mode = condition.condtionNum();

        this.extraArgs = new Object[]{compare, set, eps, (double) mode};
    }


    /**
     * With this constructor, op will check each X element against given Condition, and if condition met, element will be replaced with Set value
     *
     *
     * Pseudocode:
     * z[i] = condition(x[i]) ? set : x[i];
     *
     * PLEASE NOTE: X will be modified inplace.
     *
     * @param x
     * @param set
     * @param condition
     */
    public CompareAndSet(INDArray x, double set, Condition condition) {
        this(x, x, set, condition);
    }


    /**
     * With this constructor, op will check each X element against given Condition, and if condition met, element will be replaced with Set value
     *
     * Pseudocode:
     * z[i] = condition(x[i]) ? set : x[i];
     *
     * @param x
     * @param set
     * @param condition
     */
    public CompareAndSet(INDArray x, INDArray z, double set, Condition condition) {
        super(x, null, z);
        this.compare = condition.getValue();
        this.set = set;
        this.eps = condition.epsThreshold();
        this.mode = condition.condtionNum();
        this.extraArgs = new Object[]{compare, set, eps, (double) mode};
    }

    /**
     * With this constructor, op will check each Y element against given Condition, and if condition met, element Z will be set to Y value, and X otherwise
     *
     * PLEASE NOTE: X will be modified inplace.
     *
     * Pseudocode:
     * z[i] = condition(y[i]) ? y[i] : x[i];
     *
     * @param x
     * @param y
     * @param condition
     */
    public CompareAndSet(INDArray x, INDArray y, Condition condition) {
        this(x, y, x, condition);
    }


    /**
     * With this constructor, op will check each Y element against given Condition, and if condition met, element Z will be set to Y value, and X otherwise
     *
     * Pseudocode:
     * z[i] = condition(y[i]) ? y[i] : x[i];
     *
     * @param x
     * @param y
     * @param z
     * @param condition
     */
    public CompareAndSet(INDArray x, INDArray y, INDArray z, Condition condition) {
        super(x, y, z);
        this.compare = condition.getValue();
        this.set = 0;
        this.eps = condition.epsThreshold();
        this.mode = condition.condtionNum();
        this.extraArgs = new Object[]{compare, set, eps, (double) mode};
    }

    /**
     * This constructor is shortcut to epsEquals.
     *
     * @param x
     * @param z
     * @param compare
     * @param set
     * @param eps
     */
    public CompareAndSet(INDArray x, INDArray z, double compare, double set, double eps) {
        super(x, z);
        this.compare = compare;
        this.set = set;
        this.eps = eps;
        this.mode = 0;
        this.extraArgs = new Object[]{compare, set, eps, (double) mode};
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
        if (y() == null) {
            return 13;
        } else {
            return 12;
        }
    }

    @Override
    public String opName() {
        return "cas";
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
    public List<SDVariable> doDiff(List<SDVariable> gradient) {
        //Pass through gradient where condition is NOT matched (condition matched: output replaced by scalar)
        SDVariable maskNotMatched = sameDiff.matchCondition(arg(), condition).castTo(arg().dataType()).rsub(1.0);
        SDVariable gradAtIn = gradient.get(0).mul(maskNotMatched);
        return Collections.singletonList(gradAtIn);
    }
}


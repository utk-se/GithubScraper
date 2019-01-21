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

package org.nd4j.linalg.api.ops.impl.transforms.strict;

import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformFloatOp;
import org.nd4j.linalg.api.ops.BaseTransformOp;
import org.nd4j.linalg.api.ops.BaseTransformStrictOp;

import java.util.List;

/**
 * Set range to a particular set of values
 *
 * @author Adam Gibson
 */
public class SetRange extends BaseTransformStrictOp {

    private double min, max;

    public SetRange(SameDiff sameDiff, SDVariable i_v, boolean inPlace, double min, double max) {
        super(sameDiff, i_v, inPlace);
        this.min = min;
        this.max = max;
        this.extraArgs = new Object[] {min, max};
    }

    public SetRange() {}

    public SetRange(INDArray x) {
        this(x, 0, 1);
    }

    public SetRange(INDArray x, INDArray z, double min, double max) {
        super(x, z);
        this.min = min;
        this.max = max;
        this.extraArgs = new Object[] {min, max};
    }
    public SetRange(INDArray x, double min, double max) {
        super(x);
        this.min = min;
        this.max = max;
        this.extraArgs = new Object[] {min, max};
    }

    @Override
    public int opNum() {
        return 25;
    }

    @Override
    public String opName() {
        return "setrange";
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
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        return null;
    }
}

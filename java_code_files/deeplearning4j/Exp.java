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

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformFloatOp;
import org.nd4j.linalg.api.ops.BaseTransformOp;
import org.nd4j.linalg.api.ops.BaseTransformStrictOp;

import java.util.Arrays;
import java.util.List;

/**
 * Element-wise exponential function
 *
 * @author Adam Gibson
 */
public class Exp extends BaseTransformStrictOp {
    public Exp(SameDiff sameDiff, SDVariable i_v, boolean inPlace) {
        super(sameDiff, i_v, inPlace);
    }

    public Exp() {
    }

    public Exp(INDArray x, INDArray z) {
        super(x, z);
    }

    public Exp(INDArray x) {
        super(x);
    }

    @Override
    public int opNum() {
        return 23;
    }

    @Override
    public String opName() {
        return "exp";
    }

    @Override
    public String onnxName() {
        return "Exp";
    }

    @Override
    public String tensorflowName() {
        return "Exp";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        SDVariable ret = f().mul(f().exp(arg()), i_v.get(0));
        return Arrays.asList(ret);
    }

}

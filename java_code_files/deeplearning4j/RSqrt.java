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

package org.nd4j.linalg.api.ops.impl.transforms.floating;

import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformFloatOp;
import org.nd4j.linalg.api.ops.BaseTransformOp;

import java.util.Collections;
import java.util.List;

/**
 * RSqrt function
 *
 * @author Adam Gibson
  */
public class RSqrt extends BaseTransformFloatOp {
    public RSqrt(SameDiff sameDiff, SDVariable i_v, boolean inPlace) {
        super(sameDiff, i_v, inPlace);
    }

    public RSqrt() {}

    public RSqrt(INDArray x, INDArray z) {
        super(x, z);
    }

    public RSqrt(INDArray x) {
        super(x);
    }

    @Override
    public int opNum() {
        return 3;
    }

    @Override
    public String opName() {
        return "rsqrt";
    }

    @Override
    public String onnxName() {
        return "Rsqrt";
    }

    @Override
    public String tensorflowName() {
        return "Rsqrt";
    }



    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        SDVariable xPowNeg32 = f().pow(arg(), -1.5).mul(-0.5);
        return Collections.singletonList(i_v.get(0).mul(xPowNeg32));
    }

}

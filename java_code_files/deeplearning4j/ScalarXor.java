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

package org.nd4j.linalg.api.ops.impl.scalar.comparison;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseScalarBoolOp;

import java.util.Arrays;
import java.util.List;

/**
 * Return a binary (0 or 1)
 * when greater than a number
 *
 * @author Adam Gibson
 */
public class ScalarXor extends BaseScalarBoolOp {
    public ScalarXor() {
    }

    public ScalarXor(INDArray x, INDArray z, Number num) {
        super(x, null, z, num);
    }

    public ScalarXor(INDArray x, Number num) {
        super(x, num);
    }


    public ScalarXor(SameDiff sameDiff, SDVariable i_v, Number scalar) {
        super(sameDiff, i_v, scalar);
    }

    public ScalarXor(SameDiff sameDiff, SDVariable i_v, Number scalar, boolean inPlace) {
        super(sameDiff, i_v, scalar, inPlace);
    }

    @Override
    public int opNum() {
        return 9;
    }

    @Override
    public String opName() {
        return "xor_scalar";
    }

    @Override
    public String onnxName() {
        return "Xor_scalar";
    }

    @Override
    public String tensorflowName() {
        return "Xor_scalar";
    }


    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        //Not continuously differentiable, but 0 gradient in most places

        return Arrays.asList(sameDiff.zerosLike(arg()));
    }
}

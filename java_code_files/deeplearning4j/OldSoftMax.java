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
import org.nd4j.base.Preconditions;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformStrictOp;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Collections;
import java.util.List;

/**
 * Soft max function
 * row_maxes is a row vector (max for each row)
 * row_maxes = rowmaxes(input)
 * diff = exp(input - max) / diff.rowSums()
 * Outputs a probability distribution.
 * Note that this is a parameterized model and requires
 * the sum and max for the vector being calculated
 *
 * @author Adam Gibson
 */

public class OldSoftMax extends BaseTransformStrictOp {
    public OldSoftMax(SameDiff sameDiff, SDVariable i_v1, SDVariable i_v2) {
        super(sameDiff, i_v1, i_v2);
    }

    public OldSoftMax(SameDiff sameDiff, SDVariable i_v1, SDVariable i_v2, boolean inPlace) {
        super(sameDiff, i_v1, i_v2, inPlace);
    }

    public OldSoftMax(SameDiff sameDiff, SDVariable i_v, boolean inPlace) {
        super(sameDiff, i_v, inPlace);
    }

    public OldSoftMax() {
    }

    public OldSoftMax(INDArray x){
        this(x,x);
    }

    public OldSoftMax(INDArray x, INDArray z) {
        super(x, z);
        Preconditions.checkArgument(x != null && x.rank() == 2, "OldSoftMax op supports rank 2 (2d) arrays only. Got x (source) array with shape: %ndShape", x);
        Preconditions.checkArgument(z != null && z.rank() == 2, "OldSoftMax op supports rank 2 (2d) arrays only. Got z (result) array with shape: %ndShape", z);
    }

    @Override
    public int opNum() {
        return 0;
    }

    @Override
    public String opName() {
        return "old_softmax";
    }


    @Override
    public String onnxName() {
        return "Softmax";
    }

    @Override
    public String tensorflowName() {
        throw new NoOpNameFoundException("No tensorflow op opName found for " +  opName());
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        SDVariable ret = f().softmaxDerivative(arg(), i_v.get(0), 1);
        return Collections.singletonList(ret);
    }
}

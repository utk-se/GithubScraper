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
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformSameOp;

import java.util.Collections;
import java.util.List;

/**
 * Calculate the absolute minimum over a vector
 *
 * @author raver119@gmail.com
 */
public class Max extends BaseTransformSameOp  {

    public Max(SameDiff sameDiff, SDVariable i_v, SDVariable i_v2) {
        super(sameDiff, i_v, i_v2);
    }

    public Max() {}

    public Max(INDArray x, INDArray y, INDArray z) {
        super(x, y, z);
    }


    @Override
    public int opNum() {
        return 7;
    }

    @Override
    public String opName() {
        return "max_pairwise";
    }


    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        SDVariable sgn = sameDiff.sign(arg());
        SDVariable minBp = f().minBp(sameDiff.abs(arg()), f1.get(0), false, dimensions);
        return Collections.singletonList(sgn.mul(minBp));
    }


    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx op opName found for " +  opName());
    }

    @Override
    public String tensorflowName() {
        throw new NoOpNameFoundException("No tensorflow op opName found for " +  opName());
    }
}

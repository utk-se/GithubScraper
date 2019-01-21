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
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformOp;
import org.nd4j.linalg.api.ops.BaseTransformSameOp;

import java.util.Arrays;
import java.util.List;

/**
 * Ceiling elementwise function
 *
 * @author Adam Gibson
 */
public class Ceil extends BaseTransformSameOp {
    public Ceil(SameDiff sameDiff, SDVariable i_v, boolean inPlace) {
        super(sameDiff, i_v, inPlace);
    }

    public Ceil(SameDiff sameDiff, SDVariable i_v) {
        super(sameDiff, i_v, (Object[]) null);
    }

    public Ceil() {
    }

    public Ceil(INDArray x, INDArray z) {
        super(x, z);
    }

    public Ceil(INDArray x) {
        super(x);
    }

    @Override
    public int opNum() {
        return 17;
    }

    @Override
    public String opName() {
        return "ceil";
    }

    @Override
    public String onnxName() {
        return "Ceil";
    }

    @Override
    public String tensorflowName() {
        return "Ceil";
    }


    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        //not continuously differentiable, but dOut/dIn = 0 in most places

        return Arrays.asList(f().zerosLike(arg()));
    }
}

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

package org.nd4j.linalg.api.ops.impl.reduce.same;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseReduceSameOp;

import java.util.Collections;
import java.util.List;

/**
 * Calculate the max over an array
 *
 * @author Adam Gibson
 */
public class Max extends BaseReduceSameOp {
    public Max(SameDiff sameDiff, SDVariable i_v, boolean keepDims, int[] dimensions) {
        super(sameDiff, i_v, dimensions, keepDims);
    }

    public Max(SameDiff sameDiff, SDVariable i_v, SDVariable i_v2, int[] dimensions) {
        super(sameDiff, i_v, i_v2, dimensions);
    }

    public Max() {
    }

    /**
     * Initialize with the given
     * input, pairwise transform, result, and number
     * of elements
     *
     * @param x the input
     * @param y the pairwise transform
     * @param z the result
     * @param n the number of elements
     */
    public Max(INDArray x, int... axis) {
        super(x, null, null, axis);
    }

    public Max(INDArray x, INDArray z, int... axis) {
        super(x, null, z, axis);
    }

    public Max(INDArray x, INDArray z, boolean newFormat, boolean keepDims, int... dimensions) {
        super(x,  z, newFormat, keepDims, dimensions);
    }


    @Override
    public int opNum() {
        return 1;
    }

    @Override
    public String opName() {
        return "reduce_max";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> grad) {
        return Collections.singletonList(f().maxBp(arg(), grad.get(0), keepDims, dimensions));
    }

    @Override
    public String onnxName() {
        return "ReduceMax";
    }

    @Override
    public String tensorflowName() {
        return "Max";
    }
}

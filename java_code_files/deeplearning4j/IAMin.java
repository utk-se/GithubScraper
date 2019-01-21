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

package org.nd4j.linalg.api.ops.impl.indexaccum;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseIndexAccumulation;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Collections;
import java.util.List;

/**
 * Calculate the index of the max absolute value over a vector
 *
 * @author Adam Gibson
 */
public class IAMin extends BaseIndexAccumulation {
    public IAMin(SameDiff sameDiff, SDVariable i_v, boolean keepDims, int[] dimensions) {
        super(sameDiff, i_v, keepDims, dimensions);
    }

    public IAMin() {}

    public IAMin(INDArray x, int... dimensions) {
        super(x, dimensions);
    }

    public IAMin(INDArray x, INDArray z, int... dimensions) {
        super(x, z, dimensions);
    }



    @Override
    public int opNum() {
        return 3;
    }

    @Override
    public String opName() {
        return "iamin";
    }

    @Override
    public String onnxName() {
        return "AbsArgMin";
    }

    @Override
    public String tensorflowName() {
        return "absargmin";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> grad){
        return Collections.singletonList(f().zerosLike(arg()));
    }
}

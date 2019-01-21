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
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseIndexAccumulation;

import java.util.Collections;
import java.util.List;

/**
 * Calculate the index
 * of max value over a vector
 *
 * @author Alex Black
 */
public class IMax extends BaseIndexAccumulation {
    public IMax(SameDiff sameDiff, SDVariable i_v, boolean keepDims, int[] dimensions) {
        super(sameDiff, i_v, keepDims, dimensions);
    }

    public IMax() {
    }

    public IMax(INDArray x, INDArray z, int... dimensions) {
        super(x, z, dimensions);
    }

    public IMax(INDArray x, int... dimensions) {
        super(x, null, dimensions);
    }


    @Override
    public int opNum() {
        return 0;
    }

    @Override
    public String opName() {
        return "imax";
    }

    @Override
    public String onnxName() {
        return "arg_max";
    }

    @Override
    public String tensorflowName() {
        throw new NoOpNameFoundException("No tensorflow op opName found for " +  opName());
    }

    @Override
    public Type opType() {
        return Type.INDEXREDUCE;
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        //Not differentiable, but (assuming no ties) output does not change for a given infinitesimal change in the input
        return Collections.singletonList(f().zerosLike(arg()));
    }
}

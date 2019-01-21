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

package org.nd4j.linalg.api.ops.impl.reduce3;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseReduceFloatOp;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Arrays;
import java.util.List;

/**
 * Operation for fast INDArrays equality checks
 *
 * @author raver119@gmail.com
 */
public class EqualsWithEps extends BaseReduce3Op {
    private double eps;

    public EqualsWithEps(SameDiff sameDiff, SDVariable i_v, int[] dimensions, double eps) {
        super(sameDiff, i_v, dimensions);
        this.eps = eps;
    }

    public EqualsWithEps(SameDiff sameDiff, SDVariable i_v, SDVariable i_v2, int[] dimensions, double eps) {
        super(sameDiff, i_v, i_v2, dimensions);
        this.eps = eps;
    }

    public EqualsWithEps() {}

    public EqualsWithEps(INDArray x, INDArray y, INDArray z, double eps, int... dimensions) {
        super(x, y, z,true, false, dimensions);
        this.extraArgs = new Object[] {eps};
    }

    public EqualsWithEps(INDArray x, INDArray y, double eps, int... dimensions) {
        this(x, y, null, eps, dimensions);
    }

    public EqualsWithEps(INDArray x, INDArray y, INDArray z) {
        this(x, y, z, Nd4j.EPS_THRESHOLD, null);
    }

    @Override
    public int opNum() {
        return 4;
    }

    @Override
    public String opName() {
        return "equals_with_eps";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        return Arrays.asList(outputVariables()[0]);
    }
}

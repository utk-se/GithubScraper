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
 * Dot product
 * @author Adam Gibson
 */
public class Dot extends BaseReduce3Op {

    public Dot(SameDiff sameDiff, SDVariable i_v, SDVariable i_v2, int... dimensions) {
        super(sameDiff, i_v, i_v2, dimensions);
    }

    public Dot() {}

    public Dot(INDArray x, INDArray y, INDArray z, int... dimensions) {
        this(x, y, z, true, false, dimensions);
    }

    public Dot(INDArray x, INDArray y,  int... dimensions) {
        this(x, y, null, dimensions);
    }

    public Dot(INDArray x, INDArray y, INDArray z) {
        this(x, y, z, null);
    }

    public Dot(INDArray x, INDArray y, INDArray z, boolean newFormat, boolean keepDims, int... dimensions){
        super(x, y, z, newFormat, keepDims, dimensions);
    }

    @Override
    public int opNum() {
        return 3;
    }

    @Override
    public String opName() {
        return "dot";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        //TODO KEEP DIMS
        return Arrays.asList(f().dotBp(arg(0), arg(1), f1.get(0), false, dimensions));
    }
}

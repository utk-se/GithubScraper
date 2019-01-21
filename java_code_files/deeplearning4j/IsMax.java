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

package org.nd4j.linalg.api.ops.impl.transforms.any;

import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformAnyOp;
import org.nd4j.linalg.api.ops.executioner.OpExecutioner;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Collections;
import java.util.List;

/**
 * [1, 2, 3, 1] -> [0, 0, 1, 0]
 * @author Adam Gibson
 */
public class IsMax extends BaseTransformAnyOp {
    public IsMax(SameDiff sameDiff, SDVariable i_v, boolean inPlace) {
        super(sameDiff, i_v, inPlace);
    }

    public IsMax(SameDiff sameDiff, SDVariable i_v, int[] shape, boolean inPlace, Object[] extraArgs) {
        super(sameDiff, i_v, shape, inPlace, extraArgs);
    }

    public IsMax(SameDiff sameDiff, SDVariable i_v, Object[] extraArgs) {
        super(sameDiff, i_v, extraArgs);
    }

    public IsMax(INDArray x, INDArray z) {
        super(x, z);
    }

    public IsMax() {}
    public IsMax(INDArray x) {
        super(x, Nd4j.createUninitialized(DataType.BOOL, x.shape(), x.ordering()));
    }

    public IsMax(INDArray x, INDArray z, int... dimensions) {
        super(x, z);
        this.extraArgs = new Object[dimensions.length + 1];
        this.extraArgs[0] = dimensions.length;
        for (int i = 0; i < dimensions.length; i++)
            this.extraArgs[i + 1] = dimensions[i];
    }

    public IsMax(INDArray x, int... dimensions) {
        super(x, Nd4j.createUninitialized(x.dataType(), x.shape(), x.ordering()));
        this.extraArgs = new Object[dimensions.length + 1];
        this.extraArgs[0] = dimensions.length;
        for (int i = 0; i < dimensions.length; i++)
            this.extraArgs[i + 1] = dimensions[i];
    }

    @Override
    public int opNum() {
        return 1;
    }

    @Override
    public String opName() {
        return "ismax";
    }


    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx op opName found for " +  opName());
    }

    @Override
    public String tensorflowName() {
        throw new NoOpNameFoundException("No tensorflow op opName found for " +  opName());
    }

    @Override
    public DataBuffer extraArgsDataBuff(DataType dtype) {
        if (Nd4j.getExecutioner().type() == OpExecutioner.ExecutionerType.CUDA)
            return this.extraArgs == null ? null : Nd4j.createBuffer(DataType.LONG, 1, false);
        else
            return super.extraArgsDataBuff(dtype);
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        return Collections.singletonList(f().zerosLike(arg()));
    }
}

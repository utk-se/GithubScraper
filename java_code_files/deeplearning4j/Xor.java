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

package org.nd4j.linalg.api.ops.impl.transforms.pairwise.bool;

import lombok.NonNull;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformBoolOp;
import org.nd4j.linalg.api.ops.BaseTransformOp;

import java.util.Arrays;
import java.util.List;

/**
 * Boolean XOR pairwise transform
 *
 * @author raver119@gmail.com
 */
public class Xor extends BaseTransformBoolOp {

    protected double comparable = 0.0;

    public Xor(SameDiff sameDiff, SDVariable ix, SDVariable iy){
        super(sameDiff, ix, iy);
        this.extraArgs = new Object[] {this.comparable};
    }

    public Xor(SameDiff sameDiff, SDVariable i_v, boolean inPlace, double comparable) {
        super(sameDiff, i_v, inPlace);
        this.comparable = comparable;
        this.extraArgs = new Object[] {this.comparable};
    }

    public Xor() {}

    public Xor(@NonNull INDArray x, @NonNull INDArray y) {
        this(x, y, x, 0.0);
    }

    public Xor(@NonNull INDArray x, @NonNull INDArray y, INDArray z) {
        this(x, y, z, 0.0);
    }

    public Xor(@NonNull INDArray x, @NonNull INDArray y, INDArray z, Number comparable) {
        super(x, y, z);
        this.comparable = comparable.doubleValue();
        this.extraArgs = new Object[] {this.comparable};
    }


    @Override
    public int opNum() {
        return 9;
    }

    @Override
    public Type getOpType() {
        return Type.PAIRWISE_BOOL;
    }

    @Override
    public Type opType() {
        return Type.PAIRWISE_BOOL;
    }

    @Override
    public String opName() {
        return "xor";
    }

    @Override
    public String onnxName() {
        return "Xor";
    }

    @Override
    public String tensorflowName() {
        throw new NoOpNameFoundException("No Tensorflow op opName found for " +  opName());
    }


    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        return Arrays.asList( sameDiff.zerosLike(larg()), sameDiff.zerosLike(rarg()));
    }
}

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

package org.nd4j.linalg.api.ops.impl.transforms.pairwise.arithmetic;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformAnyOp;
import org.nd4j.linalg.api.ops.BaseTransformFloatOp;
import org.nd4j.linalg.api.ops.BaseTransformOp;
import org.nd4j.linalg.api.ops.BaseTransformSameOp;

import java.util.List;

/**
 * atan2 operation
 *
 * @author raver119@gmail.com
 */
public class OldAtan2Op extends BaseTransformAnyOp {
    public OldAtan2Op(SameDiff sameDiff, SDVariable i_v1, SDVariable i_v2) {
        super(sameDiff, i_v1, i_v2);
    }

    public OldAtan2Op(SameDiff sameDiff, SDVariable i_v1, SDVariable i_v2, boolean inPlace) {
        super(sameDiff, i_v1, i_v2, inPlace);
    }

    public OldAtan2Op(SameDiff sameDiff) {
        super(sameDiff);
    }

    public OldAtan2Op(SameDiff sameDiff, SDVariable i_v, boolean inPlace) {
        super(sameDiff, i_v, inPlace);
    }

    public OldAtan2Op() {}

    public OldAtan2Op(INDArray x, INDArray y) {
        super(x, y, x);
    }

    public OldAtan2Op(INDArray x, INDArray y, INDArray z) {
        super(x, y, z);
    }

    @Override
    public int opNum() {
        return 16;
    }

    @Override
    public String opName() {
        return "old_atan2";
    }

    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No  onnx opName found for " + opName());
    }

    @Override
    public String tensorflowName() {
        return "ATan2";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        return null;
    }
}

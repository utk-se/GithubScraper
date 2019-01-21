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

package org.nd4j.linalg.api.ops.impl.transforms.comparison;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformOp;
import org.nd4j.linalg.api.ops.BaseTransformSameOp;

import java.util.List;

/**
 * Min function
 *
 * @author Adam Gibson
 */
public class OldMin extends BaseTransformSameOp {
    public OldMin(SameDiff sameDiff, SDVariable i_v1, SDVariable i_v2) {
        super(sameDiff, i_v1, i_v2);
    }

    public OldMin(SameDiff sameDiff, SDVariable i_v1, SDVariable i_v2, boolean inPlace) {
        super(sameDiff, i_v1, i_v2, inPlace);
    }

    public OldMin(SameDiff sameDiff) {
        super(sameDiff);
    }

    public OldMin(SameDiff sameDiff, SDVariable i_v, boolean inPlace) {
        super(sameDiff, i_v, inPlace);
    }

    public OldMin() {}

    public OldMin(INDArray x, INDArray y, INDArray z) {
        super(x, y, z);
    }

    public OldMin(INDArray x) {
        super(x);
    }

    public OldMin(INDArray x, INDArray z) {
        super(x, z);
    }

    @Override
    public int opNum() {
        return 8;
    }

    @Override
    public String opName() {
        return "old_min_transform";
    }

    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("This is not meant to be mapped, use Max instead");
    }

    @Override
    public String tensorflowName() {
        throw new NoOpNameFoundException("This is not meant to be mapped, use Max instead");
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        return null;
    }
}

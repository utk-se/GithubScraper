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

package org.nd4j.linalg.api.ops.impl.transforms.strict;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseTransformFloatOp;
import org.nd4j.linalg.api.ops.BaseTransformOp;
import org.nd4j.linalg.api.ops.BaseTransformStrictOp;

import java.util.Arrays;
import java.util.List;

/**
 * Log elementwise function
 *
 * @author Adam Gibson
 */
public class Sin extends BaseTransformStrictOp {
    public Sin(SameDiff sameDiff, SDVariable i_v, boolean inPlace) {
        super(sameDiff, i_v, inPlace);
    }

    public Sin(INDArray x, INDArray z) {
        super(x, z);
    }

    public Sin(INDArray x) {
        super(x);
    }

    public Sin() {
    }

    @Override
    public int opNum() {
        return 27;
    }

    @Override
    public String opName() {
        return "sin";
    }

    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx op found for " + opName());
    }

    @Override
    public String tensorflowName() {
        return "Sin";
    }


    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        SDVariable ret = f().cos(arg()).mul(i_v.get(0));
        return Arrays.asList(ret);
    }

}

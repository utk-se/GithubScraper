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

package org.nd4j.linalg.api.ops.impl.transforms.custom;

import lombok.val;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ops.impl.transforms.BaseDynamicTransformOp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Arc Tangent elementwise function
 *
 * @author Adam Gibson
 */
public class ATan2 extends BaseDynamicTransformOp {

    public ATan2(SameDiff sameDiff, SDVariable y, SDVariable x) {
        super(sameDiff, new SDVariable[] {y, x} ,false);
    }

    public ATan2() {}

    @Override
    public String opName() {
        return "tf_atan2";
    }


    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx op opName found for " +  opName());
    }

    @Override
    public String tensorflowName() {
        return "Atan2";
    }


    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        //Let z=atan2(r), with r=y/x
        //dz/dr = 1/(r^2+1), dr/dy = 1/x, dr/dx = -y/x^2
        SDVariable y = larg();
        SDVariable x = rarg();

/*        SDVariable r = y.div(x);

        SDVariable dOutdr = f().square(r).add(1.0).rdiv(1.0);
        SDVariable drdy = x.rdiv(1.0);
        SDVariable drdx = f().neg(y).div(f().square(x));

        SDVariable xGrad = dOutdr.mul(drdx).mul(i_v.get(0));
        SDVariable yGrad = dOutdr.mul(drdy).mul(i_v.get(0));
*/

        val xGrad = f().neg(y.div(x.pow(2).add(y.pow(2)))).mul(i_v.get(0));
        val yGrad = x.div(x.pow(2).add(y.pow(2))).mul(i_v.get(0));

        return Arrays.asList(yGrad, xGrad);
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        Preconditions.checkState(dataTypes != null && dataTypes.size() == 2, "Expected exactly 2 input datatypes for %s, got %s", getClass(), dataTypes);
        Preconditions.checkState(dataTypes.get(0) == dataTypes.get(1), "Input datatypes must be same type: got %s", dataTypes);
        return Collections.singletonList(dataTypes.get(0));
    }
}

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

package org.nd4j.linalg.api.ops.impl.scalar;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseScalarOp;

import java.util.Arrays;
import java.util.List;

/**
 * Scalar reverse subtraction
 *
 * @author Adam Gibson
 */
public class ScalarReverseSubtraction extends BaseScalarOp {

    public ScalarReverseSubtraction() {}

    public ScalarReverseSubtraction(INDArray x, Number num) {
        super(x, num);
    }

    public ScalarReverseSubtraction(INDArray x, INDArray z, Number num) {
        super(x, z, num);
    }

    public ScalarReverseSubtraction(INDArray x, INDArray y, INDArray z, Number num) {
        super(x, y, z, num);
    }

    public ScalarReverseSubtraction(SameDiff sameDiff, SDVariable i_v, Number scalar) {
        super(sameDiff, i_v, scalar);
    }

    public ScalarReverseSubtraction(SameDiff sameDiff, SDVariable i_v, Number scalar, boolean inPlace) {
        super(sameDiff, i_v, scalar, inPlace);
    }

    @Override
    public int opNum() {
        return 5;
    }

    @Override
    public String opName() {
        return "rsub_scalar";
    }


    @Override
    public String onnxName(){
        throw new NoOpNameFoundException("No onnx op opName found for " +  opName());
    }

    @Override
    public String tensorflowName(){
        throw new NoOpNameFoundException("No tensorflow op opName found for " +  opName());
    }



    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v1) {
        SDVariable g = f().neg(i_v1.get(0));
        return Arrays.asList(g);
    }

}

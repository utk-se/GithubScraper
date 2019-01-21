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

package org.nd4j.linalg.api.ops.impl.reduce.floating;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.BaseReduceFloatOp;

import java.util.Collections;
import java.util.List;

/**
 * Log Entropy Op - returns the log entropy (information gain, or uncertainty of a random variable).
 * log(-sum( x * log(x)))
 *
 * @author raver119@gmail.com
 */
public class  LogEntropy extends BaseReduceFloatOp {

    public LogEntropy(SameDiff sameDiff, SDVariable i_v, int[] dimensions) {
        super(sameDiff, i_v, dimensions);
    }

    public LogEntropy() {}

    public LogEntropy(INDArray x, INDArray z, int... dimensions) {
        super(x, null, z, dimensions);
    }

    public LogEntropy(INDArray x, int... dimensions) {
        super(x, dimensions);
    }

    @Override
    public int opNum() {
        return 9;
    }

    @Override
    public String opName() {
        return "logentropy";
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
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        //If y=log(x), and x=entropy(in) then dL/dx = dL/dy * dy/dx; d(log(x))/dx = 1/x
        List<SDVariable> entropyGrad = Entropy.grad(f(), arg(), f1.get(0), dimensions);
        return Collections.singletonList(entropyGrad.get(0).div(f().exp(outputVariable())));
    }
}

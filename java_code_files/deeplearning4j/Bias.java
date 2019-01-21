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
import org.nd4j.linalg.api.ops.BaseReduceOp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculate a bias
 *
 * @author Adam Gibson
 */
public class Bias extends BaseReduceFloatOp {

    private double mean;

    public Bias(SameDiff sameDiff, SDVariable i_v, int[] dimensions, double mean) {
        super(sameDiff, i_v, dimensions);
        this.mean = mean;
    }

    public Bias(SameDiff sameDiff, SDVariable i_v, SDVariable i_v2, int[] dimensions, double mean) {
        super(sameDiff, i_v, i_v2, dimensions);
        this.mean = mean;
    }

    public Bias() {}

    public Bias(INDArray x, int... dimensions) {
        super(x, dimensions);
    }

    @Override
    public Map<String, Object> propertiesForFunction() {
        Map<String,Object> ret = new LinkedHashMap<>();
        ret.put("mean",mean);
        return ret;
    }

    @Override
    public int opNum() {
        return 2;
    }

    @Override
    public String opName() {
        return "bias";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        return null;
    }

    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx op opName found for " +  opName());
    }

    @Override
    public String tensorflowName() {
        throw new NoOpNameFoundException("No tensorflow op opName found for " +  opName());
    }
}

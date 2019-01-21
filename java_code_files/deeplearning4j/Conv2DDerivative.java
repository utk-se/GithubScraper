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

package org.nd4j.linalg.api.ops.impl.layers.convolution;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.layers.convolution.config.Conv2DConfig;

import java.util.ArrayList;
import java.util.List;


/**
 * Conv2DDerivative operation
 */
@Slf4j
public class Conv2DDerivative extends Conv2D {

    @Builder(builderMethodName = "derivativeBuilder")
    public Conv2DDerivative(SameDiff sameDiff, SDVariable[] inputFunctions, INDArray[] inputArrays, INDArray[] outputs, Conv2DConfig config) {
        super(sameDiff, inputFunctions, inputArrays, outputs, config);
    }

    public Conv2DDerivative() {}



    @Override
    public String onnxName() {
       throw new NoOpNameFoundException("No op name found for backwards.");
    }

    @Override
    public String tensorflowName() {
       throw new NoOpNameFoundException("No op name found for backwards");
    }

    @Override
    public String[] tensorflowNames() {
        throw new NoOpNameFoundException("No op name found for backwards");
    }

    @Override
    public String opName() {
        return "conv2d_bp";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        throw new UnsupportedOperationException("Unable to take derivative of derivative.");
    }

    @Override
    public int getNumOutputs(){
        if(args().length == 4){
            return 3;   //Includes bias
        } else {
            return 2;   //No bias - only input + weight grads
        }
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataTypes){
        int n = args().length;  //Original inputs + gradient at
        Preconditions.checkState(inputDataTypes != null && inputDataTypes.size() == n, "Expected %s input data types for %s, got %s", n, getClass(), inputDataTypes);
        List<DataType> out = new ArrayList<>(n-1);
        for( int i=0; i<n-1; i++ ){
            out.add(inputDataTypes.get(i));
        }
        return out;
    }
}

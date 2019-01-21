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
import lombok.Getter;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.impl.layers.convolution.config.Conv2DConfig;
import org.nd4j.linalg.util.ArrayUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Col2Im operation.
 *
 * Created by agibsonccc on 3/9/16.
 */
@Getter
public class Col2Im extends DynamicCustomOp {

    protected Conv2DConfig conv2DConfig;

    @Builder(builderMethodName = "builder")
    public Col2Im(SameDiff sameDiff, SDVariable[] inputFunctions, INDArray[] inputArrays, INDArray[] outputs, Conv2DConfig conv2DConfig) {
        super(null,inputArrays,outputs);
        if(sameDiff != null) {
            this.sameDiff = sameDiff;
        }

        this.conv2DConfig = conv2DConfig;

        addArgs();
    }

    public Col2Im(SameDiff sd, SDVariable input, Conv2DConfig config){
        super(null, sd, new SDVariable[]{input});
        this.conv2DConfig = config;
        addArgs();
    }

    public Col2Im() {}

    protected void addArgs() {
        addIArgument(conv2DConfig.getSH());
        addIArgument(conv2DConfig.getSW());
        addIArgument(conv2DConfig.getPH());
        addIArgument(conv2DConfig.getPW());
        addIArgument(conv2DConfig.getKH());
        addIArgument(conv2DConfig.getKW());
        addIArgument(conv2DConfig.getDH());
        addIArgument(conv2DConfig.getDW());
        addIArgument(ArrayUtil.fromBoolean(conv2DConfig.isSameMode()));

    }

    @Override
    public Map<String, Object> propertiesForFunction() {
        return conv2DConfig.toProperties();
    }



    @Override
    public String opName() {
        return "col2im";
    }


    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        throw new UnsupportedOperationException("Unable to run derivative op on col2im");
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataTypes){
        Preconditions.checkState(inputDataTypes != null && inputDataTypes.size() == 1, "Expected 1 input data type for %s, got %s", getClass(), inputDataTypes);
        return Collections.singletonList(inputDataTypes.get(0));
    }
}

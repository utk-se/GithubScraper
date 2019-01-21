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

package org.nd4j.linalg.api.ops.impl.controlflow;

import lombok.NoArgsConstructor;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 */
@NoArgsConstructor
public class Select extends DynamicCustomOp {
    public Select(SameDiff sameDiff, SDVariable[] args) {
        super(null, sameDiff, args);
    }

    public Select( INDArray[] inputs, INDArray[] outputs, List<Double> tArguments, List<Integer> iArguments) {
        super(null, inputs, outputs, tArguments, iArguments);
    }

    public Select( INDArray[] inputs, INDArray[] outputs) {
        super(null, inputs, outputs);
    }

    public Select(SameDiff sameDiff, SDVariable[] args, boolean inPlace) {
        super(null, sameDiff, args, inPlace);
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        TFGraphMapper.getInstance().initFunctionFromProperties(nodeDef.getOp(), this, attributesForNode, nodeDef, graph);

    }

    @Override
    public String tensorflowName() {
        return "Select";
    }

    @Override
    public String opName() {
        return "select";
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataType){
        Preconditions.checkState(inputDataType != null && inputDataType.size() == 3, "Expected 3 input datatypes for %s, got %s", getClass(), inputDataType );
        //3 inputs: condition, x, y
        return Collections.singletonList(inputDataType.get(1));
    }
}

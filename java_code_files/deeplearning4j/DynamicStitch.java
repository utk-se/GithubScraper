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

import org.apache.commons.lang3.ArrayUtils;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.dataset.DataSet;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.*;


/**
 * Transforms a given input tensor into numPartitions partitions, as indicated by the indices in "partitions".
 * Output tensor has one more dimension than input tensor, the first dimension indicates the partition.
 * <p>
 * Example:
 * <p>
 * input:           [4, 3, 5, 7, 8, 0]
 * input shape:     [1, 6]
 * partitions:      [1, 0, 1, 0, 0, 1]
 * numPartitions:   2
 * outputs[0]:      [3, 7, 8]
 * outputs[1]:      [4, 5, 0]
 *
 * @author Max Pumperla
 */
public class DynamicStitch extends DynamicCustomOp {

    private int numPartitions;
    private SDVariable[] indices;

    public DynamicStitch() {
    }

    public DynamicStitch(SameDiff sameDiff, SDVariable[] indices, SDVariable[] inputs) {
        super(null, sameDiff, ArrayUtils.addAll(indices, inputs), false);

        this.indices = indices;
        this.numPartitions = inputs.length;
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        // DynamicPartition and DynamicStitch are mutually inverse
        SDVariable gradient = i_v.get(0);
        SDVariable[] partitionData = new SDVariable[indices.length];
        for (int i = 0; i < indices.length; i++)
            partitionData[i] = sameDiff.onesLike(indices[i]).mul(i);
        SDVariable partitions = sameDiff.dynamicStitch(indices, partitionData);

        SDVariable[] partition = sameDiff.dynamicPartition(gradient, partitions, numPartitions);
        List<SDVariable> ret = new ArrayList<>();
        for (SDVariable i : indices)
            ret.add(f().zerosLike(i));
        Collections.addAll(ret, partition);
        return ret;
    }


    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
//        TFGraphMapper.getInstance().initFunctionFromProperties(nodeDef.getOp(), this, attributesForNode, nodeDef, graph);
        this.numPartitions = (int)attributesForNode.get("N").getI();
    }



    @Override
    public String opName() {
        return "dynamic_stitch";
    }


    @Override
    public String[] tensorflowNames() {
        return new String[]{"DynamicStitch", "ParallelDynamicStitch"};
    }

    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx name found for shape " + opName());
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        Preconditions.checkState(dataTypes != null && dataTypes.size() == 2*numPartitions, "Expected %s input datatypes for %s partitions for %s, got %s",
                2*numPartitions, numPartitions, getClass(), dataTypes);
        //Output type: same as (data) input type... only 1 output, however
        DataType inputType = dataTypes.get(dataTypes.size()-1);
        return Collections.singletonList(inputType);
    }
}

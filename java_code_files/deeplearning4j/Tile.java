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

package org.nd4j.linalg.api.ops.impl.shape;

import lombok.val;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.descriptors.properties.PropertyMapping;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.util.ArrayUtil;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.*;

/**
 * Tile function
 *
 * @author Adam Gibson
 */
public class Tile extends DynamicCustomOp {

    private int[] jaxis;
    private boolean is_static_reps = false;

    public Tile(SameDiff sameDiff, SDVariable i_v, int[] axis) {
        super(null,sameDiff, new SDVariable[]{i_v}, false);
        this.jaxis = axis;
        addArguments();
    }

    public Tile(INDArray[] inputs, INDArray[] outputs, int[] axis, boolean is_static_reps) {
        super(null, inputs, outputs);
        this.jaxis = axis;
        this.is_static_reps = is_static_reps;
        addArguments();
    }


    public Tile(INDArray[] inputs, INDArray[] outputs, int[] axis) {
        this(inputs,outputs,axis,false);
    }


    public Tile() {}

    private void addArguments() {
        this.is_static_reps = true;
        addIArgument(jaxis);
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        val lastNode = TFGraphMapper.getInstance().getNodeWithNameFromGraph(graph,nodeDef.getInput(nodeDef.getInputCount() - 1));
        val arr = TFGraphMapper.getInstance().getNDArrayFromTensor("value",lastNode,graph);
        if(arr != null) {
            this.jaxis = arr.data().asInt();
            addArguments();
        }
    }


    @Override
    public Map<String, Map<String, PropertyMapping>> mappingsForFunction() {
        Map<String,Map<String,PropertyMapping>> ret = new HashMap<>();
        Map<String,PropertyMapping> map = new HashMap<>();

        val axisMapping = PropertyMapping.builder()
                .onnxAttrName("axis")
                .tfInputPosition(-1)
                .propertyNames(new String[]{"axis"})
                .build();

        map.put("axis",axisMapping);

        ret.put(tensorflowName(),map);
        ret.put(onnxName(),map);

        return ret;
    }

    @Override
    public List<LongShapeDescriptor> calculateOutputShape() {

        if(inputArguments.size() == 0)
            return Collections.emptyList();

        /**
         * This op is special case: we can't infer its shape before both inputs are available.
         * So if reps argument is full of 0.0s - we skip shape inference
         *
         * And during actual op invocation both inputs should be available due to topo sort
         */
        if (is_static_reps)
            return Nd4j.getExecutioner().calculateOutputShape(this);

        if (inputArguments().length < 2)
            return Collections.emptyList();

        val array = inputArguments()[1];

        // FIXME: int cast
        val reps = new long[(int) array.length()];

        for (int e = 0; e < reps.length; e++)
            reps[e] = (int) array.getDouble(e);

        if (ArrayUtil.prodLong(reps) == 0)
            return Collections.emptyList();
        else
            return Nd4j.getExecutioner().calculateOutputShape(this);
    }


    @Override
    public String opName() {
        return "tile";
    }

    @Override
    public String onnxName() {
        return "Tile";
    }

    @Override
    public String tensorflowName() {
        return "Tile";
    }


    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        return Collections.singletonList(f().tileBp(arg(), i_v.get(0), jaxis));
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        //2nd isput is dynamic repeat
        Preconditions.checkState(dataTypes != null && (dataTypes.size() == 1 || dataTypes.size() == 2),
                "Expected 1 or 2 input datatypes for %s, got %s", getClass(), dataTypes);
        //Output type is same as input type
        return Collections.singletonList(dataTypes.get(0));
    }
}

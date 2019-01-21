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

import com.google.common.primitives.Ints;
import lombok.val;
import onnx.OnnxProto3;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.autodiff.samediff.VariableType;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.descriptors.properties.PropertyMapping;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.util.ArrayUtil;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.*;

/**
 * Transpose function
 *
 * @author Adam Gibson
 */
public class Transpose extends DynamicCustomOp {
    protected int[] permuteDims;

    public Transpose(SameDiff sameDiff, SDVariable i_v) {
        super(null, sameDiff, new SDVariable[]{i_v});
    }

    public Transpose(INDArray input, INDArray result){
        super(null, new INDArray[]{input}, result == null ? null : new INDArray[]{result}, null, (List<Integer>) null);
    }

    public Transpose() {
    }

    @Override
    public void resolvePropertiesFromSameDiffBeforeExecution() {
        super.resolvePropertiesFromSameDiffBeforeExecution();
    }

    @Override
    public Map<String, Map<String, PropertyMapping>> mappingsForFunction() {
        Map<String, Map<String, PropertyMapping>> ret = new LinkedHashMap<>();
        Map<String, PropertyMapping> map = new LinkedHashMap<>();

        val mapping = PropertyMapping.builder()
                .onnxAttrName("perm")
                .propertyNames(new String[]{"permuteDims"})
                .tfInputPosition(1)
                .build();


        map.put("permuteDims", mapping);
        ret.put(tensorflowName(), map);
        ret.put(onnxName(), map);
        return ret;
    }


    @Override
    public String opName() {
        return "transpose";
    }

    @Override
    public String onnxName() {
        return "Transpose";
    }

    @Override
    public String tensorflowName() {
        return "Transpose";
    }


    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        super.initFromTensorFlow(nodeDef, initWith, attributesForNode, graph);
        //permute dimensions are not specified as second input
        if (nodeDef.getInputCount() < 2)
            return;
        NodeDef permuteDimsNode = null;
        for (int i = 0; i < graph.getNodeCount(); i++) {
            if (graph.getNode(i).getName().equals(nodeDef.getInput(1))) {
                permuteDimsNode = graph.getNode(i);
            }

        }

        INDArray permuteArrayOp = TFGraphMapper.getInstance().getNDArrayFromTensor("value", permuteDimsNode, graph);
        if (permuteArrayOp != null) {
            this.permuteDims = permuteArrayOp.data().asInt();
        }

        //handle once properly mapped
        if (arg().getShape() == null || arg().getVariableType() == VariableType.PLACEHOLDER || arg().getArr() == null) {
            return;
        }

        INDArray arr = sameDiff.getArrForVarName(arg().getVarName());
        if (arr == null) {
            val arrVar = sameDiff.getVariable(arg().getVarName());

            arr = arrVar.getWeightInitScheme().create(arrVar.dataType(), arrVar.getShape());
            sameDiff.setArrayForVariable(arg().getVarName(), arr);
        }

        if(permuteArrayOp != null){
            addInputArgument(arr, permuteArrayOp);
        } else {
            addInputArgument(arr);
        }



        if (arr != null && permuteDims == null) {
            this.permuteDims = ArrayUtil.reverseCopy(ArrayUtil.range(0, arr.rank()));
        }

        if (permuteDims != null && permuteDims.length < arg().getShape().length)
            throw new ND4JIllegalStateException("Illegal permute found. Not all dimensions specified");


    }

    @Override
    public void initFromOnnx(OnnxProto3.NodeProto node, SameDiff initWith, Map<String, OnnxProto3.AttributeProto> attributesForNode, OnnxProto3.GraphProto graph) {
        if (!attributesForNode.containsKey("perm")) {

        } else
            this.permuteDims = Ints.toArray(attributesForNode.get("perm").getIntsList());
    }

    @Override
    public List<LongShapeDescriptor> calculateOutputShape() {
        if(numInputArguments() > 1){
            return super.calculateOutputShape();
        } else if (args().length > 1) {
            if (args()[0].getArr() != null && args()[1].getArr() != null) {
                return super.calculateOutputShape();
            }
        } else  if (permuteDims == null && arg() != null && arg().getShape() != null) {
            this.permuteDims = ArrayUtil.reverseCopy(ArrayUtil.range(0, arg().getShape().length));
            val permutedShape = ArrayUtil.permute(arg().getShape(), permuteDims);
            return Arrays.asList(LongShapeDescriptor.fromShape(permutedShape, larg().dataType()));
        } else if (permuteDims != null && arg() != null && arg().getShape() != null) {
            val permutedShape = ArrayUtil.permute(arg().getShape(), permuteDims);
            SDVariable lArg = larg();
            DataType lArgType = lArg.dataType();
            return Arrays.asList(LongShapeDescriptor.fromShape(permutedShape, lArgType));
        }

        return Collections.emptyList();
    }


    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        SDVariable ret = sameDiff.transpose(i_v.get(0));
        return Arrays.asList(ret);
    }

    @Override
    public List<org.nd4j.linalg.api.buffer.DataType> calculateOutputDataTypes(List<org.nd4j.linalg.api.buffer.DataType> dataTypes){
        Preconditions.checkState(dataTypes != null && (dataTypes.size() == 1 || dataTypes.size() == 2),
                "Expected list with 1 or 2 datatype for %s, got %s", getClass(), dataTypes);
        //Output type is same as input type. Second input is permute dimensions as array
        return Collections.singletonList(dataTypes.get(0));
    }

}

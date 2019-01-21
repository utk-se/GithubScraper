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
import onnx.OnnxProto3;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.descriptors.properties.adapters.DataTypeAdapter;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.Op;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.factory.Nd4j;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * Fill an array of given "shape" with the provided "value", e.g.
 * shape [2, 2] and value 42 returns [[42, 42], [42, 42]].
 *
 * @author Max Pumperla
 */
public class Fill extends DynamicCustomOp {

    private double value;
    private DataType outputDataType;

    public Fill() {
    }


    public Fill(SameDiff sameDiff, SDVariable shape, DataType outputDataType, double value) {
        super(null,sameDiff, new SDVariable[] {shape}, false);
        this.value = value;
        val shp = shape.getArr();
        this.outputDataType = outputDataType;
        addArgs();
    }

    public Fill(INDArray shape, INDArray result, double value) {
        super(null, shape, result, Collections.singletonList(value), null);
        this.value = value;
    }


    protected void addArgs() {
        addTArgument(value);
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        if(nodeDef.getInputCount() == 2) {
            val targetNode = TFGraphMapper.getInstance().getNodeWithNameFromGraph(graph,nodeDef.getInput(1));
            val mapper = TFGraphMapper.getInstance();
            val secondInputAsScalar = mapper.getNDArrayFromTensor("value",targetNode,graph);
            //must be scalar
            if(secondInputAsScalar.length() == 1) {
                addTArgument(secondInputAsScalar.getDouble(0));
            }
            else {
                throw new ND4JIllegalStateException("Second input to node " + nodeDef + " should be scalar!");
            }

            org.tensorflow.framework.DataType dt = attributesForNode.get("T").getType();
            this.outputDataType = DataTypeAdapter.dtypeConv(dt);
        }
    }

    @Override
    public void initFromOnnx(OnnxProto3.NodeProto node, SameDiff initWith, Map<String, OnnxProto3.AttributeProto> attributesForNode, OnnxProto3.GraphProto graph) {
        super.initFromOnnx(node, initWith, attributesForNode, graph);
    }

    @Override
    public void assertValidForExecution() {
        val descriptor = getDescriptor();
        if(descriptor.getNumInputs() > 0 && numInputArguments() >  2 || numInputArguments() < 1)
            throw new ND4JIllegalStateException("Op failure for " + opName() + " Number of inputs is invalid for execution. Specified " + numInputArguments() + " but should be " + descriptor.getNumInputs());

        if(descriptor.getNumOutputs() > 0 && numOutputArguments() != descriptor.getNumOutputs())
            throw new ND4JIllegalStateException("Op failure for " + opName() + " Number of outputs is invalid for execution. Specified " + numOutputArguments() + " but should be " + descriptor.getNumInputs());

        //< 0 means dynamic size
        if(descriptor.getNumIArgs() >= 0 && numIArguments() != descriptor.getNumIArgs())
            throw new ND4JIllegalStateException("Op failure for " + opName() + " Number of integer arguments is invalid for execution. Specified " + numIArguments() + " but should be " + descriptor.getNumIArgs());

        if(descriptor.getNumTArgs() >= 0 && numTArguments() < 1)
            throw new ND4JIllegalStateException("Op failure for " + opName() + " Number of inputs is invalid for execution. Specified " + numTArguments() + " but should be " + descriptor.getNumTArgs());

    }


    @Override
    public List<LongShapeDescriptor> calculateOutputShape() {

        int numArgs = args().length;
        if(numArgs < 1)
            return Collections.emptyList();

        SDVariable[] args = args();
        INDArray shape = args()[0].getArr();
        INDArray value = (args.length > 1 ? args()[1].getArr() : null);
        if(shape == null)
            return Collections.emptyList();
        else {
            //TODO properly allow customizing datatype
            if(shape.isEmpty()){
                //Edge case, mainly for TF import
                return Collections.singletonList(LongShapeDescriptor.fromShape(new long[0], value == null ? Nd4j.defaultFloatingPointType() : value.dataType()));   //TODO is this OK?
            } else {
                return Arrays.asList(LongShapeDescriptor.fromShape(shape.data().asLong(), value == null ? Nd4j.defaultFloatingPointType() : value.dataType()));
            }
        }
    }

    @Override
    public String opName() {
        return "fill";
    }

    @Override
    public String onnxName() {
        return "ConstantFill";
    }

    @Override
    public String tensorflowName() {
        return "Fill";
    }

    @Override
    public Op.Type opType() {
        return Op.Type.CUSTOM;
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> gradients){
        return Collections.singletonList(sameDiff.zerosLike(arg()));
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        //1 or 2 possible: 2 for TF import (fill with specified value
        Preconditions.checkState(dataTypes != null && (dataTypes.size() == 1 || dataTypes.size() == 2),
                "Expected 1 or 2 input datatypes for %s, got %s", getClass(), dataTypes);
        Preconditions.checkNotNull(outputDataType, "Output datatype was null (not set)");
        return Collections.singletonList(outputDataType);
    }
}

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

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import onnx.OnnxProto3;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.imports.descriptors.properties.PropertyMapping;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.Op;
import org.nd4j.linalg.api.ops.impl.shape.bp.ConcatBp;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.*;

@Slf4j
public class Concat extends DynamicCustomOp {
    private int concatDimension = -1;

    public Concat(){

    }

    public Concat(SameDiff sameDiff, int concatDimension, SDVariable... inputs){
        super(null, sameDiff, inputs);
        addIArgument(concatDimension);
        this.concatDimension = concatDimension;
    }

    @Override
    public String opName() {
        return "concat";
    }

    @Override
    public void assertValidForExecution() {
        val descriptor = getDescriptor();
        if(descriptor == null)
            throw new NoOpNameFoundException("No descriptor found for op name " + opName());


        if(descriptor.getNumInputs() > 0 && numInputArguments() < 2)
            throw new ND4JIllegalStateException("Op failure for " + opName() + " Number of inputs is invalid for execution. Specified " + numInputArguments() + " but should be " + descriptor.getNumInputs());

        if(descriptor.getNumOutputs() > 0 && numOutputArguments() != descriptor.getNumOutputs())
            throw new ND4JIllegalStateException("Op failure for " + opName() + " Number of outputs is invalid for execution. Specified " + numOutputArguments() + " but should be " + descriptor.getNumOutputs());

        //< 0 means dynamic size
        if(descriptor.getNumIArgs() >= 0 && numIArguments() != descriptor.getNumIArgs())
            throw new ND4JIllegalStateException("Op failure for " + opName() + " Number of integer arguments is invalid for execution. Specified " + numIArguments() + " but should be " + descriptor.getNumIArgs());

        if(descriptor.getNumTArgs() >= 0 && numTArguments() != descriptor.getNumTArgs())
            throw new ND4JIllegalStateException("Op failure for " + opName() + " Number of inputs is invalid for execution. Specified " + numTArguments() + " but should be " + descriptor.getNumTArgs());

    }

    @Override
    public Map<String, Map<String, PropertyMapping>> mappingsForFunction() {
        Map<String, Map<String, PropertyMapping>> ret = new HashMap<>();

        Map<String,PropertyMapping> concatMap = new HashMap<>();
        val concatDimProps = PropertyMapping.builder()
                .tfInputPosition(0)
                .onnxAttrName("axis")
                .build();
        concatMap.put("concatDimension",concatDimProps);


        Map<String,PropertyMapping> concatV2Map = new HashMap<>();
        val concat2DimProps = PropertyMapping.builder()
                //lalst position
                .tfInputPosition(-1)
                .onnxAttrName("axis")
                .build();
        concatV2Map.put("concatDimension",concat2DimProps);

        //note that onnx is already covered here
        ret.put(tensorflowNames()[0],concatMap);
        ret.put(tensorflowNames()[1],concatV2Map);


        return ret;
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        int concatDimension = -1;
        String input = null;
        val inputCount = nodeDef.getInputCount();
        for(int i = 0; i < inputCount; i++) {
            if(nodeDef.getInput(i).contains("/concat_dim")) {
                input = nodeDef.getInput(i);
                break;
            }
        }

        //older versions may specify a concat_dim, usually it's the last argument
        if(input == null) {
            input = nodeDef.getInput(nodeDef.getInputCount() - 1);
        }

        val variable = initWith.getVariable(input);
        // concat dimension is only possible
        if (variable != null && variable.getArr() == null) {
            sameDiff.addPropertyToResolve(this, input);

        } else if (variable != null) {
            val arr = variable.getArr();
            if (arr.length() == 1) {
                concatDimension = arr.getInt(0);
            }

            this.concatDimension = concatDimension;
            addIArgument(this.concatDimension);
            log.trace("Concat dimension: {}", concatDimension);

        }

        //don't pass both iArg and last axis down to libnd4j
        if(inputArguments().length == nodeDef.getInputCount()) {
            val inputArgs = inputArguments();
            removeInputArgument(inputArgs[inputArguments().length - 1]);
        }

        sameDiff.removeArgFromFunction(input,this);
    }

    @Override
    public Map<String, Object> propertiesForFunction() {
        Map<String,Object> ret = new LinkedHashMap<>();
        ret.put("concatDimension",concatDimension);
        return ret;
    }


    @Override
    public void initFromOnnx(OnnxProto3.NodeProto node, SameDiff initWith, Map<String, OnnxProto3.AttributeProto> attributesForNode, OnnxProto3.GraphProto graph) {
        super.initFromOnnx(node, initWith, attributesForNode, graph);
    }

    @Override
    public String onnxName() {
        return "Concat";
    }

    @Override
    public String tensorflowName() {
        return "Concat";
    }


    @Override
    public String[] tensorflowNames() {
        return new String[]  {"Concat","ConcatV2"};
    }

    @Override
    public Op.Type opType() {
        return Op.Type.CUSTOM;
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        SDVariable[] args = args();
        SDVariable[] bpArgs = Arrays.copyOf(args, args.length + 1);
        bpArgs[bpArgs.length-1] = i_v.get(0);
        return Arrays.asList(new ConcatBp(sameDiff, concatDimension, bpArgs).outputVariables());
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        DataType first = dataTypes.get(0);
        for( int i=1; i<dataTypes.size(); i++ ){
            DataType dt = dataTypes.get(i);
            Preconditions.checkState(first == dt, "All inputs must have same datatype - got %s and %s for inputs 0 and %s respectively", first, dt, i);
        }
        //Output type is same as input types
        return Collections.singletonList(first);
    }
}

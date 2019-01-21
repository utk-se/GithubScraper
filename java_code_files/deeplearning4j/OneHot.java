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
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.imports.descriptors.properties.PropertyMapping;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.factory.Nd4j;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.*;

/**
 * Created by susaneraly on 3/14/18.
 */
public class OneHot extends DynamicCustomOp {

    private int depth;
    private int jaxis = -1;
    private double on;
    private double off;

    public  OneHot() {

    }

    public OneHot(SameDiff sameDiff, SDVariable indices, int depth) {
        this(sameDiff, indices, depth, -1, 1, 0);
    }

    public OneHot(SameDiff sameDiff, SDVariable indices, int depth, int axis, double on, double off) {
        super(null, sameDiff,  new SDVariable[] {indices}, false);
        this.depth = depth;
        this.jaxis = axis;
        this.on = on;
        this.off = off;
        addArgs();
    }

    public OneHot(INDArray indices, INDArray output, int depth) {
        this(indices, output, depth, -1, 1, 0);
    }

    public OneHot(INDArray indices, INDArray output, int depth, int axis, double on, double off) {
        super(null, indices, output, null, null);
        this.depth = depth;
        this.jaxis = axis;
        this.on = on;
        this.off = off;
        addArgs();
    }




    protected void addArgs() {
        addIArgument(jaxis);
        addIArgument(depth);
        addTArgument(on);
        addTArgument(off);
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        TFGraphMapper.getInstance().initFunctionFromProperties(nodeDef.getOp(), this, attributesForNode, nodeDef, graph);
        addArgs();
    }


    @Override
    public Map<String, Map<String, PropertyMapping>> mappingsForFunction() {
        Map<String, Map<String, PropertyMapping>> ret = new HashMap<>();
        Map<String,PropertyMapping> attrs = new LinkedHashMap<>();

        val depth = PropertyMapping.builder()
                .propertyNames(new String[]{"depth"})
                .tfInputPosition(1)
                .build();
        attrs.put("depth", depth);

        val on = PropertyMapping.builder()
                .propertyNames(new String[]{"on"})
                .tfInputPosition(2)
                .build();
        attrs.put("on", on);

        val off = PropertyMapping.builder()
                .propertyNames(new String[]{"off"})
                .tfInputPosition(3)
                .build();
        attrs.put("off", off);


        val axis = PropertyMapping.builder()
                .propertyNames(new String[] {"jaxis"})
                .tfAttrName("axis")
                .build();
        attrs.put("jaxis",axis);

        ret.put(tensorflowName(),attrs);
        return ret;
    }

    @Override
    public String tensorflowName() {
        return "OneHot";
    }

    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx name found for " + opName());
    }

    @Override
    public String opName() {
        return "onehot";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        return Collections.singletonList(sameDiff.zerosLike(arg()));
    }

    @Override
    public List<org.nd4j.linalg.api.buffer.DataType> calculateOutputDataTypes(List<org.nd4j.linalg.api.buffer.DataType> dataTypes){
        Preconditions.checkState(dataTypes.size() == 1, "Expected list with exactly 1 datatype for %s, got %s", getClass(), dataTypes);
        //Output type defaults to floats
        //TODO allow configuration + test import of output array dtype: https://github.com/deeplearning4j/deeplearning4j/issues/6854
        DataType dt = Nd4j.defaultFloatingPointType();
        return Collections.singletonList(dt);
    }
}

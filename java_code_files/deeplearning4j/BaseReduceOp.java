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

package org.nd4j.linalg.api.ops;

import com.google.common.primitives.Ints;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import onnx.OnnxProto3;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.graphmapper.onnx.OnnxGraphMapper;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.factory.Nd4j;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Base class for accumulation, initiates the initial entry
 * with respect to the child class. Also contains baseline fields
 * for the over all field with accumulation.
 *
 * @author Adam Gibson
 */
@Slf4j
public abstract class BaseReduceOp extends BaseOp implements ReduceOp {
    protected Number finalResult;
    @Setter @Getter
    protected boolean keepDims = false;

    // flag for tf imported ops, shows that there's probably one more value appended in axis
    @Setter @Getter
    protected boolean newFormat = false;
    protected boolean isComplex = false;


    public BaseReduceOp(SameDiff sameDiff,
                        SDVariable i_v,
                        int[] dimensions, boolean keepDims) {
        super(sameDiff,new Object[]{dimensions});
        if (i_v != null) {
            if(dimensions == null || dimensions.length < 1)
                dimensions = new int[] {Integer.MAX_VALUE};

            this.dimensions = dimensions;
            f().validateDifferentialFunctionsameDiff(i_v);
            this.keepDims = keepDims;
            this.xVertexId = i_v.getVarName();
            sameDiff.addArgsFor(new String[]{xVertexId},this);
            if(Shape.isPlaceholderShape(i_v.getShape())) {
                sameDiff.addPropertyToResolve(this,i_v.getVarName());
            }

        } else {
            throw new IllegalArgumentException("Input not null variable.");
        }

        this.newFormat = true;
        defineDimensions(dimensions);
    }

    public BaseReduceOp(SameDiff sameDiff,
                        SDVariable i_v,
                        SDVariable i_v2,
                        int[] dimensions, boolean keepDims) {
        super(sameDiff,new Object[]{dimensions});
        if (i_v != null) {
            if(dimensions == null || dimensions.length < 1)
                dimensions = new int[] {Integer.MAX_VALUE};

            this.dimensions = dimensions;

            this.xVertexId = i_v.getVarName();
            this.yVertexId = i_v2.getVarName();
            f().validateDifferentialFunctionsameDiff(i_v);
            f().validateDifferentialFunctionsameDiff(i_v2);
            this.keepDims = keepDims;
            sameDiff.addArgsFor(new String[]{xVertexId,yVertexId},this);

        } else {
            throw new IllegalArgumentException("Input not null variable.");
        }

        this.newFormat = true;
        defineDimensions(dimensions);
    }


    public BaseReduceOp(SameDiff sameDiff,
                        SDVariable i_v) {
        this(sameDiff, i_v, null, false);
    }


    public BaseReduceOp(SameDiff sameDiff,
                        SDVariable i_v,
                        int[] dimensions) {
        this(sameDiff,i_v,dimensions,false);

    }

    public BaseReduceOp(SameDiff sameDiff,
                        SDVariable i_v,
                        SDVariable i_v2,
                        int[] dimensions) {
        this(sameDiff,i_v,i_v2,dimensions,false);
    }



    public BaseReduceOp() {}


    public BaseReduceOp(INDArray x, INDArray y, INDArray z, boolean newFormat, boolean keepDims, int[] dimensions) {
        super(x, y, z);
        this.newFormat = newFormat;
        this.keepDims = keepDims;
        this.dimensions = dimensions;
        defineDimensions(dimensions);
    }

    public BaseReduceOp(INDArray x, int... dimensions) {
        this(x, null, dimensions);
    }

    public BaseReduceOp(INDArray x, INDArray y, int... dimensions) {
        this(x, y, null, dimensions);
    }

    public BaseReduceOp(INDArray x, INDArray y, INDArray z, int... dimensions) {
        this(x, y, z, true, false, dimensions);
    }

    public BaseReduceOp(SameDiff sameDiff) {
        this.sameDiff = sameDiff;
    }

    @Override
    public INDArray noOp() {
        if (z != null && x != z)
            return z().assign(x);
        else
            return x().dup(x().ordering());
    }

    @Override
    public boolean isKeepDims() {
        return keepDims;
    }


    public abstract List<LongShapeDescriptor> calculateOutputShape();


    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        newFormat = true;

        if (!attributesForNode.containsKey("axis") && !hasReductionIndices(nodeDef)) {
            this.dimensions = new int[] { Integer.MAX_VALUE };
        }   //Otherwise: dimensions are dynamically set during execution in InferenceSession

        if(attributesForNode.containsKey("keep_dims")) {
            val keepDims = attributesForNode.get("keep_dims").getB();
            this.keepDims = keepDims;
        }
        defineDimensions(this.dimensions);
    }

    protected boolean hasReductionIndices(NodeDef nodeDef) {
        for(int i = 0; i < nodeDef.getInputCount(); i++) {
            if(nodeDef.getInput(i).contains("reduction_indices")) {
                return true;
            }
        }

        return false;
    }


    @Override
    public void initFromOnnx(OnnxProto3.NodeProto node, SameDiff initWith, Map<String, OnnxProto3.AttributeProto> attributesForNode, OnnxProto3.GraphProto graph) {
        if (!attributesForNode.containsKey("axes")) {
            this.dimensions = new int[] { Integer.MAX_VALUE };
        }
        else {
            val map = OnnxGraphMapper.getInstance().getAttrMap(node);
            val dims = Ints.toArray(map.get("axes").getIntsList());
            this.dimensions = dims;
        }
    }

    @Override
    public boolean isComplexAccumulation() {
        return isComplex;
    }

    @Override
    public void setDimensions(int... dimensions) {
        this.dimensions = dimensions;
        defineDimensions(dimensions);
    }
}

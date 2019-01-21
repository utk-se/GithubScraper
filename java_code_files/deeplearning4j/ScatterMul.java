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

package org.nd4j.linalg.api.ops.impl.scatter;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.NoOpNameFoundException;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * Created by farizrahman4u on 3/23/18.
 */

public class ScatterMul extends DynamicCustomOp {

    public ScatterMul(SameDiff sameDiff, SDVariable ref, SDVariable indices, SDVariable updates) {
        super(null, sameDiff, new SDVariable[]{ref, indices, updates}, false);
    }

    public ScatterMul() {}

    @Override
    public String opName() {
        return "scatter_mul";
    }

    @Override
    public String onnxName() {
        throw new NoOpNameFoundException("No onnx op opName found for " + opName());
    }

    @Override
    public String tensorflowName() {
        return "ScatterMul";
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        TFGraphMapper.getInstance().initFunctionFromProperties(nodeDef.getOp(), this, attributesForNode, nodeDef, graph);

        if (nodeDef.containsAttr("use_locking")) {
            if (nodeDef.getAttrOrThrow("use_locking").getB() == true) {
                bArguments.add(true);
            } else {
                bArguments.add(false);
            }
        } else
            bArguments.add(false);
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> gradOut){
        //3 args: ref, indices, updates
        //For non-modified indices, input gradient (reference) is same as output gradient
        //For modified indices, dL/dref = dL/dOut * dOut/dRef = dL/dOut * d(ref * update)/dRef = dL/dOut * update
        //And for updates, dL/du = dL/dOut * dOut/du = dL/dOut * d(ref * update)/du = dL/dOut * ref

        SDVariable ref = arg(0);
        SDVariable indices = arg(1);
        SDVariable updates = arg(2);

        List<SDVariable> ret = new ArrayList<>(3);
        SDVariable gradRef = f().scatterMul(gradOut.get(0), indices, updates);
        ret.add(gradRef);            //Reference array
        ret.add(f().zerosLike(arg(1)));  //Indices

        SDVariable gatherOutGrad = f().gather(gradOut.get(0), indices, 0);       //Updates
        SDVariable gatherRef = f().gather(ref, indices, 0);
        SDVariable updateGrad = gatherOutGrad.mul(gatherRef);
        ret.add(updateGrad);

        return ret;
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataTypes){
        Preconditions.checkState(inputDataTypes != null && inputDataTypes.size() == 3, "Expected exactly 3 input datatypes for %s, got %s", getClass(), inputDataTypes);
        Preconditions.checkState(inputDataTypes.get(0) == inputDataTypes.get(2), "Reference (input 0) and updates (input 2) must have exactly same data types, got %s and %s",
                inputDataTypes.get(0), inputDataTypes.get(2));
        return Collections.singletonList(inputDataTypes.get(0));
    }
}

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

package org.nd4j.linalg.api.ops.impl.shape.tensorops;

import onnx.OnnxProto3;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.imports.descriptors.properties.PropertyMapping;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TensorArraySize extends BaseTensorOp {
   @Override
   public String tensorflowName() {
      return "TensorArraySizeV3";
   }


   @Override
   public String opName() {
      return "tensorarraysizev3";
   }

   @Override
   public List<LongShapeDescriptor> calculateOutputShape() {
      // output is scalar only
      return Collections.singletonList(LongShapeDescriptor.fromShape(new long[]{}, DataType.LONG));
   }

   @Override
   public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
      super.initFromTensorFlow(nodeDef, initWith, attributesForNode, graph);
   }

   @Override
   public Map<String, Map<String, PropertyMapping>> mappingsForFunction() {
      return super.mappingsForFunction();
   }

   @Override
   public void initFromOnnx(OnnxProto3.NodeProto node, SameDiff initWith, Map<String, OnnxProto3.AttributeProto> attributesForNode, OnnxProto3.GraphProto graph) {
   }

   @Override
   public List<DataType> calculateOutputDataTypes(List<DataType> inputDataType){
      //Size is always int32
      return Collections.singletonList(DataType.INT);
   }
}

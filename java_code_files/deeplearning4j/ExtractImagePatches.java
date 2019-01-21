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

package org.nd4j.linalg.api.ops.impl.image;

import lombok.NonNull;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.imports.graphmapper.tf.TFGraphMapper;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.factory.Nd4j;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extract image patches op - a sliding window operation over 4d activations that puts the
 * output images patches into the depth dimension
 *
 * @author Alex Black
 */
public class ExtractImagePatches extends DynamicCustomOp {

    private int[] kSizes;
    private int[] strides;
    private int[] rates;
    private boolean isSameMode;

    public ExtractImagePatches(){ }

    public ExtractImagePatches(@NonNull SameDiff samediff, @NonNull SDVariable input, @NonNull int[] kSizes,
                               @NonNull int[] strides, @NonNull int[] rates, boolean sameMode){
        super(samediff, input);
        Preconditions.checkState(kSizes.length == 2, "Expected exactly 2 kernel sizes, got %s", kSizes);
        Preconditions.checkState(strides.length == 2, "Expected exactly 2 strides, got %s", strides);
        Preconditions.checkState(rates.length == 2, "Expected exactly 2 rate values, got %s", rates);
        this.isSameMode = sameMode;
    }

    @Override
    public String opName() {
        return "extract_image_patches";
    }

    @Override
    public String tensorflowName() {
        return "ExtractImagePatches";
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {
        //TF includes redundant leading and training 1s for kSizes, strides, rates (positions 0/3)
        kSizes = parseIntList(attributesForNode.get("ksizes").getList());
        strides = parseIntList(attributesForNode.get("strides").getList());
        rates = parseIntList(attributesForNode.get("rates").getList());
        String s = attributesForNode.get("padding").getS().toStringUtf8();
        isSameMode = s.equalsIgnoreCase("SAME");
        addArgs();
    }

    protected void addArgs() {
        iArguments.clear();
        addIArgument(kSizes);
        addIArgument(strides);
        addIArgument(rates);
        addIArgument(isSameMode ? 1 : 0);
        addIArgument();
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getNumOutputs(){
        return 1;
    }

    private static int[] parseIntList(AttrValue.ListValue ilist){
        //TF includes redundant leading and training 1s for kSizes, strides, rates (positions 0/3)
        return new int[]{(int)ilist.getI(1), (int)ilist.getI(2)};
    }
}

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

package org.nd4j.linalg.lossfunctions.impl;

import lombok.EqualsAndHashCode;
import onnx.OnnxProto3;
import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.ops.Op;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by susaneraly on 9/9/16.
 */
@EqualsAndHashCode
public class LossCosineProximity extends DifferentialFunction implements ILossFunction {

    /**
     *
     * @param labels
     * @param preOutput
     * @param activationFn
     * @param mask
     * @return
     */
    public INDArray scoreArray(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        if(!labels.equalShapes(preOutput)){
            Preconditions.throwEx("Labels and preOutput must have equal shapes: got shapes %s vs %s", labels.shape(), preOutput.shape());
        }
        /*
         mean of -(y.dot(yhat)/||y||*||yhat||)
         */
        INDArray postOutput = activationFn.getActivation(preOutput.dup(), true);

        INDArray yhatmag = postOutput.norm2(1);
        INDArray ymag = labels.norm2(1);
        yhatmag = Transforms.max(yhatmag, Nd4j.EPS_THRESHOLD, false);
        ymag = Transforms.max(ymag, Nd4j.EPS_THRESHOLD, false);

        INDArray scoreArr = postOutput.mul(labels);
        scoreArr.diviColumnVector(yhatmag);
        scoreArr.diviColumnVector(ymag);

        if (mask != null) {
            if (!mask.isColumnVector()) {
                //Per-output masking doesn't really make sense for cosine proximity
                throw new UnsupportedOperationException("Expected column vector mask array for LossCosineProximity."
                                + " Got mask array with shape " + Arrays.toString(mask.shape())
                                + "; per-output masking is not " + "supported for LossCosineProximity");
            }
            scoreArr.muliColumnVector(mask);
        }
        return scoreArr.muli(-1);
    }

    @Override
    public double computeScore(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask,
                    boolean average) {
        INDArray scoreArr = scoreArray(labels, preOutput, activationFn, mask);

        double score = scoreArr.sumNumber().doubleValue();

        if (average)
            score /= scoreArr.size(0);

        return score;
    }

    @Override
    public INDArray computeScoreArray(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        INDArray scoreArr = scoreArray(labels, preOutput, activationFn, mask);
        return scoreArr.sum(true,1);
    }

    @Override
    public INDArray computeGradient(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        if(!labels.equalShapes(preOutput)){
            Preconditions.throwEx("Labels and preOutput must have equal shapes: got shapes %s vs %s", labels.shape(), preOutput.shape());
        }
        INDArray yhat = activationFn.getActivation(preOutput.dup(), true);
        INDArray yL2norm = labels.norm2(1);

        INDArray yhatL2norm = yhat.norm2(1);
        INDArray yhatL2normSq = yhatL2norm.mul(yhatL2norm);

        //Note: This is not really the L1 norm since I am not taking abs values
        INDArray yhatDotyL1norm = labels.mul(yhat).sum(true,1);

        INDArray dLda = labels.mulColumnVector(yhatL2normSq);
        dLda.subi(yhat.mulColumnVector(yhatDotyL1norm));

        // transform vals to avoid nans before div
        yL2norm = Transforms.max(yL2norm, Nd4j.EPS_THRESHOLD, false);
        yhatL2norm = Transforms.max(yhatL2norm, Nd4j.EPS_THRESHOLD, false);
        yhatL2normSq = Transforms.max(yhatL2normSq, Nd4j.EPS_THRESHOLD, false);

        dLda.diviColumnVector(yL2norm);
        dLda.diviColumnVector(yhatL2norm.mul(yhatL2normSq));
        dLda.muli(-1);

        //dL/dz
        INDArray gradients = activationFn.backprop(preOutput, dLda).getFirst(); //TODO loss functions with params

        if (mask != null) {
            gradients.muliColumnVector(mask);
        }

        return gradients;
    }

    @Override
    public Pair<Double, INDArray> computeGradientAndScore(INDArray labels,
                    INDArray preOutput, IActivation activationFn, INDArray mask, boolean average) {
        //TODO: probably a more efficient way to do this...

        return new Pair<>(computeScore(labels, preOutput, activationFn, mask, average),
                        computeGradient(labels, preOutput, activationFn, mask));
    }

    /**
     * The opName of this function
     *
     * @return
     */
    @Override
    public String name() {
        return toString();
    }


    @Override
    public String toString() {
        return "LossCosineProximity()";
    }

    @Override
    public SDVariable[] outputVariables() {
        return new SDVariable[0];
    }

    @Override
    public SDVariable[] outputVariables(String baseName) {
        return new SDVariable[0];
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        return null;
    }



    @Override
    public String opName() {
        return "losscosinedistance";
    }

    @Override
    public Op.Type opType() {
        return Op.Type.CUSTOM;
    }

    @Override
    public void initFromTensorFlow(NodeDef nodeDef, SameDiff initWith, Map<String, AttrValue> attributesForNode, GraphDef graph) {

    }

    @Override
    public void initFromOnnx(OnnxProto3.NodeProto node, SameDiff initWith, Map<String, OnnxProto3.AttributeProto> attributesForNode, OnnxProto3.GraphProto graph) {

    }

    @Override
    public String onnxName() {
        return "CosineDistance";
    }

    @Override
    public String tensorflowName() {
        return "CosineDistance";
    }
}

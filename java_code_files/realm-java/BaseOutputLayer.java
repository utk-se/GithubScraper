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

package org.deeplearning4j.nn.layers;

import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.api.layers.IOutputLayer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.deeplearning4j.optimize.Solver;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.primitives.Pair;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Output layer with different objective
 * in co-occurrences for different objectives.
 * This includes classification as well as prediction
 * @author Adam Gibson
 *
 */
public abstract class BaseOutputLayer<LayerConfT extends org.deeplearning4j.nn.conf.layers.BaseOutputLayer>
        extends BaseLayer<LayerConfT> implements Serializable, IOutputLayer {

    //current input and label matrices
    protected INDArray labels;

    private transient Solver solver;

    private double fullNetworkL1;
    private double fullNetworkL2;

    protected INDArray inputMaskArray;
    protected MaskState inputMaskArrayState;

    public BaseOutputLayer(NeuralNetConfiguration conf) {
        super(conf);
    }

    public BaseOutputLayer(NeuralNetConfiguration conf, INDArray input) {
        super(conf, input);
    }

    /** Compute score after labels and input have been set.
     * @param fullNetworkL1 L1 regularization term for the entire network
     * @param fullNetworkL2 L2 regularization term for the entire network
     * @param training whether score should be calculated at train or test time (this affects things like application of
     *                 dropout, etc)
     * @return score (loss function)
     */
    @Override
    public double computeScore(double fullNetworkL1, double fullNetworkL2, boolean training, LayerWorkspaceMgr workspaceMgr) {
        if (input == null || labels == null)
            throw new IllegalStateException("Cannot calculate score without input and labels " + layerId());
        this.fullNetworkL1 = fullNetworkL1;
        this.fullNetworkL2 = fullNetworkL2;
        INDArray preOut = preOutput2d(training, workspaceMgr);

        ILossFunction lossFunction = layerConf().getLossFn();

        double score = lossFunction.computeScore(getLabels2d(workspaceMgr, ArrayType.FF_WORKING_MEM), preOut,
                layerConf().getActivationFn(), maskArray,false);

        boolean legacy = layerConf().isLegacyBatchScaledL2();
        if(legacy){
            score += fullNetworkL1 + fullNetworkL2;
        }

        if(conf().isMiniBatch())
            score /= getInputMiniBatchSize();

        if(!legacy){
            score += fullNetworkL1 + fullNetworkL2;
        }

        this.score = score;

        return score;
    }

    @Override
    public boolean needsLabels() {
        return true;
    }

    /**Compute the score for each example individually, after labels and input have been set.
     *
     * @param fullNetworkL1 L1 regularization term for the entire network (or, 0.0 to not include regularization)
     * @param fullNetworkL2 L2 regularization term for the entire network (or, 0.0 to not include regularization)
     * @return A column INDArray of shape [numExamples,1], where entry i is the score of the ith example
     */
    @Override
    public INDArray computeScoreForExamples(double fullNetworkL1, double fullNetworkL2, LayerWorkspaceMgr workspaceMgr) {
        if (input == null || labels == null)
            throw new IllegalStateException("Cannot calculate score without input and labels " + layerId());
        INDArray preOut = preOutput2d(false, workspaceMgr);

        ILossFunction lossFunction = layerConf().getLossFn();
        INDArray scoreArray =
                lossFunction.computeScoreArray(getLabels2d(workspaceMgr, ArrayType.FF_WORKING_MEM),
                        preOut, layerConf().getActivationFn(), maskArray);
        double l1l2 = fullNetworkL1 + fullNetworkL2;
        if (l1l2 != 0.0) {
            scoreArray.addi(l1l2);
        }
        return workspaceMgr.leverageTo(ArrayType.ACTIVATIONS, scoreArray);
    }

    @Override
    public void computeGradientAndScore(LayerWorkspaceMgr workspaceMgr) {
        if (input == null || labels == null)
            return;

        INDArray preOut = preOutput2d(true, workspaceMgr);
        Pair<Gradient, INDArray> pair = getGradientsAndDelta(preOut, workspaceMgr);
        this.gradient = pair.getFirst();

        score = computeScore(fullNetworkL1, fullNetworkL2, true, workspaceMgr);
    }

    @Override
    protected void setScoreWithZ(INDArray z) {
        throw new RuntimeException("Not supported - " + layerId());
    }

    @Override
    public Pair<Gradient, Double> gradientAndScore() {
        return new Pair<>(gradient(), score());
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(true);
        Pair<Gradient, INDArray> pair = getGradientsAndDelta(preOutput2d(true, workspaceMgr), workspaceMgr); //Returns Gradient and delta^(this), not Gradient and epsilon^(this-1)
        INDArray delta = pair.getSecond();

        INDArray w = getParamWithNoise(DefaultParamInitializer.WEIGHT_KEY, true, workspaceMgr);
        INDArray epsilonNext = workspaceMgr.createUninitialized(ArrayType.ACTIVATION_GRAD, new long[]{w.size(0), delta.size(0)}, 'f');
        epsilonNext = w.mmuli(delta.transpose(), epsilonNext).transpose();

        //Normally we would clear weightNoiseParams here - but we want to reuse them for forward + backward + score
        // So this is instead done in MultiLayerNetwork/CompGraph backprop methods

        epsilonNext = backpropDropOutIfPresent(epsilonNext);
        return new Pair<>(pair.getFirst(), epsilonNext);
    }

    /**
     * Gets the gradient from one training iteration
     * @return the gradient (bias and weight matrix)
     */
    @Override
    public Gradient gradient() {
        return gradient;
    }

    /** Returns tuple: {Gradient,Delta,Output} given preOut */
    private Pair<Gradient, INDArray> getGradientsAndDelta(INDArray preOut, LayerWorkspaceMgr workspaceMgr) {
        ILossFunction lossFunction = layerConf().getLossFn();
        INDArray labels2d = getLabels2d(workspaceMgr, ArrayType.BP_WORKING_MEM);
        //INDArray delta = lossFunction.computeGradient(labels2d, preOut, layerConf().getActivationFunction(), maskArray);
        INDArray delta = lossFunction.computeGradient(labels2d, preOut, layerConf().getActivationFn(), maskArray);

        Gradient gradient = new DefaultGradient();

        INDArray weightGradView = gradientViews.get(DefaultParamInitializer.WEIGHT_KEY);
        Nd4j.gemm(input.castTo(weightGradView.dataType()), delta, weightGradView, true, false, 1.0, 0.0); //Equivalent to:  weightGradView.assign(input.transpose().mmul(delta));         //TODO can we avoid cast?
        gradient.gradientForVariable().put(DefaultParamInitializer.WEIGHT_KEY, weightGradView);

        if(hasBias()){
            INDArray biasGradView = gradientViews.get(DefaultParamInitializer.BIAS_KEY);
            delta.sum(biasGradView, 0); //biasGradView is initialized/zeroed first in sum op
            gradient.gradientForVariable().put(DefaultParamInitializer.BIAS_KEY, biasGradView);
        }

        delta = workspaceMgr.leverageTo(ArrayType.ACTIVATION_GRAD, delta);
        return new Pair<>(gradient, delta);
    }


    @Override
    public INDArray activate(INDArray input, boolean training, LayerWorkspaceMgr workspaceMgr) {
        setInput(input, workspaceMgr);
        return activate(training, workspaceMgr);
    }


    /**
     * Sets the input and labels and returns a score for the prediction
     * wrt true labels
     *
     * @param data the data to score
     * @return the score for the given input,label pairs
     */
    @Override
    public double f1Score(DataSet data) {
        return f1Score(data.getFeatures(), data.getLabels());
    }

    /**
     * Returns the f1 score for the given examples.
     *
     * @param examples te the examples to classify (one example in each row)
     * @param labels   the true labels
     * @return the scores for each ndarray
     */
    @Override
    public double f1Score(INDArray examples, INDArray labels) {
        Evaluation eval = new Evaluation();
        eval.eval(labels, activate(examples, false, LayerWorkspaceMgr.noWorkspacesImmutable()));
        return eval.f1();
    }

    /**
     * Returns the number of possible labels
     *
     * @return the number of possible labels for this classifier
     */
    @Override
    public int numLabels() {
        // FIXME: int cast
        return (int) labels.size(1);
    }

    @Override
    public void fit(DataSetIterator iter) {
        while (iter.hasNext())
            fit(iter.next());
    }

    /**
     * Returns the predictions for each example in the dataset
     * @param input the matrix to predict
     * @return the prediction for the dataset
     */
    @Override
    public int[] predict(INDArray input) {
        INDArray output = activate(input, false, LayerWorkspaceMgr.noWorkspacesImmutable());
        int[] ret = new int[input.rows()];
        for (int i = 0; i < ret.length; i++)
            ret[i] = Nd4j.getBlasWrapper().iamax(output.getRow(i));
        return ret;
    }

    /**
     * Return predicted label names
     *
     * @param dataSet to predict
     * @return the predicted labels for the dataSet
     */
    @Override
    public List<String> predict(DataSet dataSet) {
        int[] intRet = predict(dataSet.getFeatures());
        List<String> ret = new ArrayList<>();
        for (int i : intRet) {
            ret.add(i, dataSet.getLabelName(i));
        }
        return ret;
    }

    /**
     * Fit the model
     *
     * @param input the examples to classify (one example in each row)
     * @param labels   the example labels(a binary outcome matrix)
     */
    @Override
    public void fit(INDArray input, INDArray labels) {
        throw new UnsupportedOperationException("Not supported");
    }

    /**
     * Fit the model
     *
     * @param data the data to train on
     */
    @Override
    public void fit(DataSet data) {
        throw new UnsupportedOperationException("Not supported");
    }

    /**
     * Fit the model
     *
     * @param examples the examples to classify (one example in each row)
     * @param labels   the labels for each example (the number of labels must match
     */
    @Override
    public void fit(INDArray examples, int[] labels) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void clear() {
        super.clear();
        labels = null;
        solver = null;
        inputMaskArrayState = null;
        inputMaskArray = null;
        fullNetworkL1 = 0.0;
        fullNetworkL2 = 0.0;
    }

    @Override
    public void fit(INDArray data, LayerWorkspaceMgr workspaceMgr) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public INDArray getLabels() {
        return labels;
    }

    public void setLabels(INDArray labels) {
        this.labels = labels;
    }

    protected INDArray preOutput2d(boolean training, LayerWorkspaceMgr workspaceMgr) {
        return preOutput(training, workspaceMgr);
    }

    @Override
    protected void applyMask(INDArray to) {
        //For output layers: can be either per-example masking, or per-
        if (maskArray.isColumnVectorOrScalar()) {
            to.muliColumnVector(maskArray);
        } else if (Arrays.equals(to.shape(), maskArray.shape())) {
            to.muli(maskArray);
        } else {
            throw new IllegalStateException("Invalid mask array: per-example masking should be a column vector, "
                    + "per output masking arrays should be the same shape as the output/labels arrays. Mask shape: "
                    + Arrays.toString(maskArray.shape()) + ", output shape: " + Arrays.toString(to.shape())
                    + layerId());
        }
    }

    protected abstract INDArray getLabels2d(LayerWorkspaceMgr workspaceMgr, ArrayType arrayType);

    @Override
    public boolean isPretrainLayer() {
        return false;
    }

    @Override
    public boolean hasBias() {
        return layerConf().hasBias();
    }
}

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

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.api.TrainingConfig;
import org.deeplearning4j.nn.api.layers.LayerConstraint;
import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.deeplearning4j.optimize.api.ConvexOptimizer;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.primitives.Pair;

import java.util.*;

/**
 * A layer with input and output, no parameters or gradients
 */
@Data
@NoArgsConstructor
public abstract class AbstractLayer<LayerConfT extends org.deeplearning4j.nn.conf.layers.Layer> implements Layer {

    @Setter(AccessLevel.NONE)
    protected INDArray input;
    protected INDArray preOutput;
    protected NeuralNetConfiguration conf;
    protected boolean dropoutApplied = false;
    protected Collection<TrainingListener> trainingListeners = new ArrayList<>();
    protected int index = 0;
    protected INDArray maskArray;
    protected MaskState maskState;
    protected CacheMode cacheMode = CacheMode.NONE;
    protected boolean inputModificationAllowed = false;

    protected int iterationCount;
    protected int epochCount;

    public AbstractLayer(NeuralNetConfiguration conf) {
        this.conf = conf;
        cacheMode = conf.getCacheMode();
    }

    public AbstractLayer(NeuralNetConfiguration conf, INDArray input) {
        this(conf);
        this.input = input;
    }

    @Override
    public void setCacheMode(CacheMode mode) {
        if (mode == null)
            mode = CacheMode.NONE;

        this.cacheMode = mode;
    }

    public LayerConfT layerConf() {
        return (LayerConfT) this.conf.getLayer();
    }

    @Override
    public TrainingConfig getConfig(){
        return conf.getLayer();
    }

    protected String layerId() {
        String name = this.conf().getLayer().getLayerName();
        return "(layer name: " + (name == null ? "\"\"" : name) + ", layer index: " + index + ", layer type: " +
                getClass().getSimpleName() + ")";
    }

    public INDArray getInput() {
        return input;
    }

    public int getEpochCount() {
        return epochCount;
    }

    public void setEpochCount(int epochCount) {
        this.epochCount = epochCount;
    }

    /**
     * Init the model
     */
    @Override
    public void init() {

    }

    @Override
    public void setInput(INDArray input, LayerWorkspaceMgr workspaceMgr) {
        this.input = workspaceMgr.leverageTo(ArrayType.INPUT, input);
        dropoutApplied = false;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public void setIndex(int index) {
        this.index = index;
    }


    @Override
    public Collection<TrainingListener> getListeners() {
        return trainingListeners;
    }

    @Override
    public void setListeners(Collection<TrainingListener> listeners) {
        this.trainingListeners = listeners != null ? listeners : new ArrayList<TrainingListener>();
    }

    /**
     * This method ADDS additional TrainingListener to existing listeners
     *
     * @param listeners
     */
    @Override
    public void addListeners(TrainingListener... listeners) {
        if (this.trainingListeners == null) {
            setListeners(listeners);
            return;
        }

        Collections.addAll(trainingListeners, listeners);
    }

    @Override
    public void setListeners(TrainingListener... listeners) {
        setListeners(Arrays.asList(listeners));
    }

    @Override
    public void computeGradientAndScore(LayerWorkspaceMgr workspaceMgr) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void update(Gradient gradient) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(INDArray gradient, String paramType) {
        throw new UnsupportedOperationException();
    }


    @Override
    public ConvexOptimizer getOptimizer() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void setConf(NeuralNetConfiguration conf) {
        this.conf = conf;
    }

    /**Returns the parameters of the neural network as a flattened row vector
     * @return the parameters of the neural network
     */
    @Override
    public INDArray params() {
        return null;
    }

    @Override
    public INDArray getParam(String param) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void setParam(String key, INDArray val) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void setParams(INDArray params) {
        if (params != null) {
            throw new UnsupportedOperationException("Not supported");
        }
    }

    protected void setParams(INDArray params, char order) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void setParamsViewArray(INDArray params) {
        if (params != null) {
            throw new UnsupportedOperationException("Not supported");
        }
    }

    @Override
    public INDArray getGradientsViewArray() {
        return null;
    }

    @Override
    public void setBackpropGradientsViewArray(INDArray gradients) {
        if (gradients != null) {
            throw new UnsupportedOperationException("Not supported");
        }
    }

    @Override
    public void setParamTable(Map<String, INDArray> paramTable) {
        if (paramTable != null && !paramTable.isEmpty()) {
            throw new UnsupportedOperationException("Not supported");
        }
    }

    @Override
    public Map<String, INDArray> paramTable() {
        return paramTable(false);
    }

    @Override
    public Map<String, INDArray> paramTable(boolean backpropParamsOnly) {
        return Collections.emptyMap();
    }

    protected void applyMask(INDArray to) {
        to.muliColumnVector(maskArray);
    }

    @Override
    public INDArray activate(INDArray input, boolean training, LayerWorkspaceMgr workspaceMgr) {
        setInput(input, workspaceMgr);
        return activate(training, workspaceMgr);
    }

    @Override
    public double calcL2(boolean backpropParamsOnly) {
        return 0.0;
    }

    @Override
    public double calcL1(boolean backpropParamsOnly) {
        return 0.0;
    }

    @Override
    public int batchSize() {
        return (int) input.size(0);
    }

    @Override
    public NeuralNetConfiguration conf() {
        return conf;
    }


    @Override
    public void clear() {
        input = null;
        maskArray = null;
        maskState = null;
        if(layerConf().getIDropout() != null){
            layerConf().getIDropout().clear();
        }
    }

    protected void applyDropOutIfNecessary(boolean training, LayerWorkspaceMgr workspaceMgr){
        if(training && !dropoutApplied && layerConf().getIDropout() != null ){
            INDArray result;
            if(inputModificationAllowed){
                result = input;
            } else {
                result = workspaceMgr.createUninitialized(ArrayType.INPUT, input.shape(), input.ordering());
            }

            input = layerConf().getIDropout().applyDropout(input, result, getIterationCount(), getEpochCount(), workspaceMgr);
            dropoutApplied = true;
        }
    }

    protected INDArray backpropDropOutIfPresent(INDArray epsilon){
        if(layerConf().getIDropout() != null ){
            layerConf().getIDropout().backprop(epsilon, epsilon, getIterationCount(), getEpochCount());
        }
        return epsilon;
    }


    @Override
    public Type type() {
        return Type.FEED_FORWARD;
    }

    /**
     * The number of parameters for the model
     *
     * @return the number of parameters for the model
     */
    @Override
    public long numParams() {
        return 0;
    }

    @Override
    public long numParams(boolean backwards) {
        return numParams();
    }

    @Override
    public void fit(INDArray input, LayerWorkspaceMgr workspaceMgr) {
        throw new UnsupportedOperationException("Not supported");
    }


    @Override
    public Pair<Gradient, Double> gradientAndScore() {
        return new Pair<>(gradient(), score());
    }

    @Override
    public INDArray input() {
        return input;
    }

    @Override
    public void setInputMiniBatchSize(int size) {}

    @Override
    public int getInputMiniBatchSize() {
        // FIXME: int cast
        return (int) input.size(0);
    }

    @Override
    public void setMaskArray(INDArray maskArray) {
        this.maskArray = maskArray;
    }

    @Override
    public INDArray getMaskArray() {
        return maskArray;
    }


    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArray(INDArray maskArray, MaskState currentMaskState, int minibatchSize) {
        //Most layers: CNN, dense, activation, etc - set mask array, mask state and then leave the mask unmodified

        this.maskArray = maskArray;
        this.maskState = currentMaskState;

        return new Pair<>(maskArray, currentMaskState);
    }


    @Override
    public Gradient gradient() {
        throw new UnsupportedOperationException(
                        "Not supported for this layer, or should be overridden for layers requiring it");
    }

    @Override
    public void fit() {
        throw new UnsupportedOperationException(
                        "Not supported for this layer, or should be overridden for layers requiring it");
    }

    @Override
    public double score() {
        throw new UnsupportedOperationException(
                        "Not supported for this layer, or should be overridden for layers requiring it");
    }


    @Override
    public void applyConstraints(int iteration, int epoch){
        if(layerConf().getConstraints() != null){
            for(LayerConstraint lc : layerConf().getConstraints()){
                lc.applyConstraint(this, iteration, epoch);
            }
        }
    }

    public void assertInputSet(boolean backprop){
        if(input == null){
            if(backprop){
                throw new IllegalStateException("Cannot perform backprop in layer " + getClass().getSimpleName()
                        + ": layer input field is not set");
            } else {
                throw new IllegalStateException("Cannot perform forward pass in layer " + getClass().getSimpleName()
                        + ": layer input field is not set");
            }
        }
    }

    @Override
    public void allowInputModification(boolean allow){
        inputModificationAllowed = allow;
    }

    @Override
    public LayerHelper getHelper() {
        //Layers with helpers should override this method!
        return null;
    }

    @Override
    public boolean updaterDivideByMinibatch(String paramName) {
        //Majority of params's gradients should be... Exception: batch norm mean/variance estimate
        return true;
    }
}

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

package org.deeplearning4j.nn.updater;

import lombok.Getter;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.api.Trainable;
import org.deeplearning4j.nn.api.Updater;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.CustomOp;
import org.nd4j.linalg.api.ops.DynamicCustomOp;

import org.nd4j.linalg.api.ops.impl.reduce.floating.Norm2;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.nd4j.linalg.learning.config.IUpdater;

import java.util.*;

/**
 * BaseMultiLayerUpdater - core functionality for applying updaters to MultiLayerNetwork and ComputationGraph.
 * <p>
 * This implements updater combining: that is, for any layers (and variables) that:<br>
 * (a) have contiguous parameters/gradients in the view arrays, and<br>
 * (b) have identical updater configuration (including updater, LR, LR/momentum schedules, etc - different L1/L2 are OK,
 *     however)<br>
 * are combined into a single {@link org.nd4j.linalg.learning.GradientUpdater} operation, instead of having a set of
 * smaller operations. A smaller number of larger operations improves performance, especially for GPUs.
 *
 * @author Alex Black
 */
@Getter
public abstract class BaseMultiLayerUpdater<T extends Model> implements Updater {

    protected final T network;
    protected Map<String, Trainable> layersByName;
    protected final List<UpdaterBlock> updaterBlocks;
    protected INDArray updaterStateViewArray;
    protected boolean legacyBatchScaledL2;
    protected boolean initializedMinibatchDivision;
    protected List<INDArray> gradientsForMinibatchDivision;

    public BaseMultiLayerUpdater(T network, boolean legacyBatchScaledL2) {
        this(network, null, legacyBatchScaledL2);
    }

    /**
     *
     * @param network      Network to create the updater for
     * @param updaterState The updater state to use. Note: This array is used *directly* and isn't copied/cloned
     */
    public BaseMultiLayerUpdater(T network, INDArray updaterState, boolean legacyBatchScaledL2) {
        this.network = network;
        this.legacyBatchScaledL2 = legacyBatchScaledL2;
        Trainable[] layers = getOrderedLayers();    //May also include vertices

        int updaterStateSize = 0;
        //Iterate through layers, and variables for each layer.
        //While the updater configuration is the same: combine into one op, rather than doing a lot of smaller
        // (yet identical) ops.
        Trainable lastLayer = null;
        String lastVariable = null;
        UpdaterBlock currentBlock = null;
        updaterBlocks = new ArrayList<>();


        INDArray paramsView = network.params();
        INDArray gradientView = getFlattenedGradientsView();
        int paramsViewSoFar = 0;
        int currentUpdaterOffset = 0;
        for (int i = 0; i < layers.length; i++) {
            Map<String, INDArray> layerParamTable = layers[i].paramTable(false);
            if (layerParamTable != null) {
                List<String> variables = new ArrayList<>(layerParamTable.keySet()); //Is from a set, but iteration order should be fixed per layer as it's a from a LinkedHashSet
                for (int j = 0; j < variables.size(); j++) {
                    String var = variables.get(j);
                    long paramSizeThisVariable = layerParamTable.get(var).length();
                    IUpdater u = layers[i].getConfig().getUpdaterByParam(var);
                    Preconditions.checkNotNull(u, "Updater for parameter %s, layer \"%s\" was null", var, layers[i].getConfig().getLayerName());
                    int updaterStateSizeThisVariable = (int) u.stateSize(paramSizeThisVariable);

                    INDArray gradientViewSubset = null;
                    INDArray paramsViewSubset = null;
                    if (paramSizeThisVariable > 0) {
                        paramsViewSubset = paramsView.get(NDArrayIndex.point(0), NDArrayIndex.interval(paramsViewSoFar,
                                        paramsViewSoFar + paramSizeThisVariable));
                        gradientViewSubset = gradientView.get(NDArrayIndex.point(0), NDArrayIndex
                                        .interval(paramsViewSoFar, paramsViewSoFar + paramSizeThisVariable));
                    }

                    //First: decide whether to add to the existing updater block, or create a new one
                    if (currentBlock == null || !UpdaterUtils.updaterConfigurationsEquals(lastLayer, lastVariable,
                                    layers[i], var)) {

                        // FIXME: int cast
                        //Create a new block
                        List<UpdaterBlock.ParamState> list = new ArrayList<>();
                        list.add(new UpdaterBlock.ParamState(layers[i], var, paramsViewSoFar,
                                (int) (paramsViewSoFar + paramSizeThisVariable), paramsViewSubset, gradientViewSubset));
                        currentBlock = new UpdaterBlock(paramsViewSoFar, (int) (paramsViewSoFar + paramSizeThisVariable),
                                        currentUpdaterOffset, currentUpdaterOffset + updaterStateSizeThisVariable,
                                        list);

                        updaterBlocks.add(currentBlock);
                    } else {
                        // FIXME: int cast
                        //Add to existing updater block
                        currentBlock.setParamOffsetEnd((int) (currentBlock.getParamOffsetEnd() + paramSizeThisVariable));
                        currentBlock.setUpdaterViewOffsetEnd(
                                        currentBlock.getUpdaterViewOffsetEnd() + updaterStateSizeThisVariable);
                        currentBlock.getLayersAndVariablesInBlock()
                                        .add(new UpdaterBlock.ParamState(layers[i], var, paramsViewSoFar,
                                                (int) (paramsViewSoFar + paramSizeThisVariable), paramsViewSubset,
                                                        gradientViewSubset));
                    }

                    lastLayer = layers[i];
                    lastVariable = variables.get(j);
                    updaterStateSize += updaterStateSizeThisVariable;
                    paramsViewSoFar += paramSizeThisVariable;
                    currentUpdaterOffset += updaterStateSizeThisVariable;
                }
            }
        }

        //Initialize the updater state, if required
        boolean updaterRequiresInit = false;
        if (updaterState != null) {
            updaterStateViewArray = updaterState;
            updaterRequiresInit = false;
        } else if (updaterStateSize > 0) {
            //May be 0 if all SGD or NONE updaters, for example
            updaterStateViewArray = Nd4j.createUninitialized(new int[] {1, updaterStateSize}, Nd4j.order());
            updaterRequiresInit = true;
        }

        //Create and set up the updaters, for the updater blocks:
        int updaterViewSoFar = 0;
        paramsViewSoFar = 0;
        for (int i = 0; i < updaterBlocks.size(); i++) {
            UpdaterBlock ub = updaterBlocks.get(i);

            int viewStateSize = ub.getUpdaterViewOffsetEnd() - ub.getUpdaterViewOffsetStart();
            int gradSize = ub.getParamOffsetEnd() - ub.getParamOffsetStart();

            if (viewStateSize > 0) {
                INDArray updaterViewSubset = updaterStateViewArray.get(NDArrayIndex.point(0),
                                NDArrayIndex.interval(updaterViewSoFar, updaterViewSoFar + viewStateSize));
                ub.setUpdaterView(updaterViewSubset);
                ub.setUpdaterViewRequiresInitialization(updaterRequiresInit);
            }

            if (gradSize > 0) {
                INDArray gradientViewSubset = gradientView.get(NDArrayIndex.point(0),
                                NDArrayIndex.interval(paramsViewSoFar, paramsViewSoFar + gradSize));
                ub.setGradientView(gradientViewSubset);
            }

            ub.init();

            updaterViewSoFar += viewStateSize;
            paramsViewSoFar += gradSize;
        }
    }

    /**
     *
     * @return Array of layers, in the correct order (i.e., same order as the parameter/gradient/updater flattening
     * order - input to output for MultiLayerNetwork, or topological order for ComputationGraph)
     */
    protected abstract Trainable[] getOrderedLayers();

    /**
     * @return The flattened gradient view array for the model
     */
    protected abstract INDArray getFlattenedGradientsView();

    /**
     * @return The flattened parameter array for the model
     */
    protected abstract INDArray getParams();

    /**
     * @return True if the configuration for the model is set to minibatch (divide by minibatch size), false otherwise
     */
    protected abstract boolean isMiniBatch();

    /**
     * Set the view array. Note that this does an assign operation - the provided array is not stored internally.
     *
     * @param viewArray The new updater state
     */
    public void setStateViewArray(INDArray viewArray) {
        if(this.updaterStateViewArray == null){
            if(viewArray == null)
                return; //No op - for example, SGD and NoOp updater - i.e., no stored state
            else {
                throw new IllegalStateException("Attempting to set updater state view array with null value");
            }
        }
        if (this.updaterStateViewArray.length() != viewArray.length())
            throw new IllegalStateException("Invalid input: view arrays differ in length. " + "Expected length "
                            + this.updaterStateViewArray.length() + ", got length " + viewArray.length());
        this.updaterStateViewArray.assign(viewArray);
    }

    @Override
    public void setStateViewArray(Trainable layer, INDArray viewArray, boolean initialize) {
        this.setStateViewArray(viewArray);
    }

    @Override
    public INDArray getStateViewArray() {
        return updaterStateViewArray;
    }

    /**
     * A synchronized version of {@link #getStateViewArray()} that duplicates the view array internally.
     * This should be used in preference to {@link #getStateViewArray()} when the updater state is accessed in one
     * thread while another thread is using the updater for training.
     * @return A copy (duplicate) of the updater state
     */
    public synchronized INDArray getStateViewArrayCopy(){
        Nd4j.getExecutioner().commit();
        return updaterStateViewArray.dup();
    }

    @Override
    public void update(Trainable layer, Gradient gradient, int iteration, int epoch, int batchSize, LayerWorkspaceMgr workspaceMgr) {
        update(gradient, iteration, epoch, batchSize, workspaceMgr);
    }

    /**
     * Update the gradient for the model.
     * This operates in 3 steps:
     * 1. Pre-apply: gradient clipping, etc on a per-layer basis
     * 2. Execute the updater (Adam, Nesterov momentum, etc) - in blocks of layers at a time
     * 3. Divide by minibatch size
     *
     * @param gradient  Gradient to updater
     * @param iteration The current iteration (i.e., number of parameter updates so far)
     * @param batchSize The current minibatch size (number of examples)
     */
    public synchronized void update(Gradient gradient, int iteration, int epoch, int batchSize, LayerWorkspaceMgr workspaceMgr) {

        //First: check if gradient is standard or external...
        //In a MultiLayerNetwork, the INDArray returned by .gradient() is always the standard full view array
        // hence should be the same object under normal circumstances
        boolean isExternal = gradient.gradient() != getFlattenedGradientsView();

        //Split up the gradients on a per-layer basis, for pre-apply
        Map<String, Gradient> layerGradients = new HashMap<>();

        Trainable[] layers = getOrderedLayers();
        if (layers.length == 1 && isSingleLayerUpdater()) {
            layerGradients.put(layers[0].getConfig().getLayerName(), gradient);
        } else {
            for (Map.Entry<String, INDArray> gradientPair : gradient.gradientForVariable().entrySet()) {
                String key = gradientPair.getKey();
                int idx = key.lastIndexOf('_');
                if (idx == -1)
                    throw new IllegalStateException(
                                    "Invalid key: Gradient key does not have layer separator: \"" + key + "\"");
                String layerName = key.substring(0, idx);

                Gradient g = layerGradients.get(layerName);
                if (g == null) {
                    g = new DefaultGradient();
                    layerGradients.put(layerName, g);
                }

                String newKey = key.substring(idx + 1);
                g.setGradientFor(newKey, gradientPair.getValue());
            }
        }

        if(!legacyBatchScaledL2 && isMiniBatch()){
            divideByMinibatch(isExternal, gradient, batchSize);
        }

        //PRE apply (gradient clipping, etc): done on a per-layer basis
        for (Map.Entry<String, Gradient> entry : layerGradients.entrySet()) {
            String layerName = entry.getKey();
            Trainable layer = layersByName.get(layerName);

            preApply(layer, layerGradients.get(layerName), iteration);
        }

        //Apply the updaters in blocks. This also applies LR and momentum schedules, L1 and L2
        if(getClass() != LayerUpdater.class){
            //OK for LayerUpdater as this is part of layerwise pretraining
            workspaceMgr.assertNotOpen(ArrayType.UPDATER_WORKING_MEM, "Updater working memory");
        }
        for (UpdaterBlock ub : updaterBlocks) {
            if (ub.skipDueToPretrainConfig(this instanceof LayerUpdater)) {
                //Should skip some updater blocks sometimes
                //For example, VAE decoder params while doing supervised backprop
                continue;
            }
            try(MemoryWorkspace ws = workspaceMgr.notifyScopeEntered(ArrayType.UPDATER_WORKING_MEM)){
                if (isExternal) {
                    //RL4J etc type case: calculate gradients in 1 net, update them in another
                    ub.updateExternalGradient(iteration, epoch, gradient.gradient(), getParams());
                } else {
                    //Standard case
                    ub.update(iteration, epoch);
                }
            }
        }

        if(legacyBatchScaledL2 && isMiniBatch()){
            divideByMinibatch(isExternal, gradient, batchSize);
        }
    }

    protected void divideByMinibatch(boolean isExternal, Gradient gradient, int batchSize){
        //Challenge here: most gradients are actual gradients, and should be divided by the minibatch to get the average
        //However, some 'gradients' are actually updates - an example being BatchNorm mean/variance estimates... these
        // shouldn't be modified

        if(!initializedMinibatchDivision){
            gradientsForMinibatchDivision = getMinibatchDivisionSubsets(getFlattenedGradientsView());
            initializedMinibatchDivision = true;
        }

        List<INDArray> toDivide;
        if(isExternal){
            toDivide = getMinibatchDivisionSubsets(gradient.gradient());
        } else {
            toDivide = gradientsForMinibatchDivision;
        }
        for(INDArray arr : toDivide){
            arr.divi(batchSize);
        }
    }

    protected List<INDArray> getMinibatchDivisionSubsets(INDArray from){
        List<INDArray> out = new ArrayList<>();
        long paramsSoFar = 0;
        long currentStart = 0;
        long currentEnd = 0;
        for(Trainable t : getOrderedLayers()){
            Set<String> layerParams = t.paramTable(false).keySet();
            Map<String,INDArray> paramTable = t.paramTable(false);
            for(String s : layerParams) {
                if(t.updaterDivideByMinibatch(s)){
                    currentEnd += paramTable.get(s).length();
                } else {
                    //This param/gradient subset should be excluded
                    if(currentEnd > currentStart){
                        INDArray subset = from.get(NDArrayIndex.point(0), NDArrayIndex.interval(currentStart, currentEnd));
                        out.add(subset);
                    }
                    currentStart = paramsSoFar + paramTable.get(s).length();
                    currentEnd = currentStart;
                }
                paramsSoFar += paramTable.get(s).length();
            }
        }

        if(currentEnd > currentStart && currentStart < from.length()){
            //Process last part of the gradient view array
            INDArray subset = from.get(NDArrayIndex.point(0), NDArrayIndex.interval(currentStart, currentEnd));
            out.add(subset);
        }
        return out;
    }

    protected boolean isSingleLayerUpdater() {
        return false;
    }

    /**
     * Pre-apply: Apply gradient normalization/clipping
     *
     * @param layer     Layer to apply gradient normalization/clipping for
     * @param gradient  Gradient to update
     * @param iteration The current iteration (i.e., number of parameter updates so far)
     */
    public void preApply(Trainable layer, Gradient gradient, int iteration) {

        if (layer.getConfig() == null || layer.numParams() == 0) {
            //Layer does not have parameters -> no gradient
            return;
        }

        GradientNormalization normalization = layer.getConfig().getGradientNormalization();
        if (normalization == null || normalization == GradientNormalization.None)
            return; //no op

        final double threshold = layer.getConfig().getGradientNormalizationThreshold();
        INDArray layerGradientView = layer.getGradientsViewArray();

        switch (normalization) {
            case RenormalizeL2PerLayer:
                if (layerGradientView != null) {
                    double l2 = layerGradientView.norm2Number().doubleValue();
                    if (l2 == 0.0)
                        l2 = 1e-5;  //Avoid 0/0 -> NaN
                    layerGradientView.divi(l2);
                }
                break;
            case RenormalizeL2PerParamType:
                for (INDArray g : gradient.gradientForVariable().values()) {
                    double l2 = Nd4j.getExecutioner().execAndReturn(new Norm2(g)).getFinalResult().doubleValue();
                    if (l2 == 0.0)
                        l2 = 1e-5;  //Avoid 0/0 -> NaN
                    g.divi(l2);
                }
                break;
            case ClipElementWiseAbsoluteValue:
                if (layerGradientView != null) {
                    CustomOp op = DynamicCustomOp.builder("clipbyvalue")
                            .addInputs(layerGradientView)
                            .callInplace(true)
                            .addFloatingPointArguments(-threshold, threshold)
                            .build();
                    Nd4j.getExecutioner().exec(op);
                }
                break;
            case ClipL2PerLayer:
                if (layerGradientView != null) {
                    double layerL2 = layerGradientView.norm2Number().doubleValue();
                    if (layerL2 > threshold) {
                        double scalingFactor = threshold / layerL2; // g = g / l2 * threshold ->
                        layerGradientView.muli(scalingFactor);
                    }
                }
                break;
            case ClipL2PerParamType:
                for (INDArray g : gradient.gradientForVariable().values()) {
                    double l2 = g.norm2Number().doubleValue();
                    if (l2 > threshold) {
                        double scalingFactor = l2 / threshold;
                        g.divi(scalingFactor);
                    }
                }
                break;
            default:
                throw new RuntimeException(
                                "Unknown (or not implemented) gradient normalization strategy: " + normalization);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        BaseMultiLayerUpdater<?> that = (BaseMultiLayerUpdater<?>) o;
        return updaterStateViewArray != null ? updaterStateViewArray.equals(that.updaterStateViewArray)
                        : that.updaterStateViewArray == null;
    }

    @Override
    public int hashCode() {
        int result = layersByName != null ? layersByName.hashCode() : 0;
        result = 31 * result + (updaterBlocks != null ? updaterBlocks.hashCode() : 0);
        result = 31 * result + (updaterStateViewArray != null ? updaterStateViewArray.hashCode() : 0);
        return result;
    }
}

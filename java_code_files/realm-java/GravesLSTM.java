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

package org.deeplearning4j.nn.layers.recurrent;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.params.GravesLSTMParamInitializer;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.primitives.Pair;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;

/**
 * LSTM layer implementation.
 * Based on Graves: Supervised Sequence Labelling with Recurrent Neural Networks
 * <a href="http://www.cs.toronto.edu/~graves/phd.pdf">http://www.cs.toronto.edu/~graves/phd.pdf</a>
 * See also for full/vectorized equations (and a comparison to other LSTM variants):
 * Greff et al. 2015, "LSTM: A Search Space Odyssey", pg11. This is the "vanilla" variant in said paper
 * <a href="http://arxiv.org/pdf/1503.04069.pdf">http://arxiv.org/pdf/1503.04069.pdf</a>
 *
 * @author Alex Black
 * @see LSTM LSTM class, for the version without peephole connections
 * @deprecated Will be eventually removed. Use {@link LSTM} instead, which has similar prediction accuracy, but supports
 * CuDNN for faster network training on CUDA (Nvidia) GPUs
 */
@Deprecated
@Slf4j
public class GravesLSTM extends BaseRecurrentLayer<org.deeplearning4j.nn.conf.layers.GravesLSTM> {
    public static final String STATE_KEY_PREV_ACTIVATION = "prevAct";
    public static final String STATE_KEY_PREV_MEMCELL = "prevMem";

    protected FwdPassReturn cachedFwdPass;

    public GravesLSTM(NeuralNetConfiguration conf) {
        super(conf);
    }

    public GravesLSTM(NeuralNetConfiguration conf, INDArray input) {
        super(conf, input);
    }

    @Override
    public Gradient gradient() {
        throw new UnsupportedOperationException(
                        "gradient() method for layerwise pretraining: not supported for LSTMs (pretraining not possible)"
                                        + layerId());
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        return backpropGradientHelper(epsilon, false, -1, workspaceMgr);
    }

    @Override
    public Pair<Gradient, INDArray> tbpttBackpropGradient(INDArray epsilon, int tbpttBackwardLength, LayerWorkspaceMgr workspaceMgr) {
        return backpropGradientHelper(epsilon, true, tbpttBackwardLength, workspaceMgr);
    }


    private Pair<Gradient, INDArray> backpropGradientHelper(final INDArray epsilon, final boolean truncatedBPTT,
                    final int tbpttBackwardLength, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(true);

        final INDArray inputWeights = getParamWithNoise(GravesLSTMParamInitializer.INPUT_WEIGHT_KEY, true, workspaceMgr);
        final INDArray recurrentWeights = getParamWithNoise(GravesLSTMParamInitializer.RECURRENT_WEIGHT_KEY, true, workspaceMgr); //Shape: [hiddenLayerSize,4*hiddenLayerSize+3]; order: [wI,wF,wO,wG,wFF,wOO,wGG]

        //First: Do forward pass to get gate activations, zs etc.
        FwdPassReturn fwdPass;
        if (truncatedBPTT) {
            fwdPass = activateHelper(true, stateMap.get(STATE_KEY_PREV_ACTIVATION),
                            stateMap.get(STATE_KEY_PREV_MEMCELL), true, workspaceMgr);
            //Store last time step of output activations and memory cell state in tBpttStateMap
            tBpttStateMap.put(STATE_KEY_PREV_ACTIVATION, fwdPass.lastAct.detach());
            tBpttStateMap.put(STATE_KEY_PREV_MEMCELL, fwdPass.lastMemCell.detach());
        } else {
            fwdPass = activateHelper(true, null, null, true, workspaceMgr);
        }


        Pair<Gradient, INDArray> p = LSTMHelpers.backpropGradientHelper(this.conf, this.layerConf().getGateActivationFn(), this.input,
                        recurrentWeights, inputWeights, epsilon, truncatedBPTT, tbpttBackwardLength, fwdPass, true,
                        GravesLSTMParamInitializer.INPUT_WEIGHT_KEY, GravesLSTMParamInitializer.RECURRENT_WEIGHT_KEY,
                        GravesLSTMParamInitializer.BIAS_KEY, gradientViews, maskArray, true, null,
                        workspaceMgr);

        weightNoiseParams.clear();
        p.setSecond(backpropDropOutIfPresent(p.getSecond()));
        return p;
    }

    @Override
    public INDArray activate(INDArray input, boolean training, LayerWorkspaceMgr workspaceMgr) {
        setInput(input, workspaceMgr);
        return activateHelper(training, null, null, false, workspaceMgr).fwdPassOutput;
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        return activateHelper(training, null, null, false, workspaceMgr).fwdPassOutput;
    }

    private FwdPassReturn activateHelper(final boolean training, final INDArray prevOutputActivations,
                    final INDArray prevMemCellState, boolean forBackprop, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(false);
        Preconditions.checkState(input.rank() == 3,
                "3D input expected to RNN layer expected, got " + input.rank());
        applyDropOutIfNecessary(training, workspaceMgr);

//        if (cacheMode == null)
//            cacheMode = CacheMode.NONE;

        //TODO LSTM cache mode is disabled for now - not passing all tests
        cacheMode = CacheMode.NONE;

        if (forBackprop && cachedFwdPass != null) {
            FwdPassReturn ret = cachedFwdPass;
            cachedFwdPass = null;
            return ret;
        }

        final INDArray recurrentWeights = getParamWithNoise(GravesLSTMParamInitializer.RECURRENT_WEIGHT_KEY, training, workspaceMgr); //Shape: [hiddenLayerSize,4*hiddenLayerSize+3]; order: [wI,wF,wO,wG,wFF,wOO,wGG]
        final INDArray inputWeights = getParamWithNoise(GravesLSTMParamInitializer.INPUT_WEIGHT_KEY, training, workspaceMgr); //Shape: [n^(L-1),4*hiddenLayerSize]; order: [wi,wf,wo,wg]
        final INDArray biases = getParamWithNoise(GravesLSTMParamInitializer.BIAS_KEY, training, workspaceMgr); //by row: IFOG			//Shape: [4,hiddenLayerSize]; order: [bi,bf,bo,bg]^T

        FwdPassReturn fwd = LSTMHelpers.activateHelper(this, this.conf, this.layerConf().getGateActivationFn(),
                        this.input, recurrentWeights, inputWeights, biases, training, prevOutputActivations,
                        prevMemCellState, forBackprop || (cacheMode != CacheMode.NONE && training), true,
                        GravesLSTMParamInitializer.INPUT_WEIGHT_KEY, maskArray, true, null,
                        cacheMode, workspaceMgr);


        if (training && cacheMode != CacheMode.NONE) {
            cachedFwdPass = fwd;
        }

        return fwd;
    }

    @Override
    public Type type() {
        return Type.RECURRENT;
    }

    @Override
    public boolean isPretrainLayer() {
        return false;
    }

    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArray(INDArray maskArray, MaskState currentMaskState,
                    int minibatchSize) {
        //LSTM (standard, not bi-directional) don't make any changes to the data OR the mask arrays
        //Any relevant masking occurs during backprop
        //They also set the current mask array as inactive: this is for situations like the following:
        // in -> dense -> lstm -> dense -> lstm
        // The first dense should be masked using the input array, but the second shouldn't. If necessary, the second
        // dense will be masked via the output layer mask

        return new Pair<>(maskArray, MaskState.Passthrough);
    }

    @Override
    public INDArray rnnTimeStep(INDArray input, LayerWorkspaceMgr workspaceMgr) {
        setInput(input, workspaceMgr);
        FwdPassReturn fwdPass = activateHelper(false, stateMap.get(STATE_KEY_PREV_ACTIVATION),
                        stateMap.get(STATE_KEY_PREV_MEMCELL), false, workspaceMgr);
        INDArray outAct = fwdPass.fwdPassOutput;
        //Store last time step of output activations and memory cell state for later use:
        stateMap.put(STATE_KEY_PREV_ACTIVATION, fwdPass.lastAct.detach());
        stateMap.put(STATE_KEY_PREV_MEMCELL, fwdPass.lastMemCell.detach());

        return outAct;
    }



    @Override
    public INDArray rnnActivateUsingStoredState(INDArray input, boolean training, boolean storeLastForTBPTT, LayerWorkspaceMgr workspaceMgr) {
        setInput(input, workspaceMgr);
        FwdPassReturn fwdPass = activateHelper(training, stateMap.get(STATE_KEY_PREV_ACTIVATION),
                        stateMap.get(STATE_KEY_PREV_MEMCELL), false, workspaceMgr);
        INDArray outAct = fwdPass.fwdPassOutput;
        if (storeLastForTBPTT) {
            //Store last time step of output activations and memory cell state in tBpttStateMap
            tBpttStateMap.put(STATE_KEY_PREV_ACTIVATION, fwdPass.lastAct.detach());
            tBpttStateMap.put(STATE_KEY_PREV_MEMCELL, fwdPass.lastMemCell.detach());
        }

        return outAct;
    }
}

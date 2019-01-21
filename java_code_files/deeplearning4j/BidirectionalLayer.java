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

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.val;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.api.TrainingConfig;
import org.deeplearning4j.nn.api.layers.RecurrentLayer;
import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.recurrent.Bidirectional;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.LayerHelper;
import org.deeplearning4j.nn.params.BidirectionalParamInitializer;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.deeplearning4j.optimize.api.ConvexOptimizer;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.deeplearning4j.util.TimeSeriesUtils;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;

import java.util.*;

import static org.nd4j.linalg.indexing.NDArrayIndex.*;

/**
 * Bidirectional is a "wrapper" layer: it wraps any uni-directional RNN layer to make it bidirectional.<br>
 * Note that multiple different modes are supported - these specify how the activations should be combined from
 * the forward and backward RNN networks. See {@link Bidirectional.Mode} javadoc for more details.<br>
 * Parameters are not shared here - there are 2 separate copies of the wrapped RNN layer, each with separate parameters.
 * <br>
 * Usage: {@code .layer(new Bidirectional(new LSTM.Builder()....build())}
 *
 * @author Alex Black
 */
public class BidirectionalLayer implements RecurrentLayer {

    private NeuralNetConfiguration conf;
    private Layer fwd;
    private Layer bwd;

    private Bidirectional layerConf;
    private INDArray paramsView;
    private INDArray gradientView;
    private transient Map<String, INDArray> gradientViews;
    private INDArray input;

    //Next 2 variables: used *only* for MUL case (needed for backprop)
    private INDArray outFwd;
    private INDArray outBwd;

    public BidirectionalLayer(@NonNull NeuralNetConfiguration conf, @NonNull Layer fwd, @NonNull Layer bwd, @NonNull INDArray paramsView) {
        this.conf = conf;
        this.fwd = fwd;
        this.bwd = bwd;
        this.layerConf = (Bidirectional) conf.getLayer();
        this.paramsView = paramsView;
    }

    @Override
    public INDArray rnnTimeStep(INDArray input, LayerWorkspaceMgr workspaceMgr) {
        throw new UnsupportedOperationException("Cannot RnnTimeStep bidirectional layers");
    }

    @Override
    public Map<String, INDArray> rnnGetPreviousState() {
        throw new UnsupportedOperationException("Not supported: cannot RnnTimeStep bidirectional layers therefore " +
                "no previous state is supported");
    }

    @Override
    public void rnnSetPreviousState(Map<String, INDArray> stateMap) {
        throw new UnsupportedOperationException("Not supported: cannot RnnTimeStep bidirectional layers therefore " +
                "no previous state is supported");
    }

    @Override
    public void rnnClearPreviousState() {
        //No op
    }

    @Override
    public INDArray rnnActivateUsingStoredState(INDArray input, boolean training, boolean storeLastForTBPTT, LayerWorkspaceMgr workspaceMgr) {
        throw new UnsupportedOperationException("Not supported: cannot use this method (or truncated BPTT) with bidirectional layers");
    }

    @Override
    public Map<String, INDArray> rnnGetTBPTTState() {
        throw new UnsupportedOperationException("Not supported: cannot use this method (or truncated BPTT) with bidirectional layers");
    }

    @Override
    public void rnnSetTBPTTState(Map<String, INDArray> state) {
        throw new UnsupportedOperationException("Not supported: cannot use this method (or truncated BPTT) with bidirectional layers");
    }

    @Override
    public Pair<Gradient, INDArray> tbpttBackpropGradient(INDArray epsilon, int tbpttBackLength, LayerWorkspaceMgr workspaceMgr) {
        throw new UnsupportedOperationException("Not supported: cannot use this method (or truncated BPTT) with bidirectional layers");
    }

    @Override
    public void setCacheMode(CacheMode mode) {
        fwd.setCacheMode(mode);
        bwd.setCacheMode(mode);
    }

    @Override
    public double calcL2(boolean backpropOnlyParams) {
        return fwd.calcL2(backpropOnlyParams) + bwd.calcL2(backpropOnlyParams);
    }

    @Override
    public double calcL1(boolean backpropOnlyParams) {
        return fwd.calcL1(backpropOnlyParams) + bwd.calcL1(backpropOnlyParams);
    }

    @Override
    public Type type() {
        return Type.RECURRENT;
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        INDArray eFwd;
        INDArray eBwd;

        val n = epsilon.size(1)/2;
        switch (layerConf.getMode()){
            case ADD:
                eFwd = epsilon;
                eBwd = epsilon;
                break;
            case MUL:
                eFwd = epsilon.dup(epsilon.ordering()).muli(outBwd);
                eBwd = epsilon.dup(epsilon.ordering()).muli(outFwd);
                break;
            case AVERAGE:
                eFwd = epsilon.dup(epsilon.ordering()).muli(0.5);
                eBwd = eFwd;
                break;
            case CONCAT:
                eFwd = epsilon.get(all(), interval(0,n), all());
                eBwd = epsilon.get(all(), interval(n, 2*n), all());
                break;
            default:
                throw new RuntimeException("Unknown mode: " + layerConf.getMode());
        }

        eBwd = TimeSeriesUtils.reverseTimeSeries(eBwd, workspaceMgr, ArrayType.BP_WORKING_MEM);

        Pair<Gradient,INDArray> g1 = fwd.backpropGradient(eFwd, workspaceMgr);
        Pair<Gradient,INDArray> g2 = bwd.backpropGradient(eBwd, workspaceMgr);

        Gradient g = new DefaultGradient(gradientView);
        for(Map.Entry<String,INDArray> e : g1.getFirst().gradientForVariable().entrySet()){
            g.gradientForVariable().put(BidirectionalParamInitializer.FORWARD_PREFIX + e.getKey(), e.getValue());
        }
        for(Map.Entry<String,INDArray> e : g2.getFirst().gradientForVariable().entrySet()){
            g.gradientForVariable().put(BidirectionalParamInitializer.BACKWARD_PREFIX + e.getKey(), e.getValue());
        }

        INDArray g2Reversed = TimeSeriesUtils.reverseTimeSeries(g2.getRight(), workspaceMgr, ArrayType.BP_WORKING_MEM);
        INDArray epsOut = g1.getRight().addi(g2Reversed);

        return new Pair<>(g, epsOut);
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        INDArray out1 = fwd.activate(training, workspaceMgr);
        INDArray out2 = bwd.activate(training, workspaceMgr);
        //Reverse the output time series. Note: when using LastTimeStepLayer, output can be rank 2
        out2 = out2.rank() == 2 ? out2 : TimeSeriesUtils.reverseTimeSeries(out2, workspaceMgr, ArrayType.FF_WORKING_MEM);

        switch (layerConf.getMode()){
            case ADD:
                return out1.addi(out2);
            case MUL:
                //TODO may be more efficient ways than this...
                this.outFwd = out1.detach();
                this.outBwd = out2.detach();
                return workspaceMgr.dup(ArrayType.ACTIVATIONS, out1).muli(out2);
            case AVERAGE:
                return out1.addi(out2).muli(0.5);
            case CONCAT:
                INDArray ret = Nd4j.concat(1, out1, out2);
                return workspaceMgr.leverageTo(ArrayType.ACTIVATIONS, ret);
            default:
                throw new RuntimeException("Unknown mode: " + layerConf.getMode());
        }
    }

    @Override
    public INDArray activate(INDArray input, boolean training, LayerWorkspaceMgr workspaceMgr) {
        setInput(input, workspaceMgr);
        return activate(training, workspaceMgr);
    }

    @Override
    public Collection<TrainingListener> getListeners() {
        return fwd.getListeners();
    }

    @Override
    public void setListeners(TrainingListener... listeners) {
        fwd.setListeners(listeners);
        bwd.setListeners(listeners);
    }

    @Override
    public void addListeners(TrainingListener... listener) {
        fwd.addListeners(listener);
        bwd.addListeners(listener);
    }

    @Override
    public void fit() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void update(Gradient gradient) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void update(INDArray gradient, String paramType) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public double score() {
        return fwd.score() + bwd.score();
    }

    @Override
    public void computeGradientAndScore(LayerWorkspaceMgr workspaceMgr) {
        fwd.computeGradientAndScore(workspaceMgr);
        bwd.computeGradientAndScore(workspaceMgr);
    }

    @Override
    public INDArray params() {
        return paramsView;
    }

    @Override
    public TrainingConfig getConfig() {
        return conf.getLayer();
    }

    @Override
    public long numParams() {
        return fwd.numParams() + bwd.numParams();
    }

    @Override
    public long numParams(boolean backwards) {
        return fwd.numParams(backwards) + bwd.numParams(backwards);
    }

    @Override
    public void setParams(INDArray params) {
        this.paramsView.assign(params);
    }

    @Override
    public void setParamsViewArray(INDArray params) {
        this.paramsView = params;
        val n = params.length();
        fwd.setParamsViewArray(params.get(point(0), interval(0, n)));
        bwd.setParamsViewArray(params.get(point(0), interval(n, 2*n)));
    }

    @Override
    public INDArray getGradientsViewArray() {
        return gradientView;
    }

    @Override
    public void setBackpropGradientsViewArray(INDArray gradients) {
        if (this.paramsView != null && gradients.length() != numParams())
            throw new IllegalArgumentException("Invalid input: expect gradients array of length " + numParams(true)
                    + ", got array of length " + gradients.length());

        this.gradientView = gradients;
        val n = gradients.length() / 2;
        INDArray g1 = gradients.get(point(0), interval(0,n));
        INDArray g2 = gradients.get(point(0), interval(n, 2*n));
        fwd.setBackpropGradientsViewArray(g1);
        bwd.setBackpropGradientsViewArray(g2);
    }

    @Override
    public void fit(INDArray data, LayerWorkspaceMgr workspaceMgr) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Gradient gradient() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Pair<Gradient, Double> gradientAndScore() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int batchSize() {
        return fwd.batchSize();
    }

    @Override
    public NeuralNetConfiguration conf() {
        return conf;
    }

    @Override
    public void setConf(NeuralNetConfiguration conf) {
        this.conf = conf;
    }

    @Override
    public INDArray input() {
        return input;
    }

    @Override
    public ConvexOptimizer getOptimizer() {
        return null;
    }

    @Override
    public INDArray getParam(String param) {
        String sub = param.substring(1);
        if(param.startsWith(BidirectionalParamInitializer.FORWARD_PREFIX)){
            return fwd.getParam(sub);
        } else {
            return bwd.getParam(sub);
        }
    }

    @Override
    public Map<String, INDArray> paramTable() {
        return paramTable(false);
    }

    @Override
    public Map<String, INDArray> paramTable(boolean backpropParamsOnly) {
        Map<String,INDArray> m = new LinkedHashMap<>();
        for(Map.Entry<String,INDArray> e : fwd.paramTable(backpropParamsOnly).entrySet()){
            m.put(BidirectionalParamInitializer.FORWARD_PREFIX + e.getKey(), e.getValue());
        }
        for(Map.Entry<String,INDArray> e : bwd.paramTable(backpropParamsOnly).entrySet()){
            m.put(BidirectionalParamInitializer.BACKWARD_PREFIX + e.getKey(), e.getValue());
        }
        return m;
    }

    @Override
    public boolean updaterDivideByMinibatch(String paramName) {
        String sub = paramName.substring(1);
        if(paramName.startsWith(BidirectionalParamInitializer.FORWARD_PREFIX)){
            return fwd.updaterDivideByMinibatch(paramName);
        } else {
            return bwd.updaterDivideByMinibatch(paramName);
        }
    }

    @Override
    public void setParamTable(Map<String, INDArray> paramTable) {
        for(Map.Entry<String,INDArray> e : paramTable.entrySet()){
            setParam(e.getKey(), e.getValue());
        }
    }

    @Override
    public void setParam(String key, INDArray val) {
        String sub = key.substring(1);
        if(key.startsWith(BidirectionalParamInitializer.FORWARD_PREFIX)){
            fwd.setParam(sub, val);
        } else {
            bwd.setParam(sub, val);
        }
    }

    @Override
    public void clear() {
        fwd.clear();
        bwd.clear();
        input = null;
        outFwd = null;
        outBwd = null;
    }

    @Override
    public void applyConstraints(int iteration, int epoch) {
        fwd.applyConstraints(iteration, epoch);
        bwd.applyConstraints(iteration, epoch);
    }

    @Override
    public void init() {
        //No op
    }

    @Override
    public void setListeners(Collection<TrainingListener> listeners) {
        fwd.setListeners(listeners);
        bwd.setListeners(listeners);
    }

    @Override
    public void setIndex(int index) {
        fwd.setIndex(index);
        bwd.setIndex(index);
    }

    @Override
    public int getIndex() {
        return fwd.getIndex();
    }

    @Override
    public int getIterationCount() {
        return fwd.getIterationCount();
    }

    @Override
    public int getEpochCount() {
        return fwd.getEpochCount();
    }

    @Override
    public void setIterationCount(int iterationCount) {
        fwd.setIterationCount(iterationCount);
        bwd.setIterationCount(iterationCount);
    }

    @Override
    public void setEpochCount(int epochCount) {
        fwd.setEpochCount(epochCount);
        bwd.setEpochCount(epochCount);
    }

    @Override
    public void setInput(INDArray input, LayerWorkspaceMgr layerWorkspaceMgr) {
        this.input = input;
        fwd.setInput(input, layerWorkspaceMgr);

        INDArray reversed;
        if(!input.isAttached()){
            try(MemoryWorkspace ws = Nd4j.getWorkspaceManager().scopeOutOfWorkspaces()) {
                reversed = TimeSeriesUtils.reverseTimeSeries(input);
            }
        } else {
            MemoryWorkspace ws = input.data().getParentWorkspace();
            try(MemoryWorkspace ws2 = ws.notifyScopeBorrowed()){
                //Put the reversed input into the same workspace as the original input
                reversed = TimeSeriesUtils.reverseTimeSeries(input);
            }
        }
        bwd.setInput(reversed, layerWorkspaceMgr);
    }

    @Override
    public void setInputMiniBatchSize(int size) {
        fwd.setInputMiniBatchSize(size);
        bwd.setInputMiniBatchSize(size);
    }

    @Override
    public int getInputMiniBatchSize() {
        return fwd.getInputMiniBatchSize();
    }

    @Override
    public void setMaskArray(INDArray maskArray) {
        fwd.setMaskArray(maskArray);
        bwd.setMaskArray(TimeSeriesUtils.reverseTimeSeriesMask(maskArray, LayerWorkspaceMgr.noWorkspaces(), ArrayType.INPUT));  //TODO
    }

    @Override
    public INDArray getMaskArray() {
        return fwd.getMaskArray();
    }

    @Override
    public boolean isPretrainLayer() {
        return fwd.isPretrainLayer();
    }

    @Override
    public void clearNoiseWeightParams() {
        fwd.clearNoiseWeightParams();
        bwd.clearNoiseWeightParams();
    }

    @Override
    public void allowInputModification(boolean allow) {
        fwd.allowInputModification(allow);
        bwd.allowInputModification(true);   //Always allow: always safe due to reverse op
    }

    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArray(INDArray maskArray, MaskState currentMaskState, int minibatchSize) {
        Pair<INDArray,MaskState> ret = fwd.feedForwardMaskArray(maskArray, currentMaskState, minibatchSize);
        bwd.feedForwardMaskArray(TimeSeriesUtils.reverseTimeSeriesMask(maskArray, LayerWorkspaceMgr.noWorkspaces(), ArrayType.INPUT),   //TODO
                currentMaskState, minibatchSize);
        return ret;
    }

    @Override
    public LayerHelper getHelper() {
        LayerHelper f = fwd.getHelper();
        LayerHelper b = bwd.getHelper();
        if(f != null || b != null){
            return new BidirectionalHelper(f,b);
        }
        return null;
    }

    @AllArgsConstructor
    private static class BidirectionalHelper implements LayerHelper {
        private final LayerHelper helperFwd;
        private final LayerHelper helperBwd;

        @Override
        public Map<String, Long> helperMemoryUse() {
            Map<String,Long> fwd = (helperFwd != null ? helperFwd.helperMemoryUse() : null);
            Map<String,Long> bwd = (helperBwd != null ? helperBwd.helperMemoryUse() : null);

            Set<String> keys = new HashSet<>();
            if(fwd != null)
                keys.addAll(fwd.keySet());
            if(bwd != null)
                keys.addAll(bwd.keySet());

            Map<String,Long> ret = new HashMap<>();
            for(String s : keys){
                long sum = 0;
                if(fwd != null && fwd.containsKey(s)){
                    sum += fwd.get(s);
                }
                if(bwd != null && bwd.containsKey(s)){
                    sum += bwd.get(s);
                }
                ret.put(s, sum);
            }
            return ret;
        }
    }
}

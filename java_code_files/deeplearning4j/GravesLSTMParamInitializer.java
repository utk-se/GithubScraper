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

package org.deeplearning4j.nn.params;

import lombok.val;
import org.deeplearning4j.nn.api.ParamInitializer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.weights.IWeightInit;
import org.deeplearning4j.nn.weights.WeightInitUtil;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.*;

/**LSTM Parameter initializer, for LSTM based on
 * Graves: Supervised Sequence Labelling with Recurrent Neural Networks
 * <a href="http://www.cs.toronto.edu/~graves/phd.pdf">http://www.cs.toronto.edu/~graves/phd.pdf</a>
 */
public class GravesLSTMParamInitializer implements ParamInitializer {

    private static final GravesLSTMParamInitializer INSTANCE = new GravesLSTMParamInitializer();

    public static GravesLSTMParamInitializer getInstance() {
        return INSTANCE;
    }

    /** Weights for previous time step -> current time step connections */
    public final static String RECURRENT_WEIGHT_KEY = LSTMParamInitializer.RECURRENT_WEIGHT_KEY;
    public final static String BIAS_KEY = LSTMParamInitializer.BIAS_KEY;
    public final static String INPUT_WEIGHT_KEY = LSTMParamInitializer.INPUT_WEIGHT_KEY;

    @Override
    public long numParams(NeuralNetConfiguration conf) {
        return numParams(conf.getLayer());
    }

    @Override
    public long numParams(Layer l) {
        org.deeplearning4j.nn.conf.layers.GravesLSTM layerConf = (org.deeplearning4j.nn.conf.layers.GravesLSTM) l;

        val nL = layerConf.getNOut(); //i.e., n neurons in this layer
        val nLast = layerConf.getNIn(); //i.e., n neurons in previous layer

        val nParams = nLast * (4 * nL) //"input" weights
                        + nL * (4 * nL + 3) //recurrent weights
                        + 4 * nL; //bias

        return nParams;
    }

    @Override
    public List<String> paramKeys(Layer layer) {
        return Arrays.asList(INPUT_WEIGHT_KEY, RECURRENT_WEIGHT_KEY, BIAS_KEY);
    }

    @Override
    public List<String> weightKeys(Layer layer) {
        return Arrays.asList(INPUT_WEIGHT_KEY, RECURRENT_WEIGHT_KEY);
    }

    @Override
    public List<String> biasKeys(Layer layer) {
        return Collections.singletonList(BIAS_KEY);
    }

    @Override
    public boolean isWeightParam(Layer layer, String key) {
        return RECURRENT_WEIGHT_KEY.equals(key) || INPUT_WEIGHT_KEY.equals(key);
    }

    @Override
    public boolean isBiasParam(Layer layer, String key) {
        return BIAS_KEY.equals(key);
    }

    @Override
    public Map<String, INDArray> init(NeuralNetConfiguration conf, INDArray paramsView, boolean initializeParams) {
        Map<String, INDArray> params = Collections.synchronizedMap(new LinkedHashMap<String, INDArray>());
        org.deeplearning4j.nn.conf.layers.GravesLSTM layerConf =
                        (org.deeplearning4j.nn.conf.layers.GravesLSTM) conf.getLayer();
        double forgetGateInit = layerConf.getForgetGateBiasInit();

        val nL = layerConf.getNOut(); //i.e., n neurons in this layer
        val nLast = layerConf.getNIn(); //i.e., n neurons in previous layer

        conf.addVariable(INPUT_WEIGHT_KEY);
        conf.addVariable(RECURRENT_WEIGHT_KEY);
        conf.addVariable(BIAS_KEY);

        val length = numParams(conf);
        if (paramsView.length() != length)
            throw new IllegalStateException(
                            "Expected params view of length " + length + ", got length " + paramsView.length());

        val nParamsIn = nLast * (4 * nL);
        val nParamsRecurrent = nL * (4 * nL + 3);
        val nBias = 4 * nL;
        INDArray inputWeightView = paramsView.get(NDArrayIndex.point(0), NDArrayIndex.interval(0, nParamsIn));
        INDArray recurrentWeightView = paramsView.get(NDArrayIndex.point(0),
                        NDArrayIndex.interval(nParamsIn, nParamsIn + nParamsRecurrent));
        INDArray biasView = paramsView.get(NDArrayIndex.point(0),
                        NDArrayIndex.interval(nParamsIn + nParamsRecurrent, nParamsIn + nParamsRecurrent + nBias));

        if (initializeParams) {
            val fanIn = nL;
            val fanOut = nLast + nL;
            val inputWShape = new long[] {nLast, 4 * nL};
            val recurrentWShape = new long[] {nL, 4 * nL + 3};

            IWeightInit rwInit;
            if(layerConf.getWeightInitFnRecurrent() != null){
                rwInit = layerConf.getWeightInitFnRecurrent();
            } else {
                rwInit = layerConf.getWeightInitFn();
            }

            params.put(INPUT_WEIGHT_KEY,layerConf.getWeightInitFn().init(fanIn, fanOut, inputWShape,
                            IWeightInit.DEFAULT_WEIGHT_INIT_ORDER, inputWeightView));
            params.put(RECURRENT_WEIGHT_KEY, rwInit.init(fanIn, fanOut, recurrentWShape,
                            IWeightInit.DEFAULT_WEIGHT_INIT_ORDER, recurrentWeightView));
            biasView.put(new INDArrayIndex[] {NDArrayIndex.point(0), NDArrayIndex.interval(nL, 2 * nL)},
                            Nd4j.valueArrayOf(new long[]{1, nL}, forgetGateInit)); //Order: input, forget, output, input modulation, i.e., IFOG}
            /*The above line initializes the forget gate biases to specified value.
             * See Sutskever PhD thesis, pg19:
             * "it is important for [the forget gate activations] to be approximately 1 at the early stages of learning,
             *  which is accomplished by initializing [the forget gate biases] to a large value (such as 5). If it is
             *  not done, it will be harder to learn long range dependencies because the smaller values of the forget
             *  gates will create a vanishing gradients problem."
             *  http://www.cs.utoronto.ca/~ilya/pubs/ilya_sutskever_phd_thesis.pdf
             */
            params.put(BIAS_KEY, biasView);
        } else {
            params.put(INPUT_WEIGHT_KEY, WeightInitUtil.reshapeWeights(new long[] {nLast, 4 * nL}, inputWeightView));
            params.put(RECURRENT_WEIGHT_KEY,
                            WeightInitUtil.reshapeWeights(new long[] {nL, 4 * nL + 3}, recurrentWeightView));
            params.put(BIAS_KEY, biasView);
        }

        return params;
    }

    @Override
    public Map<String, INDArray> getGradientsFromFlattened(NeuralNetConfiguration conf, INDArray gradientView) {
        org.deeplearning4j.nn.conf.layers.GravesLSTM layerConf =
                        (org.deeplearning4j.nn.conf.layers.GravesLSTM) conf.getLayer();

        val nL = layerConf.getNOut(); //i.e., n neurons in this layer
        val nLast = layerConf.getNIn(); //i.e., n neurons in previous layer

        val length = numParams(conf);
        if (gradientView.length() != length)
            throw new IllegalStateException(
                            "Expected gradient view of length " + length + ", got length " + gradientView.length());

        val nParamsIn = nLast * (4 * nL);
        val nParamsRecurrent = nL * (4 * nL + 3);
        val nBias = 4 * nL;
        INDArray inputWeightGradView = gradientView.get(NDArrayIndex.point(0), NDArrayIndex.interval(0, nParamsIn))
                        .reshape('f', nLast, 4 * nL);
        INDArray recurrentWeightGradView = gradientView
                        .get(NDArrayIndex.point(0), NDArrayIndex.interval(nParamsIn, nParamsIn + nParamsRecurrent))
                        .reshape('f', nL, 4 * nL + 3);
        INDArray biasGradView = gradientView.get(NDArrayIndex.point(0),
                        NDArrayIndex.interval(nParamsIn + nParamsRecurrent, nParamsIn + nParamsRecurrent + nBias)); //already a row vector

        Map<String, INDArray> out = new LinkedHashMap<>();
        out.put(INPUT_WEIGHT_KEY, inputWeightGradView);
        out.put(RECURRENT_WEIGHT_KEY, recurrentWeightGradView);
        out.put(BIAS_KEY, biasGradView);

        return out;
    }
}

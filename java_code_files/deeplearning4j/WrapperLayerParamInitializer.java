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

import org.deeplearning4j.nn.api.ParamInitializer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.conf.layers.wrapper.BaseWrapperLayer;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.List;
import java.util.Map;

public class WrapperLayerParamInitializer implements ParamInitializer {

    private static final WrapperLayerParamInitializer INSTANCE = new WrapperLayerParamInitializer();

    public static WrapperLayerParamInitializer getInstance(){
        return INSTANCE;
    }

    private WrapperLayerParamInitializer(){

    }

    @Override
    public long numParams(NeuralNetConfiguration conf) {
        return numParams(conf.getLayer());
    }

    @Override
    public long numParams(Layer layer) {
        Layer l = underlying(layer);
        return l.initializer().numParams(l);
    }

    @Override
    public List<String> paramKeys(Layer layer) {
        Layer l = underlying(layer);
        return l.initializer().paramKeys(l);
    }

    @Override
    public List<String> weightKeys(Layer layer) {
        Layer l = underlying(layer);
        return l.initializer().weightKeys(l);
    }

    @Override
    public List<String> biasKeys(Layer layer) {
        Layer l = underlying(layer);
        return l.initializer().biasKeys(l);
    }

    @Override
    public boolean isWeightParam(Layer layer, String key) {
        Layer l = underlying(layer);
        return l.initializer().isWeightParam(layer, key);
    }

    @Override
    public boolean isBiasParam(Layer layer, String key) {
        Layer l = underlying(layer);
        return l.initializer().isBiasParam(layer, key);
    }

    @Override
    public Map<String, INDArray> init(NeuralNetConfiguration conf, INDArray paramsView, boolean initializeParams) {
        Layer orig = conf.getLayer();
        Layer l = underlying(conf.getLayer());
        conf.setLayer(l);
        Map<String,INDArray> m = l.initializer().init(conf, paramsView, initializeParams);
        conf.setLayer(orig);
        return m;
    }

    @Override
    public Map<String, INDArray> getGradientsFromFlattened(NeuralNetConfiguration conf, INDArray gradientView) {
        Layer orig = conf.getLayer();
        Layer l = underlying(conf.getLayer());
        conf.setLayer(l);
        Map<String,INDArray> m = l.initializer().getGradientsFromFlattened(conf, gradientView);
        conf.setLayer(orig);
        return m;
    }

    private Layer underlying(Layer layer){
        while (layer instanceof BaseWrapperLayer) {
            layer = ((BaseWrapperLayer)layer).getUnderlying();
        }
        return layer;
    }
}

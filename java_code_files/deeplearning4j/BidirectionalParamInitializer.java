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
import org.deeplearning4j.nn.conf.layers.BaseLayer;
import org.deeplearning4j.nn.conf.layers.BaseRecurrentLayer;
import org.deeplearning4j.nn.conf.layers.FeedForwardLayer;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.conf.layers.recurrent.Bidirectional;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.nd4j.linalg.indexing.NDArrayIndex.interval;
import static org.nd4j.linalg.indexing.NDArrayIndex.point;

/**
 * Parameter initializer for bidirectional wrapper layer
 *
 * @author Alex Black
 */
public class BidirectionalParamInitializer implements ParamInitializer {
    public static final String FORWARD_PREFIX = "f";
    public static final String BACKWARD_PREFIX = "b";

    private final Bidirectional layer;
    private final Layer underlying;

    private List<String> paramKeys;
    private List<String> weightKeys;
    private List<String> biasKeys;

    public BidirectionalParamInitializer(Bidirectional layer){
        this.layer = layer;
        this.underlying = underlying(layer);
    }

    @Override
    public long numParams(NeuralNetConfiguration conf) {
        return numParams(conf.getLayer());
    }

    @Override
    public long numParams(Layer layer) {
        return 2 * underlying(layer).initializer().numParams(underlying(layer));
    }

    @Override
    public List<String> paramKeys(Layer layer) {
        if(paramKeys == null) {
            Layer u = underlying(layer);
            List<String> orig = u.initializer().paramKeys(u);
            paramKeys = withPrefixes(orig);
        }
        return paramKeys;
    }

    @Override
    public List<String> weightKeys(Layer layer) {
        if(weightKeys == null) {
            Layer u = underlying(layer);
            List<String> orig = u.initializer().weightKeys(u);
            weightKeys = withPrefixes(orig);
        }
        return weightKeys;
    }

    @Override
    public List<String> biasKeys(Layer layer) {
        if(biasKeys == null) {
            Layer u = underlying(layer);
            List<String> orig = u.initializer().weightKeys(u);
            biasKeys = withPrefixes(orig);
        }
        return biasKeys;
    }

    @Override
    public boolean isWeightParam(Layer layer, String key) {
        return weightKeys(this.layer).contains(key);
    }

    @Override
    public boolean isBiasParam(Layer layer, String key) {
        return biasKeys(this.layer).contains(key);
    }

    @Override
    public Map<String, INDArray> init(NeuralNetConfiguration conf, INDArray paramsView, boolean initializeParams) {
        val n = paramsView.length()/2;
        INDArray forwardView = paramsView.get(point(0), interval(0, n));
        INDArray backwardView = paramsView.get(point(0), interval(n, 2*n));

        conf.clearVariables();

        NeuralNetConfiguration c1 = conf.clone();
        NeuralNetConfiguration c2 = conf.clone();
        c1.setLayer(underlying);
        c2.setLayer(underlying);
        Map<String, INDArray> origFwd = underlying.initializer().init(c1, forwardView, initializeParams);
        Map<String, INDArray> origBwd = underlying.initializer().init(c2, backwardView, initializeParams);
        List<String> variables = addPrefixes(c1.getVariables(), c2.getVariables());
        conf.setVariables(variables);

        Map<String,INDArray> out = new LinkedHashMap<>();
        for( Map.Entry<String, INDArray> e : origFwd.entrySet()){
            out.put(FORWARD_PREFIX + e.getKey(), e.getValue());
        }
        for( Map.Entry<String, INDArray> e : origBwd.entrySet()){
            out.put(BACKWARD_PREFIX + e.getKey(), e.getValue());
        }

        return out;
    }

    private <T> Map<String,T> addPrefixes(Map<String,T> fwd, Map<String,T> bwd){
        Map<String,T> out = new LinkedHashMap<>();
        for(Map.Entry<String,T> e : fwd.entrySet()){
            out.put(FORWARD_PREFIX + e.getKey(), e.getValue());
        }
        for(Map.Entry<String,T> e : bwd.entrySet()){
            out.put(BACKWARD_PREFIX + e.getKey(), e.getValue());
        }

        return out;
    }

    private List<String> addPrefixes(List<String> fwd, List<String> bwd){
        List<String> out = new ArrayList<>();
        for(String s : fwd){
            out.add(FORWARD_PREFIX + s);
        }
        for(String s : bwd){
            out.add(BACKWARD_PREFIX + s);
        }
        return out;
    }

    @Override
    public Map<String, INDArray> getGradientsFromFlattened(NeuralNetConfiguration conf, INDArray gradientView) {
        val n = gradientView.length()/2;
        INDArray forwardView = gradientView.get(point(0), interval(0, n));
        INDArray backwardView = gradientView.get(point(0), interval(n, 2*n));

        Map<String, INDArray> origFwd = underlying.initializer().getGradientsFromFlattened(conf, forwardView);
        Map<String, INDArray> origBwd = underlying.initializer().getGradientsFromFlattened(conf, backwardView);

        Map<String,INDArray> out = new LinkedHashMap<>();
        for( Map.Entry<String, INDArray> e : origFwd.entrySet()){
            out.put(FORWARD_PREFIX + e.getKey(), e.getValue());
        }
        for( Map.Entry<String, INDArray> e : origBwd.entrySet()){
            out.put(BACKWARD_PREFIX + e.getKey(), e.getValue());
        }

        return out;
    }

    private Layer underlying(Layer layer){
        Bidirectional b = (Bidirectional)layer;
        return b.getFwd();
    }

    private List<String> withPrefixes(List<String> orig){
        List<String> out = new ArrayList<>();
        for(String s : orig){
            out.add(FORWARD_PREFIX + s);
        }
        for(String s : orig){
            out.add(BACKWARD_PREFIX + s);
        }
        return out;
    }
}

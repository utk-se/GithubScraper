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

package org.deeplearning4j.nn.conf.layers;

import lombok.*;
import org.deeplearning4j.nn.api.ParamInitializer;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.dropout.Dropout;
import org.deeplearning4j.nn.conf.dropout.IDropout;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.conf.memory.MemoryReport;
import org.deeplearning4j.nn.params.EmptyParamInitializer;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Collection;
import java.util.Map;

/**
 * Dropout layer. This layer simply applies dropout at training time, and passes activations through unmodified at test
 * time. Internally, this uses an {@link IDropout} instance. See the IDropout instances for details:<br> {@link
 * Dropout}<br> {@link org.nd4j.linalg.api.ops.random.impl.AlphaDropOut}<br> {@link
 * org.deeplearning4j.nn.conf.dropout.GaussianDropout}<br> {@link org.deeplearning4j.nn.conf.dropout.GaussianNoise}<br>
 * {@link org.deeplearning4j.nn.conf.dropout.SpatialDropout}
 */
@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class DropoutLayer extends FeedForwardLayer {

    private DropoutLayer(Builder builder) {
        super(builder);
    }

    @Override
    public DropoutLayer clone() {
        return (DropoutLayer) super.clone();
    }

    @Override
    public org.deeplearning4j.nn.api.Layer instantiate(NeuralNetConfiguration conf,
                    Collection<TrainingListener> trainingListeners, int layerIndex, INDArray layerParamsView,
                    boolean initializeParams) {
        org.deeplearning4j.nn.layers.DropoutLayer ret = new org.deeplearning4j.nn.layers.DropoutLayer(conf);
        ret.setListeners(trainingListeners);
        ret.setIndex(layerIndex);
        ret.setParamsViewArray(layerParamsView);
        Map<String, INDArray> paramTable = initializer().init(conf, layerParamsView, initializeParams);
        ret.setParamTable(paramTable);
        ret.setConf(conf);
        return ret;
    }

    @Override
    public ParamInitializer initializer() {
        return EmptyParamInitializer.getInstance();
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType inputType) {
        if (inputType == null) {
            throw new IllegalStateException("Invalid input type: null for layer name \"" + getLayerName() + "\"");
        }
        return inputType;
    }

    @Override
    public void setNIn(InputType inputType, boolean override) {
        //No op: dropout layer doesn't have a fixed nIn value
    }

    @Override
    public InputPreProcessor getPreProcessorForInputType(InputType inputType) {
        //No input preprocessor required; dropout applies to any input type
        return null;
    }

    @Override
    public double getL1ByParam(String paramName) {
        //Not applicable
        return 0;
    }

    @Override
    public double getL2ByParam(String paramName) {
        //Not applicable
        return 0;
    }

    @Override
    public boolean isPretrainParam(String paramName) {
        throw new UnsupportedOperationException("Dropout layer does not contain parameters");
    }

    @Override
    public LayerMemoryReport getMemoryReport(InputType inputType) {
        val actElementsPerEx = inputType.arrayElementsPerExample();
        //During inference: not applied. During  backprop: dup the input, in case it's used elsewhere
        //But: this will be counted in the activations
        //(technically inference memory is over-estimated as a result)

        return new LayerMemoryReport.Builder(layerName, DropoutLayer.class, inputType, inputType).standardMemory(0, 0) //No params
                        .workingMemory(0, 0, 0, 0) //No working mem, other than activations etc
                        .cacheMemory(MemoryReport.CACHE_MODE_ALL_ZEROS, MemoryReport.CACHE_MODE_ALL_ZEROS) //No caching
                        .build();
    }


    @NoArgsConstructor
    public static class Builder extends FeedForwardLayer.Builder<DropoutLayer.Builder> {

        /**
         * Create a dropout layer with standard {@link Dropout}, with the specified probability of retaining the input
         * activation. See {@link Dropout} for the full details
         *
         * @param dropout Activation retain probability.
         */
        public Builder(double dropout) {
            this.dropOut(new Dropout(dropout));
        }

        /**
         * @param dropout Specified {@link IDropout} instance for the dropout layer
         */
        public Builder(IDropout dropout) {
            this.dropOut(dropout);
        }

        @Override
        @SuppressWarnings("unchecked")
        public DropoutLayer build() {

            return new DropoutLayer(this);
        }
    }


}

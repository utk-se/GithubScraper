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
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.ParamInitializer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.conf.memory.MemoryReport;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Collection;
import java.util.Map;

/**
 * Embedding layer: feed-forward layer that expects single integers per example as input (class numbers, in range 0 to
 * numClass-1) as input. This input has shape {@code [numExamples,1]} instead of {@code [numExamples,numClasses]} for
 * the equivalent one-hot representation. Mathematically, EmbeddingLayer is equivalent to using a DenseLayer with a
 * one-hot representation for the input; however, it can be much more efficient with a large number of classes (as a
 * dense layer + one-hot input does a matrix multiply with all but one value being zero).<br>
 * <b>Note</b>: can only be used as the first layer for a network<br>
 * <b>Note 2</b>: For a given example index i, the output is activationFunction(weights.getRow(i) + bias), hence the
 * weight rows can be considered a vector/embedding for each example.<br> Note also that embedding layer has an
 * activation function (set to IDENTITY to disable) and optional bias (which is disabled by default)
 *
 * @author Alex Black
 */
@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class EmbeddingLayer extends FeedForwardLayer {

    private boolean hasBias = true; //Default for pre-0.9.2 implementations

    private EmbeddingLayer(Builder builder) {
        super(builder);
        this.hasBias = builder.hasBias;
        initializeConstraints(builder);
    }

    @Override
    public Layer instantiate(NeuralNetConfiguration conf, Collection<TrainingListener> trainingListeners,
                    int layerIndex, INDArray layerParamsView, boolean initializeParams) {
        org.deeplearning4j.nn.layers.feedforward.embedding.EmbeddingLayer ret =
                        new org.deeplearning4j.nn.layers.feedforward.embedding.EmbeddingLayer(conf);
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
        return DefaultParamInitializer.getInstance();
    }

    @Override
    public LayerMemoryReport getMemoryReport(InputType inputType) {
        //Basically a dense layer, but no dropout is possible here, and no epsilons
        InputType outputType = getOutputType(-1, inputType);

        val actElementsPerEx = outputType.arrayElementsPerExample();
        val numParams = initializer().numParams(this);
        val updaterStateSize = (int) getIUpdater().stateSize(numParams);

        //Embedding layer does not use caching.
        //Inference: no working memory - just activations (pullRows)
        //Training: preout op, the only in-place ops on epsilon (from layer above) + assign ops

        return new LayerMemoryReport.Builder(layerName, EmbeddingLayer.class, inputType, outputType)
                        .standardMemory(numParams, updaterStateSize).workingMemory(0, 0, 0, actElementsPerEx)
                        .cacheMemory(MemoryReport.CACHE_MODE_ALL_ZEROS, MemoryReport.CACHE_MODE_ALL_ZEROS) //No caching
                        .build();
    }

    public boolean hasBias() {
        return hasBias;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class Builder extends FeedForwardLayer.Builder<Builder> {

        /**
         * If true: include bias parameters in the layer. False (default): no bias.
         *
         */
        private boolean hasBias = false;

        /**
         * If true: include bias parameters in the layer. False (default): no bias.
         *
         * @param hasBias If true: include bias parameters in this layer
         */
        public Builder hasBias(boolean hasBias) {
            this.hasBias = hasBias;
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public EmbeddingLayer build() {
            return new EmbeddingLayer(this);
        }
    }
}

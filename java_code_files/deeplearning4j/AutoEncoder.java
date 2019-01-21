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
import org.deeplearning4j.nn.params.PretrainParamInitializer;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Collection;
import java.util.Map;

/**
 * Autoencoder layer. Adds noise to input and learn a reconstruction function.
 */
@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class AutoEncoder extends BasePretrainNetwork {

    protected double corruptionLevel;
    protected double sparsity;

    // Builder
    private AutoEncoder(Builder builder) {
        super(builder);
        this.corruptionLevel = builder.corruptionLevel;
        this.sparsity = builder.sparsity;
        initializeConstraints(builder);
    }

    @Override
    public Layer instantiate(NeuralNetConfiguration conf, Collection<TrainingListener> trainingListeners,
                    int layerIndex, INDArray layerParamsView, boolean initializeParams) {
        org.deeplearning4j.nn.layers.feedforward.autoencoder.AutoEncoder ret =
                        new org.deeplearning4j.nn.layers.feedforward.autoencoder.AutoEncoder(conf);
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
        return PretrainParamInitializer.getInstance();
    }

    @Override
    public LayerMemoryReport getMemoryReport(InputType inputType) {
        //Because of supervised + unsupervised modes: we'll assume unsupervised, which has the larger memory requirements
        InputType outputType = getOutputType(-1, inputType);

        val actElementsPerEx = outputType.arrayElementsPerExample() + inputType.arrayElementsPerExample();
        val numParams = initializer().numParams(this);
        val updaterStateSize = (int) getIUpdater().stateSize(numParams);

        int trainSizePerEx = 0;
        if (getIDropout() != null) {
            if (false) {
                //TODO drop connect
                //Dup the weights... note that this does NOT depend on the minibatch size...
            } else {
                //Assume we dup the input
                trainSizePerEx += inputType.arrayElementsPerExample();
            }
        }

        //Also, during backprop: we do a preOut call -> gives us activations size equal to the output size
        // which is modified in-place by loss function
        trainSizePerEx += actElementsPerEx;

        return new LayerMemoryReport.Builder(layerName, AutoEncoder.class, inputType, outputType)
                        .standardMemory(numParams, updaterStateSize).workingMemory(0, 0, 0, trainSizePerEx)
                        .cacheMemory(MemoryReport.CACHE_MODE_ALL_ZEROS, MemoryReport.CACHE_MODE_ALL_ZEROS) //No caching
                        .build();
    }

    @AllArgsConstructor
    @Getter
    @Setter
    public static class Builder extends BasePretrainNetwork.Builder<Builder> {

        /**
         * Level of corruption - 0.0 (none) to 1.0 (all values corrupted)
         *
         */
        private double corruptionLevel = 3e-1f;

        /**
         * Autoencoder sparity parameter
         *
         */
        private double sparsity = 0f;

        public Builder() {}

        /**
         * Builder - sets the level of corruption - 0.0 (none) to 1.0 (all values corrupted)
         *
         * @param corruptionLevel Corruption level (0 to 1)
         */
        public Builder(double corruptionLevel) {
            this.corruptionLevel = corruptionLevel;
        }

        /**
         * Level of corruption - 0.0 (none) to 1.0 (all values corrupted)
         *
         * @param corruptionLevel Corruption level (0 to 1)
         */
        public Builder corruptionLevel(double corruptionLevel) {
            this.corruptionLevel = corruptionLevel;
            return this;
        }

        /**
         * Autoencoder sparity parameter
         *
         * @param sparsity Sparsity
         */
        public Builder sparsity(double sparsity) {
            this.sparsity = sparsity;
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public AutoEncoder build() {
            return new AutoEncoder(this);
        }
    }
}

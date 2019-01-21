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

package org.deeplearning4j.nn.conf.layers.convolutional;

import com.google.common.base.Preconditions;
import lombok.*;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.InputTypeUtil;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.conf.layers.NoParamLayer;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.layers.convolution.Cropping1DLayer;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Cropping layer for convolutional (1d) neural networks. Allows cropping to be done separately for top/bottom
 *
 * @author Max Pumperla
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Cropping1D extends NoParamLayer {

    private int[] cropping;

    /**
     * @param cropTopBottom Amount of cropping to apply to both the top and the bottom of the input activations
     */
    public Cropping1D(int cropTopBottom) {
        this(cropTopBottom, cropTopBottom);
    }

    /**
     * @param cropTop Amount of cropping to apply to the top of the input activations
     * @param cropBottom Amount of cropping to apply to the bottom of the input activations
     */
    public Cropping1D(int cropTop, int cropBottom) {
        this(new Builder(cropTop, cropBottom));
    }

    /**
     * @param cropping Cropping as a length 2 array, with values {@code [cropTop, cropBottom]}
     */
    public Cropping1D(int[] cropping) {
        this(new Builder(cropping));
    }

    protected Cropping1D(Builder builder) {
        super(builder);
        this.cropping = builder.cropping;
    }

    @Override
    public org.deeplearning4j.nn.api.Layer instantiate(NeuralNetConfiguration conf,
                    Collection<TrainingListener> trainingListeners, int layerIndex, INDArray layerParamsView,
                    boolean initializeParams) {
        Cropping1DLayer ret = new Cropping1DLayer(conf);
        ret.setListeners(trainingListeners);
        ret.setIndex(layerIndex);
        Map<String, INDArray> paramTable = initializer().init(conf, layerParamsView, initializeParams);
        ret.setParamTable(paramTable);
        ret.setConf(conf);
        return ret;
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType inputType) {
        if (inputType == null || inputType.getType() != InputType.Type.RNN) {
            throw new IllegalStateException("Invalid input for 1D Cropping layer (layer index = " + layerIndex
                            + ", layer name = \"" + getLayerName() + "\"): expect RNN input type with size > 0. Got: "
                            + inputType);
        }
        InputType.InputTypeRecurrent cnn1d = (InputType.InputTypeRecurrent) inputType;
        val length = cnn1d.getTimeSeriesLength();
        val outLength = length - cropping[0] - cropping[1];
        return InputType.recurrent(cnn1d.getSize(), outLength);
    }

    @Override
    public InputPreProcessor getPreProcessorForInputType(InputType inputType) {
        Preconditions.checkArgument(inputType != null, "Invalid input for Cropping1D layer (layer name=\""
                        + getLayerName() + "\"): InputType is null");
        return InputTypeUtil.getPreProcessorForInputTypeCnnLayers(inputType, getLayerName());
    }

    @Override
    public LayerMemoryReport getMemoryReport(InputType inputType) {
        return null;
    }


    @Getter
    @Setter
    public static class Builder extends Layer.Builder<Builder> {
        /**
         * Cropping amount for top/bottom (in that order). Must be length 1 or 2 array.
         */
        private int[] cropping = new int[] {0, 0};

        /**
         * @param cropping Cropping amount for top/bottom (in that order). Must be length 1 or 2 array.
         */
        public void setCropping(int[] cropping) {
            if (cropping.length == 2) {
                this.cropping = cropping;
            } else if (cropping.length == 1) {
                this.cropping = new int[] {cropping[0], cropping[0]};
            } else {
                org.nd4j.base.Preconditions.checkArgument(false, "Must have 1 or 2 cropping values - got %s",
                                this.cropping);
            }
        }

        public Builder() {

        }

        /**
         * @param cropping Cropping amount for top/bottom (in that order). Must be length 1 or 2 array.
         */
        public Builder(@NonNull int[] cropping) {
            Preconditions.checkArgument(cropping.length == 2 || cropping.length == 1,
                            "Two cropping values, i.e. top and bottom, or one value for bot top and bottom"
                                            + "must be provided. Got " + cropping.length + " values: "
                                            + Arrays.toString(cropping));
            if (cropping.length == 2) {
                this.cropping = cropping;
            } else {
                this.cropping = new int[] {cropping[0], cropping[0]};
            }
        }

        /**
         * @param cropTopBottom Amount of cropping to apply to both the top and the bottom of the input activations
         */
        public Builder(int cropTopBottom) {
            this(cropTopBottom, cropTopBottom);
        }

        /**
         * @param cropTop Amount of cropping to apply to the top of the input activations
         * @param cropBottom Amount of cropping to apply to the bottom of the input activations
         */
        public Builder(int cropTop, int cropBottom) {
            this.cropping = new int[] {cropTop, cropBottom};
            Preconditions.checkArgument(cropTop >= 0 && cropBottom >= 0,
                            "Invalid arguments: crop dimensions must be > 0. Got [t,b] = "
                                            + Arrays.toString(this.cropping));
        }

        public Cropping1D build() {
            return new Cropping1D(this);
        }
    }
}

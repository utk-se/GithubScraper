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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.conf.memory.MemoryReport;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Collection;
import java.util.Map;

/**
 * Upsampling 1D layer<br> Repeats each step {@code size} times along the temporal/sequence axis (dimension 2)<br> For
 * input shape {@code [minibatch, channels, sequenceLength]} output has shape {@code [minibatch, channels, size *
 * sequenceLength]}<br> Example:
 * <pre>
 * If input (for a single example, with channels down page, and sequence from left to right) is:
 * [ A1, A2, A3]
 * [ B1, B2, B3]
 * Then output with size = 2 is:
 * [ A1, A1, A2, A2, A3, A3]
 * [ B1, B1, B2, B2, B3, B2]
 * </pre>
 *
 * @author Max Pumperla
 */
@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Upsampling1D extends BaseUpsamplingLayer {

    protected int[] size;

    protected Upsampling1D(UpsamplingBuilder builder) {
        super(builder);
        this.size = builder.size;
    }

    @Override
    public org.deeplearning4j.nn.api.Layer instantiate(NeuralNetConfiguration conf,
                    Collection<TrainingListener> trainingListeners, int layerIndex, INDArray layerParamsView,
                    boolean initializeParams) {
        org.deeplearning4j.nn.layers.convolution.upsampling.Upsampling1D ret =
                        new org.deeplearning4j.nn.layers.convolution.upsampling.Upsampling1D(conf);
        ret.setListeners(trainingListeners);
        ret.setIndex(layerIndex);
        ret.setParamsViewArray(layerParamsView);
        Map<String, INDArray> paramTable = initializer().init(conf, layerParamsView, initializeParams);
        ret.setParamTable(paramTable);
        ret.setConf(conf);
        return ret;
    }

    @Override
    public Upsampling1D clone() {
        Upsampling1D clone = (Upsampling1D) super.clone();
        return clone;
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType inputType) {
        if (inputType == null || inputType.getType() != InputType.Type.RNN) {
            throw new IllegalStateException("Invalid input for 1D Upsampling layer (layer index = " + layerIndex
                            + ", layer name = \"" + getLayerName() + "\"): expect RNN input type with size > 0. Got: "
                            + inputType);
        }
        InputType.InputTypeRecurrent recurrent = (InputType.InputTypeRecurrent) inputType;
        long outLength = recurrent.getTimeSeriesLength();
        if (outLength > 0) {
            outLength *= size[0];
        }
        return InputType.recurrent(recurrent.getSize(), outLength);
    }

    @Override
    public InputPreProcessor getPreProcessorForInputType(InputType inputType) {
        if (inputType == null) {
            throw new IllegalStateException("Invalid input for Upsampling layer (layer name=\"" + getLayerName()
                            + "\"): input is null");
        }
        return InputTypeUtil.getPreProcessorForInputTypeCnnLayers(inputType, getLayerName());
    }

    @Override
    public LayerMemoryReport getMemoryReport(InputType inputType) {
        InputType.InputTypeRecurrent recurrent = (InputType.InputTypeRecurrent) inputType;
        InputType.InputTypeRecurrent outputType = (InputType.InputTypeRecurrent) getOutputType(-1, inputType);

        long im2colSizePerEx = recurrent.getSize() * outputType.getTimeSeriesLength() * size[0];
        long trainingWorkingSizePerEx = im2colSizePerEx;
        if (getIDropout() != null) {
            trainingWorkingSizePerEx += inputType.arrayElementsPerExample();
        }

        return new LayerMemoryReport.Builder(layerName, Upsampling1D.class, inputType, outputType).standardMemory(0, 0) //No params
                        .workingMemory(0, im2colSizePerEx, 0, trainingWorkingSizePerEx)
                        .cacheMemory(MemoryReport.CACHE_MODE_ALL_ZEROS, MemoryReport.CACHE_MODE_ALL_ZEROS) //No caching
                        .build();
    }

    @NoArgsConstructor
    public static class Builder extends UpsamplingBuilder<Builder> {

        public Builder(int size) {
            super(new int[] {size, size});
        }

        /**
         * Upsampling size
         *
         * @param size upsampling size in single spatial dimension of this 1D layer
         */
        public Builder size(int size) {

            this.size = new int[] {size, size};
            return this;
        }

        /**
         * Upsampling size int array with a single element. Array must be length 1
         *
         * @param size upsampling size in single spatial dimension of this 1D layer
         */
        public Builder size(int[] size) {
            Preconditions.checkArgument(size.length == 1, "Input array must be length 1");
            this.size = new int[] {size[0], size[0]}; // Since this is 2D under the hood, we need to hide this.
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Upsampling1D build() {
            return new Upsampling1D(this);
        }

        @Override
        public void setSize(int[] size) {
            size(size);
        }
    }

}

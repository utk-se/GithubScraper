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
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.conf.memory.MemoryReport;
import org.deeplearning4j.nn.params.EmptyParamInitializer;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Collection;
import java.util.Map;

/**
 * Space to channels utility layer configuration for convolutional input types.
 * <p>
 * This operation takes 4D array in, in either NCHW or NHWC format, and moves data from spatial dimensions (HW) to
 * channels (C) for given blockSize.<br> The idea is that blocks of the input of size [blockSize,blockSize] are moved
 * from the spatial dimension to the depth dimension.<br> Thus, for NCHW input format, input shape {@code [mb,
 * inChannels, H, W]}, output has shape {@code [mb, inChannels * blockSize * blockSize, H/blockSize, W/blockSize]}
 * <p></p>
 * Example:
 * <pre>
 * blockSize = 4
 * dataFormat = "NCHW"
 * input shape =  [128, 16, 16, 3]
 * output shape = [128, 16/4, 16/4, 3*4*4]
 * </pre>
 *
 * @author Max Pumperla
 */

@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SpaceToDepthLayer extends NoParamLayer {

    public enum DataFormat {
        NCHW, NHWC
    }

    protected int blockSize;
    protected DataFormat dataFormat;


    protected SpaceToDepthLayer(Builder builder) {
        super(builder);
        this.blockSize = builder.blockSize;
        this.dataFormat = builder.dataFormat;
    }

    @Override
    public SpaceToDepthLayer clone() {
        return (SpaceToDepthLayer) super.clone();
    }

    @Override
    public org.deeplearning4j.nn.api.Layer instantiate(NeuralNetConfiguration conf,
                    Collection<TrainingListener> trainingListeners, int layerIndex, INDArray layerParamsView,
                    boolean initializeParams) {
        org.deeplearning4j.nn.layers.convolution.SpaceToDepth ret =
                        new org.deeplearning4j.nn.layers.convolution.SpaceToDepth(conf);
        ret.setListeners(trainingListeners);
        ret.setIndex(layerIndex);
        ret.setParamsViewArray(layerParamsView);
        Map<String, INDArray> paramTable = initializer().init(conf, layerParamsView, initializeParams);
        ret.setParamTable(paramTable);
        ret.setConf(conf);
        return ret;
    }

    @Override
    public LayerMemoryReport getMemoryReport(InputType inputType) {
        InputType.InputTypeConvolutional c = (InputType.InputTypeConvolutional) inputType;
        InputType.InputTypeConvolutional outputType = (InputType.InputTypeConvolutional) getOutputType(-1, inputType);

        return new LayerMemoryReport.Builder(layerName, SpaceToDepthLayer.class, inputType, outputType)
                        .standardMemory(0, 0) //No params
                        .cacheMemory(MemoryReport.CACHE_MODE_ALL_ZEROS, MemoryReport.CACHE_MODE_ALL_ZEROS) //No caching
                        .build();
    }

    @Override
    public InputType getOutputType(int layerIndex, InputType inputType) {
        if (inputType == null || inputType.getType() != InputType.Type.CNN) {
            throw new IllegalStateException("Invalid input for space to channels layer (layer name=\"" + getLayerName()
                            + "\"): Expected CNN input, got " + inputType);
        }
        InputType.InputTypeConvolutional i = (InputType.InputTypeConvolutional) inputType;
        return InputType.convolutional(i.getHeight() / blockSize, i.getWidth() / blockSize,
                        i.getChannels() * blockSize * blockSize);
    }

    @Override
    public ParamInitializer initializer() {
        return EmptyParamInitializer.getInstance();
    }


    @Override
    public void setNIn(InputType inputType, boolean override) {
        //No op: space to batch layer doesn't have nIn value
    }

    @Override
    public InputPreProcessor getPreProcessorForInputType(InputType inputType) {
        if (inputType == null) {
            throw new IllegalStateException("Invalid input for space to channels layer (layer name=\"" + getLayerName()
                            + "\"): input is null");
        }
        return InputTypeUtil.getPreProcessorForInputTypeCnnLayers(inputType, getLayerName());
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
        throw new UnsupportedOperationException("SpaceToDepthLayer does not contain parameters");
    }


    @NoArgsConstructor
    @Getter
    @Setter
    public static class Builder<T extends Builder<T>> extends Layer.Builder<T> {

        protected int blockSize;

        /**
         * Data format for input activations. Note DL4J uses NCHW in most cases
         */
        protected DataFormat dataFormat = DataFormat.NCHW;

        /**
         * @param blockSize Block size
         */
        public Builder(int blockSize) {
            this.blockSize = blockSize;
        }

        /**
         * @param blockSize Block size
         * @param dataFormat Data format for input activations. Note DL4J uses NCHW in most cases
         */
        public Builder(int blockSize, DataFormat dataFormat) {
            this.blockSize = blockSize;
            this.dataFormat = dataFormat;
        }

        /**
         * @param blockSize Block size
         */
        public T blocks(int blockSize) {
            this.blockSize = blockSize;
            return (T) this;
        }

        /**
         * @param dataFormat Data format for input activations. Note DL4J uses NCHW in most cases
         */
        public T dataFormat(DataFormat dataFormat) {
            this.dataFormat = dataFormat;
            return (T) this;
        }

        @Override
        public T name(String layerName) {
            this.layerName = layerName;
            return (T) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public SpaceToDepthLayer build() {
            return new SpaceToDepthLayer(this);
        }
    }

}

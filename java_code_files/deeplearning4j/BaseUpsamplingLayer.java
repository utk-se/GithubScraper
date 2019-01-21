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
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.params.EmptyParamInitializer;

/**
 * Upsampling base layer
 *
 * @author Max Pumperla
 */

@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class BaseUpsamplingLayer extends NoParamLayer {

    protected int[] size;

    protected BaseUpsamplingLayer(UpsamplingBuilder builder) {
        super(builder);
        this.size = builder.size;
    }

    @Override
    public BaseUpsamplingLayer clone() {
        BaseUpsamplingLayer clone = (BaseUpsamplingLayer) super.clone();
        return clone;
    }

    @Override
    public InputPreProcessor getPreProcessorForInputType(InputType inputType) {
        if (inputType == null) {
            throw new IllegalStateException("Invalid input for Upsampling layer (layer name=\"" + getLayerName()
                            + "\"): input is null");
        }
        return InputTypeUtil.getPreProcessorForInputTypeCnnLayers(inputType, getLayerName());
    }


    @NoArgsConstructor
    @Getter
    @Setter
    protected static abstract class UpsamplingBuilder<T extends UpsamplingBuilder<T>> extends Layer.Builder<T> {

        /**
         * An int array to specify upsampling dimensions, the length of which has to equal to the number of spatial
         * dimensions (e.g. 2 for Upsampling2D etc.)
         *
         */
        protected int[] size = new int[] {1};

        /**
         * A single size integer is used for upsampling in all spatial dimensions
         *
         * @param size int for upsampling
         */
        protected UpsamplingBuilder(int size) {
            this.size = new int[] {size};
        }

        /**
         * An int array to specify upsampling dimensions, the length of which has to equal to the number of spatial
         * dimensions (e.g. 2 for Upsampling2D etc.)
         *
         * @param size int for upsampling
         */
        protected UpsamplingBuilder(int[] size) {
            this.size = size;
        }
    }

}

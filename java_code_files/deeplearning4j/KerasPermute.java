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

package org.deeplearning4j.nn.modelimport.keras.layers.core;


import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.modelimport.keras.KerasLayer;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.preprocessors.PermutePreprocessor;
import org.deeplearning4j.nn.modelimport.keras.utils.KerasLayerUtils;
import org.nd4j.linalg.util.ArrayUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Imports Permute layer from Keras
 *
 * @author Max Pumperla
 */
public class KerasPermute extends KerasLayer {

    private int[] permutationIndices;


    /**
     * Constructor from parsed Keras layer configuration dictionary.
     *
     * @param layerConfig dictionary containing Keras layer configuration
     * @throws InvalidKerasConfigurationException     Invalid Keras config
     * @throws UnsupportedKerasConfigurationException Unsupported Keras config
     */
    public KerasPermute(Map<String, Object> layerConfig)
            throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        this(layerConfig, true);
    }

    /**
     * Constructor from parsed Keras layer configuration dictionary.
     *
     * @param layerConfig           dictionary containing Keras layer configuration
     * @param enforceTrainingConfig whether to enforce training-related configuration options
     * @throws InvalidKerasConfigurationException     Invalid Keras config
     * @throws UnsupportedKerasConfigurationException Unsupported Keras config
     */
    public KerasPermute(Map<String, Object> layerConfig, boolean enforceTrainingConfig)
            throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        super(layerConfig, enforceTrainingConfig);
        Map<String, Object> innerConfig = KerasLayerUtils.getInnerLayerConfigFromConfig(layerConfig, conf);
        String permutationInfo = "dims";
        if (innerConfig.containsKey(permutationInfo)) {
            @SuppressWarnings("unchecked")
            List<Integer> targetShapeList = (List<Integer>) innerConfig.get(permutationInfo);
            this.permutationIndices = ArrayUtil.toArray(targetShapeList);
        }

    }

    /**
     * KerasPermute is an InputPreProcessor
     */
    @Override
    public boolean isInputPreProcessor() {
        return true;
    }

    /**
     * Gets appropriate DL4J InputPreProcessor for given InputTypes.
     *
     * @param inputType Array of InputTypes
     * @return DL4J InputPreProcessor
     * @throws InvalidKerasConfigurationException Invalid Keras config
     * @see InputPreProcessor
     */
    @Override
    public InputPreProcessor getInputPreprocessor(InputType... inputType) throws
            InvalidKerasConfigurationException {
        if (inputType.length > 1)
            throw new InvalidKerasConfigurationException(
                    "Keras Permute layer accepts only one input (received " + inputType.length + ")");
        InputPreProcessor preprocessor = null;
        if (inputType[0] instanceof InputType.InputTypeConvolutional) {
            switch (this.getDimOrder()) {
                case THEANO:
                    preprocessor = new PermutePreprocessor(permutationIndices);
                    break;
                case NONE: // TF by default
                case TENSORFLOW:
                    // account for channels last
                    permutationIndices = new int[] {permutationIndices[2], permutationIndices[0], permutationIndices[1]};
                    preprocessor = new PermutePreprocessor(new int[]{1, 3, 2});
            }
        } else if (inputType[0] instanceof InputType.InputTypeRecurrent) {
            if (Arrays.equals(permutationIndices, new int[] {2, 1}))
                preprocessor = new PermutePreprocessor(permutationIndices);
            else
                throw new InvalidKerasConfigurationException("For RNN type input data, permutation dims have to be" +
                        "(2, 1) in Permute layer, got " + Arrays.toString(permutationIndices));
        } else if (inputType[0] instanceof InputType.InputTypeFeedForward) {
            preprocessor = null;
        } else {
            throw new InvalidKerasConfigurationException("Input type not supported: " + inputType[0]);
        }
        return preprocessor;
    }

    /**
     * Get layer output type.
     *
     * @param inputType Array of InputTypes
     * @return output type as InputType
     * @throws InvalidKerasConfigurationException Invalid Keras config
     */
    @Override
    public InputType getOutputType(InputType... inputType) throws InvalidKerasConfigurationException {
        if (inputType.length > 1)
            throw new InvalidKerasConfigurationException(
                    "Keras Permute layer accepts only one input (received " + inputType.length + ")");
        PermutePreprocessor reshape = (PermutePreprocessor) getInputPreprocessor(inputType);
        return reshape.getOutputType(inputType[0]);
    }
}

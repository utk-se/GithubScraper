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

package org.deeplearning4j.nn.conf.preprocessor;

import lombok.*;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.util.TimeSeriesUtils;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.linalg.util.ArrayUtil;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.nd4j.shade.jackson.annotation.JsonCreator;
import org.nd4j.shade.jackson.annotation.JsonProperty;

import java.util.Arrays;

/**A preprocessor to allow CNN and RNN layers to be used together.<br>
 * For example, ConvolutionLayer -> GravesLSTM
 * Functionally equivalent to combining CnnToFeedForwardPreProcessor + FeedForwardToRnnPreProcessor<br>
 * Specifically, this does two things:<br>
 * (a) Reshape 4d activations out of CNN layer, with shape [timeSeriesLength*miniBatchSize, numChannels, inputHeight, inputWidth])
 * into 3d (time series) activations (with shape [numExamples, inputHeight*inputWidth*numChannels, timeSeriesLength])
 * for use in RNN layers<br>
 * (b) Reshapes 3d epsilons (weights.*deltas) out of RNN layer (with shape
 * [miniBatchSize,inputHeight*inputWidth*numChannels,timeSeriesLength]) into 4d epsilons with shape
 * [miniBatchSize*timeSeriesLength, numChannels, inputHeight, inputWidth] suitable to feed into CNN layers.
 * Note: numChannels is equivalent to channels or featureMaps referenced in different literature
 * @author Alex Black
 */
@Data
@EqualsAndHashCode(exclude = {"product"})
public class CnnToRnnPreProcessor implements InputPreProcessor {
    private long inputHeight;
    private long inputWidth;
    private long numChannels;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private long product;

    @JsonCreator
    public CnnToRnnPreProcessor(@JsonProperty("inputHeight") long inputHeight,
                    @JsonProperty("inputWidth") long inputWidth, @JsonProperty("numChannels") long numChannels) {
        this.inputHeight = inputHeight;
        this.inputWidth = inputWidth;
        this.numChannels = numChannels;
        this.product = inputHeight * inputWidth * numChannels;
    }

    @Override
    public INDArray preProcess(INDArray input, int miniBatchSize, LayerWorkspaceMgr workspaceMgr) {
        if (input.rank() != 4)
            throw new IllegalArgumentException(
                            "Invalid input: expect CNN activations with rank 4 (received input with shape "
                                            + Arrays.toString(input.shape()) + ")");
        if(input.size(1) != numChannels || input.size(2) != inputHeight || input.size(3) != inputWidth){
            throw new IllegalStateException("Invalid input, does not match configuration: expected [minibatch, numChannels="
                    + numChannels + ", inputHeight=" + inputHeight + ", inputWidth=" + inputWidth + "] but got input array of" +
                    "shape " + Arrays.toString(input.shape()));
        }
        //Input: 4d activations (CNN)
        //Output: 3d activations (RNN)

        if (input.ordering() != 'c' || !Shape.hasDefaultStridesForShape(input))
            input = input.dup('c');

        val shape = input.shape(); //[timeSeriesLength*miniBatchSize, numChannels, inputHeight, inputWidth]

        //First: reshape 4d to 2d, as per CnnToFeedForwardPreProcessor
        INDArray twod = input.reshape('c', input.size(0), ArrayUtil.prod(input.shape()) / input.size(0));
        //Second: reshape 2d to 3d, as per FeedForwardToRnnPreProcessor
        INDArray reshaped = workspaceMgr.dup(ArrayType.ACTIVATIONS, twod, 'f');
        reshaped = reshaped.reshape('f', miniBatchSize, shape[0] / miniBatchSize, product);
        return reshaped.permute(0, 2, 1);
    }

    @Override
    public INDArray backprop(INDArray output, int miniBatchSize, LayerWorkspaceMgr workspaceMgr) {
        if (output.ordering() == 'c' || !Shape.hasDefaultStridesForShape(output))
            output = output.dup('f');

        val shape = output.shape();
        INDArray output2d;
        if (shape[0] == 1) {
            //Edge case: miniBatchSize = 1
            output2d = output.tensorAlongDimension(0, 1, 2).permutei(1, 0);
        } else if (shape[2] == 1) {
            //Edge case: timeSeriesLength = 1
            output2d = output.tensorAlongDimension(0, 1, 0);
        } else {
            //As per FeedForwardToRnnPreprocessor
            INDArray permuted3d = output.permute(0, 2, 1);
            output2d = permuted3d.reshape('f', shape[0] * shape[2], shape[1]);
        }

        if (shape[1] != product)
            throw new IllegalArgumentException("Invalid input: expected output size(1)=" + shape[1]
                            + " must be equal to " + inputHeight + " x columns " + inputWidth + " x channels "
                            + numChannels + " = " + product + ", received: " + shape[1]);
        INDArray ret = workspaceMgr.dup(ArrayType.ACTIVATION_GRAD, output2d, 'c');
        return ret.reshape('c', output2d.size(0), numChannels, inputHeight, inputWidth);
    }

    @Override
    public CnnToRnnPreProcessor clone() {
        return new CnnToRnnPreProcessor(inputHeight, inputWidth, numChannels);
    }

    @Override
    public InputType getOutputType(InputType inputType) {
        if (inputType == null || inputType.getType() != InputType.Type.CNN) {
            throw new IllegalStateException("Invalid input type: Expected input of type CNN, got " + inputType);
        }

        InputType.InputTypeConvolutional c = (InputType.InputTypeConvolutional) inputType;
        val outSize = c.getChannels() * c.getHeight() * c.getWidth();
        return InputType.recurrent(outSize);
    }

    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArray(INDArray maskArray, MaskState currentMaskState,
                    int minibatchSize) {
        //Assume mask array is 4d - a mask array that has been reshaped from [minibatch,timeSeriesLength] to [minibatch*timeSeriesLength, 1, 1, 1]
        if (maskArray == null) {
            return new Pair<>(maskArray, currentMaskState);
        } else {
            //Need to reshape mask array from [minibatch*timeSeriesLength, 1, 1, 1] to [minibatch,timeSeriesLength]
            return new Pair<>(TimeSeriesUtils.reshapeCnnMaskToTimeSeriesMask(maskArray, minibatchSize),currentMaskState);
        }
    }
}

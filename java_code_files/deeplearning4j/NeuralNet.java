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

package org.deeplearning4j.rl4j.network;

import org.deeplearning4j.nn.api.NeuralNetwork;
import org.deeplearning4j.nn.gradient.Gradient;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author rubenfiszel (ruben.fiszel@epfl.ch) on 8/5/16.
 *
 * Factorisation between ActorCritic and DQN neural net.
 * Useful for AsyncLearning and Thread code.
 */
public interface NeuralNet<NN extends NeuralNet> {

    /**
     * Returns the underlying MultiLayerNetwork or ComputationGraph objects.
     */
    NeuralNetwork[] getNeuralNetworks();

    /**
     * returns true if this is a recurrent network
     */
    boolean isRecurrent();

    /**
     * required for recurrent networks during init
     */
    void reset();

    /**
     * @param batch batch to evaluate
     * @return evaluation by the model of the input by all outputs
     */
    INDArray[] outputAll(INDArray batch);

    /**
     * clone the Neural Net with the same paramaeters
     * @return the cloned neural net
     */
    NN clone();

    /**
     * copy the parameters from a neural net
     * @param from where to copy parameters
     */
    void copy(NN from);

    /**
     * Calculate the gradients from input and label (target) of all outputs
     * @param input input batch
     * @param labels target batch
     * @return the gradients
     */
    Gradient[] gradient(INDArray input, INDArray[] labels);

    /**
     * fit from input and labels
     * @param input input batch
     * @param labels target batch
     */
    void fit(INDArray input, INDArray[] labels);

    /**
     * update the params from the gradients and the batchSize
     * @param gradients gradients to apply the gradient from
     * @param batchSize batchSize from which the gradient was calculated on (similar to nstep)
     */
    void applyGradient(Gradient[] gradients, int batchSize);


    /**
     * latest score from lastest fit
     * @return latest score
     */
    double getLatestScore();

    /**
     * save the neural net into an OutputStream
     * @param os OutputStream to save in
     */
    void save(OutputStream os) throws IOException;

    /**
     * save the neural net into a filename
     * @param filename filename to save in
     */
    void save(String filename) throws IOException;

}

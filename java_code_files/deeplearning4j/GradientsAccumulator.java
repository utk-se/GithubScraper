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

package org.deeplearning4j.optimize.solvers.accumulation;

import org.deeplearning4j.optimize.api.StepFunction;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.Serializable;
import java.util.Queue;

/**
 * @author raver119@gmail.com
 */
public interface GradientsAccumulator extends Serializable {

    /**
     * This method allows to pass external updates to accumulator, they will be populated across all workers using this GradientsAccumulator instance
     *
     * @param source
     */
    void setExternalSource(IndexedTail source);


    IndexedTail getExternalSource();

    /**
     * This method applies accumulated updates via given StepFunction
     *
     * @param function
     * @param params
     */
    void applyUpdate(StepFunction function, INDArray params, INDArray updates, boolean isFinalStep);

    /**
     * This method applies accumulated updates via given StepFunction
     *
     * @param function
     * @param params
     */
    void applyUpdate(StepFunction function, INDArray params, INDArray updates, double alpha);

    /**
     * This method accepts updates suitable for StepFunction, and accumulates/propagates it across all workers
     *
     * @param array
     */
    void storeUpdate(INDArray array, int iterationNumber, int epochNumber);

    /**
     * This method accepts updates suitable for StepFunction and puts them to the queue, which is used in backpropagation loop
     *
     * PLEASE NOTE: array is expected to be ready for use and match params dimensionality
     *
     * @param array
     */
    void receiveUpdate(INDArray array);

    /**
     * This method allows to highlight early availability of updates
     *
     * @param updatesAvailable
     */
    void markExternalUpdates(boolean updatesAvailable);

    /**
     * This method resets all accumulated updates (if any)
     */
    void reset();

    /**
     * This method does initialization of given worker wrt Thread-Device Affinity
     */
    void touch();

    /**
     * This method checks if there are any (probably external) updates available
     * @return
     */
    boolean hasAnything();
}

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

package org.deeplearning4j.nn.api;

import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.Serializable;

/**
 * This interface describes entity used to conver neural network output to specified class.
 * I.e. INDArray -> int[] on the fly.
 *
 * PLEASE NOTE: Implementation will be used in workspace environment to avoid additional allocations during inference.
 * This means you shouldn't store or return the INDArrays passed to OutputAdapter.apply(INDArray...) directly.
 * If you need a copy of the output array, use standard network output methods, or use INDArray.detach() before storing the array
 *
 * @param <T>
 */
public interface OutputAdapter<T> extends Serializable {

    /**
     * This method provides conversion from multiple INDArrays to T
     *
     * @param outputs
     * @return
     */
    T apply(INDArray... outputs);
}

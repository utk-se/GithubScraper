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

package org.nd4j.linalg.api.ops;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.primitives.Pair;

/**An index accumulation is an operation that returns an index within
 * a NDArray.<br>
 * Examples of IndexAccumulation operations include finding the index
 * of the minimim value, index of the maximum value, index of the first
 * element equal to value y, index of the maximum pair-wise difference
 * between two NDArrays X and Y etc.<br>
 *
 * Index accumulation is similar to {@link ReduceOp} in that both are
 * accumulation/reduction operations, however index accumulation returns
 * an integer corresponding to an index, rather than a real (or complex)
 * value.<br>
 *
 * Index accumulation operations generally have 3 inputs:<br>
 * x -> the origin ndarray<br>
 * y -> the pairwise ndarray (frequently null/not applicable)<br>
 * n -> the number of times to accumulate<br>
 *
 * Note that IndexAccumulation op implementations should be stateless
 * (other than the final result and x/y/n arguments) and hence threadsafe,
 * such that they may be parallelized using the update, combineSubResults
 * and set/getFinalResults methods.
 */
public interface IndexAccumulation extends Op {

    boolean validateDataTypes();

    /**
     * This method returns TRUE if we're going to keep axis, FALSE otherwise
     *
     * @return
     */
    boolean isKeepDims();

    /**
     * This method returns true if scalar is 0D, false otherwise
     * @return
     */
    boolean isNewFormat();

    /**
     * This method returns dimensions for this op
     * @return
     */
    INDArray dimensions();

    Number getFinalResult();
}

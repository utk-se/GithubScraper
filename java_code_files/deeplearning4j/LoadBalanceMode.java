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

package org.deeplearning4j.parallelism.inference;

/**
 * This enum describes various load balance modes for ParallelInference
 *
 * @author raver119@gmail.com
 */
public enum LoadBalanceMode {
    /**
     * In this mode, `n+1 % nodes` node will be used for next request
     */
    ROUND_ROBIN,

    /**
     * in this mode we'll be picking free node for next request, blocking if we don't have free nodes at the moment
     */
    FIFO,
}

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

package org.nd4j.jita.allocator.enums;

/**
 * @author raver119@gmail.com
 */
public class CudaConstants {
    public static int cudaMemcpyHostToHost = 0;
    /**< Host   -> Host */
    public static int cudaMemcpyHostToDevice = 1;
    /**< Host   -> Device */
    public static int cudaMemcpyDeviceToHost = 2;
    /**< Device -> Host */
    public static int cudaMemcpyDeviceToDevice = 3;
    /**< Device -> Device */
    public static int cudaMemcpyDefault = 4;
}

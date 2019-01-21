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

package org.nd4j.jita.allocator.pointers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bytedeco.javacpp.Pointer;

/**
 * @author raver119@gmail.com
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointersPair {
    /**
     * this field can be null, on system without any special devices
     */
    private volatile Pointer devicePointer;

    /**
     * this should always contain long pointer to host memory
     */
    private volatile Pointer hostPointer;

    public PointersPair(long devicePointer, long hostPointer) {
        this.devicePointer = new CudaPointer(devicePointer);
        this.hostPointer = new CudaPointer(hostPointer);
    }
}

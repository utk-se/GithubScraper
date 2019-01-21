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

package org.nd4j.linalg.api.memory;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.nd4j.linalg.api.memory.enums.AllocationKind;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author raver119@gmail.com
 */
@Slf4j
public class DeviceAllocationsTracker {
    private Map<AllocationKind, AtomicLong> bytesMap = new HashMap<>();

    public DeviceAllocationsTracker() {
        for (val e:AllocationKind.values()) {
            bytesMap.put(e, new AtomicLong(0));
        }
    }

    public void updateState(@NonNull AllocationKind kind, long bytes) {
        bytesMap.get(kind).addAndGet(bytes);
    }

    public long getState(@NonNull AllocationKind kind) {
        return bytesMap.get(kind).get();
    }
}

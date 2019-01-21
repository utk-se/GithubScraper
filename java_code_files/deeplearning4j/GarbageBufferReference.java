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

package org.nd4j.jita.allocator.garbage;

import org.nd4j.jita.allocator.impl.AllocationPoint;
import org.nd4j.linalg.api.buffer.BaseDataBuffer;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * @author raver119@gmail.com
 */
public class GarbageBufferReference extends WeakReference<BaseDataBuffer> {
    private final AllocationPoint point;

    public GarbageBufferReference(BaseDataBuffer referent, ReferenceQueue<? super BaseDataBuffer> q,
                    AllocationPoint point) {
        super(referent, q);
        this.point = point;
    }

    public AllocationPoint getPoint() {
        return point;
    }
}

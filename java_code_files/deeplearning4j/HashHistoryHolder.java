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

package org.nd4j.parameterserver.distributed.v2.messages.history;

import org.nd4j.parameterserver.distributed.v2.messages.MessagesHistoryHolder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Basic implementation of Model
 * @author raver119@gmail.com
 */
public class HashHistoryHolder<T> implements MessagesHistoryHolder<T> {
    protected final Set<T> set;
    /**
     *
     * @param tailLength number of elements to hold in history
     */
    public HashHistoryHolder(final int tailLength) {
        set = Collections.newSetFromMap(new LinkedHashMap<T, Boolean>(tailLength) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<T, Boolean> eldest) {
                return size() > tailLength;
            }
        });
    }

    @Override
    public synchronized boolean storeIfUnknownMessageId(T id) {
        if (!isKnownMessageId(id)) {
            set.add(id);
            return false;
        } else
            return true;
    }

    @Override
    public synchronized boolean isKnownMessageId(T id) {
        return set.contains(id);
    }
}

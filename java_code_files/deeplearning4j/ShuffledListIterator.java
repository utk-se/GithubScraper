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

package org.datavec.api.util.files;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A very simple iterator over a list, that takes an optional int[] for the order.
 * If the order array is not present, elements are returned in sequential order.
 *
 * @author Alex Black
 */
public class ShuffledListIterator<T> implements Iterator<T> {

    private final List<T> list;
    private final int[] order;
    private int currentPosition = 0;

    public ShuffledListIterator(List<T> list, int[] order) {
        if (order != null && list.size() != order.length) {
            throw new IllegalArgumentException("Order array and list sizes differ");
        }
        this.list = list;
        this.order = order;
    }

    @Override
    public boolean hasNext() {
        return currentPosition < list.size();
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        int nextPos = (order != null ? order[currentPosition] : currentPosition);
        currentPosition++;
        return list.get(nextPos);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Not supported");
    }
}

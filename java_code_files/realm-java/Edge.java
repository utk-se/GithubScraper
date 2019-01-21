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

package org.deeplearning4j.models.sequencevectors.graph.primitives;

import lombok.Data;

/** Edge in a graph. May be a directed or undirected edge.<br>
 * Parameterized, and may store a value/object associated with the edge
 */
@Data
public class Edge<T extends Number> {

    private final int from;
    private final int to;
    private final T value;
    private final boolean directed;

    public Edge(int from, int to, T value, boolean directed) {
        this.from = from;
        this.to = to;
        this.value = value;
        this.directed = directed;
    }

    @Override
    public String toString() {
        return "edge(" + (directed ? "directed" : "undirected") + "," + from + (directed ? "->" : "--") + to + ","
                        + (value != null ? value : "") + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Edge))
            return false;
        Edge<?> e = (Edge<?>) o;
        if (directed != e.directed)
            return false;
        if (directed) {
            if (from != e.from)
                return false;
            if (to != e.to)
                return false;
        } else {
            if (from == e.from) {
                if (to != e.to)
                    return false;
            } else {
                if (from != e.to)
                    return false;
                if (to != e.from)
                    return false;
            }
        }
        if ((value != null && e.value == null) || (value == null && e.value != null))
            return false;
        return value == null || value.equals(e.value);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (directed ? 1 : 0);
        result = 31 * result + from;
        result = 31 * result + to;
        result = 31 * result + (value == null ? 0 : value.hashCode());
        return result;
    }
}

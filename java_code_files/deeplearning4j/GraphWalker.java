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

package org.deeplearning4j.models.sequencevectors.graph.walkers;

import org.deeplearning4j.models.sequencevectors.graph.primitives.IGraph;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.sequence.SequenceElement;

/**
 * This interface describes methods needed for various DeepWalk-related implementations
 *
 * @author raver119@gmail.com
 */
public interface GraphWalker<T extends SequenceElement> {

    IGraph<T, ?> getSourceGraph();

    /**
     * This method checks, if walker has any more sequences left in queue
     *
     * @return
     */
    boolean hasNext();

    /**
     * This method returns next walk sequence from this graph
     *
     * @return
     */
    Sequence<T> next();

    /**
     * This method resets walker
     *
     * @param shuffle if TRUE, order of walks will be shuffled
     */
    void reset(boolean shuffle);


    boolean isLabelEnabled();
}

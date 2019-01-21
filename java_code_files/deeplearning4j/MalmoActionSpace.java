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

package org.deeplearning4j.malmo;

import org.deeplearning4j.rl4j.space.DiscreteSpace;

/**
 * Abstract base class for all Malmo-specific action spaces
 * @author howard-abrams (howard.abrams@ca.com) on 1/12/17.
 */
public abstract class MalmoActionSpace extends DiscreteSpace {
    /**
     * Array of action strings that will be sent to Malmo
     */
    protected String[] actions;

    /**
     * Protected constructor
     * @param size number of discrete actions in this space
     */
    protected MalmoActionSpace(int size) {
        super(size);
    }

    @Override
    public Object encode(Integer action) {
        return actions[action];
    }

    @Override
    public Integer noOp() {
        return -1;
    }

    /**
     * Sets the seed used for random generation of actions
     * @param seed random number generator seed
     */
    public void setRandomSeed(long seed) {
        rd.setSeed(seed);
    }
}

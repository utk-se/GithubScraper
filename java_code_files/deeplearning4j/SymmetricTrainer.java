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

package org.deeplearning4j.parallelism.trainer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.listeners.SharedGradient;
import org.deeplearning4j.optimize.solvers.accumulation.GradientsAccumulator;
import org.deeplearning4j.parallelism.ParallelWrapper;

/**
 * This trainer implementation does parallel training via gradients broadcasts.
 * After each iteration, gradients from this trainer will be propagated & applied to all other trainers
 *
 * @author raver119@gmail.com
 */
@Slf4j
public class SymmetricTrainer extends DefaultTrainer implements CommunicativeTrainer {
    protected GradientsAccumulator accumulator;

    public SymmetricTrainer(@NonNull Model originalModel, String uuid, int threadIdx, @NonNull WorkspaceMode mode,
                    @NonNull ParallelWrapper wrapper, boolean useMDS) {
        super();
        this.uuid = uuid + "_thread_" + threadIdx;
        this.useMDS = useMDS;
        this.originalModel = originalModel;
        this.threadId = threadIdx;
        this.workspaceMode = mode;
        this.parallelWrapper = wrapper;
        this.accumulator = wrapper.getGradientsAccumulator();

    }

    // FIXME: delete this method, it's not needed anymore
    @Deprecated
    public void enqueueGradient(SharedGradient gradient) {
        //
    }


    @Override
    public boolean averagingRequired() {
        return false;
    }

    @Override
    protected void postInit() {
        super.postInit();

        if (accumulator == null) {
            log.warn("GradientsAccumulator is undefined, gradients sharing will be skipped");
            return;
        }

        // just pass accumulator down the hill
        if (replicatedModel instanceof ComputationGraph) {
            ((ComputationGraph) replicatedModel).setGradientsAccumulator(accumulator);
        } else if (replicatedModel instanceof MultiLayerNetwork) {
            ((MultiLayerNetwork) replicatedModel).setGradientsAccumulator(accumulator);
        }

        // need to attach this device id to accumulator's workspaces
        accumulator.touch();
    }



}

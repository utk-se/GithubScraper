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

package org.deeplearning4j.spark.parameterserver.conf;

import lombok.*;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.optimize.solvers.accumulation.MessageHandler;
import org.deeplearning4j.optimize.solvers.accumulation.encoding.ResidualPostProcessor;
import org.deeplearning4j.optimize.solvers.accumulation.encoding.ThresholdAlgorithm;
import org.nd4j.parameterserver.distributed.conf.VoidConfiguration;

import java.io.Serializable;

/**
 *
 * @author raver119@gmail.com
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharedTrainingConfiguration implements Serializable {
    protected VoidConfiguration voidConfiguration;

    @Builder.Default
    protected WorkspaceMode workspaceMode = WorkspaceMode.ENABLED;
    @Builder.Default
    protected int prefetchSize = 2;
    @Builder.Default
    protected boolean epochReset = false;
    @Builder.Default
    protected int numberOfWorkersPerNode = -1;
    @Builder.Default
    protected long debugLongerIterations = 0L;
    @Builder.Default
    protected boolean encodingDebugMode = false;

    /**
     * This value **overrides** bufferSize calculations for gradients accumulator
     */
    @Builder.Default
    protected int bufferSize = 0;

    protected ThresholdAlgorithm thresholdAlgorithm;
    protected ResidualPostProcessor residualPostProcessor;
    protected String messageHandlerClass;



    public void setMessageHandlerClass(@NonNull String messageHandlerClass) {
        this.messageHandlerClass = messageHandlerClass;
    }

    public void setMessageHandlerClass(@NonNull MessageHandler handler) {
        this.messageHandlerClass = handler.getClass().getCanonicalName();
    }
}

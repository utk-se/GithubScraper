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

package org.deeplearning4j.optimize.solvers.accumulation.encoding.residual;

import org.deeplearning4j.optimize.solvers.accumulation.encoding.ResidualPostProcessor;
import org.nd4j.linalg.api.ndarray.INDArray;

/**
 * This residual post process is a "no op" post processor.
 * i.e., it does not modify the residual array at all
 */
public class NoOpResidualPostProcessor implements ResidualPostProcessor {
    @Override
    public void processResidual(int iteration, int epoch, double lastThreshold, INDArray residualVector) {
        //No op
    }

    @Override
    public ResidualPostProcessor clone() {
        return new NoOpResidualPostProcessor();
    }
}

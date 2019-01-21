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

package org.deeplearning4j.perf.listener;

import lombok.NonNull;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import oshi.json.SystemInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Using {@link SystemInfo} - it prints a json representation
 * on each callback to the specified file.
 *
 * @author Adam Gibson
 */
@Slf4j
@Builder
public class SystemInfoFilePrintListener implements TrainingListener {

    private boolean printOnEpochStart;
    private boolean printOnEpochEnd;
    private boolean printOnForwardPass;
    private boolean printOnBackwardPass;
    private boolean printOnGradientCalculation;
    private File printFileTarget;

    public SystemInfoFilePrintListener(boolean printOnEpochStart, boolean printOnEpochEnd, boolean printOnForwardPass, boolean printOnBackwardPass, boolean printOnGradientCalculation, @NonNull File printFileTarget) {
        this.printOnEpochStart = printOnEpochStart;
        this.printOnEpochEnd = printOnEpochEnd;
        this.printOnForwardPass = printOnForwardPass;
        this.printOnBackwardPass = printOnBackwardPass;
        this.printOnGradientCalculation = printOnGradientCalculation;
        this.printFileTarget = printFileTarget;

    }

    @Override
    public void iterationDone(Model model, int iteration, int epoch) {

    }

    @Override
    public void onEpochStart(Model model) {
        if(!printOnEpochStart || printFileTarget == null)
            return;

        writeFileWithMessage("epoch end");

    }

    @Override
    public void onEpochEnd(Model model) {
        if(!printOnEpochEnd || printFileTarget == null)
            return;

        writeFileWithMessage("epoch begin");

    }

    @Override
    public void onForwardPass(Model model, List<INDArray> activations) {
        if(!printOnBackwardPass || printFileTarget == null)
            return;

        writeFileWithMessage("forward pass");

    }

    @Override
    public void onForwardPass(Model model, Map<String, INDArray> activations) {
        if(!printOnForwardPass || printFileTarget == null)
            return;

        writeFileWithMessage("forward pass");

    }

    @Override
    public void onGradientCalculation(Model model) {
        if(!printOnGradientCalculation || printFileTarget == null)
            return;

        writeFileWithMessage("gradient calculation");


    }

    @Override
    public void onBackwardPass(Model model) {
        if(!printOnBackwardPass || printFileTarget == null)
            return;

        writeFileWithMessage("backward pass");
    }

    private void writeFileWithMessage(String status) {
        if(printFileTarget == null) {
            log.warn("File not specified for writing!");
        }

        SystemInfo systemInfo = new SystemInfo();
        log.info("Writing system info to file on " + status + ": "  + printFileTarget.getAbsolutePath());
        try {
            FileUtils.write(printFileTarget,systemInfo.toPrettyJSON(), true);
        } catch (IOException e) {
            log.error("Error writing file for system info",e);
        }
    }
}



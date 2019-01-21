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

package org.deeplearning4j.arbiter.conf.updater.schedule;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.deeplearning4j.arbiter.optimize.api.ParameterSpace;
import org.deeplearning4j.arbiter.optimize.parameter.FixedValue;
import org.nd4j.linalg.schedule.ExponentialSchedule;
import org.nd4j.linalg.schedule.ISchedule;
import org.nd4j.linalg.schedule.ScheduleType;
import org.nd4j.shade.jackson.annotation.JsonProperty;

import java.util.*;

@NoArgsConstructor //JSON
@Data
public class ExponentialScheduleSpace implements ParameterSpace<ISchedule> {

    private ScheduleType scheduleType;
    private ParameterSpace<Double> initialValue;
    private ParameterSpace<Double> gamma;

    public ExponentialScheduleSpace(@NonNull ScheduleType scheduleType,
                                    @NonNull ParameterSpace<Double> initialValue, double gamma){
        this(scheduleType, initialValue, new FixedValue<>(gamma));
    }

    public ExponentialScheduleSpace(@NonNull @JsonProperty("scheduleType") ScheduleType scheduleType,
                                    @NonNull @JsonProperty("initialValue") ParameterSpace<Double> initialValue,
                                    @NonNull @JsonProperty("gamma") ParameterSpace<Double> gamma){
        this.scheduleType = scheduleType;
        this.initialValue = initialValue;
        this.gamma = gamma;
    }

    @Override
    public ISchedule getValue(double[] parameterValues) {
        return new ExponentialSchedule(scheduleType, initialValue.getValue(parameterValues), gamma.getValue(parameterValues));
    }

    @Override
    public int numParameters() {
        return initialValue.numParameters() + gamma.numParameters();
    }

    @Override
    public List<ParameterSpace> collectLeaves() {
        return Arrays.<ParameterSpace>asList(initialValue, gamma);
    }

    @Override
    public Map<String, ParameterSpace> getNestedSpaces() {
        Map<String,ParameterSpace> out = new LinkedHashMap<>();
        out.put("initialValue", initialValue);
        out.put("gamma", gamma);
        return out;
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public void setIndices(int... indices) {
        if(initialValue.numParameters() > 0){
            int[] sub = Arrays.copyOfRange(indices, 0, initialValue.numParameters());
            initialValue.setIndices(sub);
        }
        if(gamma.numParameters() > 0){
            int inp = initialValue.numParameters();
            int[] sub = Arrays.copyOfRange(indices, inp, inp + gamma.numParameters());
            gamma.setIndices(sub);
        }
    }
}

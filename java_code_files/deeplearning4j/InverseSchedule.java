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

package org.nd4j.linalg.schedule;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.nd4j.shade.jackson.annotation.JsonProperty;

/**
 * Inverse schedule, with 3 parameters: initial value, gamma and power.<br>
 * value(i) = initialValue * (1 + gamma * iter)^(-power)
 * where i is the iteration or epoch (depending on the setting)
 *
 * @author Alex Black
 */
@Data
@EqualsAndHashCode
public class InverseSchedule implements ISchedule {

    private final ScheduleType scheduleType;
    private final double initialValue;
    private final double gamma;
    private final double power;

    public InverseSchedule(@JsonProperty("scheduleType") ScheduleType scheduleType,
                           @JsonProperty("initialValue") double initialValue,
                           @JsonProperty("gamma") double gamma,
                           @JsonProperty("power") double power){
        this.scheduleType = scheduleType;
        this.initialValue = initialValue;
        this.gamma = gamma;
        this.power = power;
    }

    @Override
    public double valueAt(int iteration, int epoch) {
        int i = (scheduleType == ScheduleType.ITERATION ? iteration : epoch);
        return initialValue / Math.pow(1 + gamma * i, power);
    }

    @Override
    public ISchedule clone() {
        return new InverseSchedule(scheduleType, initialValue, gamma, power);
    }
}

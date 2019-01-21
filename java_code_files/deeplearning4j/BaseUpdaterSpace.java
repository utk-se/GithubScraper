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

package org.deeplearning4j.arbiter.conf.updater;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.deeplearning4j.arbiter.optimize.api.AbstractParameterSpace;
import org.deeplearning4j.arbiter.optimize.api.ParameterSpace;
import org.nd4j.linalg.learning.config.IUpdater;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Data
public abstract class BaseUpdaterSpace extends AbstractParameterSpace<IUpdater> {

    @Override
    public int numParameters() {
        int count = 0;
        for(ParameterSpace p : collectLeaves()){
            count += p.numParameters();
        }
        return count;
    }

    @Override
    public List<ParameterSpace> collectLeaves() {
        Map<String, ParameterSpace> nested = getNestedSpaces();
        List<ParameterSpace> out = new ArrayList<>();
        for(ParameterSpace p : nested.values()){
            out.addAll(p.collectLeaves());
        }
        return out;
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public void setIndices(int... indices){
        int soFar = 0;
        for(ParameterSpace p : collectLeaves()){
            int numParams = p.numParameters();
            if(numParams <= 0){
                continue;
            }
            int[] subset = Arrays.copyOfRange(indices, soFar, soFar + numParams);
            p.setIndices(subset);
        }
    }
}

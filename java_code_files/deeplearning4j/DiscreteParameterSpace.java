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

package org.deeplearning4j.arbiter.optimize.parameter.discrete;

import lombok.EqualsAndHashCode;
import org.deeplearning4j.arbiter.optimize.api.ParameterSpace;
import org.deeplearning4j.arbiter.util.ObjectUtils;
import org.nd4j.shade.jackson.annotation.JsonIgnoreProperties;
import org.nd4j.shade.jackson.annotation.JsonProperty;
import org.nd4j.shade.jackson.databind.annotation.JsonSerialize;

import java.util.*;

/**
 * A DiscreteParameterSpace is used for a set of un-ordered values
 *
 * @param <P> Parameter type
 * @author Alex Black
 */
@EqualsAndHashCode
public class DiscreteParameterSpace<P> implements ParameterSpace<P> {

    @JsonSerialize
    private List<P> values;
    private int index = -1;

    public DiscreteParameterSpace(@JsonProperty("values") P... values) {
        if (values != null)
            this.values = Arrays.asList(values);
    }

    public DiscreteParameterSpace(Collection<P> values) {
        this.values = new ArrayList<>(values);
    }

    public int numValues() {
        return values.size();
    }

    @Override
    public P getValue(double[] input) {
        if (index == -1) {
            throw new IllegalStateException("Cannot get value: ParameterSpace index has not been set");
        }
        if (values == null)
            throw new IllegalStateException("Values are null.");
        //Map a value in range [0,1] to one of the list of values
        //First value: [0,width], second: (width,2*width], third: (3*width,4*width] etc
        int size = values.size();
        if (size == 1)
            return values.get(0);
        double width = 1.0 / size;
        int val = (int) (input[index] / width);
        return values.get(Math.min(val, size - 1));
    }

    @Override
    public int numParameters() {
        return 1;
    }

    @Override
    public List<ParameterSpace> collectLeaves() {
        return Collections.singletonList((ParameterSpace) this);
    }

    @Override
    public Map<String, ParameterSpace> getNestedSpaces() {
        return Collections.emptyMap();
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public void setIndices(int... indices) {
        if (indices == null || indices.length != 1) {
            throw new IllegalArgumentException("Invalid index");
        }
        this.index = indices[0];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DiscreteParameterSpace(");
        int n = values.size();
        for (int i = 0; i < n; i++) {
            P value = values.get(i);
            sb.append(ObjectUtils.valueToString(value));
            sb.append((i == n - 1 ? ")" : ","));
        }
        return sb.toString();
    }


}

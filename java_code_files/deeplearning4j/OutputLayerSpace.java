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

package org.deeplearning4j.arbiter.layers;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.deeplearning4j.arbiter.util.LeafUtils;
import org.deeplearning4j.nn.conf.layers.OutputLayer;

/**
 * Layer hyperparameter configuration space for output layers
 *
 * @author Alex Black
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE) //For Jackson JSON/YAML deserialization
public class OutputLayerSpace extends BaseOutputLayerSpace<OutputLayer> {

    private OutputLayerSpace(Builder builder) {
        super(builder);

        this.numParameters = LeafUtils.countUniqueParameters(collectLeaves());
    }

    @Override
    public OutputLayer getValue(double[] values) {
        OutputLayer.Builder o = new OutputLayer.Builder();
        setLayerOptionsBuilder(o, values);
        return o.build();
    }

    protected void setLayerOptionsBuilder(OutputLayer.Builder builder, double[] values) {
        super.setLayerOptionsBuilder(builder, values);
    }

    public static class Builder extends BaseOutputLayerSpace.Builder<Builder> {

        @Override
        @SuppressWarnings("unchecked")
        public OutputLayerSpace build() {
            return new OutputLayerSpace(this);
        }
    }

    @Override
    public String toString() {
        return toString(", ");
    }

    @Override
    public String toString(String delim) {
        return "OutputLayerSpace(" + super.toString(delim) + ")";
    }
}

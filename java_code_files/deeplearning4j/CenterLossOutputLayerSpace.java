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
import org.deeplearning4j.arbiter.optimize.api.ParameterSpace;
import org.deeplearning4j.arbiter.optimize.parameter.FixedValue;
import org.deeplearning4j.arbiter.util.LeafUtils;
import org.deeplearning4j.nn.conf.layers.CenterLossOutputLayer;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED) //For Jackson JSON/YAML deserialization
public class CenterLossOutputLayerSpace extends BaseOutputLayerSpace<CenterLossOutputLayer> {

    ParameterSpace<Double> alpha;
    ParameterSpace<Double> lambda;

    protected CenterLossOutputLayerSpace(Builder builder){
        super(builder);
        this.alpha = builder.alpha;
        this.lambda = builder.lambda;

        this.numParameters = LeafUtils.countUniqueParameters(collectLeaves());
    }

    @Override
    public CenterLossOutputLayer getValue(double[] parameterValues) {
        CenterLossOutputLayer.Builder b = new CenterLossOutputLayer.Builder();
        setLayerOptionsBuilder(b, parameterValues);
        return b.build();
    }

    protected void setLayerBuilderOptions(CenterLossOutputLayer.Builder builder, double[] values){
        super.setLayerOptionsBuilder(builder, values);
        if(alpha != null)
            builder.alpha(alpha.getValue(values));
        if(lambda != null)
            builder.lambda(lambda.getValue(values));
    }

    public static class Builder extends BaseOutputLayerSpace.Builder<Builder> {

        ParameterSpace<Double> alpha;
        ParameterSpace<Double> lambda;

        public Builder alpha(double alpha){
            return alpha(new FixedValue<>(alpha));
        }

        public Builder alpha(ParameterSpace<Double> alpha){
            this.alpha = alpha;
            return this;
        }

        public Builder lambda(double lambda){
            return lambda(new FixedValue<>(lambda));
        }

        public Builder lambda(ParameterSpace<Double> lambda){
            this.lambda = lambda;
            return this;
        }

        @Override
        public CenterLossOutputLayerSpace build() {
            return new CenterLossOutputLayerSpace(this);
        }
    }
}

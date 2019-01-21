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

package org.deeplearning4j.nn.weights;

import org.deeplearning4j.nn.conf.distribution.Distribution;

/**
 * Weight initialization scheme
 * <p>
 * <b>DISTRIBUTION</b>: Sample weights from a provided distribution<br>
 * <p>
 * <b>ZERO</b>: Generate weights as zeros<br>
 * <p>
 * <b>ONES</b>: All weights are set to 1
 * <p>
 * <b>SIGMOID_UNIFORM</b>: A version of XAVIER_UNIFORM for sigmoid activation functions. U(-r,r) with r=4*sqrt(6/(fanIn + fanOut))
 * <p>
 * <b>NORMAL</b>: Normal/Gaussian distribution, with mean 0 and standard deviation 1/sqrt(fanIn).
 * This is the initialization recommented in Klambauer et al. 2017, "Self-Normalizing Neural Network". Equivalent to
 * DL4J's XAVIER_FAN_IN and LECUN_NORMAL (i.e. Keras' "lecun_normal")
 * <p>
 * <b>LECUN_UNIFORM</b> Uniform U[-a,a] with a=3/sqrt(fanIn).
 * <p>
 * <b>UNIFORM</b>: Uniform U[-a,a] with a=1/sqrt(fanIn). "Commonly used heuristic" as per Glorot and Bengio 2010
 * <p>
 * <b>XAVIER</b>: As per Glorot and Bengio 2010: Gaussian distribution with mean 0, variance 2.0/(fanIn + fanOut)
 * <p>
 * <b>XAVIER_UNIFORM</b>: As per Glorot and Bengio 2010: Uniform distribution U(-s,s) with s = sqrt(6/(fanIn + fanOut))
 * <p>
 * <b>XAVIER_FAN_IN</b>: Similar to Xavier, but 1/fanIn -> Caffe originally used this.
 * <p>
 * <b>XAVIER_LEGACY</b>: Xavier weight init in DL4J up to 0.6.0. XAVIER should be preferred.
 * <p>
 * <b>RELU</b>: He et al. (2015), "Delving Deep into Rectifiers". Normal distribution with variance 2.0/nIn
 * <p>
 * <b>RELU_UNIFORM</b>: He et al. (2015), "Delving Deep into Rectifiers". Uniform distribution U(-s,s) with s = sqrt(6/fanIn)
 * <p>
 * <b>IDENTITY</b>: Weights are set to an identity matrix. Note: can only be used with square weight matrices
 * <p>
 * <b>VAR_SCALING_NORMAL_FAN_IN</b> Gaussian distribution with mean 0, variance 1.0/(fanIn)
 * <p>
 * <b>VAR_SCALING_NORMAL_FAN_OUT</b> Gaussian distribution with mean 0, variance 1.0/(fanOut)
 * <p>
 * <b>VAR_SCALING_NORMAL_FAN_AVG</b> Gaussian distribution with mean 0, variance 1.0/((fanIn + fanOut)/2)
 * <p>
 * <b>VAR_SCALING_UNIFORM_FAN_IN</b> Uniform U[-a,a] with a=3.0/(fanIn)
 * <p>
 * <b>VAR_SCALING_UNIFORM_FAN_OUT</b> Uniform U[-a,a] with a=3.0/(fanOut)
 * <p>
 * <b>VAR_SCALING_UNIFORM_FAN_AVG</b> Uniform U[-a,a] with a=3.0/((fanIn + fanOut)/2)
 * <p>
 *
 * @author Adam Gibson
 */
public enum WeightInit {
    DISTRIBUTION, ZERO, ONES, SIGMOID_UNIFORM, NORMAL, LECUN_NORMAL, UNIFORM, XAVIER, XAVIER_UNIFORM, XAVIER_FAN_IN, XAVIER_LEGACY, RELU,
    RELU_UNIFORM, IDENTITY, LECUN_UNIFORM, VAR_SCALING_NORMAL_FAN_IN, VAR_SCALING_NORMAL_FAN_OUT, VAR_SCALING_NORMAL_FAN_AVG,
    VAR_SCALING_UNIFORM_FAN_IN, VAR_SCALING_UNIFORM_FAN_OUT, VAR_SCALING_UNIFORM_FAN_AVG;


    /**
     * Create an instance of the weight initialization function
     *
     * @return a new {@link IWeightInit} instance
     */
    public IWeightInit getWeightInitFunction() {
        return getWeightInitFunction(null);
    }

    /**
     * Create an instance of the weight initialization function
     *
     * @param distribution Distribution of the weights (Only used in case DISTRIBUTION)
     * @return a new {@link IWeightInit} instance
     */
    public IWeightInit getWeightInitFunction(Distribution distribution) {
        switch (this) {
            case ZERO:
                return new WeightInitConstant(0.0);
            case ONES:
                return new WeightInitConstant(1.0);
            case DISTRIBUTION:
                return new WeightInitDistribution(distribution);
            case SIGMOID_UNIFORM:
                return new WeightInitSigmoidUniform();
            case LECUN_NORMAL: //Fall through: these 3 are equivalent
            case XAVIER_FAN_IN:
            case NORMAL:
                return new WeightInitNormal();
            case UNIFORM:
                return new WeightInitUniform();
            case XAVIER:
                return new WeightInitXavier();
            case XAVIER_UNIFORM:
                return new WeightInitXavierUniform();
            case XAVIER_LEGACY:
                return new WeightInitXavierLegacy();
            case RELU:
                return new WeightInitRelu();
            case RELU_UNIFORM:
                return new WeightInitReluUniform();
            case IDENTITY:
                return new WeightInitIdentity();
            case LECUN_UNIFORM:
                return new WeightInitLecunUniform();
            case VAR_SCALING_NORMAL_FAN_IN:
                return new WeightInitVarScalingNormalFanIn();
            case VAR_SCALING_NORMAL_FAN_OUT:
                return new WeightInitVarScalingNormalFanOut();
            case VAR_SCALING_NORMAL_FAN_AVG:
                return new WeightInitVarScalingNormalFanAvg();
            case VAR_SCALING_UNIFORM_FAN_IN:
                return new WeightInitVarScalingUniformFanIn();
            case VAR_SCALING_UNIFORM_FAN_OUT:
                return new WeightInitVarScalingUniformFanOut();
            case VAR_SCALING_UNIFORM_FAN_AVG:
                return new WeightInitVarScalingUniformFanAvg();

            default:
                throw new UnsupportedOperationException("Unknown or not supported weight initialization function: " + this);
        }
    }
}

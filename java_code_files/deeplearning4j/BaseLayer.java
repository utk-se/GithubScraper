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

package org.deeplearning4j.nn.conf.layers;

import lombok.*;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.distribution.Distribution;
import org.deeplearning4j.nn.conf.weightnoise.IWeightNoise;
import org.deeplearning4j.nn.weights.IWeightInit;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.nn.weights.WeightInitDistribution;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.learning.config.IUpdater;

import java.io.Serializable;

/**
 * A neural network layer.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class BaseLayer extends Layer implements Serializable, Cloneable {

    protected IActivation activationFn;
    protected IWeightInit weightInitFn;
    protected double biasInit;
    protected double l1;
    protected double l2;
    protected double l1Bias;
    protected double l2Bias;
    protected IUpdater iUpdater;
    protected IUpdater biasUpdater;
    protected IWeightNoise weightNoise;
    protected GradientNormalization gradientNormalization = GradientNormalization.None; //Clipping, rescale based on l2 norm, etc
    protected double gradientNormalizationThreshold = 1.0; //Threshold for l2 and element-wise gradient clipping


    public BaseLayer(Builder builder) {
        super(builder);
        this.layerName = builder.layerName;
        this.activationFn = builder.activationFn;
        this.weightInitFn = builder.weightInitFn;
        this.biasInit = builder.biasInit;
        this.l1 = builder.l1;
        this.l2 = builder.l2;
        this.l1Bias = builder.l1Bias;
        this.l2Bias = builder.l2Bias;
        this.iUpdater = builder.iupdater;
        this.biasUpdater = builder.biasUpdater;
        this.gradientNormalization = builder.gradientNormalization;
        this.gradientNormalizationThreshold = builder.gradientNormalizationThreshold;
        this.weightNoise = builder.weightNoise;
    }

    /**
     * Reset the learning related configs of the layer to default. When instantiated with a global neural network
     * configuration the parameters specified in the neural network configuration will be used. For internal use with
     * the transfer learning API. Users should not have to call this method directly.
     */
    public void resetLayerDefaultConfig() {
        //clear the learning related params for all layers in the origConf and set to defaults
        this.setIUpdater(null);
        this.setWeightInitFn(null);
        this.setBiasInit(Double.NaN);
        this.setL1(Double.NaN);
        this.setL2(Double.NaN);
        this.setGradientNormalization(GradientNormalization.None);
        this.setGradientNormalizationThreshold(1.0);
        this.iUpdater = null;
        this.biasUpdater = null;
    }

    @Override
    public BaseLayer clone() {
        BaseLayer clone = (BaseLayer) super.clone();
        if (clone.iDropout != null) {
            clone.iDropout = clone.iDropout.clone();
        }
        return clone;
    }

    /**
     * Get the updater for the given parameter. Typically the same updater will be used for all updaters, but this is
     * not necessarily the case
     *
     * @param paramName Parameter name
     * @return IUpdater for the parameter
     */
    @Override
    public IUpdater getUpdaterByParam(String paramName) {
        if (biasUpdater != null && initializer().isBiasParam(this, paramName)) {
            return biasUpdater;
        }
        return iUpdater;
    }

    @Override
    public GradientNormalization getGradientNormalization() {
        return gradientNormalization;
    }

    @SuppressWarnings("unchecked")
    @Getter
    @Setter
    public abstract static class Builder<T extends Builder<T>> extends Layer.Builder<T> {

        /**
         * Set the activation function for the layer. This overload can be used for custom {@link IActivation}
         * instances
         *
         */
        protected IActivation activationFn = null;

        /**
         * Weight initialization scheme to use, for initial weight values
         *
         * @see IWeightInit
         */
        protected IWeightInit weightInitFn = null;

        /**
         * Bias initialization value, for layers with biases. Defaults to 0
         *
         */
        protected double biasInit = Double.NaN;

        /**
         * L1 regularization coefficient (weights only). Use {@link #l1Bias(double)} to configure the l1 regularization
         * coefficient for the bias.
         */
        protected double l1 = Double.NaN;

        /**
         * L2 regularization coefficient (weights only). Use {@link #l2Bias(double)} to configure the l2 regularization
         * coefficient for the bias.
         */
        protected double l2 = Double.NaN;

        /**
         * L1 regularization coefficient for the bias. Default: 0. See also {@link #l1(double)}
         */
        protected double l1Bias = Double.NaN;

        /**
         * L2 regularization coefficient for the bias. Default: 0. See also {@link #l2(double)}
         */
        protected double l2Bias = Double.NaN;

        /**
         * Gradient updater. For example, {@link org.nd4j.linalg.learning.config.Adam} or {@link
         * org.nd4j.linalg.learning.config.Nesterovs}
         *
         */
        protected IUpdater iupdater = null;

        /**
         * Gradient updater configuration, for the biases only. If not set, biases will use the updater as set by {@link
         * #updater(IUpdater)}
         *
         */
        protected IUpdater biasUpdater = null;

        /**
         * Gradient normalization strategy. Used to specify gradient renormalization, gradient clipping etc.
         *
         * @see GradientNormalization
         */
        protected GradientNormalization gradientNormalization = null;

        /**
         * Threshold for gradient normalization, only used for GradientNormalization.ClipL2PerLayer,
         * GradientNormalization.ClipL2PerParamType, and GradientNormalization.ClipElementWiseAbsoluteValue<br> Not used
         * otherwise.<br> L2 threshold for first two types of clipping, or absolute value threshold for last type of
         * clipping.
         */
        protected double gradientNormalizationThreshold = Double.NaN;

        /**
         * Set the weight noise (such as {@link org.deeplearning4j.nn.conf.weightnoise.DropConnect} and {@link
         * org.deeplearning4j.nn.conf.weightnoise.WeightNoise}) for this layer
         *
         */
        protected IWeightNoise weightNoise;

        /**
         * Set the activation function for the layer. This overload can be used for custom {@link IActivation}
         * instances
         *
         * @param activationFunction Activation function to use for the layer
         */
        public T activation(IActivation activationFunction) {
            this.activationFn = activationFunction;
            return (T) this;
        }

        /**
         * Set the activation function for the layer, from an {@link Activation} enumeration value.
         *
         * @param activation Activation function to use for the layer
         */
        public T activation(Activation activation) {
            return activation(activation.getActivationFunction());
        }

        /**
         * Weight initialization scheme to use, for initial weight values
         *
         * @see IWeightInit
         */
        public T weightInit(IWeightInit weightInit) {
            this.weightInitFn = weightInit;
            return (T) this;
        }

        /**
         * Weight initialization scheme to use, for initial weight values
         *
         * @see WeightInit
         */
        public T weightInit(WeightInit weightInit) {
            if (weightInit == WeightInit.DISTRIBUTION) {
                throw new UnsupportedOperationException(
                                "Not supported!, Use weightInit(Distribution distribution) instead!");
            }

            this.weightInitFn = weightInit.getWeightInitFunction();
            return (T) this;
        }

        /**
         * Set weight initialization scheme to random sampling via the specified distribution. Equivalent to: {@code
         * .weightInit(new WeightInitDistribution(distribution))}
         *
         * @param distribution Distribution to use for weight initialization
         */
        public T weightInit(Distribution distribution) {
            return weightInit(new WeightInitDistribution(distribution));
        }

        /**
         * Bias initialization value, for layers with biases. Defaults to 0
         *
         * @param biasInit Value to use for initializing biases
         */
        public T biasInit(double biasInit) {
            this.biasInit = biasInit;
            return (T) this;
        }

        /**
         * Distribution to sample initial weights from. Equivalent to: {@code .weightInit(new
         * WeightInitDistribution(distribution))}
         */
        @Deprecated
        public T dist(Distribution dist) {
            return weightInit(dist);
        }

        /**
         * L1 regularization coefficient (weights only). Use {@link #l1Bias(double)} to configure the l1 regularization
         * coefficient for the bias.
         */
        public T l1(double l1) {
            this.l1 = l1;
            return (T) this;
        }

        /**
         * L2 regularization coefficient (weights only). Use {@link #l2Bias(double)} to configure the l2 regularization
         * coefficient for the bias.
         */
        public T l2(double l2) {
            this.l2 = l2;
            return (T) this;
        }

        /**
         * L1 regularization coefficient for the bias. Default: 0. See also {@link #l1(double)}
         */
        public T l1Bias(double l1Bias) {
            this.l1Bias = l1Bias;
            return (T) this;
        }

        /**
         * L2 regularization coefficient for the bias. Default: 0. See also {@link #l2(double)}
         */
        public T l2Bias(double l2Bias) {
            this.l2Bias = l2Bias;
            return (T) this;
        }

        /**
         * Gradient updater. For example, SGD for standard stochastic gradient descent, NESTEROV for Nesterov momentum,
         * RSMPROP for RMSProp, etc.
         *
         * @see Updater
         */
        @Deprecated
        public T updater(Updater updater) {
            return updater(updater.getIUpdaterWithDefaultConfig());
        }

        /**
         * Gradient updater. For example, {@link org.nd4j.linalg.learning.config.Adam} or {@link
         * org.nd4j.linalg.learning.config.Nesterovs}
         *
         * @param updater Updater to use
         */
        public T updater(IUpdater updater) {
            this.iupdater = updater;
            return (T) this;
        }

        /**
         * Gradient updater configuration, for the biases only. If not set, biases will use the updater as set by {@link
         * #updater(IUpdater)}
         *
         * @param biasUpdater Updater to use for bias parameters
         */
        public T biasUpdater(IUpdater biasUpdater) {
            this.biasUpdater = biasUpdater;
            return (T) this;
        }

        /**
         * Gradient normalization strategy. Used to specify gradient renormalization, gradient clipping etc.
         *
         * @param gradientNormalization Type of normalization to use. Defaults to None.
         * @see GradientNormalization
         */
        public T gradientNormalization(GradientNormalization gradientNormalization) {
            this.gradientNormalization = gradientNormalization;
            return (T) this;
        }

        /**
         * Threshold for gradient normalization, only used for GradientNormalization.ClipL2PerLayer,
         * GradientNormalization.ClipL2PerParamType, and GradientNormalization.ClipElementWiseAbsoluteValue<br> Not used
         * otherwise.<br> L2 threshold for first two types of clipping, or absolute value threshold for last type of
         * clipping.
         */
        public T gradientNormalizationThreshold(double threshold) {
            this.gradientNormalizationThreshold = threshold;
            return (T) this;
        }

        /**
         * Set the weight noise (such as {@link org.deeplearning4j.nn.conf.weightnoise.DropConnect} and {@link
         * org.deeplearning4j.nn.conf.weightnoise.WeightNoise}) for this layer
         *
         * @param weightNoise Weight noise instance to use
         */
        public T weightNoise(IWeightNoise weightNoise) {
            this.weightNoise = weightNoise;
            return (T) this;
        }
    }
}

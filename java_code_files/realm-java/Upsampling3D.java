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

package org.deeplearning4j.nn.layers.convolution.upsampling;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.exception.DL4JInvalidInputException;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.AbstractLayer;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.CustomOp;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;

import java.util.Arrays;


/**
 * 3D Upsampling layer.
 * <p>
 * Used for upsampling a 3D convolution
 *
 * @author Max Pumperla
 */
@Slf4j
public class Upsampling3D extends AbstractLayer<org.deeplearning4j.nn.conf.layers.Upsampling3D> {


    public Upsampling3D(NeuralNetConfiguration conf) {
        super(conf);
    }

    public Upsampling3D(NeuralNetConfiguration conf, INDArray input) {
        super(conf, input);
    }


    @Override
    public double calcL2(boolean backpropParamsOnly) {
        return 0;
    }

    @Override
    public double calcL1(boolean backpropParamsOnly) {
        return 0;
    }

    @Override
    public Type type() {
        return Type.UPSAMPLING;
    }


    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(true);

        // FIXME: int cast
        // Assumes NCDHW order
        int miniBatch = (int) input.size(0);
        int inChannels = (int) input.size(1);
        int inD = (int) input.size(2);
        int inH = (int) input.size(3);
        int inW = (int) input.size(4);

        int[] intArgs = new int[] {1}; // 1 is channels first

        INDArray reshapedEpsilon = workspaceMgr.createUninitialized(
                ArrayType.ACTIVATION_GRAD, new int[]{miniBatch, inChannels, inD, inH, inW}, 'c');


        Gradient gradient = new DefaultGradient();

        CustomOp op = DynamicCustomOp.builder("upsampling3d_bp")
                .addIntegerArguments(intArgs)
                .addInputs(input, epsilon)
                .addOutputs(reshapedEpsilon)
                .callInplace(false)
                .build();
        Nd4j.getExecutioner().exec(op);

        reshapedEpsilon = backpropDropOutIfPresent(reshapedEpsilon);
        return new Pair<>(gradient, reshapedEpsilon);
    }

    protected int[] getSize() {
        return layerConf().getSize();
    }

    protected INDArray preOutput(boolean training, boolean forBackprop, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(false);
        applyDropOutIfNecessary(training, workspaceMgr);

        if (input.rank() != 5) {
            throw new DL4JInvalidInputException("Got rank " + input.rank()
                    + " array as input to Upsampling3DLayer with shape " + Arrays.toString(input.shape())
                    + ". Expected rank 5 array with shape "
                    + "[minibatchSize, channels, inputDepth, inputHeight, inputWidth]. "
                    + layerId());
        }

        if (preOutput != null && forBackprop) {
            return preOutput;
        }

        // FIXME: int cast
        int miniBatch = (int) input.size(0);
        int inChannels = (int) input.size(1);
        int inD = (int) input.size(2);
        int inH = (int) input.size(3);
        int inW = (int) input.size(4);

        int[] size = getSize();
        int outD = inD * size[0];
        int outH = inH * size[1];
        int outW = inW * size[2];

        int[] intArgs = new int[] {size[0], size[1], size[2], 1}; // 1 is channels first

        INDArray reshapedOutput = workspaceMgr.createUninitialized(ArrayType.ACTIVATIONS,
                new int[]{miniBatch, inChannels, outD, outH, outW}, 'c');


        CustomOp upsampling = DynamicCustomOp.builder("upsampling3d")
                .addIntegerArguments(intArgs)
                .addInputs(input)
                .addOutputs(reshapedOutput)
                .callInplace(false)
                .build();
        Nd4j.getExecutioner().exec(upsampling);

        return reshapedOutput;
    }

    @Override
    public INDArray activate(boolean training, LayerWorkspaceMgr workspaceMgr) {
        assertInputSet(false);
        applyDropOutIfNecessary(training, workspaceMgr);

        if (cacheMode == null)
            cacheMode = CacheMode.NONE;

        INDArray z = preOutput(training, false, workspaceMgr);

        // we do cache only if cache workspace exists. Skip otherwise
        if (training && cacheMode != CacheMode.NONE && workspaceMgr.hasConfiguration(ArrayType.FF_CACHE)
                && workspaceMgr.isWorkspaceOpen(ArrayType.FF_CACHE)) {
            try (MemoryWorkspace wsB = workspaceMgr.notifyScopeBorrowed(ArrayType.FF_CACHE)) {
                preOutput = z.unsafeDuplication();
            }
        }
        return z;
    }

    @Override
    public boolean isPretrainLayer() {
        return false;
    }

    @Override
    public void clearNoiseWeightParams() {
        //No op
    }

    @Override
    public Gradient gradient() {
        throw new UnsupportedOperationException("Not supported - no parameters");
    }

    @Override
    public void fit() {

    }

    @Override
    public long numParams() {
        return 0;
    }

    @Override
    public void fit(INDArray input, LayerWorkspaceMgr workspaceMgr) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public double score() {
        return 0;
    }

    @Override
    public void update(INDArray gradient, String paramType) {

    }

    @Override
    public INDArray params() {
        return null;
    }

    @Override
    public INDArray getParam(String param) {
        return params();
    }

    @Override
    public void setParams(INDArray params) {

    }

}

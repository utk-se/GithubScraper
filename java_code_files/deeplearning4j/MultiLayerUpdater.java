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

package org.deeplearning4j.nn.updater;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.Trainable;
import org.deeplearning4j.nn.api.Updater;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.HashMap;

/**
 * MultiLayerUpdater: Gradient updater for MultiLayerNetworks.
 * Expects backprop gradients for all layers to be in single Gradient object,
 * keyed by "0_b", "1_w" etc., as per MultiLayerNetwork.backward()
 *
 * @author Alex Black
 */
@Getter
@Slf4j
public class MultiLayerUpdater extends BaseMultiLayerUpdater<MultiLayerNetwork> {

    public MultiLayerUpdater(MultiLayerNetwork network) {
        this(network, null, network.getLayerWiseConfigurations().isLegacyBatchScaledL2());
    }

    public MultiLayerUpdater(MultiLayerNetwork network, INDArray updaterState, boolean legacyBatchScaledL2) {
        super(network, updaterState, legacyBatchScaledL2);

        layersByName = new HashMap<>();
        Layer[] l = network.getLayers();
        for (int i = 0; i < l.length; i++) {
            layersByName.put(String.valueOf(i), l[i]);
        }
    }

    @Override
    protected Trainable[] getOrderedLayers() {
        Layer[] layers = network.getLayers();
        Trainable[] t = new Trainable[layers.length];
        System.arraycopy(layers, 0, t, 0, layers.length);
        return t;
    }

    @Override
    protected INDArray getFlattenedGradientsView() {
        if (network.getFlattenedGradients() == null) {
            network.initGradientsView();
        }
        return network.getFlattenedGradients();
    }

    @Override
    protected INDArray getParams() {
        return network.params();
    }

    @Override
    protected boolean isMiniBatch() {
        return network.conf().isMiniBatch();
    }

    @Override
    public Updater clone() {
        return new MultiLayerUpdater(network, null, legacyBatchScaledL2);
    }
}

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

package org.deeplearning4j.earlystopping.scorecalc;

import org.deeplearning4j.earlystopping.scorecalc.base.BaseScoreCalculator;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.feedforward.autoencoder.AutoEncoder;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.evaluation.regression.RegressionEvaluation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;

/**
 * Score function for a MultiLayerNetwork or ComputationGraph with a single
 * {@link org.deeplearning4j.nn.conf.layers.AutoEncoder} layer.
 * Calculates the specified {@link RegressionEvaluation.Metric} on the layer's reconstructions.
 *
 * @author Alex Black
 */
public class AutoencoderScoreCalculator extends BaseScoreCalculator<Model> {

    protected final RegressionEvaluation.Metric metric;
    protected RegressionEvaluation evaluation;

    public AutoencoderScoreCalculator(RegressionEvaluation.Metric metric, DataSetIterator iterator){
        super(iterator);
        this.metric = metric;
    }

    @Override
    protected void reset() {
        evaluation = new RegressionEvaluation();
    }

    @Override
    protected INDArray output(Model net, INDArray input, INDArray fMask, INDArray lMask) {

        Layer l;
        if(net instanceof MultiLayerNetwork) {
            MultiLayerNetwork network = (MultiLayerNetwork)net;
            l = network.getLayer(0);
        } else {
            ComputationGraph network = (ComputationGraph)net;
            l = network.getLayer(0);
        }

        if (!(l instanceof AutoEncoder)) {
            throw new UnsupportedOperationException("Can only score networks with autoencoder layers as first layer -" +
                    " got " + l.getClass().getSimpleName());
        }
        AutoEncoder ae = (AutoEncoder) l;

        LayerWorkspaceMgr workspaceMgr = LayerWorkspaceMgr.noWorkspaces();
        INDArray encode = ae.encode(input, false, workspaceMgr);
        return ae.decode(encode, workspaceMgr);
    }

    @Override
    protected INDArray[] output(Model network, INDArray[] input, INDArray[] fMask, INDArray[] lMask) {
        return new INDArray[]{output(network, get0(input), get0(fMask), get0(lMask))};
    }

    @Override
    protected double scoreMinibatch(Model network, INDArray features, INDArray labels, INDArray fMask,
                                    INDArray lMask, INDArray output) {
        evaluation.eval(features, output);
        return 0.0; //Not used
    }

    @Override
    protected double scoreMinibatch(Model network, INDArray[] features, INDArray[] labels, INDArray[] fMask, INDArray[] lMask, INDArray[] output) {
        return scoreMinibatch(network, get0(features), get0(labels), get0(fMask), get0(lMask), get0(output));
    }

    @Override
    protected double finalScore(double scoreSum, int minibatchCount, int exampleCount) {
        return evaluation.scoreForMetric(metric);
    }

    @Override
    public boolean minimizeScore() {
        return metric.minimize();
    }
}

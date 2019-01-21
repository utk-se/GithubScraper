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

package org.deeplearning4j.ui.play;

import org.deeplearning4j.api.storage.StatsStorage;
import org.deeplearning4j.datasets.iterator.impl.IrisDataSetIterator;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.variational.GaussianReconstructionDistribution;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.ui.api.UIServer;
import org.deeplearning4j.ui.stats.StatsListener;
import org.deeplearning4j.ui.storage.InMemoryStatsStorage;
import org.junit.Ignore;
import org.junit.Test;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by Alex on 08/10/2016.
 */
@Ignore
public class TestPlayUI {

    @Test
    @Ignore
    public void testUI() throws Exception {

        StatsStorage ss = new InMemoryStatsStorage();

        PlayUIServer uiServer = (PlayUIServer) UIServer.getInstance();
        assertEquals(9000, uiServer.getPort());
        uiServer.stop();
        PlayUIServer playUIServer = new PlayUIServer();
        playUIServer.runMain(new String[] {"--uiPort", "9100", "-r", "true"});

        assertEquals(9100, playUIServer.getPort());
        playUIServer.stop();


        //        uiServer.attach(ss);
        //
        //        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
        //                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
        //                .list()
        //                .layer(0, new DenseLayer.Builder().activation(Activation.TANH).nIn(4).nOut(4).build())
        //                .layer(1, new OutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(4).nOut(3).build())
        //                .build();
        //
        //        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        //        net.init();
        //        net.setListeners(new StatsListener(ss, 3), new ScoreIterationListener(1));
        //
        //        DataSetIterator iter = new IrisDataSetIterator(150, 150);
        //
        //        for (int i = 0; i < 500; i++) {
        //            net.fit(iter);
        ////            Thread.sleep(100);
        //            Thread.sleep(100);
        //        }
        //
        ////        uiServer.stop();

        Thread.sleep(100000);
    }

    @Test
    @Ignore
    public void testUI_VAE() throws Exception {
        //Variational autoencoder - for unsupervised layerwise pretraining

        StatsStorage ss = new InMemoryStatsStorage();

        UIServer uiServer = UIServer.getInstance();
        uiServer.attach(ss);

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                        .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                        .updater(new Sgd(1e-5))
                        .list().layer(0,
                                        new VariationalAutoencoder.Builder().nIn(4).nOut(3).encoderLayerSizes(10, 11)
                                                        .decoderLayerSizes(12, 13).weightInit(WeightInit.XAVIER)
                                                        .pzxActivationFunction(Activation.IDENTITY)
                                                        .reconstructionDistribution(
                                                                        new GaussianReconstructionDistribution())
                                                        .activation(Activation.LEAKYRELU).build())
                        .layer(1, new VariationalAutoencoder.Builder().nIn(3).nOut(3).encoderLayerSizes(7)
                                        .decoderLayerSizes(8).weightInit(WeightInit.XAVIER)
                                        .pzxActivationFunction(Activation.IDENTITY)
                                        .reconstructionDistribution(new GaussianReconstructionDistribution())
                                        .activation(Activation.LEAKYRELU).build())
                        .layer(2, new OutputLayer.Builder().nIn(3).nOut(3).build())
                        .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        net.setListeners(new StatsListener(ss), new ScoreIterationListener(1));

        DataSetIterator iter = new IrisDataSetIterator(150, 150);

        for (int i = 0; i < 50; i++) {
            net.fit(iter);
            Thread.sleep(100);
        }


        Thread.sleep(100000);
    }


    @Test
    @Ignore
    public void testUIMultipleSessions() throws Exception {

        for (int session = 0; session < 3; session++) {

            StatsStorage ss = new InMemoryStatsStorage();

            UIServer uiServer = UIServer.getInstance();
            uiServer.attach(ss);

            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).list()
                    .layer(0, new DenseLayer.Builder().activation(Activation.TANH).nIn(4).nOut(4).build())
                    .layer(1, new OutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MCXENT)
                            .activation(Activation.SOFTMAX).nIn(4).nOut(3).build())
                    .build();

            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();
            net.setListeners(new StatsListener(ss), new ScoreIterationListener(1));

            DataSetIterator iter = new IrisDataSetIterator(150, 150);

            for (int i = 0; i < 20; i++) {
                net.fit(iter);
                Thread.sleep(100);
            }
        }


        Thread.sleep(1000000);
    }
    
    @Test
    @Ignore
    public void testUISequentialSessions() throws Exception {
        UIServer uiServer = UIServer.getInstance();
        StatsStorage ss = null;
        for (int session = 0; session < 3; session++) {
            
            if (ss != null) {
                uiServer.detach(ss);
            }
            ss = new InMemoryStatsStorage();
            uiServer.attach(ss);

            int numInputs = 4;
            int outputNum = 3;
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .activation(Activation.TANH)
                .weightInit(WeightInit.XAVIER)
                .updater(new Sgd(0.03))
                .l2(1e-4)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(numInputs).nOut(3)
                        .build())
                .layer(1, new DenseLayer.Builder().nIn(3).nOut(3)
                        .build())
                .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .activation(Activation.SOFTMAX)
                        .nIn(3).nOut(outputNum).build())
                .build();

            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();
            net.setListeners(new StatsListener(ss), new ScoreIterationListener(1));

            DataSetIterator iter = new IrisDataSetIterator(150, 150);

            for (int i = 0; i < 1000; i++) {
                net.fit(iter);
            }
            Thread.sleep(5000);
        }


        Thread.sleep(1000000);
    }

    @Test
    @Ignore
    public void testUICompGraph() throws Exception {

        StatsStorage ss = new InMemoryStatsStorage();

        UIServer uiServer = UIServer.getInstance();
        uiServer.attach(ss);

        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder().graphBuilder().addInputs("in")
                        .addLayer("L0", new DenseLayer.Builder().activation(Activation.TANH).nIn(4).nOut(4).build(),
                                        "in")
                        .addLayer("L1", new OutputLayer.Builder().lossFunction(LossFunctions.LossFunction.MCXENT)
                                        .activation(Activation.SOFTMAX).nIn(4).nOut(3).build(), "L0")
                        .setOutputs("L1").build();

        ComputationGraph net = new ComputationGraph(conf);
        net.init();

        net.setListeners(new StatsListener(ss), new ScoreIterationListener(1));

        DataSetIterator iter = new IrisDataSetIterator(150, 150);

        for (int i = 0; i < 100; i++) {
            net.fit(iter);
            Thread.sleep(100);
        }

        Thread.sleep(100000);
    }

    @Test
    public void testUIAttachDetach() throws Exception {
        StatsStorage ss = new InMemoryStatsStorage();

        UIServer uiServer = UIServer.getInstance();
        uiServer.attach(ss);
        assertFalse(uiServer.getStatsStorageInstances().isEmpty());
        uiServer.detach(ss);
        assertTrue(uiServer.getStatsStorageInstances().isEmpty());
    }
}

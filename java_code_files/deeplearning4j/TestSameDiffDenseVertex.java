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

package org.deeplearning4j.nn.layers.samediff;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.BaseDL4JTest;
import org.deeplearning4j.TestUtils;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.samediff.testlayers.SameDiffDenseVertex;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.Test;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.Map;

import static org.junit.Assert.assertEquals;

@Slf4j
public class TestSameDiffDenseVertex extends BaseDL4JTest {

    @Test
    public void testSameDiffDenseVertex() {

        int nIn = 3;
        int nOut = 4;

        for (boolean workspaces : new boolean[]{false, true}) {

            for (int minibatch : new int[]{5, 1}) {

                Activation[] afns = new Activation[]{
                        Activation.TANH,
                        Activation.SIGMOID
                };

                for (Activation a : afns) {
                    log.info("Starting test - " + a + " - minibatch " + minibatch + ", workspaces: " + workspaces);
                    ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                            .trainingWorkspaceMode(workspaces ? WorkspaceMode.ENABLED : WorkspaceMode.NONE)
                            .inferenceWorkspaceMode(workspaces ? WorkspaceMode.ENABLED : WorkspaceMode.NONE)
                            .updater(new Sgd(0.0))
                            .graphBuilder()
                            .addInputs("in")
                            .addVertex("0", new SameDiffDenseVertex(nIn, nOut, a, WeightInit.XAVIER), "in")
                            .addVertex("1", new SameDiffDenseVertex(nOut, nOut, a, WeightInit.XAVIER), "0")
                            .layer("2", new OutputLayer.Builder().nIn(nOut).nOut(nOut).activation(Activation.SOFTMAX)
                                    .lossFunction(LossFunctions.LossFunction.MCXENT).build(), "1")
                            .setOutputs("2")
                            .build();

                    ComputationGraph netSD = new ComputationGraph(conf);
                    netSD.init();

                    ComputationGraphConfiguration conf2 = new NeuralNetConfiguration.Builder()
                            .trainingWorkspaceMode(workspaces ? WorkspaceMode.ENABLED : WorkspaceMode.NONE)
                            .inferenceWorkspaceMode(workspaces ? WorkspaceMode.ENABLED : WorkspaceMode.NONE)
//                            .updater(new Sgd(1.0))
                            .updater(new Sgd(0.0))
                            .graphBuilder()
                            .addInputs("in")
                            .addLayer("0", new DenseLayer.Builder().nIn(nIn).nOut(nOut).activation(a).build(), "in")
                            .addLayer("1", new DenseLayer.Builder().nIn(nOut).nOut(nOut).activation(a).build(), "0")
                            .layer("2", new OutputLayer.Builder().nIn(nOut).nOut(nOut).activation(Activation.SOFTMAX)
                                    .lossFunction(LossFunctions.LossFunction.MCXENT).build(), "1")
                            .setOutputs("2")
                            .build();

                    ComputationGraph netStandard = new ComputationGraph(conf2);
                    netStandard.init();

                    netSD.params().assign(netStandard.params());

                    //Check params:
                    assertEquals(netStandard.params(), netSD.params());
                    assertEquals(netStandard.paramTable(), netSD.paramTable());

                    INDArray in = Nd4j.rand(minibatch, nIn);
                    INDArray l = TestUtils.randomOneHot(minibatch, nOut, 12345);

                    INDArray outSD = netSD.outputSingle(in);
                    INDArray outStd = netStandard.outputSingle(in);

                    assertEquals(outStd, outSD);

                    netSD.setInput(0, in);
                    netStandard.setInput(0, in);
                    netSD.setLabels(l);
                    netStandard.setLabels(l);

                    netSD.computeGradientAndScore();
                    netStandard.computeGradientAndScore();

                    Gradient gSD = netSD.gradient();
                    Gradient gStd = netStandard.gradient();

                    Map<String, INDArray> m1 = gSD.gradientForVariable();
                    Map<String, INDArray> m2 = gStd.gradientForVariable();

                    assertEquals(m2.keySet(), m1.keySet());

                    for (String s : m1.keySet()) {
                        INDArray i1 = m1.get(s);
                        INDArray i2 = m2.get(s);

                        assertEquals(s, i2, i1);
                    }

                    assertEquals(gStd.gradient(), gSD.gradient());

                    System.out.println("========================================================================");

                    //Sanity check: different minibatch size
                    in = Nd4j.rand(2 * minibatch, nIn);
                    l = TestUtils.randomOneHot(2 * minibatch, nOut, 12345);
                    netSD.setInputs(in);
                    netStandard.setInputs(in);
                    netSD.setLabels(l);
                    netStandard.setLabels(l);

                    netSD.computeGradientAndScore();
                    netStandard.computeGradientAndScore();
                    assertEquals(netStandard.gradient().gradient(), netSD.gradient().gradient());

                    //Check training:
                    DataSet ds = new DataSet(in, l);
                    for( int i=0; i<3; i++ ){
                        netSD.fit(ds);
                        netStandard.fit(ds);

                        assertEquals(netStandard.paramTable(), netSD.paramTable());
                        assertEquals(netStandard.params(), netSD.params());
                        assertEquals(netStandard.getFlattenedGradients(), netSD.getFlattenedGradients());
                    }

                    //Check serialization:
                    ComputationGraph loaded = TestUtils.testModelSerialization(netSD);

                    outSD = loaded.outputSingle(in);
                    outStd = netStandard.outputSingle(in);
                    assertEquals(outStd, outSD);
                }
            }
        }
    }
}

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

package org.deeplearning4j;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.datasets.iterator.impl.MnistDataSetIterator;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.BatchNormalization;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.SubsamplingLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.executioner.OpExecutioner;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@Slf4j
public class TestDataTypes extends BaseDL4JTest {

    private static DataType typeBefore;

    @BeforeClass
    public static void beforeClass(){
        typeBefore = Nd4j.dataType();
    }

    @AfterClass
    public static void afterClass(){
        Nd4j.setDataType(typeBefore);
    }

    @Override
    public void beforeTest(){
        Nd4j.getExecutioner().setProfilingMode(getProfilingMode());
        Nd4j.setDataType(DataType.HALF);
    }

    @Override
    public OpExecutioner.ProfilingMode getProfilingMode(){
        return OpExecutioner.ProfilingMode.NAN_PANIC;
    }

    @Test
    public void testDataTypesSimple() throws Exception {

        Map<DataType, INDArray> outMapTrain = new HashMap<>();
        Map<DataType, INDArray> outMapTest = new HashMap<>();
        for(DataType type : new DataType[]{DataType.HALF, DataType.FLOAT, DataType.DOUBLE}) {
            log.info("Starting test: {}", type);
            Nd4j.setDataType(type);
            assertEquals(type, Nd4j.dataType());

            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .convolutionMode(ConvolutionMode.Same)
                    .activation(Activation.TANH)
                    .seed(12345)
                    .weightInit(WeightInit.XAVIER)
                    .list()
                    .layer(new ConvolutionLayer.Builder().kernelSize(2, 2).stride(1, 1).padding(0, 0).nOut(3).build())
                    .layer(new SubsamplingLayer.Builder().kernelSize(2, 2).stride(1, 1).padding(0, 0).build())
                    .layer(new BatchNormalization())
                    .layer(new ConvolutionLayer.Builder().kernelSize(2, 2).stride(1, 1).padding(0, 0).nOut(3).build())
                    .layer(new OutputLayer.Builder().nOut(10).activation(Activation.SOFTMAX).lossFunction(LossFunctions.LossFunction.MCXENT).build())
                    .setInputType(InputType.convolutionalFlat(28, 28, 1))
                    .build();

            MultiLayerNetwork net = new MultiLayerNetwork(conf);
            net.init();


            Field f1 = org.deeplearning4j.nn.layers.convolution.ConvolutionLayer.class.getDeclaredField("helper");
            f1.setAccessible(true);

            Field f2 = org.deeplearning4j.nn.layers.convolution.subsampling.SubsamplingLayer.class.getDeclaredField("helper");
            f2.setAccessible(true);

            Field f3 = org.deeplearning4j.nn.layers.normalization.BatchNormalization.class.getDeclaredField("helper");
            f3.setAccessible(true);

            assertNotNull(f1.get(net.getLayer(0)));
            assertNotNull(f2.get(net.getLayer(1)));
            assertNotNull(f3.get(net.getLayer(2)));
            assertNotNull(f1.get(net.getLayer(3)));

            DataSet ds = new MnistDataSetIterator(32, true, 12345).next();

            //Simple sanity checks:
            //System.out.println("STARTING FIT");
            net.fit(ds);
            net.fit(ds);

            //System.out.println("STARTING OUTPUT");
            INDArray outTrain = net.output(ds.getFeatures(), false);
            INDArray outTest = net.output(ds.getFeatures(), true);

            outMapTrain.put(type, outTrain.castTo(DataType.DOUBLE));
            outMapTest.put(type, outTest.castTo(DataType.DOUBLE));
        }

        Nd4j.setDataType(DataType.DOUBLE);
        INDArray fp64Train = outMapTrain.get(DataType.DOUBLE);
        INDArray fp32Train = outMapTrain.get(DataType.FLOAT).castTo(DataType.DOUBLE);
        INDArray fp16Train = outMapTrain.get(DataType.HALF).castTo(DataType.DOUBLE);

        assertTrue(fp64Train.equalsWithEps(fp32Train, 1e-3));
        assertTrue(fp64Train.equalsWithEps(fp16Train, 1e-2));


    }


}

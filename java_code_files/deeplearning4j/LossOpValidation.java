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

package org.nd4j.autodiff.opvalidation;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.nd4j.OpValidationSuite;
import org.nd4j.autodiff.loss.LossReduce;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.autodiff.validation.OpValidation;
import org.nd4j.autodiff.validation.TestCase;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.CustomOp;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.random.impl.BernoulliDistribution;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@Slf4j
public class LossOpValidation extends BaseOpValidation {
    public LossOpValidation(Nd4jBackend backend) {
        super(backend);
    }

    public static final Set<String> NO_BP_YET = new HashSet<>();
    static {
        NO_BP_YET.addAll(Arrays.asList("hinge", "huber", "l2_loss", "poisson", "mpwse"));
    }

    @Test
    public void testLoss2d() {
        OpValidationSuite.ignoreFailing();  //2019/01/17 - Some passing, some not yet implemented, some issues: Issue 17 https://github.com/deeplearning4j/deeplearning4j/issues/6958

        Nd4j.getRandom().setSeed(12345);

        List<String> failed = new ArrayList<>();

        int totalRun = 0;
        for (String fn : new String[]{
                "absdiff", "cosine", "hinge", "huber", "log", "mse",
                "sigmoidxent", "sigmoidxent_smooth", "softmaxxent", "softmaxxent_smooth", "mpwse",
                "sparsesoftmax"}) {


            for(String weights : new String[]{"none", "scalar", "perExample", "perOutput"}) {
                if((fn.startsWith("softmax") || fn.equals("cosine")) && weights.equals("perOutput"))
                    continue;   //Skip this combination (not possible)

                if(fn.equals("mpwse") && weights.equals("perOutput"))
                    continue;   //MPWSE only supports scalar, none, or per example weights

                for (LossReduce reduction : LossReduce.values()) {

                    if(fn.equals("mpwse") && (reduction != LossReduce.MEAN_BY_WEIGHT || weights.equals("perOutput"))) //LossReduce.MEAN_BY_NONZERO_WEIGHT_COUNT)
                        continue;   //MPWSE only provides scalar output - i.e., no other reduction modes. And only none/scalar/per-example weights

                    if((fn.equals("softmaxxent") || fn.equals("softmaxxent_smooth")) && reduction == LossReduce.NONE)
                        continue;       //Combination not supported (doesn't make sense)

                    if(fn.equals("sparsesoftmax") && (!weights.equals("none") || reduction != LossReduce.SUM) )
                        continue;   //sparse softmax doesn't support weights or reduction confic

                    SameDiff sd = SameDiff.create();

                    int nOut = 4;
                    int minibatch = 10;
                    SDVariable predictions = sd.var("in", DataType.DOUBLE, -1, nOut);
                    SDVariable labels;
                    if("sparsesoftmax".equalsIgnoreCase(fn)){
                        labels = sd.var("labels", DataType.INT, -1);
                    } else {
                        //ALl other loss functions
                        labels = sd.var("labels", DataType.DOUBLE, -1, nOut);
                    }

                    SDVariable w;
                    INDArray wArrBroadcast;
                    switch (weights){
                        case "none":
                            w = null;
                            wArrBroadcast = Nd4j.ones(DataType.DOUBLE, minibatch, nOut);
                            break;
                        case "scalar":
                            w = sd.var("weights", Nd4j.scalar(DataType.DOUBLE, 1.0));
                            wArrBroadcast = Nd4j.valueArrayOf(minibatch, nOut, 1.0).castTo(DataType.DOUBLE);
                            break;
                        case "perExample":
                            INDArray wpe = Nd4j.create(new double[]{0,0,1,1,2,2,3,3,4,4});
                            if(!fn.equals("softmaxxent") && !fn.equals("softmaxxent_smooth")){
                                //Softmaxxent only supports rank 1 not rank 2??
                                wpe = wpe.reshape(minibatch, 1);
                            }
                            w = sd.var("weights", wpe);
                            wArrBroadcast = Nd4j.create(DataType.DOUBLE, minibatch, nOut).addiColumnVector(w.getArr());
                            break;
                        case "perOutput":
                            w = sd.var("weights", Nd4j.create(new double[][]{
                                    {0,0,0,0}, {0,0,1,1}, {1,1,0,0}, {1,1,1,1}, {1,1,1,1},
                                    {2,2,2,2}, {2,2,2,2}, {2,2,2,2}, {2,2,2,2}, {2,2,2,2}}));
                            wArrBroadcast = w.getArr();
                            break;
                        default:
                            throw new RuntimeException();
                    }
                    INDArray wArr = w == null ? Nd4j.scalar(DataType.DOUBLE, 1.0) : w.getArr();


                    INDArray predictionsArr = Nd4j.randn(DataType.DOUBLE, minibatch, nOut);
                    INDArray labelsArr = Nd4j.randn(DataType.DOUBLE, minibatch, nOut);

                    INDArray expOut = null;
                    SDVariable loss = null;
                    switch (fn) {
                        case "absdiff":
                            expOut = Transforms.abs(predictionsArr.sub(labelsArr));
                            loss = sd.lossAbsoluteDifference("loss", labels, predictions, w, reduction);
                            break;
                        case "cosine":
                            //Cosine _similarity_: dot(a,b)/(l2Norm(a) * l2Norm(b))
                            //Cosine distance = 1 - cosineSimilarity
                            //NOTE: both we and TF assume the inputs are normalized
                            predictionsArr.diviColumnVector(predictionsArr.norm2(1));
                            labelsArr.diviColumnVector(labelsArr.norm2(1));
                            expOut = predictionsArr.mul(labelsArr).sum(1).rsub(1.0);
                            loss = sd.lossCosineDistance("loss", labels, predictions, w, reduction, 1);
                            break;
                        case "hinge":
                            //0 or 1 labels, but -1 or 1 when calculating loss
                            //L = max(0, 1 - prediction * label)
                            Nd4j.getExecutioner().exec(new BernoulliDistribution(labelsArr, 0.5));
                            INDArray labelMinusOneToOne = labelsArr.mul(2).subi(1);
                            expOut = Transforms.max(predictionsArr.mul(labelMinusOneToOne).rsubi(1), 0);
                            loss = sd.lossHinge("loss", labels, predictions, w, reduction);
                            break;
                        case "huber":
                            //https://en.wikipedia.org/wiki/Huber_loss
                            double delta = 1.0;
                            INDArray absDiff = Transforms.abs(labelsArr.sub(predictionsArr));
                            INDArray diff = labelsArr.sub(predictionsArr);
                            INDArray lte = absDiff.lte(delta).castTo(DataType.DOUBLE);
                            INDArray gt = absDiff.gt(delta).castTo(DataType.DOUBLE);
                            expOut = diff.mul(diff).mul(0.5).muli(lte);
                            expOut.addi(absDiff.mul(delta).subi(0.5 * delta * delta).mul(gt));
                            loss = sd.lossHuber("loss", labels, predictions, w, reduction, delta);
                            break;
                        case "log":
                            double eps = 1e-7;
                            //Loss loss aka binary cross entropy loss
                            //Labels are random bernoulli
                            Nd4j.getExecutioner().exec(new BernoulliDistribution(labelsArr, 0.5));
                            predictionsArr = Nd4j.rand(predictionsArr.shape());
                            INDArray logP = Transforms.log(predictionsArr.add(eps), true);
                            INDArray log1p = Transforms.log(predictionsArr.rsub(1.0).add(eps), true);
                            expOut = labelsArr.mul(logP).addi(labelsArr.rsub(1).mul(log1p)).negi();
                            loss = sd.lossLog("loss", labels, predictions, w, reduction, eps);
                            break;
                        case "mse":
                            //To match TF, this is actually sum of squares - 1/numExamples (prediction-label)^2
                            INDArray sqDiff = labelsArr.sub(predictionsArr);
                            sqDiff.muli(sqDiff);
                            expOut = sqDiff;
                            loss = sd.lossMeanSquaredError("loss", labels, predictions, w, reduction);
                            break;
                        case "sigmoidxent_smooth":  //Sigmoid xent with label smoothing
                        case "sigmoidxent":
                            //-1/numExamples * (label * log(p) + (1-label) * log(1-p))
                            Nd4j.getExecutioner().exec(new BernoulliDistribution(labelsArr, 0.5));
                            double lblSmoothing = fn.equals("sigmoidxent_smooth") ? 0.3 : 0.0;
                            INDArray labelArrCopy = labelsArr.dup();
                            if (fn.equals("sigmoidxent_smooth")) {
                                labelArrCopy.muli(1.0 - lblSmoothing).addi(0.5 * lblSmoothing);
                            }

                            INDArray onePlusExpNegX = Transforms.log(Transforms.exp(predictionsArr.neg()).add(1.0));
                            expOut = predictionsArr.mul(labelArrCopy.rsub(1.0)).add(onePlusExpNegX);

                            loss = sd.lossSigmoidCrossEntropy("loss", labels, predictions, w, reduction, lblSmoothing);
                            break;
                        case "softmaxxent":
                        case "softmaxxent_smooth":
                            //Same as negative log likelihood, but apply softmax on predictions first: For singe example, -sum_outputs label_i * log(p_i)
                            //Labels are random one-hot
                            //Note that output is shape [minibatch] for NONE reduction, or scalar otherwise
                            INDArray softmaxPredictions = Transforms.softmax(predictionsArr, true);
                            labelsArr.assign(0);
                            for (int i = 0; i < labelsArr.size(0); i++) {
                                labelsArr.putScalar(i, i % labelsArr.size(1), 1.0);
                            }
                            double lblSmooth2 = fn.equals("softmaxxent_smooth") ? 0.1 : 0.0;
                            INDArray labelsArrCopy = labelsArr.dup();
                            if (fn.equals("softmaxxent_smooth")) {
                                labelsArrCopy.muli(1.0 - lblSmooth2).addi(lblSmooth2 / labelsArrCopy.size(1));
                            }
                            INDArray logP2 = Transforms.log(softmaxPredictions, true);
                            expOut = labelsArrCopy.mul(logP2).negi().sum(1);
                            loss = sd.lossSoftmaxCrossEntropy("loss", labels.castTo(DataType.INT), predictions, w, reduction, lblSmooth2);
                            break;
                        case "mpwse":
                            expOut = Nd4j.create(labelsArr.size(0));
                            int pairCount = 0;
                            for( int i=0; i<labelsArr.size(0); i++ ){
                                for( int j=0; j<labelsArr.size(1); j++){
                                    for(int k=j+1; k<labelsArr.size(1); k++){
                                        double d1 = predictionsArr.getDouble(i, j);
                                        double d2 = predictionsArr.getDouble(i, k);
                                        double d3 = labelsArr.getDouble(i, j);
                                        double d4 = labelsArr.getDouble(i, k);
                                        double add = ((d1-d2)-(d3-d4));
                                        add *= add;
                                        expOut.putScalar(i, expOut.getDouble(i) + add);
                                        if(i == 0)
                                            pairCount++;
                                    }
                                }
                            }
                            loss = sd.lossMeanPairwiseSquaredError("loss", labels, predictions, w);
                            break;
                        case "sparsesoftmax":
                            labelsArr = Nd4j.create(DataType.INT, minibatch);
                            INDArray oneHot = Nd4j.create(DataType.DOUBLE, minibatch, nOut);
                            for( int i=0; i<minibatch; i++ ){
                                labelsArr.putScalar(i, i%nOut);
                                oneHot.putScalar(i, i%nOut, 1.0);
                            }

                            INDArray softmaxPredictions2 = Transforms.softmax(predictionsArr, true);
                            INDArray logP2_2 = Transforms.log(softmaxPredictions2, true);
                            expOut = oneHot.mul(logP2_2).negi().sum(1);

                            loss = sd.lossSparseSoftmaxCrossEntropy(predictions, labels).sum("loss");
                            break;

                        default:
                            throw new RuntimeException();
                    }

                    switch (weights){
                        case "none":    //No changes
                            break;
                        case "scalar":
                            expOut.muli(wArr.getDouble(0));
                            break;
                        case "perExample":
                            expOut.muliColumnVector(wArr);
                            break;
                        case "perOutput":
                            expOut.muli(wArr);
                            break;
                        default:
                            throw new RuntimeException();
                    }

                    INDArray expOutBefore = expOut;
                    switch (reduction) {
                        case SUM:
                            expOut = expOut.sum().reshape();
                            break;
                        case MEAN_BY_WEIGHT:
                            if((fn.startsWith("softmax") || fn.equals("cosine"))){
                                //1d output, not 2d
                                expOut = expOut.sum().divi(wArrBroadcast.getColumn(0).sumNumber().doubleValue());
                            } else {
                                expOut = expOut.sum().divi(wArrBroadcast.sumNumber().doubleValue());
                            }
                            break;
                        case MEAN_BY_NONZERO_WEIGHT_COUNT:
                            if((fn.startsWith("softmax") || fn.equals("cosine"))) {
                                //1d output, not 2d
                                int countNonZero = wArrBroadcast.getColumn(0).neq(0.0).castTo(DataType.DOUBLE).sumNumber().intValue();
                                expOut = expOut.sum().divi(countNonZero);
                            } else {
                                int countNonZero = wArrBroadcast.neq(0.0).castTo(DataType.DOUBLE).sumNumber().intValue();
                                expOut = expOut.sum().divi(countNonZero);
                            }
                            break;
                    }


                    String msg = "test: " + fn + ", reduction=" + reduction + ", weights=" + weights;
                    log.info("*** Starting test: " + msg);


                    sd.associateArrayWithVariable(predictionsArr, predictions);
                    sd.associateArrayWithVariable(labelsArr, labels);

                    if(reduction == LossReduce.NONE){
                        //Sum to make scalar output for gradient check...
                        loss = loss.sum();
                    }

                    boolean doGradCheck = true;
                    if (OpValidationSuite.IGNORE_FAILING && NO_BP_YET.contains(fn)) {
                        log.warn("--- Skipping gradient check for: {} ---", fn);
                        doGradCheck = false;
                    }

                    TestCase tc = new TestCase(sd)
                            .expectedOutput("loss", expOut)
                            .gradientCheck(doGradCheck)
                            .testFlatBufferSerialization(TestCase.TestSerialization.BOTH);

                    if(fn.equals("sparsesoftmax")){
                        tc.gradCheckSkipVariables("labels");
                    }

                    String error;
                    try {
                        error = OpValidation.validate(tc);
                    } catch (Throwable t){
                        log.error("Failed: {}", msg, t);
                        error = msg + ": " + t.getMessage();
                    }
                    if (error != null) {
                        failed.add(msg + ": " + error);
                    }
                    totalRun++;
                }
            }
        }

        assertEquals(failed.size() + " of " + totalRun + " failed: " + failed.toString(), 0, failed.size());
    }


    @Test
    public void testCosineDistance(){
        INDArray arr = Nd4j.create(new double[][]{{-0.3, -0.2, -0.1}, {0, 0.1, 0.2}});
        INDArray label = Nd4j.create(new double[][]{{1.0, 2.0, 3.0}, {-1.0, 2.0, 1.0}});
        INDArray w = Nd4j.create(new double[][]{{0},{1}});
        INDArray out = Nd4j.scalar(0.0);

        CustomOp op = DynamicCustomOp.builder("cosine_distance_loss")
                .addInputs(arr, w, label)
                .addOutputs(out)
                .addIntegerArguments(2, 1) //weighted mean, dimension 1
                .build();
        Nd4j.getExecutioner().exec(op);

        INDArray exp = Nd4j.scalar(0.6);    //https://github.com/deeplearning4j/deeplearning4j/issues/6532
        assertEquals(exp, out);
    }

    @Test
    public void testL2Loss(){

        for( int rank=0; rank<=3; rank++ ){
            long[] shape;
            switch (rank){
                case 0:
                    shape = new long[0];
                    break;
                case 1:
                    shape = new long[]{5};
                    break;
                case 2:
                    shape = new long[]{3,4};
                    break;
                case 3:
                    shape = new long[]{2,3,4};
                    break;
                case 4:
                    shape = new long[]{2,3,2,3};
                    break;
                default:
                    throw new RuntimeException();
            }
            INDArray arr = Nd4j.rand(DataType.DOUBLE, shape);

            SameDiff sd = SameDiff.create();
            SDVariable in = sd.var("v", arr);
            SDVariable loss = sd.lossL2("loss", in);

            INDArray exp = arr.mul(arr).sum().muli(0.5);

            TestCase tc = new TestCase(sd)
                    .expectedOutput("loss", exp)
                    .gradientCheck(true)
                    .testFlatBufferSerialization(TestCase.TestSerialization.BOTH);

            String err = OpValidation.validate(tc);
            assertNull(err);
        }
    }
}

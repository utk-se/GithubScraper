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

package org.nd4j.autodiff.samediff;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.nd4j.OpValidationSuite;
import org.nd4j.autodiff.samediff.impl.DefaultSameDiffConditional;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.blas.params.MMulTranspose;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.impl.layers.ExternalErrorsFunction;
import org.nd4j.linalg.api.ops.impl.layers.Linear;
import org.nd4j.linalg.api.ops.impl.layers.convolution.config.Conv2DConfig;
import org.nd4j.linalg.api.ops.impl.layers.convolution.config.LocalResponseNormalizationConfig;
import org.nd4j.linalg.api.ops.impl.reduce3.ManhattanDistance;
import org.nd4j.linalg.api.ops.impl.shape.tensorops.TensorArray;
import org.nd4j.linalg.api.ops.impl.transforms.any.IsMax;
import org.nd4j.linalg.api.ops.impl.transforms.comparison.OldMax;
import org.nd4j.linalg.api.ops.impl.transforms.comparison.OldMin;
import org.nd4j.linalg.api.ops.impl.transforms.custom.*;
import org.nd4j.linalg.api.ops.random.impl.BernoulliDistribution;
import org.nd4j.linalg.checkutil.NDArrayCreationUtil;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.nativeblas.NativeOpsHolder;
import org.nd4j.weightinit.impl.OneInitScheme;
import org.nd4j.weightinit.impl.UniformInitScheme;
import org.nd4j.weightinit.impl.ZeroInitScheme;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;
import static org.nd4j.linalg.indexing.NDArrayIndex.all;

/**
 * Created by agibsonccc on 4/11/17.
 */
@Slf4j
public class SameDiffTests {
    private DataType initialType;

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();


    @Before
    public void before() {
        Nd4j.create(1);
        initialType = Nd4j.dataType();

        Nd4j.setDataType(DataType.DOUBLE);
        Nd4j.getRandom().setSeed(123);
    }

    @After
    public void after() {
        Nd4j.setDataType(initialType);

        NativeOpsHolder.getInstance().getDeviceNativeOps().enableDebugMode(false);
        NativeOpsHolder.getInstance().getDeviceNativeOps().enableVerboseMode(false);
    }

    public Map<String, INDArray> variablesForInput() {
        INDArray inputs = Nd4j.create(new double[][]{
                {0.52, 1.12, 0.77},
                {0.88, -1.08, 0.15},
                {0.52, 0.06, -1.30},
                {0.74, -2.49, 1.39}
        });

        INDArray labels = Nd4j.create(new double[]{1, 1, 0, 1}).reshape(4, 1);

        INDArray weights = Nd4j.zeros(3, 1);

        Map<String, INDArray> inputMap = new HashMap<>();
        inputMap.put("x", inputs);
        inputMap.put("w", weights);
        inputMap.put("y", labels);
        return inputMap;
    }

    @Test
    public void testVariableNaming_1() {
        val sd = SameDiff.create();

        val input = sd.var("inp", new long[]{2, 3});

        val nodeA = sd.square(input);
        val nodeB = sd.square(nodeA);

        sd.associateArrayWithVariable(Nd4j.create(new double[]{1, 2, 3, 4, 5, 6}, new long[]{2, 3}), input);

        sd.execAndEndResult();

        nodeA.isPlaceHolder();
    }


    @Test
    public void testAddArgsAndOutput() {
        SameDiff sameDiff = SameDiff.create();
        val varOne = sameDiff.var("one", Nd4j.ones(2));
    }

    @Test
    public void testMseBackwards() {

        SameDiff sd = SameDiff.create();

        int nOut = 4;
        int minibatch = 3;
        SDVariable input = sd.var("in", new long[]{-1, nOut});
        SDVariable label = sd.var("label", new long[]{-1, nOut});

        SDVariable diff = input.sub(label);
        SDVariable sqDiff = diff.mul(diff);
        SDVariable msePerEx = sd.mean("msePerEx", sqDiff, 1);
        SDVariable avgMSE = sd.mean("loss", msePerEx, 0);

        INDArray inputArr = Nd4j.rand(minibatch, nOut);
        INDArray labelArr = Nd4j.rand(minibatch, nOut);

        sd.associateArrayWithVariable(inputArr, input);
        sd.associateArrayWithVariable(labelArr, label);

        INDArray result = sd.execAndEndResult();
        assertEquals(1, result.length());

        sd.execBackwards(Collections.emptyMap());
    }

    @Test
    public void testEvalVariable() {
        SameDiff sameDiff = SameDiff.create();
        INDArray ones = Nd4j.ones(4);
        INDArray twos = ones.add(ones);
        SDVariable inputOne = sameDiff.var("inputone", ones);
        SDVariable inputResult = inputOne.add("extravarname", inputOne);
        assertEquals(twos, inputResult.eval());
    }


    @Test
    public void testSum() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1, 4, 4, DataType.FLOAT)).reshape(1,4);
        SDVariable x = sameDiff.var("x", arr);
        SDVariable result = sameDiff.sum(x, 1); //[1,4].sum(1) == [1,1]

        sameDiff.exec(Collections.emptyMap(), sameDiff.outputs());

        INDArray exp = Nd4j.scalar(arr.sumNumber().floatValue());
        INDArray resultArr = result.getArr();
        assertEquals(exp, resultArr);
    }

    @Test
    public void testSaveWriteWithTrainingConfig() throws Exception {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1, 4, 4));
        SDVariable x = sameDiff.var("x", arr);
        SDVariable result = sameDiff.sum(x, 1); //[1,4].sum(1) == [1,1]
        TrainingConfig trainingConfig = TrainingConfig.builder()
                .dataSetFeatureMapping("x")
                .dataSetLabelMapping(result.getVarName())
                .updater(new Nesterovs()).build();
        sameDiff.setTrainingConfig(trainingConfig);
        sameDiff.initializeTraining();
        File newFile = folder.newFile();
        try(BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(newFile))) {
            sameDiff.saveWithTrainingConfig(bufferedOutputStream);
        }

        SameDiff sameDiff1 = SameDiff.restoreFromTrainingConfigZip(newFile);
        assertEquals(sameDiff.getTrainingConfig().getUpdater(),
                sameDiff1.getTrainingConfig().getUpdater());
        assertEquals(sameDiff.getUpdaterState(),sameDiff1.getUpdaterState());

        sameDiff.saveWithTrainingConfig(newFile);
        sameDiff1 = SameDiff.restoreFromTrainingConfigZip(newFile);
        assertEquals(sameDiff.getTrainingConfig().getUpdater(),
                sameDiff1.getTrainingConfig().getUpdater());
        assertEquals(sameDiff.getUpdaterState(),sameDiff1.getUpdaterState());

    }

    @Test
    public void testAddEval() {
        SameDiff sameDiff = SameDiff.create();
        INDArray x = Nd4j.scalar(1.0);
        INDArray y = Nd4j.scalar(2.0);
        SDVariable xVar = sameDiff.placeHolder("x",DataType.DOUBLE, 1,1);
        SDVariable yVar = sameDiff.placeHolder("y",DataType.DOUBLE, 1,1);
        SDVariable output = xVar.add(yVar);
        Map<String,INDArray> m = new HashMap<>();
        m.put("x", x);
        m.put("y", y);
        INDArray out = sameDiff.exec(m, Collections.singletonList(output.getVarName())).get(output.getVarName());
        INDArray outputAssertion = x.add(y);
        assertEquals(outputAssertion,out);
    }

    @Test
    public void testWeightedXentWithLogits() {
        SameDiff sameDiff = SameDiff.create();
        INDArray targets = Nd4j.create(new long[]{1, 5});
        INDArray inputs = Nd4j.create(new long[]{1, 5});
        INDArray weights = Nd4j.create(new long[]{1, 5});

        SDVariable sdInputs = sameDiff.var("inputs", inputs);
        SDVariable sdWeights = sameDiff.var("weights", weights);
        SDVariable sdTargets = sameDiff.var("targets", targets);

        SDVariable res = sameDiff.weightedCrossEntropyWithLogits(sdTargets, sdInputs, sdWeights);
        sameDiff.exec(Collections.emptyMap(), sameDiff.outputs());

        INDArray resultArray = res.getArr();
        assertArrayEquals(new long[]{1, 5}, res.getShape());
    }

    @Test
    public void testMseForward() {

        SameDiff sd = SameDiff.create();

        int nOut = 4;
        int minibatch = 3;
        SDVariable input = sd.var("in", new long[]{-1, nOut});
        SDVariable label = sd.var("label", new long[]{-1, nOut});

        SDVariable diff = input.sub(label);
        SDVariable sqDiff = diff.mul(diff);
        SDVariable msePerEx = sd.mean("msePerEx", sqDiff, 1);
        SDVariable score = sd.mean("score", msePerEx);

        INDArray inputArr = Nd4j.rand(minibatch, nOut);
        INDArray labelArr = Nd4j.rand(minibatch, nOut);

        sd.associateArrayWithVariable(inputArr, input);
        sd.associateArrayWithVariable(labelArr, label);

        INDArray result = sd.execAndEndResult();
        assertNotNull(result);                          //*** Fails Here - Null output ***
        assertEquals(1, result.length());
    }

    @Test
    public void testDistance() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1, 4, 4, DataType.FLOAT)).reshape(2, 2);
        SDVariable x = sameDiff.var("x", arr);
        SDVariable y = sameDiff.var("y", arr);
        SDVariable result = sameDiff.cosineSimilarity(x, y, 1);
        SDVariable addResult = result.add(result);
        SDVariable finalReshape = sameDiff.reshape(addResult, 1, 2);
        sameDiff.exec(Collections.emptyMap(), sameDiff.outputs());
        assertArrayEquals(new long[]{1, 2}, finalReshape.getShape());
    }

    @Test
    public void testTensorGradMmul() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1, 4, 4, DataType.FLOAT)).reshape(2, 2);
        SDVariable x = sameDiff.var("x", arr);
        SDVariable y = sameDiff.var("y", arr);
        SDVariable result = sameDiff.mmul(x, y);
        SDVariable otherResult = result.add(result);
        sameDiff.exec(Collections.emptyMap(), sameDiff.outputs());
        assertArrayEquals(new long[]{2, 2}, result.getShape());
    }


    @Test
    public void testEval() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Nd4j.linspace(1, 4, 4, DataType.FLOAT);
        SDVariable x = sameDiff.var("x", arr);
        SDVariable sigmoid = sameDiff.sigmoid("s", x);
        INDArray assertion = Transforms.sigmoid(arr);
        INDArray eval = sameDiff.exec(Collections.singletonMap("x", arr), Collections.singletonList("s")).get("s");
        assertEquals(assertion, eval);
    }

    @Test
    public void testFunctionInputsAndArgs() {
        SameDiff sameDiff = SameDiff.create();
        SDVariable var = sameDiff.var("one", Nd4j.scalar(1.0));
        SDVariable variable2 = sameDiff.var("two", Nd4j.scalar(1.0));
        val sum = var.add(variable2);
        sum.eval();
        assertArrayEquals(new long[0], sum.getShape());
    }


    @Test
    public void testCrossSameDiffVariableInitWithAlloc() {
        SameDiff first = SameDiff.create();
        SameDiff second = SameDiff.create();


        SDVariable firstVar = first.var("one", new long[]{2, 2});
        SDVariable secondVar = second.var(firstVar);
        assertTrue(firstVar.getArr() == secondVar.getArr());
        assertEquals(firstVar.getVarName(), secondVar.getVarName());
    }


    @Test
    public void testCrossSameDiffVariableInitWithPlaceHolder() {
        SameDiff first = SameDiff.create();
        SameDiff second = SameDiff.create();


        SDVariable firstVar = first.var("one", new long[]{2, 2});
        SDVariable secondVar = second.var(firstVar);
        assumeNotNull(firstVar.getArr());

        assertTrue(firstVar.getArr() == secondVar.getArr());
        assertEquals(firstVar.getVarName(), secondVar.getVarName());
    }


    @Test
    public void testVariableArrayReference() {
        SameDiff sameDiff = SameDiff.create();
        SDVariable arr = sameDiff.var("one", new long[]{2, 2});
        assertArrayEquals(new long[]{2, 2}, arr.getShape());
        assumeNotNull(arr.getArr());
        assertArrayEquals(new long[]{2, 2}, arr.getArr().shape());
    }

    @Test
    public void testEvalAddSelf() {
        /**
         * Note this test fails yet due to needing
         * to validate simple cases like x * x
         * matching number of inputs.
         */
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Nd4j.linspace(1, 4, 4, DataType.FLOAT);
        SDVariable x = sameDiff.var("x", arr);
        SDVariable s = x.mul("s", x);
        INDArray assertion = arr.mul(arr);
        INDArray eval = sameDiff.exec(Collections.singletonMap("x", arr), Collections.singletonList("s")).get("s");
        assertEquals(assertion, eval);
    }

    @Test
    public void testEvalAdd() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Nd4j.linspace(1, 4, 4, DataType.FLOAT);
        INDArray yArr = arr.dup();
        SDVariable x = sameDiff.var("x", arr);
        SDVariable y = sameDiff.var("y", yArr);

        SDVariable sigmoid = x.mul(y);
        INDArray assertion = arr.mul(arr);
        Map<String, INDArray> vars = new HashMap<>();
        vars.put("x", arr);
        vars.put("y", yArr);
        INDArray eval = sameDiff.exec(vars, Collections.singletonList(sigmoid.getVarName())).get(sigmoid.getVarName());
        assertEquals(assertion, eval);
    }

    @Test
    public void testDup() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Transforms.sigmoid(Nd4j.linspace(1, 8, 8, DataType.FLOAT)).reshape(2, 2, 2);
        SDVariable x = sameDiff.var("x", arr);
        SDVariable y = sameDiff.var("y", arr);
        SameDiff tg2 = sameDiff.dup();
    }


    @Test
    public void testElementWiseDivAndRDiv() {
        SameDiff sameDiff = SameDiff.create();
        INDArray ones = Nd4j.ones(4);
        INDArray toDivBy = Nd4j.valueArrayOf(4, 0.25);
        Map<String, INDArray> xAndY = new HashMap<>();
        xAndY.put("x", ones);
        xAndY.put("y", toDivBy);
        sameDiff.defineFunction("div", new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable x = sameDiff.var("x", inputs.get("x"));
                SDVariable y = sameDiff.var("y", inputs.get("y"));
                return new SDVariable[]{x.div(y)};
            }
        }, xAndY);

        sameDiff.defineFunction("rdiv", new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable x = sameDiff.var("x", inputs.get("x"));
                SDVariable y = sameDiff.var("y", inputs.get("y"));
                return new SDVariable[]{x.rdiv(y)};
            }
        }, xAndY);


        INDArray assertionForDiv = Nd4j.valueArrayOf(4, 4.0);
        INDArray assertionForRDiv = Nd4j.valueArrayOf(4, 0.25);
        assertEquals(assertionForDiv, sameDiff.getFunction("div").execAndEndResult());
        assertEquals(assertionForRDiv, sameDiff.getFunction("rdiv").execAndEndResult());

    }


    @Test
    public void testNegativeGradient() {
        SameDiff sameDiff = SameDiff.create();
        INDArray ones = Nd4j.ones(4);
        Map<String, INDArray> xAndY = new HashMap<>();
        xAndY.put("x", ones);
        sameDiff.defineFunction("neg", new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable x = sameDiff.var("x", inputs.get("x"));
                return new SDVariable[]{sameDiff.neg(x)};
            }
        }, xAndY);

        INDArray assertionForDiv = Nd4j.valueArrayOf(4, -1);
        assertEquals(assertionForDiv, sameDiff.getFunction("neg").execAndEndResult());

    }


    @Test
    public void testSumOp() {
        SameDiff sameDiff = SameDiff.create();
        INDArray sumInput = Nd4j.linspace(1, 4, 4, DataType.FLOAT).reshape(2, 2);
        Map<String, INDArray> inputs = new HashMap<>();
        inputs.put("x", sumInput);
        sameDiff.defineFunction("sum", new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable input = sameDiff.var("x", inputs.get("x"));
                SDVariable sum = sameDiff.sum("sum", input, 1);
                return new SDVariable[]{sum};
            }
        }, inputs);

        INDArray assertion = sumInput.sum(1);
        INDArray out = sameDiff.getFunction("sum").exec(Collections.emptyMap(), Collections.singletonList("sum")).get("sum");
        assertEquals(assertion, out);
    }


    @Test
    public void testVariableReferenceNoFunction() {
        /**
         * Creating a variable should not create a differential function.
         */
        SameDiff sameDiff = SameDiff.create();
        SDVariable sdVariable = sameDiff.var("one", Nd4j.scalar(1.0));
        assumeNotNull(sameDiff.getVariable(sdVariable.getVarName()));
    }


    @Test
    public void testVariableWithFunction() {
        /**
         * A variable's function should be null
         * when just a variable but
         * have a function result
         * when the variable itself is the result of a function.
         *
         */
        SameDiff sameDiff = SameDiff.create();
        SDVariable sdVariable = sameDiff.var("one", Nd4j.scalar(1.0));
        SDVariable add = sdVariable.add(1.0);
        assertEquals(sameDiff.getVariable(add.getVarName()), add);
    }


    @Test
    public void testUpdateVariable() {
        SameDiff sameDiff = SameDiff.create();
        SDVariable one = sameDiff.one("one", new long[]{1, 1});
        sameDiff.updateVariableName(one.getVarName(), "one-diff");
        assertEquals(one.getArr(), sameDiff.getVariable("one-diff").getArr());
    }


    @Test(expected = IllegalStateException.class)
    public void testPlaceHolderWithFullShape() {
        val sd = SameDiff.create();
        val placeholder = sd.placeHolder("somevar", 2, 2);
        assertTrue(sd.isPlaceHolder(placeholder.getVarName()));
        sd.resolveVariablesWith(Collections.singletonMap(placeholder.getVarName(), Nd4j.linspace(1, 4, 4, DataType.FLOAT)));
    }


    @Test
    public void testLinearModule() {
        int nIn = 5;
        Linear linear = Linear.execBuilder()
                .nIn(nIn)
                .nOut(4)
                .weightInitScheme(new UniformInitScheme('f', nIn))
                .biasWeightInitScheme(new ZeroInitScheme('f'))
                .build();
        linear.exec(Nd4j.linspace(1, 20, 20, DataType.DOUBLE).reshape(4, 5));
        assertEquals(1, linear.numOutputArguments());

    }


    @Test
    public void testLinearModule2() {
        Linear linear = Linear.execBuilder()
                .nIn(3)
                .nOut(2)
                .weightInitScheme(new OneInitScheme('f'))
                .biasWeightInitScheme(new ZeroInitScheme('f'))
                .build();
        linear.exec(Nd4j.linspace(1, 6, 6, DataType.DOUBLE).reshape(2, 3));
        INDArray assertion = Nd4j.create(new double[][]{
                {6, 6},
                {15, 15}
        });
        assertEquals(assertion, linear.outputArguments()[0]);

    }


    @Test
    public void testDefineFunctionArrayExistence() {
        SameDiff sameDiff = SameDiff.create();
        String testFunctionName = "testfunction";
        SDVariable[] inputVars = new SDVariable[]{
                sameDiff.var("one", new long[]{1, 1}),
                sameDiff.var("two", new long[]{1, 1}),

        };

        SameDiff functionDef = sameDiff.defineFunction(testFunctionName, new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                return new SDVariable[]{variableInputs[0].add(variableInputs[1])};
            }
        }, inputVars);


        //1 input plus 2 outputs
        assertEquals(3, functionDef.variables().size());


    }


    @Test
    public void testIfStatementTrueBodyBackwards() {
        OpValidationSuite.ignoreFailing();      //2019/01/14 AB: Disabled pending overhaul of SameDiff-defined conditional operations
        SameDiff sameDiff = SameDiff.create();
        SameDiffFunctionDefinition conditionBody = new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable sum = sameDiff.sum(variableInputs[0], Integer.MAX_VALUE);
                SDVariable result = sameDiff.gt(sum, 1.0);
                return new SDVariable[]{result};
            }
        };


        SameDiffFunctionDefinition trueBody = new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable add = variableInputs[0].add(1.0);
                return new SDVariable[]{add};
            }
        };

        SameDiffFunctionDefinition falseBody = new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable sub = variableInputs[0].sub(1.0);
                return new SDVariable[]{sub};
            }
        };

        //true body trigger
        SDVariable[] firstInputs = new SDVariable[]{
                sameDiff.var("one", new long[]{1, 1})

        };


        sameDiff.ifStatement(new DefaultSameDiffConditional(), conditionBody, trueBody, falseBody, firstInputs);
        sameDiff.execBackwards(Collections.emptyMap());
        SameDiff grad = sameDiff.getFunction("grad");
       /* If ifBlock = (If) grad.getFunction(new long[]{1},new long[]{2});
        SameDiff assertComparision = SameDiff.create();
        SDVariable initialInput = assertComparision.zero("zero",new long[]{1,1});
        initialInput.addi(1.0);
        assumeNotNull(ifBlock.getTrueBodyExecuted());
        assertTrue(ifBlock.getTrueBodyExecuted());
        assertEquals(Nd4j.scalar(1.00),initialInput.getArr());
        assertEquals(Nd4j.scalar(1.0),ifBlock.getLoopBodyExecution().getVariableForVertexId(2).getArr());
*/
    }


    @Test
    public void testIfStatementTrueBody() {
        OpValidationSuite.ignoreFailing();      //2019/01/14 AB: Disabled pending overhaul of SameDiff-defined conditional operations
        SameDiff sameDiff = SameDiff.create();

        SameDiffFunctionDefinition conditionBody = new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable sum = sameDiff.sum(variableInputs[0], Integer.MAX_VALUE);
                SDVariable result = sameDiff.gt(sum, 1.0);
                return new SDVariable[]{result};
            }
        };


        SameDiffFunctionDefinition trueBody = new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable add = variableInputs[0].add(1.0);
                return new SDVariable[]{add};
            }
        };

        SameDiffFunctionDefinition falseBody = new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable sub = variableInputs[0].sub(1.0);
                return new SDVariable[]{sub};
            }
        };

        //true body trigger
        SDVariable[] firstInputs = new SDVariable[]{
                sameDiff.var("one", new long[]{1, 1})

        };


        sameDiff.ifStatement(new DefaultSameDiffConditional(), conditionBody, trueBody, falseBody, firstInputs);
        sameDiff.exec(Collections.emptyMap());
    }


    @Test
    public void testIfStatementFalseBody() {
        OpValidationSuite.ignoreFailing();      //2019/01/14 AB: Disabled pending overhaul of SameDiff-defined conditional operations
        SameDiff sameDiff = SameDiff.create();

        SameDiffFunctionDefinition conditionBody = new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable sum = sameDiff.sum(variableInputs[0], Integer.MAX_VALUE);
                SDVariable result = sameDiff.gt(sum, 1.0);
                return new SDVariable[]{result};
            }
        };


        SameDiffFunctionDefinition trueBody = new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable add = variableInputs[0].add(1.0);
                return new SDVariable[]{add};
            }
        };

        SameDiffFunctionDefinition falseBody = new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable sub = variableInputs[0].sub(1.0);
                return new SDVariable[]{sub};
            }
        };


        //false body trigger
        SDVariable[] secondInputs = new SDVariable[]{
                sameDiff.setupFunction(sameDiff.var("two", new long[]{1, 1}))

        };

        sameDiff.ifStatement(new DefaultSameDiffConditional(), conditionBody, trueBody, falseBody, secondInputs);

        sameDiff.exec(Collections.emptyMap());
    }


    @Test
    public void testAutoBroadcastAddMatrixVector() {
        SameDiff sameDiff = SameDiff.create();
        INDArray arr = Nd4j.linspace(1, 4, 4, DataType.FLOAT).reshape(2, 2);
        INDArray row = Nd4j.ones(2);
        INDArray assertion = arr.add(1.0);
        SDVariable left = sameDiff.var("arr", arr);
        SDVariable right = sameDiff.var("row", row);
        SDVariable test = left.add(right);
        sameDiff.exec(Collections.emptyMap(), sameDiff.outputs());
        assertEquals(assertion, test.getArr());
    }


    @Test
    public void testNegativeOneShape() {
        val sd = SameDiff.create();
        SDVariable var = sd.placeHolder("test", DataType.FLOAT, -1, 3);
        assertTrue(var.isPlaceHolder());
    }

    @Test
    public void testShapeResolutionMinus1() {
        int nIn = 3;
        int nOut = 4;

        int minibatch = 3;

        for (boolean useMinus1 : new boolean[]{false, true}) {
            log.info("Starting: {}", (useMinus1 ? "minibatch -1" : "minibatch 3"));

            long[] inShape;
            if (useMinus1) {
                inShape = new long[]{-1, nIn};
            } else {
                inShape = new long[]{minibatch, nIn};
            }
            val wShape = new long[]{nIn, nOut};
            val bShape = new long[]{1, nOut};

            SameDiff sd = SameDiff.create();
            SDVariable layerInput = sd.var("in", inShape);
            SDVariable weights = sd.var("W", wShape);
            SDVariable bias = sd.var("b", bShape);

            SDVariable mmul = sd.mmul("mmul", layerInput, weights);
            SDVariable z = mmul.add("z", bias);
            SDVariable out = sd.sigmoid("out", z);

            Map<String, INDArray> m = new HashMap<>();
            INDArray in = Nd4j.rand(new long[]{minibatch, nIn});
            INDArray w = Nd4j.rand(wShape);
            INDArray b = Nd4j.rand(bShape);

            sd.associateArrayWithVariable(in, sd.getVariable("in"));
            assertNotNull(sd.getArrForVarName("in"));
            sd.associateArrayWithVariable(w, sd.getVariable("W"));
            sd.associateArrayWithVariable(b, sd.getVariable("b"));

            INDArray outArr = sd.execAndEndResult();

            assertArrayEquals(new long[]{minibatch, nOut}, outArr.shape());
        }
    }

    @Test
    public void testLabelInputPlaceHolderSgd() {

        SameDiff sd = SameDiff.create();

        int nIn = 3;
        int nOut = 4;
        int minibatch = 3;
        SDVariable input = sd.var("in", new long[]{-1, nIn});
        SDVariable label = sd.var("label", new long[]{-1, nOut});
        assertTrue(input.isPlaceHolder());
        assertTrue(label.isPlaceHolder());
        SDVariable weights = sd.var("W", new long[]{nIn, nOut});
        SDVariable bias = sd.var("b", new long[]{1, nOut});


        SDVariable mmul = sd.mmul("mmul", input, weights);
        SDVariable z = mmul.add("z", bias);
        SDVariable out = sd.tanh(z);

        SDVariable diff = out.sub(label);
        SDVariable sqDiff = diff.mul(diff);
        SDVariable msePerEx = sd.mean("msePerEx", sqDiff, 1);
        SDVariable avgMSE = sd.mean("loss", msePerEx, 0);

        INDArray inputArr = Nd4j.rand(minibatch, nIn);
        INDArray labelArr = Nd4j.rand(minibatch, nOut);
        INDArray weightsArr = Nd4j.rand(nIn, nOut);
        INDArray biasArr = Nd4j.rand(1, nOut);

        sd.associateArrayWithVariable(inputArr, input);
        sd.associateArrayWithVariable(labelArr, label);
        sd.associateArrayWithVariable(weightsArr, weights);
        sd.associateArrayWithVariable(biasArr, bias);

        INDArray result = sd.execAndEndResult();
    }


    @Test
    public void testSequentialMeansPlaceholder() {
        OpValidationSuite.ignoreFailing();
        for (int dim0 : new int[]{10, -1}) {
            String msg = "Dimension 0 = " + dim0;
            System.out.println(msg);
            SameDiff sd = SameDiff.create();
            SDVariable in = sd.var("in", new long[]{dim0, 9, 8});
            SDVariable mean1 = sd.mean(in, 2);                  //[10,9,8] -> [10,9]
            SDVariable mean2 = sd.mean(mean1, 1);               //[10,9] -> [10]

            INDArray inArr = Nd4j.create(10, 9, 8);
            sd.associateArrayWithVariable(inArr, in);

            INDArray out = sd.execAndEndResult();     //Exception here, dim0=-1 case only

            long[] shape = out.shape();
            assertArrayEquals(msg, new long[]{10}, shape);
        }
    }


    @Test
    public void testReductionShapes1() {

        SameDiff sd = SameDiff.create();
        SDVariable in = sd.var("in", new long[]{10, 9, 8});
        SDVariable mean1 = sd.mean(in, 2);      //[10,9] out
        SDVariable mean2 = sd.mean(mean1, 1);   //[10] out
        sd.execAndEndResult();

        INDArray m1 = mean1.getArr();
        INDArray m2 = mean2.getArr();

        assertArrayEquals(new long[]{10, 9}, m1.shape());
        assertArrayEquals(new long[]{10}, m2.shape());
    }


    @Test
    public void testReductionShapes2() {

        SameDiff sd2 = SameDiff.create();
        SDVariable in2 = sd2.var("in", new long[]{10, 9, 8});
        SDVariable meanA = sd2.mean(in2, 0);      //[9,8] out
        sd2.exec(null, sd2.outputs());
        assertArrayEquals(new long[]{9, 8}, meanA.getShape());

        SDVariable meanB = sd2.mean(meanA, 0);   //[8] out
        sd2.exec(null, sd2.outputs());
        assertArrayEquals(new long[]{8}, meanB.getShape());

        assertArrayEquals(meanA.getShape(), meanA.getArr().shape());
        assertArrayEquals(meanB.getShape(), meanB.getArr().shape());

        sd2.exec(Collections.emptyMap(), sd2.outputs());

        INDArray mA = meanA.getArr();
        INDArray mB = meanB.getArr();

        assertArrayEquals(new long[]{9, 8}, mA.shape());
        assertArrayEquals(new long[]{8}, mB.shape());
    }

    @Test
    public void testNames() {
        SameDiff sd = SameDiff.create();
        SDVariable in1 = sd.var("in", new long[]{3, 2});
        SDVariable in2 = sd.var("in2", new long[]{3, 3});

        val m = in1.add(1.0);
        val f = m.add(2.0);
        val s = in2.add(5.0);


        val arr = sd.execSingle(null, s.getVarName());
        log.info("Result M: {}", m.getArr());
        log.info("Result F: {}", f.getArr());
        log.info("Result S: {}", s.getArr());
    }

    @Test
    public void testRunLogisticRegression() {
        Map<String, INDArray> vars = this.variablesForInput();
        SameDiff outside = SameDiff.create();
        outside.defineFunction("activate", new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                sameDiff.enableDebugMode();
                SDVariable x = sameDiff.var("x", inputs.get("x"));
                SDVariable w = sameDiff.var("w", inputs.get("w"));
                SDVariable y = sameDiff.var("y", inputs.get("y"));
                SDVariable activation = sameDiff.sigmoid("activation", sameDiff.mmul("mmul", x, w));
                SDVariable oneMinusY = y.rsub("oneminusy", 1.0);
                SDVariable oneMinusPredictions = activation.rsub("oneminusactivations", 1.0);
                SDVariable outputTimesY = y.mul("output * y", activation);
                SDVariable yHat = oneMinusPredictions.mul("yhat", oneMinusY);
                SDVariable probs = outputTimesY.add("probs", yHat);
                SDVariable logProbs = sameDiff.log("logprob", probs);
                SDVariable ret = sameDiff.sum("totalsum", logProbs, Integer.MAX_VALUE);
                SDVariable ret2 = sameDiff.neg("negtotalsum", ret);
                return new SDVariable[]{ret2};
            }
        }, vars);

        SameDiff activation = outside.getFunction("activate");
        int epochsToRun = 5;
        double lr = 0.1;
     /*   for(int i = 0; i < epochsToRun; i++) {
            activation.execBackwards();
            INDArray wGrad = activation.grad("w").getArr().reshape(vars.get("w").shape());
            vars.get("w").subi(wGrad.mul(lr));
            System.out.println("Score: " + activation.getVariable("negtotalsum").getArr());
        }*/

    }


    @Test
    public void testTransposeWithVector() {
        val sd = SameDiff.create();
        val matrix = Nd4j.linspace(1, 12, 12, DataType.FLOAT).reshape(4, 3);
        val vector = Nd4j.linspace(1, 4, 4, DataType.FLOAT).reshape(4, 1);
        val input1 = sd.var("input", matrix);
        val input2 = sd.var("input2", vector);
        val output = sd.mmul("output", input1, input2, MMulTranspose.builder().transposeA(true).transposeB(false).build());
        output.eval();
        assertArrayEquals(new long[]{3, 1}, output.getShape());
    }

    @Test
    public void testSimpleDefineFunction() {
        SameDiff sameDiffOuter = SameDiff.create();
        Map<String, INDArray> inputs = variablesForInput();
        inputs.remove("y");
        String logisticForward = "logisticPredictions";
        sameDiffOuter.defineFunction(logisticForward, new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {

                SDVariable input = sameDiff.var("x", inputs.get("x"));
                SDVariable w = sameDiff.var("w", inputs.get("w"));
                SDVariable preOutput = sameDiff.mmul(input, w);
                SDVariable sigmoid = sameDiff.sigmoid(preOutput);
                return new SDVariable[]{sigmoid};
            }

        }, inputs);

        assertEquals(1, sameDiffOuter.definedFunctionNames().size());

        //note here that we don't add the duplicate ops with define function anymore
    }

    @Test
    public void testSumGradient() {
        SameDiff sameDiff = SameDiff.create();
        SDVariable twoByTwo = sameDiff.var("initial", Nd4j.linspace(1, 4, 4, DataType.FLOAT).reshape(2, 2));
        SDVariable sum = sameDiff.sum(twoByTwo, Integer.MAX_VALUE);
        sameDiff.execBackwards(Collections.emptyMap());
        SameDiff grad = sameDiff.getFunction("grad");
        SDVariable gradArr = sameDiff.grad(twoByTwo.getVarName());
        assertEquals(Nd4j.ones(DataType.FLOAT, 2, 2), gradArr.getArr());
    }


    @Test
    public void testRsubScalar() {
        SameDiff sameDiff = SameDiff.create();
        Map<String, INDArray> params = new HashMap<>();
        INDArray var = Nd4j.valueArrayOf(4, 2);
        params.put("x", var);
        sameDiff.defineFunction("rsubop", new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable input = sameDiff.var("x", inputs.get("x"));
                SDVariable ret = input.rsub("rsub", 1.0);
                return new SDVariable[]{ret};
            }
        }, params);

        SameDiff logisticGraph = sameDiff.getFunction("rsubop");
        INDArray output = logisticGraph.exec(params, Collections.singletonList("rsub")).get("rsub");
        assertEquals(Nd4j.ones(4).muli(-1), output);
    }


    @Test
    public void testFunctionScalarResultPropagation() {
        SameDiff sameDiffOuter = SameDiff.create();
        Map<String, INDArray> inputs = variablesForInput();

        sameDiffOuter.defineFunction("logisticPredictions", new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable input = sameDiff.var("x", inputs.get("x"));
                SDVariable w = sameDiff.var("w", inputs.get("w"));
                SDVariable preOutput = sameDiff.mmul(input, w);
                SDVariable sigmoid = sameDiff.sigmoid(preOutput);
                return new SDVariable[]{sigmoid};
            }
        }, inputs);

        sameDiffOuter.defineFunction("oneminuspredictions", new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable y = sameDiff.var("y", inputs.get("y"));
                SDVariable oneMinusPredictions = y.rsub("rsub", 1.0);
                return new SDVariable[]{oneMinusPredictions};
            }
        }, inputs);


        SameDiff logisticGraph = sameDiffOuter.getFunction("oneminuspredictions");
        Map<String,INDArray> inputsSubset = new HashMap<>();
        inputsSubset.put("y", inputs.get("y"));
        INDArray output = logisticGraph.exec(inputsSubset, Collections.singletonList("rsub")).get("rsub");
        INDArray assertion = Nd4j.create(new double[]{0, 0, 1, 0});
        assertEquals(assertion, output);

    }


    @Test
    public void testMmul() {
        SameDiff sameDiffOuter = SameDiff.create();
        Map<String, INDArray> inputs = variablesForInput();
        SDVariable x = sameDiffOuter.var("x", inputs.get("x"));
        SDVariable w = sameDiffOuter.var("w", inputs.get("w"));
        SDVariable output = sameDiffOuter.mmul(x, w);
    }


    @Test
    public void testGraphBuilding() {
        final SameDiff sameDiffOuter = SameDiff.create();
        Map<String, INDArray> inputs = variablesForInput();

        sameDiffOuter.defineFunction("logisticPredictions", new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable input = sameDiff.var("x", inputs.get("x"));
                SDVariable w = sameDiff.var("w", inputs.get("w"));
                SDVariable y = sameDiff.var("y", inputs.get("y"));
                SDVariable preOutput = sameDiff.mmul(input, w);
                SDVariable sigmoid = sameDiff.sigmoid(preOutput);

                return new SDVariable[]{sigmoid};
            }
        }, inputs);

        sameDiffOuter.defineFunction("loss", new SameDiffFunctionDefinition() {
            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                SDVariable outputs = sameDiffOuter.invokeFunctionOn("logisticPredictions", sameDiff);
                SDVariable y = sameDiff.getVariable("y");
                SDVariable outputTimesY = outputs.mul(y);
                return new SDVariable[]{outputTimesY};

            }
        }, inputs);


        SameDiff logisticPrediction = sameDiffOuter.getFunction("logisticPredictions");
        List<String> logisticOpNameAssertions = Arrays.asList("mmul", "sigmoid");


    }


    @Test
    public void testScalarAdd() {
        SameDiff sameDiff = SameDiff.create();
        SDVariable twoByTwo = sameDiff.var("first", Nd4j.linspace(1, 4, 4, DataType.FLOAT).reshape('c', 2, 2));
        SDVariable add = twoByTwo.add(1.0);
        INDArray test = sameDiff.execAndEndResult();
        INDArray assertion = Nd4j.linspace(1, 4, 4, DataType.FLOAT).reshape('c', 2, 2).add(1.0);
        assertEquals(assertion, test);
    }


    @Test
    public void testSums() {
        SameDiff sameDiff = SameDiff.create();
        INDArray ones = Nd4j.ones(7, 4);
        SDVariable sdVariable = sameDiff.var("ones", ones);
        SDVariable result = sdVariable.add(1.0);
        SDVariable total = sameDiff.sum(result, Integer.MAX_VALUE);
        sameDiff.execAndEndResult();
        assertEquals(56, total.getArr().getDouble(0), 1e-1);
    }


    @Test
    public void testDenseLayerForwardPass() {
        Nd4j.getRandom().setSeed(12345);

        SameDiff sd = SameDiff.create();

        INDArray iInput = Nd4j.rand(3, 4);
        INDArray iWeights = Nd4j.rand(4, 5);
        INDArray iBias = Nd4j.rand(1, 5);

        SDVariable input = sd.var("input", iInput);
        SDVariable weights = sd.var("weights", iWeights);
        SDVariable bias = sd.var("bias", iBias);

        SDVariable mmul = sd.mmul("mmul", input, weights);
        SDVariable z = mmul.add("z", bias);
        SDVariable out = sd.sigmoid("out", z);

        INDArray expMmul = iInput.mmul(iWeights);
        INDArray expZ = expMmul.addRowVector(iBias);
        INDArray expOut = Transforms.sigmoid(expZ, true);

        sd.exec(Collections.emptyMap(), sd.outputs());

        assertEquals(expMmul, mmul.getArr());
        assertEquals(expZ, z.getArr());
        assertEquals(expOut, out.getArr());
    }

    @Test
    public void testActivationBackprop() {

        Activation[] afns = new Activation[]{
                Activation.TANH,
                Activation.SIGMOID,
                Activation.ELU,
                Activation.SOFTPLUS,
                Activation.SOFTSIGN,
                Activation.HARDTANH,
                Activation.CUBE,            //WRONG output - see issue https://github.com/deeplearning4j/nd4j/issues/2426
                Activation.RELU,            //JVM crash
                Activation.LEAKYRELU        //JVM crash
        };

        for (Activation a : afns) {

            SameDiff sd = SameDiff.create();
            INDArray inArr = Nd4j.linspace(-3, 3, 7, DataType.FLOAT);
            INDArray labelArr = Nd4j.linspace(-3, 3, 7, DataType.FLOAT).muli(0.5);
            SDVariable in = sd.var("in", inArr.dup());

//            System.out.println("inArr: " + inArr);

            INDArray outExp;
            SDVariable out;
            switch (a) {
                case ELU:
                    out = sd.elu("out", in);
                    outExp = Transforms.elu(inArr, true);
                    break;
                case HARDTANH:
                    out = sd.hardTanh("out", in);
                    outExp = Transforms.hardTanh(inArr, true);
                    break;
                case LEAKYRELU:
                    out = sd.leakyRelu("out", in, 0.01);
                    outExp = Transforms.leakyRelu(inArr, true);
                    break;
                case RELU:
                    out = sd.relu("out", in, 0.0);
                    outExp = Transforms.relu(inArr, true);
                    break;
                case SIGMOID:
                    out = sd.sigmoid("out", in);
                    outExp = Transforms.sigmoid(inArr, true);
                    break;
                case SOFTPLUS:
                    out = sd.softplus("out", in);
                    outExp = Transforms.softPlus(inArr, true);
                    break;
                case SOFTSIGN:
                    out = sd.softsign("out", in);
                    outExp = Transforms.softsign(inArr, true);
                    break;
                case TANH:
                    out = sd.tanh("out", in);
                    outExp = Transforms.tanh(inArr, true);
                    break;
                case CUBE:
                    out = sd.cube("out", in);
                    outExp = Transforms.pow(inArr, 3, true);
                    break;
                default:
                    throw new RuntimeException(a.toString());
            }

            //Sum squared error loss:
            SDVariable label = sd.var("label", labelArr.dup());
            SDVariable diff = label.sub("diff", out);
            SDVariable sqDiff = diff.mul("sqDiff", diff);
            SDVariable totSum = sd.sum("totSum", sqDiff, Integer.MAX_VALUE);    //Loss function...

            sd.exec(Collections.emptyMap(), sd.outputs());
            INDArray outAct = sd.getVariable("out").getArr();
            assertEquals(a.toString(), outExp, outAct);

            // L = sum_i (label - out)^2
            //dL/dOut = 2(out - label)
            INDArray dLdOutExp = outExp.sub(labelArr).mul(2);
            INDArray dLdInExp = a.getActivationFunction().backprop(inArr.dup(), dLdOutExp.dup()).getFirst();

            sd.execBackwards(Collections.emptyMap());
            SameDiff gradFn = sd.getFunction("grad");
            INDArray dLdOutAct = gradFn.getVariable("out-grad").getArr();
            INDArray dLdInAct = gradFn.getVariable("in-grad").getArr();

            assertEquals(a.toString(), dLdOutExp, dLdOutAct);
            assertEquals(a.toString(), dLdInExp, dLdInAct);
        }
    }



    @Test
    public void testPlaceholderReduceSimple() {
        SameDiff sd = SameDiff.create();
        SDVariable v = sd.var("in", new long[]{-1, 10});
        SDVariable vSum = sd.sum(v, 1);                             //Exception here
    }


    @Test
    public void testSequentialMeans() {
        SameDiff sd = SameDiff.create();
        SDVariable in = sd.var("in", new long[]{10, 10, 10});
        SDVariable mean1 = sd.mean(in, 2);      //[10,10] out
        SDVariable mean2 = sd.mean(mean1, 1);   //[10,1] out - ***exception here***
    }

    @Test
    public void testBatchNormTest() {
        SameDiff sd = SameDiff.create();

        INDArray input = Nd4j.rand(1, 10);
        INDArray mean = Nd4j.rand(1, 10).reshape(10);
        INDArray var = Nd4j.rand(1, 10).reshape(10);
        INDArray gamma = Nd4j.rand(1, 10).reshape(10);
        INDArray beta = Nd4j.rand(1, 10).reshape(10);

        SDVariable sdInput = sd.var("input", input);
        SDVariable sdMean = sd.var("mean", mean);
        SDVariable sdVar = sd.var("var", var);
        SDVariable sdGamma = sd.var("gamma", gamma);
        SDVariable sdBeta = sd.var("beta", beta);

        SDVariable out = sd.batchNorm(sdInput, sdMean, sdVar, sdGamma, sdBeta,
                0.0, 1);
        out = sd.tanh("out", out);

        INDArray outArr = sd.execAndEndResult();
        assertArrayEquals(new long[]{1, 10}, outArr.shape());

    }

    @Test
    public void testLrn() {
        SameDiff sd = SameDiff.create();

        INDArray input = Nd4j.create(new float[]{4, 4, 4, 4}, new long[]{1, 4, 1, 1});

        SDVariable sdInput = sd.var("input", input);

        LocalResponseNormalizationConfig lrn = LocalResponseNormalizationConfig.builder()
                .alpha(1.0)
                .beta(.5)
                .bias(0.0)
                .depth(1).build();

        SDVariable out = sd.localResponseNormalization(sdInput, lrn);
        SDVariable sdOut = sd.tanh("out", out);

        sd.exec(Collections.emptyMap(), sd.outputs());

        for (int i = 0; i < 4; i++)
            assertEquals(1, out.getArr().get(all(), NDArrayIndex.point(i), all(), all()).getInt(0));

    }

    @Test
    public void testMoments() {
        SameDiff sd = SameDiff.create();

        INDArray input = Nd4j.create(new float[]{1, 2, 3, 4}, new long[]{2, 2});

        SDVariable sdInput = sd.var("input", input);

        val axis = new int[]{0, 1};
        SDVariable[] moments = sd.moments(sdInput, axis);
        SDVariable mean = moments[0];
        SDVariable variance = moments[1];

        SDVariable sum = mean.add(variance);
        SDVariable out = sd.tanh("out", sum);

        INDArray outArr = sd.execAndEndResult();

        INDArray meanArray = mean.getArr();
        INDArray varArray = variance.getArr();

        assertEquals(meanArray.getDouble(0), 2.5, 1e-5);
        assertEquals(varArray.getDouble(0), 1.25, 1e-5);
    }

    @Test
    public void testNormalizeMoments() {
        SameDiff sd = SameDiff.create();

        INDArray counts = Nd4j.create(new float[]{2}, new long[]{1, 1});
        INDArray means = Nd4j.create(new float[]{2, 4}, new long[]{1, 2});
        INDArray vars = Nd4j.create(new float[]{6, 8}, new long[]{1, 2});


        SDVariable sdCounts = sd.var("counts", counts);
        SDVariable sdMeans = sd.var("means", means);
        SDVariable sdVars = sd.var("vars", vars);
        double shift = 0.0;

        SDVariable[] moments = sd.normalizeMoments(sdCounts, sdMeans, sdVars, shift);
        SDVariable normMean = moments[0];
        SDVariable normVariance = moments[1];

        SDVariable sum = normMean.add(normVariance);
        SDVariable out = sd.tanh("out", sum);

        INDArray outArr = sd.execAndEndResult();

        INDArray meanArray = normMean.getArr();
        INDArray varArray = normVariance.getArr();

        assertEquals(meanArray.getDouble(0, 0), 1, 1e-5);
        assertEquals(meanArray.getDouble(0, 1), 2, 1e-5);
        assertArrayEquals(meanArray.shape(), varArray.shape());

    }


    @Test
    public void testDepthWiseConv2dBasic() {
        int nIn = 3;
        int depthWise = 4;
        int kH = 2;
        int kW = 2;

        int mb = 3;
        int imgH = 28;
        int imgW = 28;


        SameDiff sd = SameDiff.create();
        INDArray depthWeightArr = Nd4j.create(kH, kW, nIn, depthWise);

        INDArray bArr = Nd4j.create(1, depthWise * nIn);
        INDArray inArr = Nd4j.create(mb, nIn, imgH, imgW);

        SDVariable in = sd.var("in", inArr);
        SDVariable dW = sd.var("dW", depthWeightArr);
        SDVariable b = sd.var("b", bArr);

        Conv2DConfig c = Conv2DConfig.builder()
                .kH(kH).kW(kW)
                .pH(0).pW(0)
                .sH(1).sW(1)
                .dH(1).dW(1)
                .isSameMode(false)
                .build();

        SDVariable out = sd.depthWiseConv2d(in, dW, b, c);
        out = sd.tanh("out", out);

        INDArray outArr = sd.execAndEndResult();
        //Expected output size: out = (in - k + 2*p)/s + 1 = (28-2+0)/1+1 = 27
        val outShape = outArr.shape();
        assertArrayEquals(new long[]{mb, depthWise * nIn, 27, 27}, outShape);
    }

    @Test
    public void validateMeanDiff() {
        Nd4j.getRandom().setSeed(12345);

        INDArray arr = Nd4j.rand(3, 4);

        SameDiff sd = SameDiff.create();
        SDVariable v = sd.var("in", arr);
        SDVariable mean = sd.mean("mean", v);

        INDArray out = sd.execAndEndResult();
        assertEquals(out, arr.mean(Integer.MAX_VALUE));

        sd.execBackwards(Collections.emptyMap());
        INDArray dLdIn = sd.grad("in").getArr();

        //If L = mean(in)
        //then dL/dIn = 1/N

        assertEquals(Nd4j.valueArrayOf(arr.shape(), 1.0 / arr.length()), dLdIn);
    }

    @Test
    public void validateSumDiff() {
        Nd4j.getRandom().setSeed(12345);

        INDArray arr = Nd4j.rand(3, 4);

        SameDiff sd = SameDiff.create();
        SDVariable v = sd.var("in", arr);
        SDVariable mean = sd.sum("sum", v);

        INDArray out = sd.execAndEndResult();
        assertEquals(out, arr.sum(Integer.MAX_VALUE));

        sd.execBackwards(Collections.emptyMap());
        INDArray dLdIn = sd.grad("in").getArr();

        //If L = sum(in)
        //then dL/dIn = 1

        assertEquals(Nd4j.ones(arr.shape()), dLdIn);
    }

    @Test
    public void validateStdevDiff() {
        for (boolean biasCorrected : new boolean[]{true, false}) {
            Nd4j.getRandom().setSeed(12345);

            INDArray arr = Nd4j.rand(3, 4);

            SameDiff sd = SameDiff.create();
            SDVariable v = sd.var("in", arr);
            SDVariable stdev = sd.standardDeviation("stdev", v, biasCorrected);

            INDArray out = sd.execAndEndResult();
            assertEquals(out, arr.std(biasCorrected, Integer.MAX_VALUE));

            sd.execBackwards(Collections.emptyMap());
            INDArray dLdIn = sd.grad("in").getArr();

            //If L = stdev(in)
            //then dL/dIn = (in-mean) / (s*(N-1))
            // or /N for non-bias corrected

            double m = arr.meanNumber().doubleValue();
            double s = arr.stdNumber(biasCorrected).doubleValue();
            INDArray exp = arr.sub(m).div(s);
            exp.divi(biasCorrected ? arr.length() - 1 : arr.length());

            assertEquals(exp, dLdIn);
        }
    }

    @Test
    public void validateVarDiff() {
        for (boolean biasCorrected : new boolean[]{true, false}) {
            Nd4j.getRandom().setSeed(12345);

            INDArray arr = Nd4j.rand(3, 4);

            SameDiff sd = SameDiff.create();
            SDVariable v = sd.var("in", arr);
            SDVariable var = sd.variance("var", v, biasCorrected);

            INDArray out = sd.execAndEndResult();
            assertEquals(out, arr.var(biasCorrected, Integer.MAX_VALUE));

            sd.execBackwards(Collections.emptyMap());
            INDArray dLdIn = sd.grad("in").getArr();

            //If L = var(in)
            //then dL/dIn = 2/(N-1) * (in-mean)
            // or /N for non-bias corrected

            double m = arr.meanNumber().doubleValue();
            INDArray exp = arr.sub(m).mul(2);
            exp.divi(biasCorrected ? arr.length() - 1 : arr.length());

            assertEquals(exp, dLdIn);
        }
    }

    @Test
    public void validateMinDiff() {
        Nd4j.getRandom().setSeed(12345);

        INDArray arr = Nd4j.rand(3, 4);

        SameDiff sd = SameDiff.create();
        SDVariable v = sd.var("in", arr);
        SDVariable min = sd.min("min", v);

        INDArray out = sd.execAndEndResult();
        assertEquals(out, arr.min(Integer.MAX_VALUE));

        sd.execBackwards(Collections.emptyMap());
        INDArray dLdIn = sd.grad("in").getArr();

        //If L = min(in)
        //then dL/dIn = 1 if in_i == min(in) or 0 otherwise

        //Note that we don't have an "IsMin" op, so use IsMax(neg(in)) which is equivalent
        INDArray exp = Nd4j.getExecutioner().exec(new IsMax(arr.neg())).castTo(Nd4j.defaultFloatingPointType());

        assertEquals(exp, dLdIn);
    }

    @Test
    public void validateMaxDiff() {
        Nd4j.getRandom().setSeed(12345);

        INDArray arr = Nd4j.rand(DataType.DOUBLE, 3, 4);

        SameDiff sd = SameDiff.create();
        SDVariable v = sd.var("in", arr);
        SDVariable min = sd.max("max", v);

        INDArray out = sd.execAndEndResult();
        assertEquals(out, arr.max(Integer.MAX_VALUE));

        sd.execBackwards(Collections.emptyMap());
        INDArray dLdIn = sd.grad("in").getArr();

        //If L = max(in)
        //then dL/dIn = 1 if in_i == max(in) or 0 otherwise

        INDArray exp = Nd4j.getExecutioner().exec(new IsMax(arr.dup())).castTo(DataType.DOUBLE);

        assertEquals(exp, dLdIn);
    }

    @Test
    public void validateProdDiff() {
        Nd4j.getRandom().setSeed(12345);

        INDArray arr = Nd4j.rand(3, 4);

        SameDiff sd = SameDiff.create();
        SDVariable v = sd.var("in", arr);
        SDVariable prod = sd.prod("prod", v);

        double p = arr.prodNumber().doubleValue();
        INDArray out = sd.execAndEndResult();
        assertEquals(out, arr.prod(Integer.MAX_VALUE));

        sd.execBackwards(Collections.emptyMap());
        INDArray dLdIn = sd.grad("in").getArr();

        //If L = prod(in)
        //then dL/dIn = prod(in) / in       i.e., product of input *excluding* in_i as (d/dx(xyzabc) = yzabc

        INDArray exp = arr.rdiv(p);
        assertEquals(exp, dLdIn);
    }

    @Test
    public void testSquare() {
        Nd4j.getRandom().setSeed(12345);

        int mb = 5;
        int nOut = 4;

        SameDiff sd = SameDiff.create();
        SDVariable in = sd.var("in", Nd4j.rand(mb, nOut));
        SDVariable label = sd.var("label", Nd4j.rand(mb, nOut));
        SDVariable diff = in.sub(label);
        SDVariable sqDiff = sd.square(diff);

        INDArray expOut = in.getArr().sub(label.getArr());
        expOut.muli(expOut);

        System.out.println("About to exec");
        INDArray out = sd.execAndEndResult();   //JVM crash

        assertEquals(out, expOut);
    }


    @Test
    public void testExpandDims() {
        for (int i = 0; i <= 2; i++) {
            SameDiff sd = SameDiff.create();
            SDVariable in = sd.var("in", Nd4j.create(2, 3));
            SDVariable expanded = sd.f().expandDims(in, i);

            INDArray out = sd.execAndEndResult();
            switch (i) {
                case 0:
                    assertArrayEquals(new long[]{1, 2, 3}, out.shape());
                    break;
                case 1:
                    assertArrayEquals(new long[]{2, 1, 3}, out.shape());
                    break;
                case 2:
                    assertArrayEquals(new long[]{2, 3, 1}, out.shape());
                    break;
                default:
                    throw new RuntimeException();
            }
        }
    }

    @Test
    public void testZerosLike() {
        SameDiff sd = SameDiff.create();
        SDVariable var0 = sd.var("in", DataType.DOUBLE, new long[]{3, 4});
        SDVariable out = sd.zerosLike("out", var0);

        INDArray out1 = sd.execAndEndResult();
        assertEquals(Nd4j.zeros(3, 4), out1);

        sd.associateArrayWithVariable(Nd4j.create(3, 4), var0);
        INDArray out2 = sd.execAndEndResult();
        assertEquals(Nd4j.zeros(DataType.DOUBLE, 3, 4), out2);
    }

    @Test
    public void testOnesLike() {
        SameDiff sd = SameDiff.create();
        SDVariable var0 = sd.var("in", new long[]{3, 4});
        SDVariable out = sd.onesLike("out", var0);

        INDArray out1 = sd.execAndEndResult();
        assertEquals(Nd4j.ones(3, 4), out1);

        sd.associateArrayWithVariable(Nd4j.create(3, 4), var0);
        INDArray out2 = sd.execAndEndResult();
        assertEquals(Nd4j.ones(3, 4), out2);
    }



    @Test
    public void testOnesLikeBackprop() {
        SameDiff sd = SameDiff.create();
        SDVariable var0 = sd.var("in", new long[]{3, 4});
        SDVariable ones = sd.onesLike("ones", var0);
        SDVariable out = sd.sum("oun", ones);

        INDArray outArr = sd.execAndEndResult();
        assertEquals(Nd4j.valueArrayOf(1, 12.0), outArr);

        sd.execBackwards(Collections.emptyMap());

        assertEquals(Nd4j.create(3, 4), sd.grad("in").getArr());
    }


    @Test
    public void testManhattanAlongDim0() {
        Nd4j.getRandom().setSeed(12345);

        INDArray a = Nd4j.rand(new long[]{3, 4, 5});
        INDArray b = Nd4j.rand(new long[]{3, 4, 5});

        INDArray expOut = Nd4j.getExecutioner().exec(new ManhattanDistance(a, b, 0));

        val expShape = new long[]{4, 5};

        assertArrayEquals(expShape, expOut.shape());
    }


    @Test
    public void testJaccardDistance() {
        Nd4j.getRandom().setSeed(12345);

        INDArray a = Nd4j.rand(new long[]{3, 4}).addi(0.1);
        INDArray b = Nd4j.rand(new long[]{3, 4}).addi(0.1);

        SameDiff sd = SameDiff.create();
        SDVariable in1 = sd.var("in1", a);
        SDVariable in2 = sd.var("in2", b);

        SDVariable jaccard = sd.jaccardDistance("out", in1, in2);

        INDArray min = Transforms.min(a, b);
        INDArray max = Transforms.max(a, b);

        double minSum = min.sumNumber().doubleValue();
        double maxSum = max.sumNumber().doubleValue();
        double jd = 1.0 - minSum / maxSum;

        INDArray out = sd.execAndEndResult();
        assertEquals(1, out.length());

        assertEquals(jd, out.getDouble(0), 1e-6);
    }

    @Test
    public void testPairwiseBooleanTransforms() {
        /*
        eq, neq, gt, lt, gte, lte, or, and, xor
         */
        //Test transforms (pairwise)
        Nd4j.getRandom().setSeed(12345);

        for (int i = 0; i < 11; i++) {
            SameDiff sd = SameDiff.create();

            int nOut = 4;
            int minibatch = 5;

            INDArray ia = Nd4j.randn(minibatch, nOut);
            INDArray ib = Nd4j.randn(minibatch, nOut);

            SDVariable in1 = sd.var("in1", ia);
            SDVariable in2 = sd.var("in2", ib);


            SDVariable t;
            INDArray expOut;
            switch (i) {
                case 0:
                    t = sd.eq(in1, in2);
                    expOut = ia.eq(ib);
                    break;
                case 1:
                    t = sd.neq(in1, in2);
                    expOut = ia.neq(ib);
                    break;
                case 2:
                    t = sd.gt(in1, in2);
                    expOut = ia.gt(ib);
                    break;
                case 3:
                    t = sd.lt(in1, in2);
                    expOut = ia.lt(ib);
                    break;
                case 4:
                    t = sd.gte(in1, in2);
                    expOut = Nd4j.create(DataType.BOOL, ia.shape());
                    Nd4j.getExecutioner().exec(new GreaterThanOrEqual(new INDArray[]{ia, ib}, new INDArray[]{expOut}));
                    break;
                case 5:
                    t = sd.lte(in1, in2);
                    expOut = Nd4j.create(DataType.BOOL, ia.shape());
                    Nd4j.getExecutioner().exec(new LessThanOrEqual(new INDArray[]{ia, ib}, new INDArray[]{expOut}));
                    break;
                case 6:
                    ia = Nd4j.getExecutioner().exec(new BernoulliDistribution(ia, 0.5));
                    ib = Nd4j.getExecutioner().exec(new BernoulliDistribution(ib, 0.5));
                    t = sd.or(in1, in2);
                    expOut = Transforms.or(ia, ib);
                    break;
                case 7:
                    t = sd.max(in1, in2);
                    expOut = Nd4j.getExecutioner().exec(new OldMax(ia, ib, ia.dup()));
                    break;
                case 8:
                    t = sd.min(in1, in2);
                    expOut = Nd4j.getExecutioner().exec(new OldMin(ia, ib, ia.dup()));
                    break;
                case 9:
                    ia = Nd4j.getExecutioner().exec(new BernoulliDistribution(ia, 0.5));
                    ib = Nd4j.getExecutioner().exec(new BernoulliDistribution(ib, 0.5));
                    t = sd.and(in1, in2);
                    expOut = Transforms.and(ia, ib);
                    break;
                case 10:
                    ia = Nd4j.getExecutioner().exec(new BernoulliDistribution(ia, 0.5));
                    ib = Nd4j.getExecutioner().exec(new BernoulliDistribution(ib, 0.5));
                    t = sd.xor(in1, in2);
                    expOut = Transforms.xor(ia, ib);
                    break;
                default:
                    throw new RuntimeException();
            }

            log.info("Executing: " + i);
            INDArray out = sd.execAndEndResult();

            assertEquals(expOut, out);
        }
    }

    @Test
    public void testBooleanChecks() {
        /*
        isNonDecreasing,
         */
        Nd4j.getRandom().setSeed(12345);

        for (int i = 0; i < 3; i++) {
            SameDiff sd = SameDiff.create();

            int nOut = 4;
            int minibatch = 5;

            INDArray ia = Nd4j.randn(minibatch, nOut);

            SDVariable in1 = sd.var("in1", ia);
            INDArray expOut = Nd4j.create(new boolean[]{true});
            SDVariable t;

            switch (i) {
                case 0:
                    t = sd.isNonDecreasing(in1);
                    Nd4j.getExecutioner().exec(new IsNonDecreasing(new INDArray[]{ia}, new INDArray[]{expOut}));
                    break;
                case 1:
                    t = sd.isStrictlyIncreasing(in1);
                    Nd4j.getExecutioner().exec(new IsStrictlyIncreasing(new INDArray[]{ia}, new INDArray[]{expOut}));
                    break;
                case 2:
                    t = sd.isNumericTensor(in1);
                    Nd4j.getExecutioner().exec(new IsNumericTensor(new INDArray[]{ia}, new INDArray[]{expOut}));
                    break;
                default:
                    throw new RuntimeException();
            }

            log.info("Executing: " + i);
            INDArray out = sd.execAndEndResult();

            assertEquals(expOut, out);
        }
    }

    @Test
    public void testExpandDims2d() {
        val origShape = new long[]{3, 4};

        for (int i = 0; i < 3; i++) {
            for (Pair<INDArray, String> p : NDArrayCreationUtil.getAllTestMatricesWithShape(origShape[0], origShape[1], 12345, DataType.FLOAT)) {
                INDArray inArr = p.getFirst().muli(100);

                SameDiff sd = SameDiff.create();
                SDVariable in = sd.var("in", inArr);
                SDVariable expand = sd.f().expandDims(in, i);

                INDArray out = sd.execAndEndResult();

                INDArray expOut;
                switch (i) {
                    case 0:
                        expOut = inArr.dup('c').reshape('c', 1, origShape[0], origShape[1]);
                        break;
                    case 1:
                        expOut = inArr.dup('c').reshape('c', origShape[0], 1, origShape[1]);
                        break;
                    case 2:
                        expOut = inArr.dup('c').reshape('c', origShape[0], origShape[1], 1);
                        break;
                    default:
                        throw new RuntimeException();
                }

                String msg = "expandDim=" + i + ", source=" + p.getSecond();

                assertEquals(msg, out, expOut);
            }
        }
    }

    @Test
    public void testSqueezeDims() {
        val origShape = new long[]{3, 4, 5};

        for (int i = 0; i < 3; i++) {

            val shape = origShape.clone();
            shape[i] = 1;

            for (Pair<INDArray, String> p : NDArrayCreationUtil.getAll3dTestArraysWithShape(12345, shape, DataType.FLOAT)) {
                INDArray inArr = p.getFirst().muli(100);

                SameDiff sd = SameDiff.create();
                SDVariable in = sd.var("in", inArr);
                SDVariable squeeze = sd.f().squeeze(in, i);

                INDArray out = sd.execAndEndResult();

                INDArray expOut;
                switch (i) {
                    case 0:
                        expOut = inArr.dup('c').reshape('c', origShape[1], origShape[2]);
                        break;
                    case 1:
                        expOut = inArr.dup('c').reshape('c', origShape[0], origShape[2]);
                        break;
                    case 2:
                        expOut = inArr.dup('c').reshape('c', origShape[0], origShape[1]);
                        break;
                    default:
                        throw new RuntimeException();
                }

                String msg = "squeezeDim=" + i + ", source=" + p.getSecond();

                assertEquals(msg, out, expOut);
            }
        }
    }

    @Test
    public void testExpandSqueezeChain() {

        val origShape = new long[]{3, 4};

        for (int i = 0; i < 3; i++) {
            for (Pair<INDArray, String> p : NDArrayCreationUtil.getAllTestMatricesWithShape(origShape[0], origShape[1], 12345, DataType.FLOAT)) {
                INDArray inArr = p.getFirst().muli(100);

                SameDiff sd = SameDiff.create();
                SDVariable in = sd.var("in", inArr);
                SDVariable expand = sd.expandDims(in, i);
                SDVariable squeeze = sd.squeeze(expand, i);

                INDArray out = sd.execAndEndResult();

                String msg = "expand/Squeeze=" + i + ", source=" + p.getSecond();

                assertEquals(msg, out, inArr);  //expand -> squeeze: should be opposite ops
            }
        }
    }

    @Test
    public void testSqueezeExpandChain() {

        val origShape = new long[]{3, 4, 5};

        for (int i = 0; i < 3; i++) {

            val shape = origShape.clone();
            shape[i] = 1;

            for (Pair<INDArray, String> p : NDArrayCreationUtil.getAll3dTestArraysWithShape(12345, shape, DataType.FLOAT)) {
                INDArray inArr = p.getFirst().muli(100);

                SameDiff sd = SameDiff.create();
                SDVariable in = sd.var("in", inArr);
                SDVariable squeeze = sd.squeeze(in, i);
                SDVariable expand = sd.expandDims(squeeze, i);

                INDArray out = sd.execAndEndResult();

                String msg = "expand/Squeeze=" + i + ", source=" + p.getSecond();

                assertEquals(msg, out, inArr);  //squeeze -> expand: should be opposite ops
            }
        }
    }

    @Test
    public void testConfusionMatrix() {
        INDArray labels = Nd4j.create(new float[]{1, 2, 4});
        INDArray pred = Nd4j.create(new float[]{2, 2, 4});
        INDArray weights = Nd4j.create(new float[]{10, 100, 1000});
        Integer numClasses = 5;
        SameDiff sd = SameDiff.create();
        SDVariable labelsVar = sd.var("labels", labels);
        SDVariable predictionsVar = sd.var("predictions", pred);
        SDVariable weightsVar = sd.var("weights", weights);
        sd.confusionMatrix(labelsVar, predictionsVar, numClasses, weightsVar);
        INDArray out = sd.execAndEndResult();

        INDArray exp = Nd4j.create(new float[][]{{0, 0, 0, 0, 0}, {0, 0, 10, 0, 0}, {0, 0, 100, 0, 0},
                {0, 0, 0, 0, 0}, {0, 0, 0, 0, 1000}});

        assertEquals(exp, out);
    }

    @Test
    public void testArgMax() {
        Nd4j.getRandom().setSeed(12345);

        for (val dim : new int[][]{{0}, {1}, {Integer.MAX_VALUE}, {0, 1}, {}}) {
            INDArray inArr = Nd4j.rand(3, 4);
            SameDiff sd = SameDiff.create();

            SDVariable in = sd.var("in", inArr);
            SDVariable argmax = sd.argmax("argmax", in, dim);

            INDArray out = sd.execAndEndResult();

            INDArray exp = Nd4j.argMax(inArr, dim);

            assertEquals(exp, out);
        }
    }

    @Test
    public void testArgMin() {

        Nd4j.getRandom().setSeed(12345);

        for (val dim : new int[][]{{0}, {1}, {Integer.MAX_VALUE}, {0, 1}, {}}) {
            INDArray inArr = Nd4j.rand(3, 4);
            SameDiff sd = SameDiff.create();

            SDVariable in = sd.var("in", inArr);
            SDVariable argmin = sd.argmin("argmin", in, dim);

            INDArray out = sd.execAndEndResult();

            INDArray exp = Nd4j.argMax(inArr.neg(), dim);   //argmin(x) == argmax(-x)

            assertEquals(exp, out);
        }
    }

    @Test
    public void testScatterAdd() {
        INDArray arr1 = Nd4j.zeros(3, 3);
        INDArray arr2 = Nd4j.createFromArray(0,1);
        INDArray arr3 = Nd4j.ones(2, 3);
        INDArray expected = Nd4j.create(new float[]{1, 1, 1,
                        1, 1, 1,
                        0, 0, 0},
                new long[]{3, 3}).castTo(Nd4j.defaultFloatingPointType());

        SameDiff sd  = SameDiff.create();
        SDVariable refs = sd.var("refs", arr1);
        SDVariable idxs = sd.var("idxs", arr2);
        SDVariable upds = sd.var("upds", arr3);

        SDVariable result = sd.scatterAdd(refs, idxs, upds);
        assertArrayEquals(new long[]{3, 3}, result.eval().shape());
        assertEquals(expected, result.eval());

    }

    @Test
    public void testScatterMul() {
        INDArray arr1 = Nd4j.ones(3, 3);
        INDArray arr2 = Nd4j.createFromArray(0,1);
        INDArray arr3 = Nd4j.zeros(2, 3);
        INDArray expected = Nd4j.create(new float[]{0, 0, 0,
                        0, 0, 0,
                        1, 1, 1},
                new long[]{3, 3}).castTo(Nd4j.defaultFloatingPointType());

        SameDiff sd  = SameDiff.create();
        SDVariable refs = sd.var("refs", arr1);
        SDVariable idxs = sd.var("idxs", arr2);
        SDVariable upds = sd.var("upds", arr3);

        SDVariable result = sd.scatterMul(refs, idxs, upds);
        assertArrayEquals(new long[]{3, 3}, result.eval().shape());
        assertEquals(expected, result.eval());

    }

    @Test
    public void testScatterSub() {
        INDArray arr1 = Nd4j.ones(3, 3);
        INDArray arr2 = Nd4j.createFromArray(0,1);
        INDArray arr3 = Nd4j.ones(2, 3);
        INDArray expected = Nd4j.create(new float[]{0, 0, 0,
                        0, 0, 0,
                        1, 1, 1},
                new long[]{3, 3}).castTo(Nd4j.defaultFloatingPointType());

        SameDiff sd  = SameDiff.create();
        SDVariable refs = sd.var("refs", arr1);
        SDVariable idxs = sd.var("idxs", arr2);
        SDVariable upds = sd.var("upds", arr3);

        SDVariable result = sd.scatterSub(refs, idxs, upds);
        assertArrayEquals(new long[]{3, 3}, result.eval().shape());
        assertEquals(expected, result.eval());

    }

    @Test
    public void testScatterDiv() {
        INDArray arr1 = Nd4j.ones(3, 3);
        INDArray arr2 = Nd4j.createFromArray(0,1);
        INDArray arr3 = Nd4j.ones(2, 3).assign(2);
        INDArray expected = Nd4j.create(new float[]{0.5f, 0.5f, 0.5f,
                        0.5f, 0.5f, 0.5f,
                        1.0f, 1.0f, 1.0f},
                new long[]{3, 3}).castTo(Nd4j.defaultFloatingPointType());

        SameDiff sd  = SameDiff.create();
        SDVariable refs = sd.var("refs", arr1);
        SDVariable idxs = sd.var("idxs", arr2);
        SDVariable upds = sd.var("upds", arr3);

        SDVariable result = sd.scatterDiv(refs, idxs, upds);
        assertArrayEquals(new long[]{3, 3}, result.eval().shape());
        assertEquals(expected, result.eval());
    }

    @Test
    public void testScatterMax() {
        INDArray arr1 = Nd4j.ones(3, 3);
        INDArray arr2 = Nd4j.createFromArray(0,1);
        INDArray arr3 = Nd4j.ones(2, 3).assign(2);
        INDArray expected = Nd4j.create(new float[]{2.0f, 2.0f, 2.0f,
                        2.0f, 2.0f, 2.0f,
                        1.0f, 1.0f, 1.0f},
                new long[]{3, 3}).castTo(Nd4j.defaultFloatingPointType());

        SameDiff sd  = SameDiff.create();
        SDVariable refs = sd.var("refs", arr1);
        SDVariable idxs = sd.var("idxs", arr2);
        SDVariable upds = sd.var("upds", arr3);

        SDVariable result = sd.scatterMax(refs, idxs, upds);
        assertArrayEquals(new long[]{3, 3}, result.eval().shape());
        assertEquals(expected, result.eval());
    }

    @Test
    public void testScatterMin() {
        INDArray arr1 = Nd4j.ones(3, 3);
        INDArray arr2 = Nd4j.createFromArray(1,2);
        INDArray arr3 = Nd4j.ones(2, 3).assign(-2.0f);
        INDArray expected = Nd4j.create(new float[]{1.0f, 1.0f, 1.0f,
                        -2.0f, -2.0f, -2.0f,
                        -2.0f, -2.0f, -2.0f},
                new long[]{3, 3}).castTo(Nd4j.defaultFloatingPointType());

        SameDiff sd  = SameDiff.create();
        SDVariable refs = sd.var("refs", arr1);
        SDVariable idxs = sd.var("idxs", arr2);
        SDVariable upds = sd.var("upds", arr3);

        SDVariable result = sd.scatterMin(refs, idxs, upds);
        assertArrayEquals(new long[]{3, 3}, result.eval().shape());
        assertEquals(expected, result.eval());
    }

    @Test
    public void testReciprocal() {
        INDArray inArr = Nd4j.linspace(1,4,4, DataType.FLOAT).reshape(2,2);
        INDArray expected = Nd4j.onesLike(inArr).divi(inArr);
        SameDiff sd = SameDiff.create();
        SDVariable in = sd.var("in", inArr);
        SDVariable reciprocal = sd.reciprocal(in);
        INDArray res = reciprocal.eval();
        assertEquals(expected,res);
    }

    @Test
    public void testGather2(){

        INDArray in = Nd4j.rand(DataType.FLOAT, 10,10);
        INDArray indices = Nd4j.createFromArray(0,1,5);

        SameDiff sd = SameDiff.create();

        SDVariable var = sd.var("in", in);
        SDVariable varIndices = sd.var("indices", indices);
        SDVariable gather = sd.gather(var, varIndices, 0);

        System.out.println(in);

        INDArray exp = Nd4j.pullRows(in, 1, new int[]{0,1,5});  //Along dimension 1 -> equiv to "indexes for axis 0"
        INDArray act = sd.execAndEndResult();

        assertEquals(exp, act);
    }

    @Test
    public void testGatherOp(){

        INDArray in = Nd4j.rand(DataType.DOUBLE, 10,10);
        INDArray indices = Nd4j.createFromArray(0,1,5);
        INDArray out = Nd4j.create(3, 10);

        DynamicCustomOp op = DynamicCustomOp.builder("gather")
                .addIntegerArguments(0) //Indexes are for dimension 0
                .addInputs(in, indices)
                .addOutputs(out)
                .build();

        Nd4j.getExecutioner().exec(op);

        System.out.println(out);

        INDArray exp = Nd4j.pullRows(in, 1, new int[]{0,1,5});  //Along dimension 1 == indexes for dimension 0

        assertEquals(exp, out);

        //Shape function:
        val shapes = Nd4j.getExecutioner().calculateOutputShape(op);
        long[] expShape = new long[]{3,10};

        assertEquals(1, shapes.size());

        assertArrayEquals(expShape, shapes.get(0).getShape());
    }


    @Test
    public void testConditions() {

        SameDiff sd = SameDiff.create();

        INDArray ia = Nd4j.create(new float[]{4, 2});
        SDVariable in = sd.var("in", 1, 2);
        sd.associateArrayWithVariable(ia, in);


        INDArray expFinite = Nd4j.create(new boolean[]{true, true});
        SDVariable finite = sd.isFinite(in);

        INDArray expInfinite = Nd4j.create(new boolean[]{false,  false});
        SDVariable infinite = sd.isInfinite(in);

        INDArray expNaN =  Nd4j.create(new boolean[]{false, false});
        SDVariable isnan = sd.isNaN(in);

        sd.exec(Collections.emptyMap(), sd.outputs());
        assertEquals(expFinite, finite.getArr());
        assertEquals(expInfinite, infinite.getArr());
        assertEquals(expNaN, isnan.getArr());

    }


    private static int binArrToInt(int[] arr){
        int x = 0;
        int m = 1;
        for(int i = arr.length - 1; i >= 0; i--){
            if(arr[i] == 1){
                x += m;
            }
            m *= 2;
        }
        return x;
    }
    @Test
    public void testGet(){

        SameDiff sd  = SameDiff.create();
        INDArray arr = Nd4j.create(10, 10);
        SDVariable x = sd.var(arr);

        INDArray expOut1 = arr.get(NDArrayIndex.point(4), NDArrayIndex.point(5));
        SDVariable result1 = x.get(SDIndex.point(4), SDIndex.point(5));
        assertEquals(expOut1, result1.eval());

        INDArray expOut2 = arr.get(NDArrayIndex.point(4), NDArrayIndex.all());
        SDVariable result2 = x.get(SDIndex.point(4), SDIndex.all());
        assertEquals(expOut2, result2.eval());

        INDArray expOut3 = arr.get(NDArrayIndex.interval(3, 8));
        SDVariable result3 = x.get(SDIndex.interval(3, 8));
        assertEquals(expOut3, result3.eval());


        INDArray expOut4 = arr.get(NDArrayIndex.point(5), NDArrayIndex.interval(3, 8));
        SDVariable result4 = x.get(SDIndex.point(5), SDIndex.interval(3, 8));
        assertEquals(expOut4, result4.eval());


    }

    @Test
    public void testTensorArray1(){
        SameDiff sd = SameDiff.create();
        TensorArray tensorArray = sd.tensorArray(DataType.FLOAT);
        INDArray arr1 = Nd4j.create(new double[]{1,2,3,4}, new int[]{2, 2});
        SDVariable var1 = sd.var(arr1);
        INDArray arr2 = Nd4j.create(new double[]{5, 6, 7, 8}, new int[]{2, 2});
        SDVariable var2 = sd.var(arr2);
        SDVariable write0 = tensorArray.write(var2, 0, var1);
        SDVariable write1 = tensorArray.write(write0,1, var2);
        SDVariable result = tensorArray.stack(write1);
        sd.exec(null, result.getVarName());
        assertEquals(Nd4j.pile(arr1, arr2), result.eval());
    }

    @Test
    public void testTensorArray2(){
        SameDiff sd = SameDiff.create();
        TensorArray tensorArray = sd.tensorArray(DataType.FLOAT);
        INDArray arr1 = Nd4j.create(new double[]{1, 2, 3, 4}, new int[]{2, 2});
        SDVariable var1 = sd.var(arr1);
        INDArray arr2 = Nd4j.create(new double[]{5, 6, 7, 8}, new int[]{2, 2});
        SDVariable var2 = sd.var(arr2);
        SDVariable write1 = tensorArray.write(var2,0, var1);
        SDVariable write2 = tensorArray.write(write1, 1, var2);
        SDVariable result1 = tensorArray.read(0);
        SDVariable result2 = tensorArray.read(1);

    }
    @Test
    public void testTensorArray3(){
        SameDiff sd = SameDiff.create();
        TensorArray tensorArray = sd.tensorArray(DataType.FLOAT);
        INDArray arr1 = Nd4j.create(new double[]{1, 2, 3, 4}, new int[]{ 2, 2});
        INDArray arr2 = Nd4j.create(new double[]{5, 6, 7, 8}, new int[]{2, 2});
        INDArray arr3 = Nd4j.pile(arr1, arr2);
        SDVariable var = sd.var(arr3);
        SDVariable unstack = tensorArray.unstack(var, var);
        SDVariable result1 = tensorArray.read(0);
        SDVariable result2 = tensorArray.read(1);
        result1.addControlDependency(unstack);
        result2.addControlDependency(unstack);
        assertEquals(arr1, result1.eval());
        assertEquals(arr2, result2.eval());
    }

    @Test
    public void testFill(){
        SameDiff sd = SameDiff.create();
        INDArray arr = Nd4j.createFromArray(2,2);
        INDArray expOut = Nd4j.valueArrayOf(new int[]{2,2}, 42.0);
        SDVariable x = sd.var(arr);
        SDVariable result = sd.fill(x, DataType.DOUBLE, 42);
        assertEquals(expOut, result.eval());
    }
    private static <T> T getObject(String fieldName, Object from, Class<?> fromClass){
        try {
            Field f = fromClass.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (T)f.get(from);
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testPermute(){
        SameDiff sd  = SameDiff.create();
        INDArray arr = Nd4j.create(new double[]{
                        /////////////
                        1, 2, 3, 4,
                        5, 6, 7, 8,
                        9, 10, 11, 12,
                        //////////////
                        13, 14, 15, 16,
                        17, 18, 19, 20,
                        21, 22, 23, 24
                        /////////////
                },
                new int[]{2, 3, 4});

        INDArray expOut = Nd4j.create(new double[]{
                        /////////////
                        1, 2, 3, 4,
                        13, 14, 15, 16,
                        /////////////
                        5, 6, 7, 8,
                        17, 18, 19, 20,
                        /////////////
                        9, 10, 11, 12,
                        21, 22, 23, 24
                        /////////////
                },
                new int[]{3, 2, 4});


        SDVariable x = sd.var(arr);
        SDVariable result = sd.permute(x, 1, 0, 2);
        assertEquals(expOut, result.eval());

    }


    @Test
    public void testExecutionDifferentShapesAccumAlongDim(){
        SameDiff sd = SameDiff.create();
        SDVariable in = sd.var("in", Nd4j.linspace(1,12,12, DataType.FLOAT).reshape(3,4));

        SDVariable sum = in.sum(1);
        INDArray exp = in.getArr().sum(1).reshape(3);

        INDArray out = sd.execAndEndResult();
        assertEquals(exp, out);

        //Now, replace with minibatch 5:
        in.setArray(Nd4j.linspace(1,20,20, DataType.FLOAT).reshape(5,4));
        INDArray out2 = sd.execAndEndResult();
        assertArrayEquals(new long[]{5}, out2.shape());

        exp = in.getArr().sum(1).reshape(5);
        assertEquals(exp, out2);
    }

    @Test
    public void testExecutionDifferentShapesIndexAccumAlongDim(){
        SameDiff sd = SameDiff.create();
        SDVariable in = sd.var("in", Nd4j.linspace(1,12,12, DataType.FLOAT).reshape(3,4));

        SDVariable sum = in.argmax(1);
        INDArray exp = in.getArr().argMax(1).reshape(3);

        INDArray out = sd.execAndEndResult();
        assertEquals(exp, out);

        //Now, replace with minibatch 5:
        in.setArray(Nd4j.linspace(1,20,20, DataType.FLOAT).reshape(5,4));
        INDArray out2 = sd.execAndEndResult();
        assertArrayEquals(new long[]{5}, out2.shape());

        exp = in.getArr().argMax(1).reshape(5);
        assertEquals(exp, out2);
    }

    @Test
    public void testExternalErrorsSimple(){
        INDArray externalGrad = Nd4j.linspace(1,12,12, DataType.FLOAT).reshape(3,4);

        SameDiff sd = SameDiff.create();
        SDVariable var = sd.var("var", externalGrad);
        SDVariable out = var.mul("out", 0.5);

        Map<String,INDArray> gradMap = new HashMap<>();
        gradMap.put("out", externalGrad);
        ExternalErrorsFunction fn = sd.f().externalErrors(out);
        //new ExternalErrorsFunction(sd, Collections.singletonList(out), gradMap);

        fn.updateVariable("out", externalGrad);
        sd.execAndEndResult();
        sd.execBackwards(Collections.emptyMap());

        INDArray gradOut = out.getGradient().getArr();
        INDArray gradVar = var.getGradient().getArr();

        assertEquals(externalGrad, gradOut);
        assertEquals(externalGrad.mul(0.5), gradVar);

        //Now, update and execute again:
        externalGrad = Nd4j.linspace(1,12,12, DataType.FLOAT).reshape(3,4).muli(10);
        fn.updateVariable("out", externalGrad);

        sd.execBackwards(Collections.emptyMap());

        gradOut = out.getGradient().getArr();
        gradVar = var.getGradient().getArr();

        assertEquals(externalGrad, gradOut);
        assertEquals(externalGrad.mul(0.5), gradVar);
    }

    @Test
    public void testUpdatingGradient(){
        Nd4j.getRandom().setSeed(12345);

        SameDiff sd = SameDiff.create();
        SDVariable in = sd.var("in", Nd4j.linspace(1,12,12, DataType.FLOAT).reshape(3,4));
        SDVariable w = sd.var("w", Nd4j.linspace(1,20,20, DataType.FLOAT).reshape(4,5));
        SDVariable out = sd.mmul(in, w);
        SDVariable loss = out.std("out", true);

        INDArray outArr = sd.execAndEndResult().dup();
        sd.execBackwards(Collections.emptyMap());

        Map<String,INDArray> origGrad = new HashMap<>();
        origGrad.put("in", in.gradient().getArr().dup());
        origGrad.put("w", w.gradient().getArr().dup());
        origGrad.put("out", out.gradient().getArr().dup());

        in.getArr().assign(Nd4j.rand(in.getArr().shape()));
        INDArray outArr2 = sd.execAndEndResult();
        sd.execBackwards(Collections.emptyMap());

        assertNotEquals(outArr, outArr2);

        //Ensure gradients are also changed:
        assertNotEquals(origGrad.get("in"), in.gradient().getArr());
        assertNotEquals(origGrad.get("w"), w.gradient().getArr());
        assertNotEquals(origGrad.get("out"), out.gradient().getArr());
    }

    @Test
    public void testUpdatingInplaceFwd(){
        SameDiff sd = SameDiff.create();
        SDVariable in = sd.var("in", Nd4j.linspace(1,12,12, DataType.FLOAT).reshape(3,4));
        SDVariable w = sd.var("w", Nd4j.linspace(1,20,20, DataType.FLOAT).reshape(4,5));
        SDVariable out = sd.mmul(in, w);
        SDVariable loss = out.std("out", true);

        INDArray outArr = sd.execAndEndResult().dup();
        sd.execBackwards(Collections.emptyMap());

        Map<String,INDArray> origGrad = new HashMap<>();
        origGrad.put("in", in.gradient().getArr().dup());
        origGrad.put("w", w.gradient().getArr().dup());
        origGrad.put("out", out.gradient().getArr().dup());

        in.getArr().muli(5);

        //check gradient function copy of array
        SameDiff sdGrad = sd.getFunction("grad");
        INDArray gradArrIn = sdGrad.getVariable("in").getArr();
        assertEquals(in.getArr(), gradArrIn);
    }

    @Test
    public void testUpdatingAssociateFwd(){
        SameDiff sd = SameDiff.create();
        SDVariable in = sd.var("in", Nd4j.linspace(1,12,12, DataType.FLOAT).reshape(3,4));
        SDVariable w = sd.var("w", Nd4j.linspace(1,20,20, DataType.FLOAT).reshape(4,5));
        SDVariable out = sd.mmul(in, w);
        SDVariable loss = out.std("out", true);

        INDArray outArr = sd.execAndEndResult().dup();
        sd.execBackwards(Collections.emptyMap());

        Map<String,INDArray> origGrad = new HashMap<>();
        origGrad.put("in", in.gradient().getArr().dup());
        origGrad.put("w", w.gradient().getArr().dup());
        origGrad.put("out", out.gradient().getArr().dup());

        INDArray newIn = in.getArr().dup().muli(5);
        in.setArray(newIn);

        //check gradient function copy of array
        SameDiff sdGrad = sd.getFunction("grad");
        INDArray gradArrIn = sdGrad.getVariable("in").getArr();
        assertEquals(newIn, gradArrIn);
    }

    @Test
    public void testUpdatingGradientSimple() {
        SameDiff sd = SameDiff.create();
        SDVariable in = sd.var("in", Nd4j.linspace(1,12,12, DataType.FLOAT).reshape(3,4));
        SDVariable out = in.mul(2.0);
        SDVariable loss = out.std("out", true);

        INDArray outArr = sd.execAndEndResult().dup();
        sd.execBackwards(Collections.emptyMap());

        SameDiff sdGrad = sd.getFunction("grad");

        Map<String,INDArray> origGrad = new HashMap<>();
        origGrad.put("in", in.gradient().getArr().dup());
        origGrad.put("out", out.gradient().getArr().dup());

        double stdBefore = in.getArr().stdNumber().doubleValue();
        in.getArr().assign(Nd4j.rand(in.getArr().shape()));
        double stdAfter = in.getArr().stdNumber().doubleValue();
        System.out.println("Before vs. after: " + stdBefore + ", " + stdAfter);
        INDArray outArr2 = sd.execAndEndResult();
        sd.execBackwards(Collections.emptyMap());

        assertNotEquals(outArr, outArr2);

        //Ensure gradients are also changed:
        assertNotEquals(origGrad.get("in"), in.gradient().getArr());
        assertNotEquals(origGrad.get("out"), out.gradient().getArr());
    }

    @Test
    public void testShapeUpdating(){

        SameDiff sd = SameDiff.create();
        SDVariable in = sd.var("in", DataType.FLOAT, 3,5);
        SDVariable w = sd.var("W", DataType.FLOAT,5,4);
        SDVariable b = sd.var("b", DataType.FLOAT, 1,4);
        SDVariable z = in.mmul(w).add(b);
        SDVariable out = sd.tanh("tanh", z);
        ExternalErrorsFunction fn = sd.f().externalErrors(out);

        INDArray inA = Nd4j.linspace(1,15,15, DataType.FLOAT).reshape(3,5);
        INDArray wA = Nd4j.linspace(1,20,20, DataType.FLOAT).reshape(5,4);
        INDArray bA = Nd4j.linspace(1,4,4, DataType.FLOAT);
        in.setArray(inA);
        w.setArray(wA);
        b.setArray(bA);

        INDArray grad = Nd4j.linspace(1,12,12, DataType.FLOAT).reshape(3,4);
        fn.updateVariable("tanh", grad);

        log.info("--------------- sd.execAndEndResult() ---------------");
        sd.execAndEndResult();
        log.info("--------------- sd.execBackwards() #1 ---------------");
        sd.execBackwards(Collections.emptyMap());

        log.info("--------------- sd.execBackwards() #2 ---------------");
        System.out.println(sd.getFunction("grad").summary());

        in.setArray(Nd4j.linspace(1, 10, 10, DataType.FLOAT).reshape(2,5));
        grad = Nd4j.linspace(1,8,8, DataType.FLOAT).reshape(2,4);
        fn.updateVariable("tanh", grad);

        sd.execBackwards(Collections.emptyMap());
        INDArray inGrad = in.getGradient().getArr();
        assertArrayEquals(new long[]{2,5}, inGrad.shape());
    }

    @Test
    public void testMultiOutput1(){

        SameDiff sd = SameDiff.create();
        SDVariable in = sd.var("in", Nd4j.create(3,4));
        SDVariable mean = in.mean();
        SDVariable sum = in.sum();

        try{
            sd.createGradFunction();
            fail("Expected exception");
        } catch (IllegalStateException e){
            assertTrue(e.getMessage(), e.getMessage().contains("multiple outputs"));
        }

        SDVariable add = mean.add(sum);
        sd.createGradFunction();
    }

    @Test
    public void testMultiOutput2(){
        //Edge case: no functions
        SameDiff sd = SameDiff.create();
        SDVariable in = sd.var("in", Nd4j.scalar(0.0));
        SDVariable in2 = sd.var("in2", Nd4j.scalar(1.0));

        try{
            sd.createGradFunction();
            fail("Expected exception");
        } catch (IllegalStateException e){
            assertTrue(e.getMessage(), e.getMessage().contains("multiple outputs"));
        }

        SDVariable add = in.add(in2);
        sd.createGradFunction();
    }

}

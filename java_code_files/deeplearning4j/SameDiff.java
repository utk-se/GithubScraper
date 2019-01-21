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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.primitives.Ints;
import com.google.flatbuffers.FlatBufferBuilder;
import com.rits.cloning.Cloner;
import com.rits.cloning.IFastCloner;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.nd4j.autodiff.execution.conf.ExecutorConfiguration;
import org.nd4j.autodiff.execution.conf.OutputMode;
import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.autodiff.functions.DifferentialFunctionFactory;
import org.nd4j.autodiff.loss.LossReduce;
import org.nd4j.autodiff.samediff.internal.*;
import org.nd4j.autodiff.samediff.serde.FlatBuffersMapper;
import org.nd4j.autodiff.util.cloner.DataBufferFastCloner;
import org.nd4j.autodiff.util.cloner.INDArrayFastCloner;
import org.nd4j.base.Preconditions;
import org.nd4j.evaluation.IEvaluation;
import org.nd4j.graph.*;
import org.nd4j.jackson.objectmapper.holder.ObjectMapperHolder;
import org.nd4j.linalg.api.blas.params.MMulTranspose;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.buffer.factory.DataBufferFactory;
import org.nd4j.linalg.api.buffer.util.DataTypeUtil;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.*;
import org.nd4j.linalg.api.ops.executioner.OpExecutioner;
import org.nd4j.linalg.api.ops.impl.controlflow.If;
import org.nd4j.linalg.api.ops.impl.controlflow.While;
import org.nd4j.linalg.api.ops.impl.controlflow.compat.Enter;
import org.nd4j.linalg.api.ops.impl.controlflow.compat.Switch;
import org.nd4j.linalg.api.ops.impl.layers.convolution.config.*;
import org.nd4j.linalg.api.ops.impl.layers.recurrent.GRUCell;
import org.nd4j.linalg.api.ops.impl.layers.recurrent.LSTMCell;
import org.nd4j.linalg.api.ops.impl.layers.recurrent.SRU;
import org.nd4j.linalg.api.ops.impl.layers.recurrent.SRUCell;
import org.nd4j.linalg.api.ops.impl.layers.recurrent.config.GRUCellConfiguration;
import org.nd4j.linalg.api.ops.impl.layers.recurrent.config.LSTMCellConfiguration;
import org.nd4j.linalg.api.ops.impl.layers.recurrent.config.SRUCellConfiguration;
import org.nd4j.linalg.api.ops.impl.layers.recurrent.config.SRUConfiguration;
import org.nd4j.linalg.api.ops.impl.loss.LogLoss;
import org.nd4j.linalg.api.ops.impl.loss.SigmoidCrossEntropyLoss;
import org.nd4j.linalg.api.ops.impl.loss.SoftmaxCrossEntropyLoss;
import org.nd4j.linalg.api.ops.impl.reduce3.CosineSimilarity;
import org.nd4j.linalg.api.ops.impl.reduce3.EuclideanDistance;
import org.nd4j.linalg.api.ops.impl.reduce3.ManhattanDistance;
import org.nd4j.linalg.api.ops.impl.shape.Eye;
import org.nd4j.linalg.api.ops.impl.shape.tensorops.TensorArray;
import org.nd4j.linalg.api.ops.impl.transforms.Assert;
import org.nd4j.linalg.api.ops.impl.transforms.gradient.GradientBackwardsMarker;
import org.nd4j.linalg.api.ops.impl.layers.ExternalErrorsFunction;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.collection.IntArrayKeyMap;
import org.nd4j.linalg.compression.CompressedDataBuffer;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.adapter.MultiDataSetIteratorAdapter;
import org.nd4j.linalg.dataset.adapter.SingletonMultiDataSetIterator;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.exception.ND4JIllegalArgumentException;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.exception.ND4UnresolvedOutputVariables;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.indexing.conditions.Condition;
import org.nd4j.linalg.learning.GradientUpdater;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.AtomicBoolean;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.linalg.util.ArrayUtil;
import org.nd4j.linalg.util.DeviceLocalNDArray;
import org.nd4j.shade.jackson.databind.ObjectMapper;
import org.nd4j.weightinit.WeightInitScheme;
import org.nd4j.weightinit.impl.ConstantInitScheme;
import org.nd4j.weightinit.impl.NDArraySupplierInitScheme;
import org.nd4j.weightinit.impl.ZeroInitScheme;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * SameDiff is the entrypoint for ND4J's automatic differentiation functionality.
 * <p>
 * You define a graph symbolically.
 * <p>
 * That graph accumulates operations.
 * <p>
 * In order to execute the graph, you run one of the execution methods, such as {@link #exec(Map, String...)}
 */
@AllArgsConstructor
@Builder
@Slf4j
public class SameDiff {

    //Fields for graph structure and execution
    @Getter     //TODO use package private instead of public getters?
    private final Map<String,Variable> variables = new HashMap<>();         //TODO concurrent maps required? Or lock?
    @Getter
    private final Map<String,SameDiffOp> ops = new HashMap<>();
    @Getter
    private final Map<Long,InferenceSession> sessions = new ConcurrentHashMap<>();      //Key: thread ID

    private final Map<String,DeviceLocalNDArray> constantArrays = new ConcurrentHashMap<>();
    private final Map<String,DeviceLocalNDArray> variablesArrays = new ConcurrentHashMap<>();     //TODO issues with DeviceLocal +  mutable / changed during training?
    private final Map<Long,Map<String,INDArray>> placeholdersPerThread = new ConcurrentHashMap<>(); //Placeholders for each thread - if the user sets them

    ///////////////////////////////////////
    //Fields related to training
    @Getter
    private TrainingConfig trainingConfig;                          //Configuration for training. Must be set for training/evaluation, but not for other operations
    @Getter
    private boolean initializedTraining;                            //True if training setup has been done
    @Getter
    private INDArray updaterState;                                  //Updater state array (1d, length equal to number of trainable parameters)
    @Getter
    private Map<String,INDArray> updaterViews;                      //Views of updaterState array for each trainable parameter
    @Getter
    private Map<String,GradientUpdater> updaterMap;                 //GradientUpdater instance for each trainable parameter

    ////////////////////////////////////////
    //map a function's instance id to a base name, used for propagating variable names
    //for output during import
    private Map<String, String> baseNameForFunctionInstanceId;

    private DifferentialFunctionFactory functionFactory;
    @Deprecated //TO BE REMOVED - to ShapeSession
    private Map<String, long[]> variableNameToShape;                //Key: SDVariable name. Value: shape for that variable
    @Deprecated //TO BE REMOVED - to Variable
    private Map<String, SDVariable> forwardVarForGrad;

    // counter for auto-naming variables
    private int variableId = 0;



    /**
     * For import, many times we have variables
     * that map to properties. Most common
     * we will have an input to a function that is mapped to an ndarray.
     * That ndarray is usually a scalar shape.
     * <p>
     * That array with a scalar shape can be something like an axis.
     * <p>
     * We often don't know that array's value till run time.
     * This map stores variable names  that we should resolve
     * from samediff. We use the value of that array
     * to update the properties.
     */
    private Map<String, List<String>> propertiesToResolve;

    /**
     * A map of own name to
     * the properties of the function (things like execution axes etc)
     * The valid values can be:
     * int
     * long
     * INDArray
     */
    private Map<String, Map<String, Object>> propertiesForFunction;

    @Deprecated //TO BE REMOVED - to Variable
    private Map<String, long[]> placeHolderOriginalShapes;
    private Map<String, SameDiffFunctionDefinition> sameDiffFunctionDefinitionMap;
    private Map<String, SameDiff> sameDiffFunctionInstances;
    private Set<String> placeHolderFunctions;
    private static Cloner cloner = newCloner();
    private static Map<String, Method> opMethods;

    private Table<String, String, String> fieldVariableResolutionMapping;

    // flag, shows if graph was already registered with libnd4j
    private transient AtomicBoolean wasRegistered = new AtomicBoolean(false);


    //debug mode variables
    @Getter
    private boolean debugMode;
    private Map<int[], Op> opsForResult;
    private boolean resolvedVariables = false;


    @Getter
    @Setter
    boolean logExecution = true;

    @Getter
    private SameDiff parent;

    @Getter
    private SameDiff child;

    public final static String TRAINING_CONFIG_JSON_ZIP_ENTRY_NAME = "trainingConfig.json";
    public final static String SAMEDIFF_FILE_ENTRY_NAME = "samediff.fb";

    static {
        opMethods = new HashMap<>();
        Method[] methods = SameDiff.class.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getReturnType().equals(SDVariable.class)) {
                opMethods.put(method.getName(), method);
            }
        }
    }

    /**
     * @return New cloner object. NOTE: INTENDED FOR DEVELOPER USE ONLY
     */
    public static Cloner newCloner() {
        Cloner cloner = new Cloner();

        //Implement custom cloning for INDArrays (default can have problems with off-heap and pointers)
        //Sadly: the cloner library does NOT support interfaces here, hence we need to use the actual classes
        //cloner.registerFastCloner(INDArray.class, new INDArrayFastCloner());  //Does not work due to interface
        IFastCloner fc = new INDArrayFastCloner();
        cloner.registerFastCloner(Nd4j.getBackend().getNDArrayClass(), fc);

        //Same thing with DataBuffers: off heap -> cloner library chokes on them, but need to know the concrete
        // buffer classes, not just the interface
        IFastCloner fc2 = new DataBufferFastCloner();
        DataBufferFactory d = Nd4j.getDataBufferFactory();
        doReg(cloner, fc2, d.intBufferClass());
        doReg(cloner, fc2, d.longBufferClass());
        doReg(cloner, fc2, d.halfBufferClass());
        doReg(cloner, fc2, d.floatBufferClass());
        doReg(cloner, fc2, d.doubleBufferClass());
        doReg(cloner, fc2, CompressedDataBuffer.class);
        return cloner;
    }

    private static void doReg(Cloner cl, IFastCloner fc, Class<?> c) {
        if (c != null)
            cl.registerFastCloner(c, fc);
    }


    /**
     * Update the opName for the variable
     * with the given vertex id
     *
     * @param varName  the vertex id to update
     * @param withName thew new opName
     */
    public void updateVariableName(String varName, String withName) {
        SDVariable oldVarNameRef = getVariable(varName);
        Variable v = variables.remove(varName);
        String oldVarName = varName;
        oldVarNameRef.setVarName(withName);
        v.setName(withName);
        variables.put(withName, v);

        for(SameDiffOp op : ops.values()){
            List<String> outputsOfOp = op.getOutputsOfOp();
            if(outputsOfOp != null && !outputsOfOp.isEmpty()) {
                for (int i = 0; i < outputsOfOp.size(); i++) {
                    if (outputsOfOp.get(i).equals(oldVarName)) {
                        outputsOfOp.set(i, withName);
                    }
                }
            }

            List<String> inputsToOp = op.getInputsToOp();
            if(inputsToOp != null && !inputsToOp.isEmpty()) {
                for (int i = 0; i < inputsToOp.size(); i++) {
                    if (inputsToOp.get(i).equals(oldVarName)) {
                        inputsToOp.set(i, withName);
                    }
                }
            }
        }

//        if (variableNameToArr.containsKey(oldVarName)) {
//            val arr = variableNameToArr.remove(oldVarName);
//            variableNameToArr.put(withName, arr);
//        }


        if (variableNameToShape.containsKey(oldVarName)) {
            val shape = variableNameToShape.remove(oldVarName);
            variableNameToShape.put(withName, shape);
        }

        if (forwardVarForGrad.containsKey(oldVarName)) {
            val forwardGrad = forwardVarForGrad.remove(oldVarName);
            forwardVarForGrad.put(withName, forwardGrad);
        }


        if (v.getInputsForOp() != null) {
            List<String> funcNames = v.getInputsForOp();
            for (String s : funcNames) {
                DifferentialFunction func = ops.get(s).getOp();
                if (func instanceof BaseOp) {
                    BaseOp baseOp = (BaseOp) func;
                    if (baseOp.getXVertexId() != null && baseOp.getXVertexId().equals(oldVarName)) {
                        baseOp.setXVertexId(withName);
                    }

                    if (baseOp.getYVertexId() != null && baseOp.getYVertexId().equals(oldVarName)) {
                        baseOp.setYVertexId(withName);
                    }

                    if (baseOp.getZVertexId() != null && baseOp.getZVertexId().equals(oldVarName)) {
                        baseOp.setZVertexId(withName);
                    }

                }
            }
        }


        if (v.getOutputOfOp() != null) {
            DifferentialFunction func = ops.get(v.getOutputOfOp()).getOp();
            if (func instanceof BaseOp) {
                BaseOp baseOp = (BaseOp) func;
                if (baseOp.getXVertexId() != null && baseOp.getXVertexId().equals(oldVarName)) {
                    baseOp.setXVertexId(withName);
                }

                if (baseOp.getYVertexId() != null && baseOp.getYVertexId().equals(oldVarName)) {
                    baseOp.setYVertexId(withName);
                }

                if (baseOp.getZVertexId() != null && baseOp.getZVertexId().equals(oldVarName)) {
                    baseOp.setZVertexId(withName);
                }
            }
        }
    }


    /**
     * Clears debugging state and disables debug mode.
     */
    public SameDiff disableDebugging() {
        debugMode = false;
        return this;
    }

    /**
     * Enables tracing of graphs automatically.
     */
    public SameDiff enableDebugMode() {
        debugMode = true;
        return this;
    }

    /**
     * Returns this samediff instance's {@link DifferentialFunctionFactory}
     *
     * @return
     */
    public DifferentialFunctionFactory f() {
        return functionFactory;
    }


    /**
     * @param sameDiff
     * @return
     */
    public SDVariable invokeGraphOn(SameDiff sameDiff) {
        //map the new vertices on to the old ones
        Map<Integer, Integer> thisVertexIdToNew = new HashMap<>();
        int idx = 1;
        for (val var : variables()) {
            val clone = cloner.deepCloneDontCloneInstances(var, var.getSameDiff());
            val newVar = sameDiff.var(clone);
            if (var.getArr() != null && var.getVariableType() != VariableType.ARRAY) {      //ARRAY type = "activations" - are overwritten anyway
                sameDiff.associateArrayWithVariable(var.getArr(), newVar);
            }


            thisVertexIdToNew.put(idx, idx);
            clone.setSameDiff(sameDiff);
            idx++;

        }


        val newFunctions = new LinkedHashMap<String, DifferentialFunction>();
        for (SameDiffOp op : ops.values()) {
            DifferentialFunction function = op.getOp();
            if (function instanceof SDVariable) {
                continue;
            }

            DifferentialFunction clone = cloner.deepCloneDontCloneInstances(
                    function,
                    function.getSameDiff());
            clone.setSameDiff(sameDiff);
            clone.setOwnName(function.getOwnName());
            if (sameDiff.functionExists(function.getOwnName()))
                sameDiff.putFunctionForId(function.getOwnName(), function);
            newFunctions.put(function.getOwnName(), clone);

            val argsForFunction = function.args();
            val outputsForFunction = function.outputVariables();


            //note that these have the same variable names
            sameDiff.addArgsFor(argsForFunction, clone);
            sameDiff.addOutgoingFor(outputsForFunction, function);

            for (val arg : clone.args()) {
                arg.setSameDiff(sameDiff);
            }

            for (val output : clone.outputVariables()) {
                output.setSameDiff(sameDiff);
            }

            sameDiff.ops.put(function.getOwnName(), op);
        }

        return sameDiff.variables().get(sameDiff.variables().size() - 1);

    }


    /**
     * Returns true if the given function id exists
     *
     * @param id the function id to test for
     * @return true if the function id exists, false otherwise
     */
    public boolean functionExists(String id) {
        return ops.containsKey(id);
    }

    public DifferentialFunction functionOutputFor(String varName){
        if(variables.get(varName).getOutputOfOp() == null)
            return null;
        String outName = variables.get(varName).getOutputOfOp();
        if(outName == null)
            return null;
        return ops.get(outName).getOp();
    }

    /**
     * Get the function by the {@link DifferentialFunction#getOwnName()}
     *
     * @param id the id of the function
     * @return the function for the given id if it exists
     */
    public DifferentialFunction getFunctionById(@NonNull String id) {
        if (!ops.containsKey(id)) {
            throw new ND4JIllegalStateException("No function with id " + id + " found!");
        }
        return ops.get(id).getOp();
    }


    /**
     * Put the function for the given id
     *
     * @param id       the id of the function
     * @param function the function
     */
    public void putFunctionForId(String id, DifferentialFunction function) {
        if (ops.containsKey(id) && ops.get(id).getOp() == null) {
            throw new ND4JIllegalStateException("Function by id already exists!");
        } else if (function instanceof SDVariable) {
            throw new ND4JIllegalStateException("Function must not be a variable!");
        }

        if(ops.containsKey(id)){

        } else {
            ops.put(id, SameDiffOp.builder().name(id).op(function).build());
        }
    }


    /**
     * Returns the name(s) of the inputs for the given function
     *
     * @param function the function to get the inputs for
     * @return the input ids for a given function
     */
    public String[] getInputsForFunction(DifferentialFunction function) {
        if (!ops.containsKey(function.getOwnName()))
            throw new ND4JIllegalStateException("Illegal function instance id found " + function.getOwnName());
        List<String> inputs = ops.get(function.getOwnName()).getInputsToOp();
        return inputs == null ? null : inputs.toArray(new String[inputs.size()]);
    }

    /**
     * Returns the name(s) of the outputs for the given function
     *
     * @param function the function to get the outputs for
     * @return the outputs ids for a given function
     */
    public String[] getOutputsForFunction(DifferentialFunction function) {
        if (!ops.containsKey(function.getOwnName()))
            throw new ND4JIllegalStateException("Illegal function instance id found " + function.getOwnName());
        List<String> outputs = ops.get(function.getOwnName()).getOutputsOfOp();
        return outputs == null ? null : outputs.toArray(new String[outputs.size()]);
    }


    /**
     * Get the output variable(s) for the specified differential function
     *
     * @param function the function reference to get the output variable(s) for
     * @return the output variables for the given function
     */
    public SDVariable[] getOutputVariablesForFunction(DifferentialFunction function) {
        val inputs = getOutputsForFunction(function);
        if (inputs == null) {
            throw new ND4JIllegalStateException("No inputs found for function " + function);
        }

        val vars = new SDVariable[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            vars[i] = getVariable(inputs[i]);
        }

        return vars;
    }


    /**
     * Get the input variable(s) for the specified differential function
     *
     * @param function the function reference to get the input variable(s) for
     * @return the input variables for the given function
     */
    public SDVariable[] getInputVariablesForFunction(DifferentialFunction function) {
        val inputs = getInputsForFunction(function);
        if (inputs == null) {
            throw new ND4JIllegalStateException("No inputs found for function " + function);
        }

        val vars = new SDVariable[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            vars[i] = getVariable(inputs[i]);
            if (vars[i] == null) {
                throw new ND4JIllegalStateException("Found null variable at index " + i);
            }
        }

        return vars;
    }


    public void setArrayForVariable(@NonNull String varName, @NonNull INDArray arr){
        Preconditions.checkState(variables.containsKey(varName), "No variable with name \"%s\" exists", varName);

        SDVariable v = getVariable(varName);
        if(v.isConstant()) {
            constantArrays.put(varName, new DeviceLocalNDArray(arr));
        } else if(v.getVariableType() == VariableType.VARIABLE) {
            variablesArrays.put(varName, new DeviceLocalNDArray(arr));
        } else if(v.isPlaceHolder()){
            long tid = Thread.currentThread().getId();
            if(!placeholdersPerThread.containsKey(tid)){
                placeholdersPerThread.put(tid, new HashMap<String, INDArray>());
            }
            placeholdersPerThread.get(tid).put(varName, arr);
        } else {
            throw new UnsupportedOperationException("Cannot set variable of type " + v.getVariableType() + " using this method");
        }
    }


    /**
     * Get the shape for the given vertex id.
     * Note that if an array is defined, it will use the shape of the array instead.
     * <p>
     * A shape *and* an array should not be defined at the same time.
     * This wastes memory. The internal map used for tracking shapes for particular
     * vertex ids should also delete redundant shapes stored to avoid redundant sources of information.
     *
     * @param varName the vertex id to get the shape for
     * @return the shape for the given vertex if any.
     */
    public long[] getShapeForVarName(String varName) {
        if (arrayAlreadyExistsForVarName(varName)) {
            return getVariable(varName).getArr().shape();
        }
        return variableNameToShape.get(varName);
    }

    public LongShapeDescriptor getShapeDescriptorForVarName(String varName) {
        if (getVariable(varName).getArr() != null) {
            return getVariable(varName).getArr().shapeDescriptor();
        }
        // FIXME: do we really want this Nd4j.dataType() here?
        return LongShapeDescriptor.fromShape(variableNameToShape.get(varName), Nd4j.dataType());
    }

    /**
     * Update a vertex id with the given shape.<br>
     * Note that you should use {@link #putShapeForVarName(String, long[])} if you want to add a new shape.
     * Update is meant to be an in place replacement of the shape for the vertex id *only*.
     *
     * @param varName the vertex id to associate
     * @param shape   the shape to associate with
     * @param clearArrayOnShapeMismatch boolean to indicate whether to clear the variable on shape mismatch
     * @see #putShapeForVarName(String, long[])
     * @see #putOrUpdateShapeForVarName(String, long[], boolean)
     */
    public void updateShapeForVarName(@NonNull String varName, @NonNull long[] shape, boolean clearArrayOnShapeMismatch) {
        if (shape == null) {
            throw new ND4JIllegalStateException("Null shapes not allowed!");
        }

        /*
        if (variableNameToArr.containsKey(varName) && !Arrays.equals(variableNameToArr.get(varName).shape(), shape)) {
            if(clearArrayOnShapeMismatch){
                if(log.isTraceEnabled()){
                    log.trace("Clearing array for variable {}: array shape {}, new shape {}", varName,
                            Arrays.toString(variableNameToArr.get(varName).shape()), Arrays.toString(shape));
                }
                variableNameToArr.remove(varName);
            } else {
                throw new ND4JIllegalStateException("Already found an existing array for variable \"" + varName
                        + "\" with shape " + Arrays.toString(variableNameToArr.get(varName).shape())
                        + " - attempting to put new array shape " + Arrays.toString(shape));
            }
        }


        if(log.isTraceEnabled()){
            long[] pShape = variableNameToShape.get(varName);
            log.trace("Updated shape for variable \"{}\": previous shape {}, new shape {}", varName,
                    (pShape == null ? "<not set>" : Arrays.toString(pShape)), Arrays.toString(shape));
        }
        variableNameToShape.put(varName, shape);
        */
        throw new UnsupportedOperationException("Not yet reimplemented");
    }


    /**
     * Associate a vertex id with the given shape.
     *
     * @param varName the vertex id to associate
     * @param shape   the shape to associate with
     * @see #putShapeForVarName(String, long[])
     * @see #putOrUpdateShapeForVarName(String, long[], boolean)
     */
    @Deprecated
    public void putShapeForVarName(String varName, long[] shape) {
        if (shape == null) {
            throw new ND4JIllegalStateException("Shape must not be null!");
        }

        if (variableNameToShape.containsKey(varName)) {
            throw new ND4JIllegalStateException("Shape for " + varName + " already exists!");
        }

        variableNameToShape.put(varName, shape);
    }


    public void putShapeForVarName(String varName, LongShapeDescriptor shape) {
        val v = getVariable(varName);
        putShapeForVarName(varName, shape.getShape());
        v.setDataType(shape.dataType());
    }

    /**
     * Put or update the shape for the given variable name. Optionally supports clearing the specified variable's
     * INDArray if it's shape does not match the new shape
     * @param varName                   Variable name
     * @param shape                     Shape to put
     * @param clearArrayOnShapeMismatch If false: no change to arrays. If true: if an INDArray is defined for the specified
     *                                  variable name, it will be removed from the graph (to be later re-generated) if
     *                                  its shape does not match the specified shape
     */
    @Deprecated
    public void putOrUpdateShapeForVarName(String varName, long[] shape, boolean clearArrayOnShapeMismatch){
        Preconditions.checkNotNull(shape, "Cannot put null shape for variable: %s", varName);
        if(variableNameToShape.containsKey(varName)){
            updateShapeForVarName(varName, shape, clearArrayOnShapeMismatch);
        } else {
            putShapeForVarName(varName, shape);
        }
    }

    /**
     * Returns true if the given vertex id and shape already exist.
     *
     * @param varName the vertex id
     * @return true if the ndarray and vertex id already exist
     */
    public boolean shapeAlreadyExistsForVarName(String varName) {
        return variableNameToShape.containsKey(varName) || arrayAlreadyExistsForVarName(varName);
    }


    /**
     * Returns true if the given vertex id and {@link INDArray} already exist.
     *
     * @param varName the vertex id
     * @return true if a vertex with the given INDArray exists, and it has an INDArray associated with it
     */
    public boolean arrayAlreadyExistsForVarName(String varName) {
        SDVariable var = getVariable(varName);
        switch(var.getVariableType()){
            case VARIABLE:
                return variablesArrays.containsKey(varName);
            case ARRAY:
                long tid = Thread.currentThread().getId();
                return sessions.containsKey(tid) && sessions.get(tid).contains(varName, InferenceSession.OUTER_FRAME, 0);
            case CONSTANT:
                return constantArrays.containsKey(varName);
            case PLACEHOLDER:
                return placeholdersPerThread.containsKey(Thread.currentThread().getId()) &&
                        placeholdersPerThread.get(Thread.currentThread().getId()).containsKey(varName);
            default:
                throw new RuntimeException("Unknown variable type: " + var.getVariableType());
        }
    }

    /**
     * Get an {@link INDArray} for a given vertex id, or null if none exists
     *
     * @param varName Variable name to get the array for
     * @return Array, or null if none exists
     */
    public INDArray getArrForVarName(@NonNull String varName) {
        Preconditions.checkState(variables.containsKey(varName), "No variable found with name \"%s\"", varName);
        SDVariable v = variables.get(varName).getVariable();
        switch(v.getVariableType()){
            case VARIABLE:
                if(!variablesArrays.containsKey(varName)) {
                    //VARIBALE type arrays should have a parameter initializer...
                    // we should use this to azy init the array if none is present
                    v.storeAndAllocateNewArray();
                }
                return variablesArrays.get(varName).get();
            case CONSTANT:
                if(!constantArrays.containsKey(varName))
                    return null;
                return constantArrays.get(varName).get();
            case ARRAY:
                //Only stored in inference session...
                InferenceSession s = sessions.get(Thread.currentThread().getId());
                if(s == null)
                    return null;
                return s.get(varName, InferenceSession.OUTER_FRAME, 0, false);
            case PLACEHOLDER:
                long tid = Thread.currentThread().getId();
                if(placeholdersPerThread.get(tid) == null || !placeholdersPerThread.get(tid).containsKey(varName))
                    return null;
                return placeholdersPerThread.get(tid).get(varName);
            default:
                throw new RuntimeException("Unknown variable type: " + v.getVariableType());
        }
    }

    /**
     * Associate the array with the given variable.
     *
     * @param arr      the array to get the variable for
     * @param variable the name of the variable to associate the array with
     */
    public void associateArrayWithVariable(INDArray arr, @NonNull String variable) {
    Preconditions.checkState(variables.containsKey(variable), "Cannot associate array with variable \"%s\": " +
            "variable \"%s\" does not exist in this SameDiff instance", variable, variable);
        associateArrayWithVariable(arr, this.getVariable(variable));
    }

    /**
     * Associate the array with the given variable.
     *
     * @param arr      the array to get the variable for
     * @param variable the variable to associate the array with
     */
    public void associateArrayWithVariable(INDArray arr, SDVariable variable) {
        if (variable == null) {
            throw new ND4JIllegalArgumentException("Variable must not be null!");
        }
        if (arr == null) {
            throw new ND4JIllegalArgumentException("Array must not be null");
        }

        if (variable.dataType() != arr.dataType())
            arr = arr.castTo(variable.dataType());

        Preconditions.checkState(variable.dataType() == arr.dataType(), "Variable \"%s\" has datatype %s: cannot associate array with type %s with this variable",
                variable.getVarName(), variable.dataType(), arr.dataType());

        // FIXME: remove this before release
        if (sessions.get(Thread.currentThread().getId()) == null) {
            sessions.put(Thread.currentThread().getId(), new InferenceSession(this));
        }

        switch(variable.getVariableType()){
            case VARIABLE:
                variablesArrays.put(variable.getVarName(), new DeviceLocalNDArray(arr));
                break;
            case CONSTANT:
                constantArrays.put(variable.getVarName(), new DeviceLocalNDArray(arr));
                break;
            case ARRAY:
                // FIXME: remove this before release
                val session = sessions.get(Thread.currentThread().getId());
                val varId = session.newVarId(variable.getVarName(), AbstractSession.OUTER_FRAME, 0);
                session.getNodeOutputs().put(varId, arr);
                //throw new UnsupportedOperationException("Cannot associate array with SDVariable of type ARRAY");
            case PLACEHOLDER:
                long tid = Thread.currentThread().getId();
                if(!placeholdersPerThread.containsKey(tid)){
                    placeholdersPerThread.put(tid, new HashMap<String, INDArray>());
                }
                placeholdersPerThread.get(tid).put(variable.getVarName(), arr);
                break;
            default:
                throw new IllegalStateException("Unknown variable type: " + variable.getVariableType());
        }

        //putOrUpdateShapeForVarName(variable.getVarName(), arr.shape(), true);

        //Also update nested SameDiff instances (such as gradient function)
        if(sameDiffFunctionInstances != null && sameDiffFunctionInstances.size() > 0){
            for(Map.Entry<String,SameDiff> e : sameDiffFunctionInstances.entrySet()){
                SameDiff sd = e.getValue();
                SDVariable v = sd.getVariable(variable.getVarName());
                if(v != null){
                    sd.associateArrayWithVariable(arr, v);
                }
            }
        }
    }


    /**
     * Associate a {@link SameDiff} namespace as a sub function.
     *
     * @param name      the opName of the function
     * @param nameSpace the namespace
     */
    public void putSubFunction(String name, SameDiff nameSpace) {
        if (sameDiffFunctionInstances.containsKey(name) && sameDiffFunctionInstances.get(name) != nameSpace) {
            throw new ND4JIllegalStateException("Unable to replace samediff namespace. Please choose another opName");
        }

        sameDiffFunctionInstances.put(name, nameSpace);
    }


    /**
     * Return the internal variable map
     *
     * @return Map of variables by name
     */
    public Map<String, SDVariable> variableMap() {
        Map<String,SDVariable> ret = new HashMap<>();
        for(Variable v : variables.values()){
            ret.put(v.getName(), v.getVariable());
        }
        return ret;
    }


    /**
     * Invoke an op by opName
     *
     * @param op the op
     * @param x  the first input
     * @param y  the second input
     * @return the result variable
     */
    @Deprecated //TO BE REMOVED - should not be part of public API
    public SDVariable invoke(Op op, SDVariable x, SDVariable y) {
        if (!opMethods.containsKey(op.opName())) {
            throw new ND4JIllegalStateException("Illegal method opName " + op.opName());
        }

        if (x != null && y != null) {
            try {
                return (SDVariable) opMethods.get(op.opName()).invoke(this, x, y);
            } catch (Exception e) {

            }
        } else {
            try {
                return (SDVariable) opMethods.get(op.opName()).invoke(this, x);
            } catch (Exception e) {

            }
        }

        throw new ND4JIllegalStateException("Illegal method opName " + op.opName());
    }

    /**
     * The set of defined SameDiff function names. SameDiff function instances should not be confused
     * with DifferentialFunction ops; an example of a SameDiff function instance is the gradient "grad" function
     *
     * @return Set of defined SameDiff function instance names
     */
    public Collection<String> definedFunctionNames() {
        return this.sameDiffFunctionInstances.keySet();
    }


    /**
     * Returns the number of bytes for the graph. Calculated as sum_i prod(shapeOf(variable[i]))
     *
     * @return Bytes for all of the arrays in the graph for the current variable shapes
     */
    public long memoryForGraph() {
        //TODO FIX ME
        return numElements() * DataTypeUtil.lengthForDtype(Nd4j.dataType());
    }

    /**
     * Invoke an op by opName
     *
     * @param op the op
     * @param x  the first input
     * @return the result variable
     */
    public SDVariable invoke(Op op, SDVariable x) {
        return invoke(op, x, null);
    }

    private SameDiff() {
        functionFactory = new DifferentialFunctionFactory(this);
        sameDiffFunctionDefinitionMap = new LinkedHashMap<>();
        sameDiffFunctionInstances = new LinkedHashMap<>();
        forwardVarForGrad = new LinkedHashMap<>();
        opsForResult = new IntArrayKeyMap<>();
        variableNameToShape = new LinkedHashMap<>();
        placeHolderOriginalShapes = new LinkedHashMap<>();
        placeHolderFunctions = new LinkedHashSet<>();
        baseNameForFunctionInstanceId = new LinkedHashMap<>();
        propertiesToResolve = new LinkedHashMap<>();
        propertiesForFunction = new LinkedHashMap<>();
        fieldVariableResolutionMapping = HashBasedTable.create();

    }

    /**
     * Adds a property that needs to be resolve for later.
     * These variables are typically values that are arrays
     * that are named but have an unknown value till execution time.
     * <p>
     * This is very common for model import.
     *
     * @param forFunction the function to add the property to resolve for
     * @param arrayName   the array name
     */
    public void addPropertyToResolve(DifferentialFunction forFunction, String arrayName) {
        if (!propertiesToResolve.containsKey(forFunction.getOwnName())) {
            List<String> newVal = new ArrayList<>();
            newVal.add(arrayName);
            propertiesToResolve.put(forFunction.getOwnName(), newVal);
        } else {
            List<String> newVal = propertiesToResolve.get(forFunction.getOwnName());
            newVal.add(arrayName);
        }
    }

    /**
     * Return the properties to resolve for the given function.
     * This is typically used right before execution in model import in
     * {@link DifferentialFunction#resolvePropertiesFromSameDiffBeforeExecution()}
     *
     * @param function the function get the properties to resolve for
     * @return the properties to resolve for the given function
     */
    public List<String> propertiesToResolveForFunction(DifferentialFunction function) {
        if (!propertiesToResolve.containsKey(function.getOwnName()))
            return Collections.emptyList();

        return propertiesToResolve.get(function.getOwnName());
    }


    /**
     * Returns true if the given function has ndarray properties to resolve.
     *
     * @param function the function to check
     * @return true if the function has yet to be resolved properties
     */
    public boolean hasPropertiesToResolve(DifferentialFunction function) {
        return propertiesToResolve.containsKey(function.getOwnName());
    }


    /**
     * Get the property for a given function
     *
     * @param functionInstance the function to get the
     *                         property for
     * @param propertyName     the name of the property to get
     * @param <T>              the inferred return type
     * @return the property for the given function
     */
    public <T> T getPropertyForFunction(DifferentialFunction functionInstance, String propertyName) {
        if (!propertiesForFunction.containsKey(functionInstance.getOwnName())) {
            return null;
        } else {
            val map = propertiesForFunction.get(functionInstance.getOwnName());
            return (T) map.get(propertyName);

        }
    }

    /**
     * Add a property for the given function
     *
     * @param functionFor  the function add a property for
     * @param propertyName the property name
     * @param property     the property value
     */
    public void addPropertyForFunction(DifferentialFunction functionFor, String propertyName, INDArray property) {
        addPropertyForFunction(functionFor, propertyName, (Object) property);
    }


    /**
     * Add a property for the given function
     *
     * @param functionFor  the function to add the property for
     * @param propertyName the name of the property to add the value for
     * @param property     the property value to add
     */
    public void addPropertyForFunction(DifferentialFunction functionFor, String propertyName, long property) {
        addPropertyForFunction(functionFor, propertyName, (Object) property);
    }


    private void addPropertyForFunction(DifferentialFunction functionFor, String propertyName, Object propertyValue) {
        if (!propertiesForFunction.containsKey(functionFor.getOwnName())) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put(propertyName, propertyValue);
            propertiesForFunction.put(functionFor.getOwnName(), fields);
        } else {
            val fieldMap = propertiesForFunction.get(functionFor.getOwnName());
            if (fieldMap.containsKey(propertyName)) {
                throw new ND4JIllegalStateException("Attempting to override property " + propertyName);
            }

            fieldMap.put(propertyName, propertyValue);
        }
    }


    /**
     * Adds a field name -> variable name mapping for a given function.<br>
     * This is used for model import where there is an unresolved variable at the time of calling any
     * {@link org.nd4j.imports.graphmapper.GraphMapper#importGraph(File)}
     * .
     * <p>
     * This data structure is typically accessed during {@link DifferentialFunction#resolvePropertiesFromSameDiffBeforeExecution()}
     * <p>
     * When a function attempts to resolve variables right before execution, there needs to be a way of knowing
     * which variable in a samediff graph should map to a function's particular field name
     *
     * @param function  the function to map
     * @param fieldName the field name for the function to map
     * @param varName   the variable name of the array to get from samediff
     */
    public void addVariableMappingForField(DifferentialFunction function, String fieldName, String varName) {
        fieldVariableResolutionMapping.put(function.getOwnName(), fieldName, varName);
    }

    /**
     * Get the variable name to use
     * for resolving a given field
     * for a given function during import time.
     * This method is u sed during {@link DifferentialFunction#resolvePropertiesFromSameDiffBeforeExecution()}
     *
     * @param function  the function to get the variable name for
     * @param fieldName the field name to resolve for
     * @return the resolve variable name if any
     */
    public String getVarNameForFieldAndFunction(DifferentialFunction function, String fieldName) {
        return fieldVariableResolutionMapping.get(function.getOwnName(), fieldName);
    }

    /**
     * Sets a base name for the function id.
     * This is used for when calling {@link #generateOutputVariableForOp(DifferentialFunction, String)}
     * for ensuring original names for model import map to current samediff names
     * when names are generated.
     *
     * @param baseName the base name to add
     * @param function the function to declare a base name for.
     */
    public void setBaseNameForFunctionInstanceId(String baseName, DifferentialFunction function) {
        baseNameForFunctionInstanceId.put(function.getOwnName(), baseName);
    }

    /**
     * Returns the base name for the given function
     * if any (may return null)
     *
     * @param function the function to get the base name for
     * @return the base name for the given function (if any) based
     * on the function's instance id.
     */
    public String getBaseNameForFunction(DifferentialFunction function) {
        return baseNameForFunctionInstanceId.get(function.getOwnName());
    }


    /**
     * Attempts to insert the {@link DifferentialFunction} reference in to this {@link SameDiff} instance.
     * If the given array field with the given index already exists, it will do a reference check to ensure that the 2
     * array fields are the same. If not, an exception is thrown.<br>
     * If the instances are the same (by semantics, not reference) then it will just return the original instance.
     * This is to ensure that instances that are created are unique and reference checked.
     *
     * @param function the array field to attempt to create
     * @return Original instance
     */
    public <X extends SDVariable> X setupFunction(X function) {
        Preconditions.checkNotNull(function, "Passed in function must not be null!");
        if (function instanceof SDVariable) {
            if (function.getSameDiff() != this) {
                function.setSameDiff(this);
            }
            return function;
        }
        return function;
    }


    /**
     * Adds outgoing arguments to the graph for the specified DifferentialFunction
     * Also checks for input arguments and updates the graph adding an appropriate edge when the full graph is declared.
     *
     * @param variables Variables - arguments for the specified differential function
     * @param function Differential function
     */
    public void addOutgoingFor(SDVariable[] variables, DifferentialFunction function) {
        String[] varNames = new String[variables.length];
        for (int i = 0; i < varNames.length; i++) {
            varNames[i] = variables[i].getVarName();
        }

        addOutgoingFor(varNames, function);
    }


    /**
     * Adds outgoing arguments to the graph for the specified DifferentialFunction
     * Also checks for input arguments and updates the graph adding an appropriate edge when the full graph is declared.
     *
     * @param varNames Name of the variables that are outputs of the specified differential function
     * @param function Differential function
     */
    public void addOutgoingFor(String[] varNames, DifferentialFunction function) {

        if (function.getOwnName() == null)
            throw new ND4JIllegalStateException("Instance id can not be null. Function not initialized properly");


        if (ops.get(function.getOwnName()).getOutputsOfOp() != null && !ops.get(function.getOwnName()).getOutputsOfOp().isEmpty()) {
            throw new ND4JIllegalStateException("Outgoing arguments already declared for " + function);
        }

        if (varNames == null)
            throw new ND4JIllegalStateException("Var names can not be null!");


        for (int i = 0; i < varNames.length; i++) {
            if (varNames[i] == null)
                throw new ND4JIllegalStateException("Variable name elements can not be null!");
        }

        ops.get(function.getOwnName()).setOutputsOfOp(Arrays.asList(varNames));

        for (String resultName : varNames) {
            variables.get(resultName).setOutputOfOp(function.getOwnName());
        }
    }

    /**
     * Adds incoming arguments for the specified differential function to the graph
     *
     * @param variables Name of the variables that are arguments (inputs) to the specified function
     * @param function  Function
     */
    public void addArgsFor(String[] variables, DifferentialFunction function) {
        if (function.getOwnName() == null)
            throw new ND4JIllegalStateException("Instance id can not be null. Function not initialized properly");

        //double check if function contains placeholder args
        for (val varName : variables) {
            if (isPlaceHolder(varName)) {
                placeHolderFunctions.add(function.getOwnName());
            }
        }

        //Add function if it doesn't exist
        //TODO could "not existing" be a bug sometimes?
        if(!ops.containsKey(function.getOwnName())){
            ops.put(function.getOwnName(), SameDiffOp.builder().name(function.getOwnName()).op(function).build());
        }

        //Update variable 'inputs to op' accounting for repeated inputs (like y = x+x)
        ops.get(function.getOwnName()).setInputsToOp(Arrays.asList(variables));     //Duplicate variables OK/required here

        for (String variableName : variables) {
            List<String> funcs = this.variables.get(variableName).getInputsForOp();
            if (funcs == null) {
                funcs = new ArrayList<>();
                this.variables.get(variableName).setInputsForOp(funcs);
            }
            if(!funcs.contains(function.getOwnName()))  //Avoid duplicates for function names.
                funcs.add(function.getOwnName());
        }
    }


    /**
     * Adds incoming arguments for the specified differential function to the graph
     *
     * @param variables variables that are arguments (inputs) to the specified function
     * @param function  Function
     */
    public void addArgsFor(SDVariable[] variables, DifferentialFunction function) {
        String[] varNames = new String[variables.length];
        for (int i = 0; i < varNames.length; i++) {
            if (variables[i] == null)
                throw new ND4JIllegalStateException("Found null variable at index " + i);
            varNames[i] = variables[i].getVarName();
        }
        addArgsFor(varNames, function);
    }

    /**
     * Get the differential function (if any) that this variable is the output for
     *
     * @param variableName Name of the variable
     * @return The differential function that this variable is an output of, or null if it is not the output of a function
     */
    public DifferentialFunction getVariableOutputFunction(String variableName) {
        Preconditions.checkState(variables.containsKey(variableName), "No variable with name \"%s\" found in graph", variableName);
        if(variables.get(variableName).getOutputOfOp() == null)
            return null;
        return ops.get(variables.get(variableName).getOutputOfOp()).getOp();
    }


    /**
     * Returns true if this function already has defined arguments
     *
     * @param function the function to check
     * @return true if the function has args, false otherwise
     */
    public boolean hasArgs(DifferentialFunction function) {
        List<String> vertexIdArgs = ops.get(function.getOwnName()).getInputsToOp();
        return vertexIdArgs != null && vertexIdArgs.size() > 0;
    }

    /**
     * Get an array of differential functions that have been defined for this SameDiff instance
     * @return Array of differential functions
     */
    public DifferentialFunction[] functions() {
        List<DifferentialFunction> out = new ArrayList<>(ops.size());
        for(SameDiffOp op : ops.values()){
            out.add(op.getOp());
        }
        return out.toArray(new DifferentialFunction[out.size()]);
    }


    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (variables != null ? variables.hashCode() : 0);
        return result;
    }


    /**
     * Create a new SameDiff instance from an existing instance.
     * Note that state (variables and functions) is shared between the two SameDiff instance
     *
     * @param originalSameDiff Original SameDiff instance
     * @return Copy
     */
    public static SameDiff create(SameDiff originalSameDiff) {
        SameDiff ret = SameDiff.builder()
                .sameDiffFunctionInstances(originalSameDiff.sameDiffFunctionInstances)
                .build();
        ret.variables.putAll(originalSameDiff.variables);
        //ensuring proper sameDiff reference
        DifferentialFunctionFactory differentialFunctionFactory = new DifferentialFunctionFactory(ret);
        ret.functionFactory = differentialFunctionFactory;
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SameDiff sameDiff = (SameDiff) o;

        if (variables != null ? !variables.equals(sameDiff.variables) : sameDiff.variables != null)
            return false;
        if (sameDiffFunctionDefinitionMap != null ? !sameDiffFunctionDefinitionMap.equals(sameDiff.sameDiffFunctionDefinitionMap) : sameDiff.sameDiffFunctionDefinitionMap != null)
            return false;
        return sameDiffFunctionInstances != null ? sameDiffFunctionInstances.equals(sameDiff.sameDiffFunctionInstances) : sameDiff.sameDiffFunctionInstances == null;
    }

    /**
     * Create a new (empty) SameDiff instance without any functions or variables
     * @return New SameDiff instance
     */
    public static SameDiff create() {
        return new SameDiff();
    }

    /**
     * Clone/duplicate the SameDiff instance, including arrays etc. The returned SameDiff instance should have no
     * shared state with the original instance
     * @return The cloned SameDiff instance
     */
    public SameDiff dup() {
        Cloner cloner = newCloner();
        val clone = cloner.deepClone(this);
        return clone;
    }


    /**
     * Count the number of elements in all arrays, according to {@link SDVariable#getShape()}
     * @return Number of array elements for all variables
     */
    public long numElements() {
        long ret = 0;
        for (SDVariable variable : variables()) {
            long[] shape = variable.getShape();
            if(shape != null) {
                ret += ArrayUtil.prod(shape);
            }
        }
        return ret;
    }

    /**
     * Returns the inputs (placeholders)
     * for the samediff graph
     * @return the inputs for this graph
     */
    public List<String> inputs() {
        List<String> out = new ArrayList<>();
        for(String s : variables.keySet()){
            if(isPlaceHolder(s))
                out.add(s);
        }
        return out;
    }

    /**
     * Outputs are those variables (not placeholders, constants, etc) that are the output of a function that aren't the
     * input to any other ops.
     * Usually these are the output of the last function(s) in the SameDiff instance.
     * @return The (inferred) outputs of the SameDiff instance, in no particular order
     */
    public List<String> outputs(){
        List<String> out = new ArrayList<>();
        for(Variable v : variables.values()){
            if(v.getVariable().isConstant() || v.getVariable().isPlaceHolder() ||                   //Exclude constants and placeholders
                    (v.getInputsForOp() != null && !v.getInputsForOp().isEmpty()) ||                //Exclude variables that are inputs to ops
                    (v.getControlDepsForOp() != null && !v.getControlDepsForOp().isEmpty()) ||      //Exclude variables that are control dependency inputs to ops
                    (v.getControlDepsForVar() != null && !v.getControlDepsForVar().isEmpty())) {    //Exclude variables that are control dependency inputs to other variables (mainly for import of cond etc ops)
                continue;
            }

            //Also exclude assert etc ops - doesn't make sense to return these "outputs" to user
            if(v.getOutputOfOp() != null){
                String opName = v.getOutputOfOp();
                SameDiffOp o = ops.get(opName);
                if(o.getOp() instanceof Assert){
                    continue;
                }

                //A bit of a hack for TF import: some TF graphs have Switch ops, where the output of one branch isn't consumed
                // by any ops. Consequently, during execution this "output" might never be available. So we'll exclude the output of execution here
                if(o.getOp() instanceof Switch){
                    continue;
                }
            }


            out.add(v.getName());
        }
        return out;
    }

    /**
     * The list of all variables in the graph
     *
     * @return All variables in the graph
     */
    public List<SDVariable> variables() {
        return new ArrayList<>(variableMap().values());
    }

    /**
     * Set the training configuration ({@link TrainingConfig}) for the SameDiff instance.
     * A TrainingConfig must be set before the SameDiff instance can be trained via the fit methods
     * @param trainingConfig Training configuration
     */
    public void setTrainingConfig(TrainingConfig trainingConfig){
        this.trainingConfig = trainingConfig;
    }

    /**
     * Fit the SameDiff instance based on a single DataSet (i.e., a single minibatch for one iteration).<br>
     * This method can only be used for singe input, single output SameDiff instances as DataSet only supports a
     * single input and a single output.<br>
     * Note that a {@link TrainingConfig} must be set via {@link #setTrainingConfig(TrainingConfig)} before training can
     * be performed.
     *
     * @param dataSet The DataSet (single minibatch) to peform training on
     */
    public void fit(DataSet dataSet){
        fit(new SingletonMultiDataSetIterator(dataSet.toMultiDataSet()), 1, false);
    }

    /**
     * Fit the SameDiff instance based on DataSetIterator for the specified number of epochs.<br>
     * This method can only be used for singe input, single output SameDiff instances as DataSet only supports a
     * single input and a single output.<br>
     * Note that a {@link TrainingConfig} must be set via {@link #setTrainingConfig(TrainingConfig)} before training can
     * be performed.
     *
     * @param iter      The iterator to train the SameDiff instance with
     * @param numEpochs The number of epochs for training. Must be > 0
     */
    public void fit(DataSetIterator iter, int numEpochs) {
        fit(new MultiDataSetIteratorAdapter(iter), numEpochs, true);
    }

    /**
     * Fit the SameDiff instance based on MultiDataSetIterator for the specified number of epochs.<br>
     * This method can both singe input, single output and multi-input, multi-output SameDiff instances<br>
     * Note that a {@link TrainingConfig} must be set via {@link #setTrainingConfig(TrainingConfig)} before training can
     * be performed.
     *
     * @param iter      The iterator to train the SameDiff instance with
     * @param numEpochs The number of epochs for training. Must be > 0
     */
    public void fit(MultiDataSetIterator iter, int numEpochs){
        fit(iter, numEpochs, true);
    }

    //Synchronized for thread safety
    protected synchronized void fit(MultiDataSetIterator iter, int numEpochs, boolean incrementEpochCount){
        Preconditions.checkNotNull(iter, "Iterator must not be null");
        Preconditions.checkState(numEpochs > 0, "Number of training epochs must be a positive number. Got: %s", numEpochs);
        Preconditions.checkState(trainingConfig != null, "No training configuration has been set. A training configuration must " +
                "be set before training. Use setTrainingConfig(TrainingConfig)");
        Preconditions.checkState(numEpochs == 1 || iter.resetSupported(), "Cannot train for multiple epochs on an iterator that" +
                " does not support resetting");

        if(!iter.hasNext() && iter.resetSupported())
            iter.reset();

        boolean performedValidation = false;

        for(int i = 0; i < numEpochs; i++) {
            while (iter.hasNext()) {
                org.nd4j.linalg.dataset.api.MultiDataSet ds = iter.next();
                if(!performedValidation){
                    Preconditions.checkState(trainingConfig.getDataSetFeatureMapping().size() == ds.numFeatureArrays(),
                            "The number of dataset feature mapping variables set in the training configuration (%s) must match" +
                                    " the number of dataset feature arrays (%s)", trainingConfig.getDataSetFeatureMapping().size(), ds.numFeatureArrays());
                    Preconditions.checkState(trainingConfig.getDataSetLabelMapping().size() == ds.numLabelsArrays(),
                            "The number of dataset label mapping variables set in the training configuration (%s) must match" +
                                    " the number of dataset label arrays (%s)", trainingConfig.getDataSetLabelMapping().size(), ds.numLabelsArrays());

                    performedValidation = true;
                }

                //Create placeholder variable map
                Map<String, INDArray> placeholders = toPlaceholderMap(ds);

                Preconditions.checkState(placeholders.size() > 0, "No placeholder variables were set for training");
                resolveVariablesWith(placeholders);

                //Calculate gradients:
                execBackwards(placeholders);


                //Apply updater:
                if (!initializedTraining)
                    initializeTraining();

                int iteration = trainingConfig.getIterationCount();
                int e = trainingConfig.getEpochCount();
                for (String s : trainingConfig.getTrainableParams()) {
                    //TODO fix using inference session
                    INDArray param = variables.get(s).getVariable().getArr();
                    INDArray grad = variables.get(s).getVariable().getGradient().getArr();
                    //Note: don't need to divide by minibatch - that should be handled in loss function and hence loss function gradients,
                    // which should flow through to here

                    //Apply updater. Note that we need to reshape to [1,length] for updater
                    INDArray reshapedView = Shape.newShapeNoCopy(grad, new long[]{1, grad.length()}, grad.ordering() == 'f');       //TODO make sure we always reshape in same order!
                    Preconditions.checkState(reshapedView != null, "Error reshaping array for parameter \"%s\": array is a view?", s);
                    GradientUpdater u = updaterMap.get(s);
                    try {
                        u.applyUpdater(reshapedView, iteration, e);
                    } catch (Throwable t) {
                        throw new RuntimeException("Error applying updater " + u.getClass().getSimpleName() + " to parameter \"" + s
                                + "\": either parameter size is inconsistent between iterations, or \"" + s + "\" should not be a trainable parameter?", t);
                    }

                    //L1 and L2 regularization:
                    if (trainingConfig.getL1() > 0) {
                        //L1: loss += lambda * sum_i |param_i|
                        //dL/dp_i: lambda * sgn(param_i)
                        INDArray signProd = Transforms.sign(param, true).muli(trainingConfig.getL1());
                        grad.addi(signProd);
                    }
                    if (trainingConfig.getL2() > 0) {
                        //L2: loss += 0.5 * lambda * sum_i param_i^2
                        //dL/dp_i: lambda * param_i
                        //TODO axpy optimization = safe/possible?
                        grad.addi(param.mul(trainingConfig.getL2()));
                    }

                    if (trainingConfig.isMinimize()) {
                        param.subi(grad);
                    } else {
                        param.addi(grad);
                    }
                }

                trainingConfig.incrementIterationCount();
            }

            if(i < numEpochs - 1) {
                iter.reset();
            }

            if(incrementEpochCount)
                trainingConfig.incrementEpochCount();
        }
    }

    /**
     * Calculate the L2 regularization component of the loss: {@code 0.5 * sum_i (weights_i)}<br>
     * Note that the training configuration must be set (via {@link #setTrainingConfig(TrainingConfig)}) before this
     * method can be called
     *
     * @return The L2 regularization component of the score
     */
    public double calculateL2Loss() {
        Preconditions.checkState(trainingConfig != null, "No training configuration has been set. A training configuration must " +
                "be set before calculating the L2 loss. Use setTrainingConfig(TrainingConfig)");

        if(trainingConfig.getL2() == 0){
            return 0.0;
        }

        if(trainingConfig.getTrainableParams() == null || trainingConfig.getTrainableParams().isEmpty())
            initializeTraining();

        double l2 = trainingConfig.getL2();
        double l2Loss = 0.0;
        for (String s : trainingConfig.getTrainableParams()) {
            //L2: loss += 0.5 * lambda * sum_i param_i^2
            double norm2 = getVariable(s).getArr().norm2Number().doubleValue();
            l2Loss += 0.5 * l2 * norm2 * norm2;
        }
        return l2Loss;
    }

    /**
     * Calculate the L1 regularization component of the loss: {@code 0sum_i (abs(weights_i))}<br>
     * Note that the training configuration must be set (via {@link #setTrainingConfig(TrainingConfig)}) before this
     * method can be called
     *
     * @return The L1 regularization component of the score
     */
    public double calculateL1Loss(){
        Preconditions.checkState(trainingConfig != null, "No training configuration has been set. A training configuration must " +
                "be set before calculating the L1 loss. Use setTrainingConfig(TrainingConfig)");

        if(trainingConfig.getL1() == 0){
            return 0.0;
        }

        if(trainingConfig.getTrainableParams() == null || trainingConfig.getTrainableParams().isEmpty())
            initializeTraining();

        double l1 = trainingConfig.getL1();
        double l1Loss = 0.0;
        for (String s : trainingConfig.getTrainableParams()) {
            //L1: loss += lambda * sum_i |param_i|
            double norm1 = getVariable(s).getArr().norm1Number().doubleValue();
            l1Loss += l1 * norm1;
        }
        return l1Loss;
    }

    /**
     * Perform setup for training. Does the following:
     * 1. Infer the set of trainable parameters - unless specified manually by the user
     * 2. Set up the updaters
     */
    protected void initializeTraining(){
        if(!initializedTraining) {
            if(trainingConfig == null) {
                throw new ND4JIllegalStateException("Please specify a training config with setTrainingConfig");
            }
            //First: infer the variables to be optimized if required
            if(trainingConfig.getTrainableParams() == null || trainingConfig.getTrainableParams().size() == 0) {
                //Variable is trainable if it's not the output of some function
                //TODO also - should be floating point type
                List<String> trainVarList = new ArrayList<>();
                for(Variable var : variables.values()){
                    SDVariable v = var.getVariable();
                    String n = v.getVarName();
                    if(variables.get(n).getOutputOfOp() == null &&       //Is a leaf (not the output of a function)
                            !isPlaceHolder(n) &&                                                                                 //and not a placeholder
                            (trainingConfig.getDataSetFeatureMapping() == null || !trainingConfig.getDataSetFeatureMapping().contains(n))   &&  //and not an input (this really should be a placeholder, but we can't guarantee that...)
                            (trainingConfig.getDataSetLabelMapping() == null || !trainingConfig.getDataSetLabelMapping().contains(n))   &&      //and not a label (this really should be a placeholder, but we can't guarantee that...)
                            (trainingConfig.getDataSetFeatureMaskMapping() == null || !trainingConfig.getDataSetFeatureMaskMapping().contains(n))   &&  //and not a feature mask (this really should be a placeholder, but we can't guarantee that...)
                            (trainingConfig.getDataSetLabelMaskMapping() == null || !trainingConfig.getDataSetLabelMaskMapping().contains(n))){  //and not a label input (this really should be a placeholder, but we can't guarantee that...)
                        trainVarList.add(n);
                    }
                }

                trainingConfig.setTrainableParams(trainVarList);
                log.info("Inferred trainable variables: {}", trainVarList);
            }

            //Allocate updater state
            long numTrainableParams = 0;
            DataType dt = null;             //TODO support mixed precision variables - https://github.com/deeplearning4j/deeplearning4j/issues/6992
            for(String s : trainingConfig.getTrainableParams()) {
                SDVariable v = variables.get(s).getVariable();
                Preconditions.checkState(v != null, "No variable found for trainable parameter name \"%s\"", s);

                INDArray arr = v.getArr();
                Preconditions.checkState(arr != null, "No array found for trainable parameter \"%s\"", s);
                numTrainableParams += arr.length();
                if(dt == null)
                    dt = arr.dataType();
            }

            long updaterStateSize = trainingConfig.getUpdater().stateSize(numTrainableParams);

            if(updaterStateSize > 0) {
                updaterState = Nd4j.createUninitialized(dt, 1, updaterStateSize);
            }

            long viewSoFar = 0;
            updaterViews = new HashMap<>();
            updaterMap = new HashMap<>();
            for(String s : trainingConfig.getTrainableParams()) {
                long thisSize = trainingConfig.getUpdater().stateSize(variables.get(s).getVariable().getArr().length());
                INDArray view = (updaterStateSize == 0 || thisSize == 0 ? null :
                        updaterState.get(NDArrayIndex.interval(0, 1), NDArrayIndex.interval(viewSoFar, viewSoFar + thisSize)));

                updaterViews.put(s, view);
                updaterMap.put(s, trainingConfig.getUpdater().instantiate(view, true));
                viewSoFar += thisSize;
            }

            initializedTraining = true;
        }
    }

    /**
     * Convert the MultiDataSet to a {@code Map<String,INDArray>} based on the TrainingConfig settings.
     * The key is the placeholder/variable that the value INDArray should be associated with.
     *
     * @param ds MultiDataSet - source of the features/labels
     * @return MultiDataSet converted to a Map, based on TrainingConfig
     */
    private Map<String,INDArray> toPlaceholderMap(org.nd4j.linalg.dataset.api.MultiDataSet ds) {
        Map<String,INDArray> placeholders = new HashMap<>();
        int count = 0;
        for(String s : trainingConfig.getDataSetFeatureMapping()){
            placeholders.put(s, ds.getFeatures(count++));
        }
        count = 0;
        for(String s : trainingConfig.getDataSetLabelMapping()){
            placeholders.put(s, ds.getLabels(count++));
        }

        if(trainingConfig.getDataSetFeatureMaskMapping() != null && trainingConfig.getDataSetFeatureMaskMapping().size() > 0){
            count = 0;
            for(String s : trainingConfig.getDataSetFeatureMaskMapping()){
                if(s == null) {
                    count++;
                    continue;
                }
                placeholders.put(s, ds.getFeaturesMaskArray(count++));
            }
        }

        if(trainingConfig.getDataSetLabelMaskMapping() != null && trainingConfig.getDataSetLabelMaskMapping().size() > 0){
            count = 0;
            for(String s : trainingConfig.getDataSetLabelMaskMapping()){
                if(s == null) {
                    count++;
                    continue;
                }
                placeholders.put(s, ds.getLabelsMaskArray(count++));
            }
        }
        return placeholders;
    }

    /**
     * Evaluate the performance of a single variable's prediction.<br>
     * For example, if the variable to evaluatate was called "softmax" you would use:
     * <pre>
     * {@code Evaluation e = new Evaluation();
     * sameDiff.evaluate(iterator, "softmax", e);}
     * </pre>
     *
     * @param iterator       Iterator as source of data to evaluate
     * @param outputVariable The variable to evaluate
     * @param evaluations    The evaluations to perform
     */
    public void evaluate(DataSetIterator iterator, String outputVariable, IEvaluation... evaluations) {
        Preconditions.checkArgument(evaluations != null && evaluations.length > 0, "No evaluations were passed to the evaluate method");
        evaluate(new MultiDataSetIteratorAdapter(iterator), Collections.singletonMap(outputVariable, Arrays.asList(evaluations)),
                Collections.singletonMap(outputVariable, 0));
    }

    /**
     * Evaluation for multiple-output networks.<br>
     * See {@link #evaluate(MultiDataSetIterator, Map, Map)}
     */
    public void evaluate(DataSetIterator iterator, Map<String,IEvaluation> variableEvals){
        Map<String,Integer> map = new HashMap<>();
        Map<String,List<IEvaluation>> variableEvalsList = new HashMap<>();
        for(String s : variableEvals.keySet()){
            map.put(s, 0);  //Only 1 possible output here with DataSetIterator
            variableEvalsList.put(s, Collections.singletonList(variableEvals.get(s)));
        }
        evaluate(new MultiDataSetIteratorAdapter(iterator), variableEvalsList, map);
    }

    /**
     * Evaluation for multiple output networks - one ore more
     * See {@link #evaluate(MultiDataSetIterator, Map, Map)}
     */
    public void evaluateMultiple(DataSetIterator iterator, Map<String,List<IEvaluation>> variableEvals){
        Map<String,Integer> map = new HashMap<>();
        for(String s : variableEvals.keySet()){
            map.put(s, 0);  //Only 1 possible output here with DataSetIterator
        }
        evaluate(new MultiDataSetIteratorAdapter(iterator), variableEvals, map);
    }

    /**
     * Perform evaluation using classes such as {@link org.nd4j.evaluation.classification.Evaluation} for classifier outputs
     * and {@link org.nd4j.evaluation.regression.RegressionEvaluation} for regression outputs.<br>
     * <br>
     * <b>Example: classifier evaluation</b><br>
     * Predictions variable name: "softmaxOutput"<br>
     * Evaluations to perform: {@link org.nd4j.evaluation.classification.Evaluation}<br>
     * Data: single input, single output MultiDataSets<br>
     * Code:<br>
     * <pre>
     * {@code
     * MultiDataSetIterator data = ...
     * Map<String,List<IEvaluation>> evals = Collections.singletonMap("softmaxOutput",Collections.singletonList(new Evaluation()));
     * Map<String,Integer> labelMapping = Collections.singletonMap("softmaxOutput",0);  //Compare: "softmaxOutput" vs. MultiDataSet.getLabels(0)
     * }
     * </pre>
     *
     * @param iterator               The iterator - the source of the data for evaluation
     * @param variableEvals          The evaluations to perform. Key: the name of the variable. Value: the evaluations to perform
     * @param predictionLabelMapping The output/label mapping. Key: the name of the variable.
     */
    public void evaluate(MultiDataSetIterator iterator, Map<String,List<IEvaluation>> variableEvals, Map<String,Integer> predictionLabelMapping){
        Preconditions.checkState(trainingConfig != null, "Training config has not been set");

        Preconditions.checkState(variableEvals.keySet().equals(predictionLabelMapping.keySet()), "Keysets for variable evaluations" +
                " and for the prediction label mapping must be equal. Keys for variables to evaluate: %s vs. keys for label mapping: %s", variableEvals.keySet(), predictionLabelMapping.keySet());

        if(!iterator.hasNext() && iterator.resetSupported())
            iterator.reset();

        List<String> reqVars = new ArrayList<>(variableEvals.keySet());

        while(iterator.hasNext()){
            MultiDataSet ds = iterator.next();
            Map<String,INDArray> placeholderMap = toPlaceholderMap(ds);

            Map<String,INDArray> m = exec(placeholderMap, reqVars);

            for(Map.Entry<String,List<IEvaluation>> e : variableEvals.entrySet()){
                INDArray prediction = m.get(e.getKey());
                for(IEvaluation eval : e.getValue()){
                    //TODO masking, time series, etc

                    INDArray label = ds.getLabels(predictionLabelMapping.get(e.getKey()));
                    eval.eval(label, prediction);
                }
            }
        }
    }


    public SDVariable one(String name, int... shape){
        return one(name, Nd4j.defaultFloatingPointType(), shape);
    }

    public SDVariable one(String name, long... shape){
        return one(name, Nd4j.defaultFloatingPointType(), shape);
    }


    /**
     * Create a new variable with the specified shape, with all values initialized to 1.0
     *
     * @param name  the name of the variable to create
     * @param shape the shape of the array to be created
     * @return the created variable
     */
    public SDVariable one(String name, org.nd4j.linalg.api.buffer.DataType dataType, int... shape) {
        return var(name, new ConstantInitScheme('f', 1.0), dataType, ArrayUtil.toLongArray(shape));
    }

    /**
     * Create a new variable with the specified shape, with all values initialized to 1.0
     *
     * @param name  the name of the variable to create
     * @param shape the shape of the array to be created
     * @return the created variable
     */
    public SDVariable one(String name, org.nd4j.linalg.api.buffer.DataType dataType, long... shape) {
        return var(name, new ConstantInitScheme('f', 1.0), dataType, shape);
    }

    /**
     * Return a variable of all 1s, with the same shape as the input variable. Note that this is dynamic:
     * if the input shape changes in later execution, the returned variable's shape will also be updated
     *
     * @param input Input SDVariable
     * @return A new SDVariable with the same (dynamic) shape as the input
     */
    public SDVariable onesLike(SDVariable input) {
        return onesLike(null, input);
    }

    /**
     * Return a variable of all 1s, with the same shape as the input variable. Note that this is dynamic:
     * if the input shape changes in later execution, the returned variable's shape will also be updated
     *
     * @param name  Name of the new SDVariable
     * @param input Input SDVariable
     * @return A new SDVariable with the same (dynamic) shape as the input
     */
    public SDVariable onesLike(String name, SDVariable input) {
        SDVariable ret = f().onesLike(name, input);
        return updateVariableNameAndReference(ret, name);
    }


    public SDVariable zero(String name, long... shape){
        return zero(name, Nd4j.defaultFloatingPointType(), shape);
    }

    public SDVariable zero(String name, int... shape){
        return zero(name, Nd4j.defaultFloatingPointType(), shape);
    }

    /**
     * Create a new variable with the specified shape, with all values initialized to 0
     *
     * @param name  the name of the variable to create
     * @param shape the shape of the array to be created
     * @return the created variable
     */
    public SDVariable zero(String name, org.nd4j.linalg.api.buffer.DataType dataType, long... shape) {
        return var(name, new ZeroInitScheme(), dataType, shape);
    }

    /**
     * Create a new variable with the specified shape, with all values initialized to 0
     *
     * @param name  the name of the variable to create
     * @param shape the shape of the array to be created
     * @return the created variable
     */
    public SDVariable zero(String name, org.nd4j.linalg.api.buffer.DataType dataType, int... shape) {
        return var(name, new ZeroInitScheme(), dataType, ArrayUtil.toLongArray(shape));
    }

    /**
     * Return a variable of all 0s, with the same shape as the input variable. Note that this is dynamic:
     * if the input shape changes in later execution, the returned variable's shape will also be updated
     *
     * @param input Input SDVariable
     * @return A new SDVariable with the same (dynamic) shape as the input
     */
    public SDVariable zerosLike(SDVariable input) {
        return zerosLike(null, input);
    }

    /**
     * Return a variable of all 0s, with the same shape as the input variable. Note that this is dynamic:
     * if the input shape changes in later execution, the returned variable's shape will also be updated
     *
     * @param name  Name of the new SDVariable
     * @param input Input SDVariable
     * @return A new SDVariable with the same (dynamic) shape as the input
     */
    public SDVariable zerosLike(String name, SDVariable input) {
        SDVariable ret = f().zerosLike(name, input);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Create an SDVariable with a fixed/constant value, with a generated name
     * @param constant Value for the constant SDVariable
     * @return
     */
    public SDVariable constant(@NonNull INDArray constant){
        return constant(getNewVarName(), constant);
    }

    /**
     * Create an SDVariable with a fixed/constant value
     * @param name  Name of the constant SDVariable
     * @param constant Value for the constant SDVariable
     * @return
     */
    public SDVariable constant(@NonNull String name, @NonNull INDArray constant){
        Preconditions.checkState(!variables.containsKey(name), "Variable with name \"%s\" already exists", name);
        SDVariable v = new SDVariable(name, VariableType.CONSTANT, this, constant.shape(), constant.dataType(), null);
        variables.put(name, Variable.builder().name(name).variable(v).build());
        constantArrays.put(name, new DeviceLocalNDArray(constant));
        return v;
    }

    /**
     * Return a variable of given shape in which all values have a given constant value.
     *
     * @param value constant to set for each value
     * @param shape shape of the variable as long array
     * @return A new SDVariable of provided shape with constant value.
     */
    @Deprecated
    public SDVariable constant(SDVariable value, long... shape) {
        return constant(null, value, shape);
    }

    /**
     * Return a variable of given shape in which all values have a given constant value.
     *
     * @param name  Name of the new SDVariable
     * @param value constant to set for each value
     * @param shape shape of the variable as long array
     * @return A new SDVariable of provided shape with constant value.
     */
    @Deprecated
    public SDVariable constant(String name, SDVariable value, long... shape) {
        SDVariable ret = f().constant(value, shape);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Create a new 1d array with values evenly spaced between values 'start' and 'stop'
     * For example, linspace(start=3.0, stop=4.0, number=3) will generate [3.0, 3.5, 4.0]
     *
     * @param start  Start value
     * @param stop   Stop value
     * @param number Number of values to generate
     * @return SDVariable with linearly spaced elements
     */
    public SDVariable linspace(double start, double stop, long number) {
        return linspace(null, start, stop, number);
    }

    /**
     * Create a new 1d array with values evenly spaced between values 'start' and 'stop'
     * For example, linspace(start=3.0, stop=4.0, number=3) will generate [3.0, 3.5, 4.0]
     *
     * @param name Name of the new variable
     * @param start  Start value
     * @param stop   Stop value
     * @param number Number of values to generate
     * @return SDVariable with linearly spaced elements
     */
    public SDVariable linspace(String name, double start, double stop, long number) {
        SDVariable ret = f().linspace(start, stop, number);
        return updateVariableNameAndReference(ret, name);
    }

    public SDVariable linspace(String name, SDVariable from, SDVariable to, SDVariable length, DataType dt){
        SDVariable ret = f().linspace(from, to, length, dt);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Create a new variable with a 1d array, where the values start at {@code from} and increment by {@code step}
     * up to (but not including) limit.<br>
     * For example, {@code range(1.0, 3.0, 0.5)} will return {@code [1.0, 1.5, 2.0, 2.5]}
     * @param from Initial/smallest value
     * @param to   Largest value (exclusive)
     * @param step Step size
     * @return 1D SDVariable with the specified values
     */
    public SDVariable range(double from, double to, double step){
        return range(null, from, to, step);
    }

    /**
     * Create a new variable with a 1d array, where the values start at {@code from} and increment by {@code step}
     * up to (but not including) limit.<br>
     * For example, {@code range(1.0, 3.0, 0.5)} will return {@code [1.0, 1.5, 2.0, 2.5]}
     * @param name Name of the new variable
     * @param from Initial/smallest value
     * @param to   Largest value (exclusive)
     * @param step Step size
     * @return 1D SDVariable with the specified values
     */
    public SDVariable range(String name, double from, double to, double step){
        SDVariable ret = f().range(from, to, step);
        return updateVariableNameAndReference(ret, name);
    }

    public SDVariable castTo(SDVariable toCast, org.nd4j.linalg.api.buffer.DataType toType){
        return castTo(null, toCast, toType);
    }

    public SDVariable castTo(String name, SDVariable toCast, org.nd4j.linalg.api.buffer.DataType toType){
        SDVariable ret = f().cast(toCast, toType);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #meshgrid(List, SDVariable...)
     */
    public SDVariable[] meshgrid(SDVariable... inputs){
        return meshgrid(null, inputs);
    }

    /**
     * Broadcast the 1D input variables onto an n-dimensional grid.<br>
     * The resulting variable can be used for example for evaluating functions at all locations on a grid.<br>
     * Example:<br>
     * <pre>
     * {@code input1 = [1, 2, 3]
     * input2 = [4, 5, 6]
     * SDVariable[] out = meshgrid(input1, input2)
     * out[0]:
     * [ 1, 2, 3]
     * [ 1, 2, 3]
     * [ 1, 2, 3]
     *
     * out[1]:
     * [ 4, 4, 4]
     * [ 5, 5, 5]
     * [ 6, 6, 6]}
     * </pre>
     * <br>
     * @param names List of names for the output variables. Must have exactly N names for N input arrays
     * @param inputs N x 1D input variables
     * @return an array of exactly N SDVariables (for N inputs), of rank N
     */
    public SDVariable[] meshgrid(List<String> names, SDVariable... inputs){
        return meshgrid(names, true, inputs);
    }

    /**
     * @see #meshgrid(List, SDVariable...)
     */
    public SDVariable[] meshgrid(List<String> names, boolean cartesian, SDVariable... inputs){
        Preconditions.checkState(names == null || names.size() == inputs.length,
                "Got %s names but %s inputs", (names == null ? 0 : names.size()), inputs.length);
        SDVariable[] ret = f().meshgrid(cartesian, inputs);
        for( int i=0; i<ret.length; i++ ){
            ret[i] = updateVariableNameAndReference(ret[i], names == null ? null : names.get(i));
        }
        return ret;
    }

    public SDVariable placeHolder(String name, long... shape){
        return placeHolder(name, Nd4j.defaultFloatingPointType(), shape);
    }

    /**
     * Create a variable with a place holder
     * @param name the name of the variable
     * @param shape the shape of the variable if any
     * @return
     */
    public SDVariable placeHolder(String name, org.nd4j.linalg.api.buffer.DataType dataType, long...shape) {
        SDVariable ret = new SDVariable(name, VariableType.PLACEHOLDER, this, shape, dataType, null);
        variables.put(name, Variable.builder().name(name).variable(ret).build());
        return ret;
    }

    /**
     * Variable initialization with a specified {@link WeightInitScheme}
     *
     * @param name             the name of the variable
     * @param shape            the shape of the array to be created
     * @param weightInitScheme the weight initialization scheme
     * @return the created variable
     */
    public SDVariable var(@NonNull String name, @NonNull WeightInitScheme weightInitScheme, @NonNull org.nd4j.linalg.api.buffer.DataType dataType, @NonNull long... shape) {
        return var(name, VariableType.VARIABLE, weightInitScheme, dataType, shape);
    }

    //TODO only allowing null datatype for TF import (it's fixed in a later step) - don't want this in the public API!
    public SDVariable var(@NonNull String name, @NonNull VariableType variableType, WeightInitScheme weightInitScheme,
                             org.nd4j.linalg.api.buffer.DataType dataType, long... shape) {
        if (variables.containsKey(name) && variables.get(name).getVariable().getArr() != null)
            throw new IllegalArgumentException("Another variable with the name " + name + " already exists.");

        if (name == null || name.length() < 1)
            name = getNewVarName();


        SDVariable ret = new SDVariable(name, variableType, this, shape, dataType, weightInitScheme);
        addVariable(ret);

        if(variableType == VariableType.PLACEHOLDER){
            setOriginalPlaceHolderShape(name, shape);
            putShapeForVarName(name, shape);
        }
        return ret;
    }

    public SDVariable var(@NonNull String name, @NonNull LongShapeDescriptor shape, WeightInitScheme weightInitScheme) {
        return var(name, weightInitScheme, shape.dataType(), shape.getShape());
    }


    /**
     * Creates a {@link SDVariable} with the given shape and name<br>
     * Any array will be generated with all zeros for the values
     *
     * @param name  the name of the variable
     * @param shape the shape of the variable
     * @return the created variable
     */
    public SDVariable var(String name, org.nd4j.linalg.api.buffer.DataType dataType, long... shape) {
        Preconditions.checkNotNull(shape != null, "Invalid shape: shape may not be null");
        if(Shape.isPlaceholderShape(shape)){
            return placeHolder(name, dataType, shape);
        }
        return var(name, new ZeroInitScheme(), dataType, shape);
    }

    public SDVariable var(String name, LongShapeDescriptor shapeDesc) {
        Preconditions.checkNotNull(shapeDesc != null, "Invalid shape: shape may not be null");
        return var(name, shapeDesc, new ZeroInitScheme());
    }

    public SDVariable var(String name, int... shape){
        return var(name, Nd4j.defaultFloatingPointType(), shape);
    }

    public SDVariable var(String name, long... shape){
        return var(name, Nd4j.defaultFloatingPointType(), shape);
    }

    /**
     * Creates a {@link SDVariable} with the given shape and name<br>
     * Any array will be generated with all zeros for the values
     *
     * @param name  the name of the variable
     * @param shape the shape of the variable
     * @return the created variable
     */
    public SDVariable var(String name, org.nd4j.linalg.api.buffer.DataType dataType, int... shape) {
        Preconditions.checkNotNull(shape, "Invalid shape: shape may not be null");
        if(Shape.isPlaceholderShape(shape)){
            return placeHolder(name, dataType, ArrayUtil.toLongArray(shape));
        }
        return var(name, new ZeroInitScheme(), dataType, ArrayUtil.toLongArray(shape));
    }


    /**
     * Initialize a {@link SDVariable} reference tying this variable to this samediff instance.
     * <p>
     * {@link NDArraySupplierInitScheme} is used to ensure that if the array is allocated anywhere
     * and {@link SameDiff} instance to exist as a copy of the variable.
     *
     * @param arr
     * @return
     */
    public SDVariable var(@NonNull final SDVariable arr) {
        if (variables.containsKey(arr.getVarName()) && variables.get(arr.getVarName()).getVariable().getArr() != null)
            return variables.get(arr.getVarName()).getVariable();

        if (arr.getVarName() == null || arr.getVarName().length() < 1)
            throw new IllegalArgumentException("Name for variable must be defined");

        VariableType vt = arr.getVariableType();
        WeightInitScheme s = null;
        if(vt == VariableType.CONSTANT || vt == VariableType.VARIABLE){
            s = new NDArraySupplierInitScheme(arr.getArr());
        }

        SDVariable ret = new SDVariable(arr.getVarName(), arr.getVariableType(), this, arr.getShape(), arr.dataType(), s);
        return addVariable(ret);
    }

    private String getNewVarName() {
        String varName = "sd_var_" + String.valueOf(variableId);
        while (variables.containsKey(varName)) {
            variableId++;
            varName = "sd_var_" + String.valueOf(variableId);
        }
        return varName;
    }

    /**
     * Creates a {@link SDVariable} with the specified shape and a generated name<br>
     * Any array will be generated with all zeros for the values
     *
     * @param shape the shape of the variable
     * @return the created variable
     */
    public SDVariable var(org.nd4j.linalg.api.buffer.DataType dataType, int... shape) {
        return var(getNewVarName(), dataType, shape);
    }

    /**
     * Creates a {@link SDVariable} with the specified shape and a generated name<br>
     * Any array will be generated with all zeros for the values
     *
     * @param shape the shape of the variable
     * @return the created variable
     */
    public SDVariable var(org.nd4j.linalg.api.buffer.DataType dataType, long... shape) {
        return var(getNewVarName(), dataType, shape);
    }

    /**
     * Creates a {@link SDVariable} with the specified shape and a generated name. The associated array will
     * then be generated using the specified weight initialization scheme
     *
     * @param weightInitScheme The weight initialization scheme to use when generating an INDArray
     * @param shape            the shape of the variable
     * @return the created variable
     */
    public SDVariable var(WeightInitScheme weightInitScheme, org.nd4j.linalg.api.buffer.DataType dataType, long... shape) {
        return var(getNewVarName(), weightInitScheme, dataType, shape);
    }

    /**
     * Create an {@link SDVariable} with a generated name, and assocate the specified array with it
     * @param arr Array to associate with the new variable
     * @return New SDVariable
     * @see #var(String, INDArray)
     */
    public SDVariable var(INDArray arr) {
        return var(getNewVarName(), arr);
    }

    /**
     * Create an {@link SDVariable} with the specified name, and assocate the specified array with it
     * @param arr Array to associate with the new variable
     * @return New SDVariable with the specified name and array
     */
    public SDVariable var(String name, INDArray arr) {
        if (variables.containsKey(name) && variables.get(name).getVariable().getArr() != null)
            throw new IllegalArgumentException("Another variable with the name " + name + " already exists.");


        if (name == null || name.length() < 1)
            name = getNewVarName();

        if (arr == null)
            throw new IllegalArgumentException("Array for " + name + " must not be null");

        arr = arr.migrate();
        SDVariable ret = new SDVariable(name, VariableType.VARIABLE, this, arr.shape(), arr.dataType(), new NDArraySupplierInitScheme(arr));

        associateArrayWithVariable(arr, ret);
        if (ArrayUtil.prod(arr.shape()) == 1)
            ret.setScalarValue(Nd4j.scalar(arr.getDouble(0)));

        addVariable(ret);
        if (getShapeForVarName(name) == null)
            putShapeForVarName(name, arr.shape());
        return ret;
    }

    /**
     * Generate a square identity matrix with the specified number of rows.
     *
     * @param rows Number of rows (and columns)
     * @return SDVariable with an identity matrix array
     */
    public SDVariable eye(int rows) {
        return eye(rows, rows);
    }

    /**
     * Generate an identity matrix with the specified number of rows and columns.
     *
     * @param rows Number of rows
     */
    public SDVariable eye(String name, int rows) {
        return eye(name, rows, rows);
    }

    /**
     * @see #eye(String, int, int)
     */
    public SDVariable eye(int rows, int cols) {
        return eye(null, rows, cols);
    }

    /**
     * Generate an identity matrix with the specified number of rows and columns
     * Example:<br>
     * <pre>
     * {@code SDVariable eye = eye(3,2)
     * eye:
     * [ 1, 0]
     * [ 0, 1]
     * [ 0, 0]}
     * </pre>
     *
     * @param name Name of the new SDVariable
     * @param rows Number of rows
     * @param cols Number of columns
     * @return SDVaribable identity matrix
     */
    public SDVariable eye(String name, int rows, int cols) {
        return eye(name, rows, cols, null);
    }

    /**
     * see {@link #eye(String, int, int, int...)}
     */
    public SDVariable eye(int rows, int cols, int... batchDimension) {
        return eye(null, rows, cols, batchDimension);
    }

    /**
     * Generate an identity matrix with the specified number of rows and columns, with optional leading dims<br>
     * Example:<br>
     * batchShape: [3,3]<br>
     * numRows: 2<br>
     * numCols: 4<br>
     * returns a tensor of shape (3, 3, 2, 4) that consists of 3 * 3 batches of (2,4)-shaped identity matrices:<br>
     * 1 0 0 0<br>
     * 0 1 0 0<br>
     *
     * @param rows           Number of rows
     * @param cols           Number of columns
     * @param batchDimension Batch dimensions. May be null
     */
    public SDVariable eye(String name, int rows, int cols, int... batchDimension) {
        SDVariable eye = new Eye(this, rows, cols, batchDimension).outputVariables()[0];
        return updateVariableNameAndReference(eye, name);
    }

    /**
     * As per {@link #eye(String, int, int, int...)} bit with the number of rows/columns specified as scalar SDVariables,
     * and the batch dimension specified as a 1D SDVariable
     */
    public SDVariable eye(String name, SDVariable rows, SDVariable cols, SDVariable batchDimension){
        SDVariable eye = new Eye(this, rows, cols, batchDimension).outputVariable();
        return updateVariableNameAndReference(eye, name);
    }

    /**
     * As per {@link #eye(int, int, int...)} bit with the number of rows/columns specified as scalar SDVariables,
     * and the batch dimension specified as a 1D SDVariable
     */
    public SDVariable eye(SDVariable rows, SDVariable cols, SDVariable batchDimension){
        return eye(null, rows, cols, batchDimension);
    }

    /**
     * As per {@link #eye(String, int, int)} bit with the number of rows/columns specified as scalar SDVariables
     */
    public SDVariable eye(String name, SDVariable rows, SDVariable cols){
        SDVariable eye = new Eye(this, rows, cols).outputVariables()[0];
        return updateVariableNameAndReference(eye, name);
    }

    /**
     * As per {@link #eye(int, int)} bit with the number of rows/columns specified as scalar SDVariables
     */
    public SDVariable eye(SDVariable rows, SDVariable cols){
        SDVariable eye = new Eye(this, rows, cols).outputVariables()[0];
        return updateVariableNameAndReference(eye, null);
    }

    /**
     * As per {@link #eye(String, int)} but with the number of rows specified as a scalar SDVariable
     */
    public SDVariable eye(String name, SDVariable rows){
        SDVariable eye = new Eye(this, rows).outputVariables()[0];
        return updateVariableNameAndReference(eye, name);
    }

    /**
     * As per {@link #eye(int)} but with the number of rows specified as a scalar SDVariable
     */
    public SDVariable eye(SDVariable rows){
        SDVariable eye = new Eye(this, rows).outputVariables()[0];
        return updateVariableNameAndReference(eye, null);
    }

    /**
     * Remove an argument for a function. Note that if this function does not contain the argument, it will just be a no op.
     *
     * @param varName  the variable name to remove
     * @param function the function to remove the argument from
     */
    public void removeArgFromFunction(String varName, DifferentialFunction function) {
        val args = function.args();

        for (int i = 0; i < args.length; i++) {
            if (args[i].getVarName().equals(varName)) {
                /**
                 * Since we are removing the variable reference
                 * from the arguments we need to  update both
                 * the reverse and forward arguments.
                 */
                List<String> reverseArgs = ops.get(function.getOwnName()).getInputsToOp();
                val newArgs = new ArrayList<String>(args.length - 1);
                for (int arg = 0; arg < args.length; arg++) {
                    if (!reverseArgs.get(arg).equals(varName)) {
                        newArgs.add(reverseArgs.get(arg));
                    }
                }

                ops.get(function.getOwnName()).setInputsToOp(newArgs);
                break;
            }
        }
    }

    /**
     * Get the variable based on the opName
     *
     * @param name the opName of the variable
     * @return the variabel instance if there is one
     */
    public SDVariable getVariable(String name) {
        Variable v = variables.get(name);
        return v == null ? null : v.getVariable();
    }

    public boolean hasVariable(String name){
        return variables.containsKey(name);
    }


    /**
     * Get the gradient for the given vertex id
     *
     * @param varName the vertex id
     * @return the gradient for this variable or null
     */
    public SDVariable getGradForVariable(String varName) {
        //TODO 2018/06/26 - Review this?
        //Gradients are being placed in the inner "grad" function SameDiff instance, but not the outer one
        // should they be synced and we just use the map in this instance?
        if (variables.containsKey(varName) && variables.get(varName).getGradient() != null) {
            return variables.get(varName).getGradient();
        } else if(sameDiffFunctionInstances.containsKey("grad") && sameDiffFunctionInstances.get("grad").variables.containsKey(varName)){
            return sameDiffFunctionInstances.get("grad").variables.get(varName).getGradient();
        }
        return null;
    }


    /**
     * Assign a SDVariable to represent the gradient of the SDVariable with the specified name
     *
     * @param variableName the variable name to assign the gradient variable for
     * @param variable     the gradient variable
     */
    public void setGradientForVariableName(String variableName, SDVariable variable) {
        Preconditions.checkState(variables.containsKey(variableName), "No variable exists with name \"%s\"", variableName);
        if (variable == null) {
            throw new ND4JIllegalStateException("Unable to set null gradient for variable name " + variableName);
        }
        variables.get(variableName).setGradient(variable);
    }


    /**
     * @param varName
     * @param forwardVariable
     */
    public void setForwardVariableForVarName(String varName, SDVariable forwardVariable) {
        forwardVarForGrad.put(varName, forwardVariable);
    }

    /**
     * Get the gradient for the variable with the specified variable name.
     * Note that in order to run this function, {@link #execBackwards()} must be executed first.
     * All gradient functions are obtained from the results of the execBackwards call.
     *
     * @param varName the variable name to get the gradient variable for.
     * @return The gradient variable for the specified variable
     */
    public SDVariable grad(String varName) {
        if (!sameDiffFunctionInstances.containsKey("grad")) {
            throw new IllegalStateException("Unable to obtain gradient. Please run execBackwards() first.");
        }

        SameDiff grad = getFunction("grad");
        SDVariable var = grad.getVariable(varName);
        return getFunction("grad").getGradForVariable(var.getVarName());
    }


    /**
     * @see #randomUniform(String, double, double, SDVariable)
     */
    public SDVariable randomUniform(double min, double max, SDVariable shape){
        return randomUniform(null, min, max, shape);
    }

    /**
     * Generate a new random SDVariable, where values are randomly sampled according to a uniform distribution,
     * U(min,max)<br>
     * See {@link #randomUniform(double, double, long...)} for the equivalent function where the shape is
     * specified as a long[] instead
     *
     * @param name  Name of the new SDVariable
     * @param min   Minimum value
     * @param max   Maximum value. Must satisfy max >= min
     * @param shape  Shape of the new random SDVariable, as a 1D array
     * @return New SDVariable
     */
    public SDVariable randomUniform(String name, double min, double max, SDVariable shape){
        SDVariable ret = f().randomUniform(min, max, shape);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #randomUniform(String, double, double, long...)
     */
    public SDVariable randomUniform(double min, double max, long... shape){
        return randomUniform(null, min, max, shape);
    }

    /**
     * Generate a new random SDVariable, where values are randomly sampled according to a uniform distribution,
     * U(min,max)<br>
     * See {@link #randomUniform(double, double, long...)} for the equivalent function where the shape is
     * specified as a SDVariable instead
     *
     * @param name  Name of the new SDVariable
     * @param min   Minimum value
     * @param max   Maximum value. Must satisfy max >= min
     * @param shape Shape of the new random SDVariable
     * @return New SDVariable
     */
    public SDVariable randomUniform(String name, double min, double max, long... shape){
        SDVariable ret = f().randomUniform(min, max, shape);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #randomNormal(String, double, double, SDVariable)
     */
    public SDVariable randomNormal(double mean, double stddev, SDVariable shape){
        return randomNormal(null, mean, stddev, shape);
    }

    /**
     * Generate a new random SDVariable, where values are randomly sampled according to a Gaussian (normal) distribution,
     * N(mean, stdev)<br>
     * See {@link #randomNormal(String, double, double, long...)} for the equivalent function where the shape is
     * specified as a long[] instead
     *
     * @param name   Name of the new SDVariable
     * @param mean   Mean value for the random array
     * @param stddev Standard deviation for the random array
     * @param shape  Shape of the new random SDVariable, as a 1D array
     * @return New SDVariable
     */
    public SDVariable randomNormal(String name, double mean, double stddev, SDVariable shape){
        SDVariable ret = f().randomNormal(mean, stddev, shape);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #randomNormal(String, double, double, long...)
     */
    public SDVariable randomNormal(double mean, double stddev, long... shape){
        return randomNormal(null, mean, stddev, shape);
    }

    /**
     * Generate a new random SDVariable, where values are randomly sampled according to a Gaussian (normal) distribution,
     * N(mean, stdev)<br>
     * See {@link #randomNormal(String, double, double, SDVariable)} for the equivalent function where the shape is
     * specified as a long[] instead
     *
     * @param name   Name of the new SDVariable
     * @param mean   Mean value for the random array
     * @param stddev Standard deviation for the random array
     * @param shape  Shape of the new random SDVariable
     * @return New SDVariable
     */
    public SDVariable randomNormal(String name, double mean, double stddev, long... shape){
        SDVariable ret = f().randomNormal(mean, stddev, shape);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #randomLogNormal(String, double, double, long...)
     */
    public SDVariable randomLogNormal(double mean, double stddev, long... shape){
        return randomLogNormal(null, mean, stddev, shape);
    }

    /**
     * Generate a new random SDVariable, where values are randomly sampled according to a Log Normal distribution,
     * i.e., {@code log(x) ~ N(mean, stdev)}<br>
     *
     * @param name   Name of the new SDVariable
     * @param mean   Mean value for the random array
     * @param stddev Standard deviation for the random array
     * @param shape  Shape of the new random SDVariable
     * @return New SDVariable
     */
    public SDVariable randomLogNormal(String name, double mean, double stddev, long... shape){
        SDVariable ret = f().randomLogNormal(mean, stddev, shape);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #randomNormalTruncated(String, double, double, long...)
     */
    public SDVariable randomNormalTruncated(double mean, double stddev, long... shape){
        return randomNormalTruncated(null, mean, stddev, shape);
    }

    /**
     * Generate a new random SDVariable, where values are randomly sampled according to a Gaussian (normal) distribution,
     * N(mean, stdev). However, any values more than 1 standard deviation from the mean are dropped and re-sampled<br>
     *
     * @param name   Name of the new SDVariable
     * @param mean   Mean value for the random array
     * @param stddev Standard deviation for the random array
     * @param shape  Shape of the new random SDVariable
     * @return New SDVariable
     */
    public SDVariable randomNormalTruncated(String name, double mean, double stddev, long... shape){
        SDVariable ret = f().randomNormalTruncated(mean, stddev, shape);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #randomBernoulli(String, double, SDVariable)
     */
    public SDVariable randomBernoulli(double p, SDVariable shape){
        return randomBernoulli(null, p, shape);
    }

    /**
     * Generate a new random SDVariable, where values are randomly sampled according to a Bernoulli distribution,
     * with the specified probability. Array values will have value 1 with probability P and value 0 with probability
     * 1-P.<br>
     * See {@link #randomBernoulli(String, double, long...)}  for the equivalent function where the shape is
     * specified as a long[] instead
     *
     * @param name   Name of the new SDVariable
     * @param p      Probability of value 1
     * @param shape  Shape of the new random SDVariable, as a 1D array
     * @return New SDVariable
     */
    public SDVariable randomBernoulli(String name, double p, SDVariable shape){
        SDVariable ret = f().randomBernoulli(p, shape);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #randomBernoulli(String, double, long...)
     */
    public SDVariable randomBernoulli(double p, long... shape){
        return randomBernoulli(null, p, shape);
    }

    /**
     * Generate a new random SDVariable, where values are randomly sampled according to a Bernoulli distribution,
     * with the specified probability. Array values will have value 1 with probability P and value 0 with probability
     * 1-P.<br>
     * See {@link #randomBernoulli(String, double, SDVariable)}  for the equivalent function where the shape is
     * specified as a SDVarible instead
     *
     * @param name   Name of the new SDVariable
     * @param p      Probability of value 1
     * @param shape  Shape of the new random SDVariable, as a 1D array
     * @return New SDVariable
     */
    public SDVariable randomBernoulli(String name, double p, long... shape){
        SDVariable ret = f().randomBernoulli(p, shape);
        return updateVariableNameAndReference(ret, name);
    }


    /**
     * Generate a new random SDVariable, where values are randomly sampled according to a Binomial distribution,
     * with the specified number of trials and probability.
     *
     * @param nTrials Number of trials parameter for the binomial distribution
     * @param p       Probability of success for each trial
     * @param shape   Shape of the new random SDVariable, as a 1D array
     * @return New SDVariable
     */
    public SDVariable randomBinomial(int nTrials, double p, long... shape){
        return randomBinomial(null, nTrials, p, shape);
    }

    /**
     * Generate a new random SDVariable, where values are randomly sampled according to a Binomial distribution,
     * with the specified number of trials and probability.
     *
     * @param name    Name of the new SDVariable
     * @param nTrials Number of trials parameter for the binomial distribution
     * @param p       Probability of success for each trial
     * @param shape   Shape of the new random SDVariable, as a 1D array
     * @return New SDVariable
     */
    public SDVariable randomBinomial(String name, int nTrials, double p, long... shape){
        SDVariable ret = f().randomBinomial(nTrials, p, shape);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Generate a new random SDVariable, where values are randomly sampled according to a exponential distribution:
     * P(x) = lambda * exp(-lambda * x)
     *
     * @param lambda Must be > 0
     * @param shape  Shape of the output
     * @return new SDVariable
     */
    public SDVariable randomExponential(double lambda, SDVariable shape) {
        return randomExponential(null, lambda, shape);
    }

    /**
     * Generate a new random SDVariable, where values are randomly sampled according to a exponential distribution:
     * P(x) = lambda * exp(-lambda * x)
     *
     * @param name   Name of the output variable
     * @param lambda Must be > 0
     * @param shape  Shape of the new variable
     * @return new SDVaribale
     */
    public SDVariable randomExponential(String name, double lambda, SDVariable shape) {
        SDVariable ret = f().randomExponential(lambda, shape);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * 2D Convolution layer operation - Upsampling 2d with same scale for both dimensions. NCHW input format.
     *
     * @param input Input - 4d CNN (image) activations in NCHW format (shape [minibatch, channels, height, width])
     * @param scale Scale to upsample in both H and W dimensions
     * @return Upsampled input
     */
    public SDVariable upsampling2d(SDVariable input, int scale) {
        return upsampling2d(null, input, true, scale, scale);
    }

    /**
     * 2D Convolution layer operation - Upsampling 2d with same scale for both dimensions. NCHW input format.
     *
     * @param input Input - 4d CNN (image) activations in NCHW format (shape [minibatch, channels, height, width])
     * @param scale Scale to upsample in both H and W dimensions
     * @return Upsampled input
     */
    public SDVariable upsampling2d(String name, SDVariable input, int scale) {
        return upsampling2d(name, input, true, scale, scale);
    }

    /**
     * 2D Convolution layer operation - Upsampling 2d
     *
     * @param input Input - 4d CNN (image) activations in NCHW format (shape [minibatch, channels, height, width])
     *              or NHWC format (shape [minibatch, height, width, channels])
     * @param nchw   If true: input is in NCHW (minibatch, channels, height, width) format. False: NHWC format
     * @param scaleH Scale to upsample in height dimension
     * @param scaleW Scale to upsample in width dimension
     * @return Upsampled input
     */
    public SDVariable upsampling2d(SDVariable input, boolean nchw, int scaleH, int scaleW) {
        return upsampling2d(null, input, nchw, scaleH, scaleW);
    }

    /**
     * 2D Convolution layer operation - Upsampling 2d
     *
     * @param input  Input, in NCHW format
     * @param nchw   If true: input is in NCHW (minibatch, channels, height, width) format. False: NHWC format
     * @param scaleH Scale to upsample in height dimension
     * @param scaleW Scale to upsample in width dimension
     * @return Upsampled input
     */
    public SDVariable upsampling2d(String name, SDVariable input, boolean nchw, int scaleH, int scaleW) {
        SDVariable ret = f().upsampling2d(input, nchw, scaleH, scaleW);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * 2D Convolution layer operation - average pooling 2d
     *
     * @param input           the input to average pooling 2d operation - 4d CNN (image) activations in NCHW format
     *                        (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param pooling2DConfig the configuration for
     * @return Result after applying average pooling on the input
     */
    public SDVariable avgPooling2d(SDVariable input, Pooling2DConfig pooling2DConfig) {
        return avgPooling2d(null, input, pooling2DConfig);
    }

    /**
     * 2D Convolution layer operation - average pooling 2d
     *
     * @param name            name of the operation in SameDiff
     * @param input           the input to average pooling 2d operation - 4d CNN (image) activations in NCHW format
     *                        (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param pooling2DConfig the configuration
     * @return Result after applying average pooling on the input
     */
    public SDVariable avgPooling2d(String name, SDVariable input, Pooling2DConfig pooling2DConfig) {
        SDVariable ret = f().avgPooling2d(input, pooling2DConfig);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * 2D Convolution layer operation - max pooling 2d
     *
     * @param input           the input to max pooling 2d operation - 4d CNN (image) activations in NCHW format
     *                        (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param pooling2DConfig the configuration
     * @return Result after applying max pooling on the input
     */
    public SDVariable maxPooling2d(SDVariable input, Pooling2DConfig pooling2DConfig) {
        return maxPooling2d(null, input, pooling2DConfig);
    }

    /**
     * 2D Convolution layer operation - max pooling 2d
     *
     * @param name            name of the operation in SameDiff
     * @param input           the input to max pooling 2d operation - 4d CNN (image) activations in NCHW format
     *                        (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param pooling2DConfig the configuration
     * @return Result after applying max pooling on the input
     */
    public SDVariable maxPooling2d(String name, SDVariable input, Pooling2DConfig pooling2DConfig) {
        SDVariable ret = f().maxPooling2d(input, pooling2DConfig);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * 3D convolution layer operation - average pooling 3d
     *
     * @param input           the input to average pooling 3d operation - 5d activations in NCDHW format
     *                        (shape [minibatch, channels, depth, height, width]) or NDHWC format
     *                        (shape [minibatch, depth, height, width, channels])
     * @param pooling3DConfig the configuration
     * @return Result after applying average pooling on the input
     */
    public SDVariable avgPooling3d(SDVariable input, Pooling3DConfig pooling3DConfig) {
        return avgPooling3d(null, input, pooling3DConfig);
    }

    /**
     * 3D convolution layer operation - average pooling 3d
     *
     * @param name            name of the operation in SameDiff
     * @param input           the input to average pooling 3d operation - 5d activations in NCDHW format
     *                        (shape [minibatch, channels, depth, height, width]) or NDHWC format
     *                        (shape [minibatch, depth, height, width, channels])
     * @param pooling3DConfig the configuration
     * @return Result after applying average pooling on the input
     */
    public SDVariable avgPooling3d(String name, SDVariable input, Pooling3DConfig pooling3DConfig) {
        SDVariable ret = f().avgPooling3d(input, pooling3DConfig);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * 3D convolution layer operation - max pooling 3d operation.
     *
     * @param input           the input to average pooling 3d operation - 5d activations in NCDHW format
     *                        (shape [minibatch, channels, depth, height, width]) or NDHWC format
     *                        (shape [minibatch, depth, height, width, channels])
     * @param pooling3DConfig the configuration
     * @return Result after applying max pooling on the input
     */
    public SDVariable maxPooling3d(SDVariable input, Pooling3DConfig pooling3DConfig) {
        return maxPooling3d(null, input, pooling3DConfig);
    }

    /**
     * 3D convolution layer operation - max pooling 3d operation.
     *
     * @param name            name of the operation in SameDiff
     * @param input           the input to average pooling 3d operation - 5d activations in NCDHW format
     *                        (shape [minibatch, channels, depth, height, width]) or NDHWC format
     *                        (shape [minibatch, depth, height, width, channels])
     * @param pooling3DConfig the configuration
     * @return Result after applying max pooling on the input
     */
    public SDVariable maxPooling3d(String name, SDVariable input, Pooling3DConfig pooling3DConfig) {
        SDVariable ret = f().maxPooling3d(input, pooling3DConfig);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * 1D Convolution layer operation - Conv1d
     *
     * @param input        the input array/activations for the conv1d op
     * @param weights      weights for conv1d op - rank 3 array with values [kernelSize, inputChannels, outputChannels]
     * @param conv1DConfig the configuration
     * @return
     */
    public SDVariable conv1d(SDVariable input, SDVariable weights, Conv1DConfig conv1DConfig) {
        return conv1d(null, input, weights, conv1DConfig);
    }

    /**
     * Conv1d operation.
     *
     * @param name         name of the operation in SameDiff
     * @param input        the inputs to conv1d
     * @param weights      weights for conv1d op - rank 3 array with values [kernelSize, inputChannels, outputChannels]
     * @param conv1DConfig the configuration
     * @return
     */
    public SDVariable conv1d(String name, SDVariable input, SDVariable weights, Conv1DConfig conv1DConfig) {
        SDVariable ret = f().conv1d(input, weights, conv1DConfig);
        return updateVariableNameAndReference(ret, name);
    }


    /**
     * 2D convolution layer operation - local response normalization
     *
     * @param inputs    the inputs to lrn
     * @param lrnConfig the configuration
     * @return
     */
    public SDVariable localResponseNormalization(SDVariable inputs, LocalResponseNormalizationConfig lrnConfig) {
        return localResponseNormalization(null, inputs, lrnConfig);
    }

    /**
     * 2D convolution layer operation - local response normalization
     *
     * @param name      name of the operation in SameDiff
     * @param input    the inputs to lrn
     * @param lrnConfig the configuration
     * @return
     */
    public SDVariable localResponseNormalization(String name, SDVariable input,
                                                 LocalResponseNormalizationConfig lrnConfig) {
        SDVariable ret = f().localResponseNormalization(input, lrnConfig);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * 2D Convolution operation (without bias)
     *
     * @param layerInput the input to max pooling 2d operation - 4d CNN (image) activations in NCHW format
     *                   (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param weights    Weights for the convolution operation. 4 dimensions with format [kernelHeight, kernelWidth, inputChannels, outputChannels]
     * @param config     Conv2DConfig configuration
     * @return result of conv2d op
     */
    public SDVariable conv2d(SDVariable layerInput, SDVariable weights, Conv2DConfig config) {
        return conv2d(layerInput, weights, null, config);
    }


    /**
     * 2D Convolution operation with optional bias
     *
     * @param layerInput the input to max pooling 2d operation - 4d CNN (image) activations in NCHW format
     *                   (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param weights    Weights for the convolution operation. 4 dimensions with format [kernelHeight, kernelWidth, inputChannels, outputChannels]
     * @param bias       Optional 1D bias array with shape [outputChannels]. May be null.
     * @param config     Conv2DConfig configuration
     * @return result of conv2d op
     */
    public SDVariable conv2d(SDVariable layerInput, SDVariable weights, SDVariable bias, Conv2DConfig config) {
        SDVariable[] arr = new SDVariable[bias == null ? 2 : 3];
        arr[0] = layerInput;
        arr[1] = weights;
        if (bias != null)
            arr[2] = bias;
        return conv2d(arr, config);
    }

    /**
     * 2D Convolution operation with optional bias
     *
     * @param inputs an array with either 2 elements (layerInput, weights) or 3 elements (layerInput, weights, bias) as
     *               described in {@link #conv2d(SDVariable, SDVariable, SDVariable, Conv2DConfig)}
     * @param config     Conv2DConfig configuration
     * @return result of convolution 2d operation
     */
    public SDVariable conv2d(SDVariable[] inputs, Conv2DConfig config) {
        return conv2d(null, inputs, config);
    }

    /**
     * 2D Convolution operation with optional bias
     *
     * @param name   Name of the output SDVariable
     * @param inputs an array with either 2 elements (layerInput, weights) or 3 elements (layerInput, weights, bias) as
     *               described in {@link #conv2d(SDVariable, SDVariable, SDVariable, Conv2DConfig)}
     * @param config Conv2DConfig configuration
     * @return result of convolution 2d operation
     */
    public SDVariable conv2d(String name, SDVariable[] inputs, Conv2DConfig config) {
        SDVariable ret = f().conv2d(inputs, config);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Depth-wise 2D convolution operation without bias
     *
     * @param layerInput   the input to max pooling 2d operation - 4d CNN (image) activations in NCHW format
     *                     (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param depthWeights Depth-wise conv2d weights. 4 dimensions with format [kernelHeight, kernelWidth, inputChannels, depthMultiplier]
     * @param config       Conv2DConfig configuration
     * @return result of conv2d op
     */
    public SDVariable depthWiseConv2d(SDVariable layerInput, SDVariable depthWeights, Conv2DConfig config) {
        return depthWiseConv2d(layerInput, depthWeights, null, config);
    }


    /**
     * Depth-wise 2D convolution operation with optional bias
     *
     * @param layerInput   the input to max pooling 2d operation - 4d CNN (image) activations in NCHW format
     *                     (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param depthWeights Depth-wise conv2d weights. 4 dimensions with format [kernelHeight, kernelWidth, inputChannels, depthMultiplier]
     * @param bias         Optional 1D bias array with shape [outputChannels]. May be null.
     * @param config       Conv2DConfig configuration
     * @return result of depthwise conv2d op
     */
    public SDVariable depthWiseConv2d(SDVariable layerInput, SDVariable depthWeights, SDVariable bias, Conv2DConfig config) {
        SDVariable[] arr = new SDVariable[bias == null ? 2 : 3];
        arr[0] = layerInput;
        arr[1] = depthWeights;
        if (bias != null)
            arr[2] = bias;
        return depthWiseConv2d(arr, config);
    }


    /**
     * Depth-wise convolution 2D operation.
     *
     * @param inputs            the inputs to depth-wise conv2d. An array with either 2 elements (layerInput, depthWeights)
     *                          or 3 elements (layerInput, depthWeights, bias) as described in
     *                          {@link #depthWiseConv2d(SDVariable, SDVariable, SDVariable, Conv2DConfig)}
     * @param depthConv2DConfig the configuration
     * @return result of depthwise conv2d op
     */
    public SDVariable depthWiseConv2d(SDVariable[] inputs, Conv2DConfig depthConv2DConfig) {
        return depthWiseConv2d(null, inputs, depthConv2DConfig);
    }


    /**
     * Depth-wise convolution 2D operation.
     *
     * @param name              name of the output variable
     * @param inputs            the inputs to depth-wise conv2d. An array with either 2 elements (layerInput, depthWeights)
     *                          or 3 elements (layerInput, depthWeights, bias) as described in
     *                          {@link #depthWiseConv2d(SDVariable, SDVariable, SDVariable, Conv2DConfig)}
     * @param depthConv2DConfig the configuration
     * @return result of depthwise conv2d op
     */
    public SDVariable depthWiseConv2d(String name, SDVariable[] inputs, Conv2DConfig depthConv2DConfig) {
        SDVariable ret = f().depthWiseConv2d(inputs, depthConv2DConfig);
        return updateVariableNameAndReference(ret, name);
    }


    /**
     * Separable 2D convolution operation without bias
     *
     * @param layerInput   the input to max pooling 2d operation - 4d CNN (image) activations in NCHW format
     *                     (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param depthWeights Separable conv2d depth weights. 4 dimensions with format [kernelHeight, kernelWidth, inputChannels, depthMultiplier]
     * @param pointWeights Point weights, rank 4 with format [1, 1, inputChannels*depthMultiplier, outputChannels]
     *                     May be null
     * @param config       Conv2DConfig configuration
     * @return result of separable convolution 2d operation
     */
    public SDVariable separableConv2d(SDVariable layerInput, SDVariable depthWeights, SDVariable pointWeights,
                                      Conv2DConfig config) {
        return separableConv2d(layerInput, depthWeights, pointWeights, null, config);
    }


    /**
     * Separable 2D convolution operation with optional bias
     *
     * @param layerInput   the input to max pooling 2d operation - 4d CNN (image) activations in NCHW format
     *                     (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param depthWeights Separable conv2d depth weights. 4 dimensions with format [kernelHeight, kernelWidth, inputChannels, depthMultiplier]
     * @param pointWeights Point weights, rank 4 with format [1, 1, inputChannels*depthMultiplier, outputChannels]
     *                     May be null
     * @param bias         Optional bias, rank 1 with shape [outputChannels]. May be null.
     * @param config       Conv2DConfig configuration
     * @return result of separable convolution 2d operation
     */
    public SDVariable separableConv2d(SDVariable layerInput, SDVariable depthWeights, SDVariable pointWeights,
                                      SDVariable bias, Conv2DConfig config) {
        SDVariable[] arr = new SDVariable[bias == null ? 3 : 4];
        arr[0] = layerInput;
        arr[1] = depthWeights;
        arr[2] = pointWeights;
        if (bias != null)
            arr[3] = bias;
        return sconv2d(arr, config);
    }

    /**
     * Separable 2D convolution operation with/without optional bias
     *
     * @param inputs       the inputs to separable conv2 operation. Should be length 3 (layerInput, depthWeights, pointWeights)
     *                     or length 4 (layerInput, depthWeights, pointWeights, bias) as described in {@link #separableConv2d(SDVariable, SDVariable, SDVariable, SDVariable, Conv2DConfig)}
     * @param conv2DConfig the configuration
     * @return result of separable convolution 2d operation
     */
    public SDVariable sconv2d(SDVariable[] inputs, Conv2DConfig conv2DConfig) {
        return sconv2d(null, inputs, conv2DConfig);
    }


    /**
     * Separable 2D convolution operation with/without optional bias
     *
     * @param name         name of the output variable
     * @param inputs       the inputs to separable conv2 operation. Should be length 3 (layerInput, depthWeights, pointWeights)
     *                     or length 4 (layerInput, depthWeights, pointWeights, bias) as described in {@link #separableConv2d(SDVariable, SDVariable, SDVariable, SDVariable, Conv2DConfig)}
     * @param conv2DConfig the configuration
     * @return result of separable convolution 2d operation
     */
    public SDVariable sconv2d(String name, SDVariable[] inputs, Conv2DConfig conv2DConfig) {
        SDVariable ret = f().sconv2d(inputs, conv2DConfig);
        return updateVariableNameAndReference(ret, name);
    }


    /**
     * 2D deconvolution operation without bias
     *
     * @param layerInput     the input to deconvolution 2d operation - 4d CNN (image) activations in NCHW format
     *                       (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param weights        Weights for the 2d deconvolution operation. 4 dimensions with format [inputChannels, outputChannels, kernelHeight, kernelWidth].
     * @param deconv2DConfig DeConv2DConfig configuration
     * @return result of deconv2d op
     */
    public SDVariable deconv2d(SDVariable layerInput, SDVariable weights, DeConv2DConfig deconv2DConfig) {
        return deconv2d(layerInput, weights, null, deconv2DConfig);
    }


    /**
     * 2D deconvolution operation with optional bias
     *
     * @param layerInput     the input to deconvolution 2d operation - 4d CNN (image) activations in NCHW format
     *                       (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param weights        Weights for the 2d deconvolution operation. 4 dimensions with format [inputChannels, outputChannels, kernelHeight, kernelWidth].
     * @param bias           Optional 1D bias array with shape [outputChannels]. May be null.
     * @param deconv2DConfig DeConv2DConfig configuration
     * @return result of deconv2d op
     */
    public SDVariable deconv2d(SDVariable layerInput, SDVariable weights, SDVariable bias, DeConv2DConfig deconv2DConfig) {
        SDVariable[] arr = new SDVariable[bias == null ? 2 : 3];
        arr[0] = layerInput;
        arr[1] = weights;
        if (bias != null)
            arr[2] = bias;
        return deconv2d(arr, deconv2DConfig);
    }

    /**
     * 2D deconvolution operation with or without optional bias
     *
     * @param inputs         Inputs to the deconvolution 2d operation - input array of length 2 (layerInput, weights)
     *                       or length 3 (layerInput, weights, bias) as described in {@link #deconv2d(SDVariable[], DeConv2DConfig)}
     * @param deconv2DConfig the configuration
     * @return result of deconv2d op
     */
    public SDVariable deconv2d(SDVariable[] inputs, DeConv2DConfig deconv2DConfig) {
        return deconv2d(null, inputs, deconv2DConfig);
    }


    /**
     * 2D deconvolution operation with or without optional bias
     *
     * @param name           Name of the output variable
     * @param inputs         Inputs to the deconvolution 2d operation - input array of length 2 (layerInput, weights)
     *                       or length 3 (layerInput, weights, bias) as described in {@link #deconv2d(SDVariable[], DeConv2DConfig)}
     * @param deconv2DConfig the configuration
     * @return result of deconv2d op
     */
    public SDVariable deconv2d(String name, SDVariable[] inputs, DeConv2DConfig deconv2DConfig) {
        SDVariable ret = f().deconv2d(inputs, deconv2DConfig);
        return updateVariableNameAndReference(ret, name);
    }


    /**
     * Convolution 3D operation without bias
     *
     * @param input        the input to average pooling 3d operation - 5d activations in NCDHW format
     *                     (shape [minibatch, channels, depth, height, width]) or NDHWC format
     *                     (shape [minibatch, depth, height, width, channels])
     * @param weights      Weights for conv3d. Rank 5 with shape [kernelDepth, kernelHeight, kernelWidth, inputChannels, outputChannels].
     * @param conv3DConfig the configuration
     * @return Conv3d output variable
     */
    public SDVariable conv3d(SDVariable input, SDVariable weights, Conv3DConfig conv3DConfig) {
        return conv3d(null, input, weights, null, conv3DConfig);
    }

    /**
     * Convolution 3D operation with optional bias
     *
     * @param input        the input to average pooling 3d operation - 5d activations in NCDHW format
     *                     (shape [minibatch, channels, depth, height, width]) or NDHWC format
     *                     (shape [minibatch, depth, height, width, channels])
     * @param weights      Weights for conv3d. Rank 5 with shape [kernelDepth, kernelHeight, kernelWidth, inputChannels, outputChannels].
     * @param bias         Optional 1D bias array with shape [outputChannels]. May be null.
     * @param conv3DConfig the configuration
     * @return Conv3d output variable
     */
    public SDVariable conv3d(SDVariable input, SDVariable weights, SDVariable bias, Conv3DConfig conv3DConfig) {
        return conv3d(null, input, weights, bias, conv3DConfig);
    }

    /**
     * Convolution 3D operation without bias
     *
     * @param name         Name of the output variable
     * @param input        the input to average pooling 3d operation - 5d activations in NCDHW format
     *                     (shape [minibatch, channels, depth, height, width]) or NDHWC format
     *                     (shape [minibatch, depth, height, width, channels])
     * @param weights      Weights for conv3d. Rank 5 with shape [kernelDepth, kernelHeight, kernelWidth, inputChannels, outputChannels].
     * @param conv3DConfig the configuration
     * @return Conv3d output variable
     */
    public SDVariable conv3d(String name, SDVariable input, SDVariable weights, Conv3DConfig conv3DConfig) {
        return conv3d(name, input, weights, null, conv3DConfig);
    }

    /**
     * Convolution 3D operation with optional bias
     *
     * @param name         Name of the output variable
     * @param input        the input to average pooling 3d operation - 5d activations in NCDHW format
     *                     (shape [minibatch, channels, depth, height, width]) or NDHWC format
     *                     (shape [minibatch, depth, height, width, channels])
     * @param weights      Weights for conv3d. Rank 5 with shape [kernelDepth, kernelHeight, kernelWidth, inputChannels, outputChannels].
     * @param bias         Optional 1D bias array with shape [outputChannels]. May be null.
     * @param conv3DConfig the configuration
     * @return Conv3d output variable
     */
    public SDVariable conv3d(String name, SDVariable input, SDVariable weights, SDVariable bias, Conv3DConfig conv3DConfig) {
        SDVariable[] args;
        if (bias == null) {
            args = new SDVariable[]{input, weights};
        } else {
            args = new SDVariable[]{input, weights, bias};
        }
        SDVariable ret = f().conv3d(args, conv3DConfig);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Batch norm operation.
     * @see #batchNorm(String, SDVariable, SDVariable, SDVariable, SDVariable, SDVariable, double, int...)
     */
    public SDVariable batchNorm(SDVariable input, SDVariable mean,
                                SDVariable variance, SDVariable gamma,
                                SDVariable beta, double epsilon, int... axis) {
        return batchNorm(null, input, mean, variance, gamma, beta, true, true, epsilon, axis);
    }

    /**
     * Neural network batch normalization operation.<br>
     * For details, see <a href="http://arxiv.org/abs/1502.03167">http://arxiv.org/abs/1502.03167</a>
     *
     * @param name       Name of the output variable
     * @param input      Input variable.
     * @param mean       Mean value. For 1d axis, this should match input.size(axis)
     * @param variance   Variance value. For 1d axis, this should match input.size(axis)
     * @param gamma      Gamma value. For 1d axis, this should match input.size(axis)
     * @param beta       Beta value. For 1d axis, this should match input.size(axis)
     * @param epsilon    Epsilon constant for numerical stability (to avoid division by 0)
     * @param axis       For 2d CNN activations: 1 for NCHW format activations, or 3 for NHWC format activations.<br>
     *                   For 3d CNN activations: 1 for NCDHW format, 4 for NDHWC<br>
     *                   For 1d/RNN activations: 1 for NCW format, 2 for NWC
     * @return Output variable for batch normalization
     */
    public SDVariable batchNorm(String name, SDVariable input, SDVariable mean,
                                SDVariable variance, SDVariable gamma,
                                SDVariable beta, double epsilon, int... axis) {
        return batchNorm(name, input, mean, variance, gamma, beta, true, true, epsilon, axis);
    }

    /**
     * Batch normalization with optional application of gamma/beta args.
     * See {@link #batchNorm(String, SDVariable, SDVariable, SDVariable, SDVariable, SDVariable, double, int...)}
     */
    public SDVariable batchNorm(String name, SDVariable input, SDVariable mean,
                                SDVariable variance, SDVariable gamma,
                                SDVariable beta, boolean applyGamma, boolean applyBeta, double epsilon, int... axis) {
        SDVariable res = f().batchNorm(input, mean, variance, gamma, beta, applyGamma, applyBeta, epsilon, axis);
        return updateVariableNameAndReference(res, name);
    }

    /**
     * im2col operation for use in 2D convolution operations. Outputs a 6d array with shape
     * [minibatch, inputChannels, kernelHeight, kernelWidth, outputHeight, outputWidth]
     *
     * @param in     Input - rank 4 input with shape [minibatch, inputChannels, height, width]
     * @param config Convolution configuration for the im2col operation
     * @return Im2Col output variable
     */
    public SDVariable im2Col(SDVariable in, Conv2DConfig config) {
        return im2Col(null, in, config);
    }

    /**
     * im2col operation for use in 2D convolution operations. Outputs a 6d array with shape
     * [minibatch, inputChannels, kernelHeight, kernelWidth, outputHeight, outputWidth]
     *
     * @param name   Name of the output variable
     * @param in     Input - rank 4 input with shape [minibatch, inputChannels, height, width]
     * @param config Convolution configuration for the im2col operation
     * @return Im2Col output variable
     */
    public SDVariable im2Col(String name, SDVariable in, Conv2DConfig config) {
        SDVariable ret = f().im2Col(in, config);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * col2im operation for use in 2D convolution operations. Outputs a 4d array with shape
     * [minibatch, inputChannels, height, width]
     *
     * @param in     Input - rank 6 input with shape [minibatch, inputChannels, kernelHeight, kernelWidth, outputHeight, outputWidth]
     * @param config Convolution configuration for the col2im operation
     * @return Col2Im output variable
     */
    public SDVariable col2Im(SDVariable in, Conv2DConfig config) {
        return col2Im(null, in, config);
    }

    /**
     * col2im operation for use in 2D convolution operations. Outputs a 4d array with shape
     * [minibatch, inputChannels, height, width]
     *
     * @param name   Name of the output variable
     * @param in     Input - rank 6 input with shape [minibatch, inputChannels, kernelHeight, kernelWidth, outputHeight, outputWidth]
     * @param config Convolution configuration for the col2im operation
     * @return Col2Im output variable
     */
    public SDVariable col2Im(String name, SDVariable in, Conv2DConfig config) {
        SDVariable ret = f().col2Im(in, config);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Extract image patches
     *
     * @param name     Name of the output variable
     * @param input    Input array. Must be rank 4, with shape [minibatch, height, width, channels]
     * @param kH       Kernel height
     * @param kW       Kernel width
     * @param sH       Stride height
     * @param sW       Stride width
     * @param rH       Rate height
     * @param rW       Rate width
     * @param sameMode If true: use same mode padding. If false
     * @return
     */
    public SDVariable extractImagePatches(String name, SDVariable input, int kH, int kW, int sH, int sW, int rH, int rW, boolean sameMode){
        SDVariable ret = f().extractImagePatches(input, kH, kW, sH, sW, rH, rW, sameMode);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Create a new scalar (rank 0) SDVariable with the specified value
     * @param name  Name of the SDVariable
     * @param value Value to initialize the variable with
     * @return SDVariable
     */
    public SDVariable scalar(String name, double value) {
        return var(name, Nd4j.trueScalar(value));
    }


    /**
     * Greater than or equals operation: elementwise x >= y<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param x Input array
     * @param y Double value argument to use in operation
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable gte(SDVariable x, double y) {
        return gte(null, x, y);
    }

    /**
     * Greater than or equals operation: elementwise x >= y<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param name Name of the output variable
     * @param x    Input array
     * @param y    Double value argument to use in operation
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable gte(String name, SDVariable x, double y) {
        SDVariable result = functionFactory.gte(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Less than or equals operation: elementwise x <= y<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param x Input array
     * @param y Double value argument to use in operation
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable lte(SDVariable x, double y) {
        return lte(null, x, y);
    }

    /**
     * Less than or equals operation: elementwise x <= y<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param name Name of the output variable
     * @param x    Input array
     * @param y    Double value argument to use in operation
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable lte(String name, SDVariable x, double y) {
        SDVariable result = functionFactory.lte(x, y);
        return updateVariableNameAndReference(result, name);
    }


    /**
     * Greater than operation: elementwise x > y<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param x Input array
     * @param y Double value argument to use in operation
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable gt(SDVariable x, double y) {
        return gt(null, x, y);
    }

    /**
     * Greater than operation: elementwise x > y<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param name Name of the output variable
     * @param x    Input array
     * @param y    Double value argument to use in operation
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable gt(String name, SDVariable x, double y) {
        SDVariable result = functionFactory.gt(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Less than operation: elementwise x < y<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param x Input array
     * @param y Double value argument to use in operation
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable lt(SDVariable x, double y) {
        return lt(null, x, y);
    }

    /**
     * Less than operation: elementwise x < y<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param name Name of the output variable
     * @param x    Input array
     * @param y    Double value argument to use in operation
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable lt(String name, SDVariable x, double y) {
        SDVariable result = functionFactory.lt(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Not equals operation: elementwise x != y<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param x Input array
     * @param y Double value argument to use in operation
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable neq(SDVariable x, double y) {
        return neq(null, x, y);
    }

    /**
     * Not equals operation: elementwise x != y<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param name Name of the output variable
     * @param x    Input array
     * @param y    Double value argument to use in operation
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable neq(String name, SDVariable x, double y) {
        SDVariable result = functionFactory.neq(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Equals operation: elementwise x == y<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param x Input array
     * @param y Double value argument to use in operation
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable eq(SDVariable x, double y) {
        return eq(null, x, y);
    }

    /**
     * Equals operation: elementwise x == y<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param name Name of the output variable
     * @param x    Input array
     * @param y    Double value argument to use in operation
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable eq(String name, SDVariable x, double y) {
        SDVariable result = functionFactory.eq(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Greater than or equal to operation: elementwise x >= y<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param x Input 1
     * @param y Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable gte(SDVariable x, SDVariable y) {
        return gte(null, x, y);
    }

    /**
     * Greater than or equal to operation: elementwise x >= y<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param name Name of the output variable
     * @param x    Input 1
     * @param y    Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable gte(String name, SDVariable x, SDVariable y) {
        SDVariable result = functionFactory.gte(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Less than or equal to operation: elementwise x <= y<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param x Input 1
     * @param y Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable lte(SDVariable x, SDVariable y) {
        return lte(null, x, y);
    }

    /**
     * Less than or equal to operation: elementwise x <= y<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param name Name of the output variable
     * @param x    Input 1
     * @param y    Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable lte(String name, SDVariable x, SDVariable y) {
        SDVariable result = functionFactory.lte(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Greater than operation: elementwise x > y<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param x Input 1
     * @param y Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable gt(SDVariable x, SDVariable y) {
        return gt(null, x, y);
    }

    /**
     * Greater than operation: elementwise x > y<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param name Name of the output variable
     * @param x    Input 1
     * @param y    Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable gt(String name, SDVariable x, SDVariable y) {
        SDVariable result = functionFactory.gt(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Less than operation: elementwise x < y<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param x Input 1
     * @param y Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable lt(SDVariable x, SDVariable y) {
        return lt(null, x, y);
    }

    /**
     * Less than operation: elementwise x < y<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param name Name of the output variable
     * @param x    Input 1
     * @param y    Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable lt(String name, SDVariable x, SDVariable y) {
        SDVariable result = functionFactory.lt(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Not equal to operation: elementwise x != y<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param x Input 1
     * @param y Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable neq(SDVariable x, SDVariable y) {
        return neq(null, x, y);
    }

    /**
     * Not equal to operation: elementwise x != y<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param name Name of the output variable
     * @param x    Input 1
     * @param y    Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable neq(String name, SDVariable x, SDVariable y) {
        SDVariable result = functionFactory.neq(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Equal to operation: elementwise x == y<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param x Input 1
     * @param y Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable eq(SDVariable x, SDVariable y) {
        return eq(null, x, y);
    }

    /**
     * Equal to operation: elementwise x == y<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param name Name of the output variable
     * @param x    Input 1
     * @param y    Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable eq(String name, SDVariable x, SDVariable y) {
        SDVariable result = functionFactory.eq(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Boolean OR operation: elementwise (x != 0) || (y != 0)<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param x Input 1
     * @param y Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable or(SDVariable x, SDVariable y) {
        return or(null, x, y);
    }

    /**
     * Boolean OR operation: elementwise (x != 0) || (y != 0)<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param name Name of the output variable
     * @param x    Input 1
     * @param y    Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable or(String name, SDVariable x, SDVariable y) {
        SDVariable result = functionFactory.or(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Boolean AND operation: elementwise (x != 0) && (y != 0)<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param x Input 1
     * @param y Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable and(SDVariable x, SDVariable y) {
        return and(null, x, y);
    }

    /**
     * Boolean AND operation: elementwise (x != 0) && (y != 0)<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param name Name of the output variable
     * @param x    Input 1
     * @param y    Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable and(String name, SDVariable x, SDVariable y) {
        SDVariable result = f().and(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Boolean XOR (exclusive OR) operation: elementwise (x != 0) XOR (y != 0)<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param x Input 1
     * @param y Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable xor(SDVariable x, SDVariable y) {
        return xor(null, x, y);
    }

    /**
     * Boolean XOR (exclusive OR) operation: elementwise (x != 0) XOR (y != 0)<br>
     * If x and y arrays have equal shape, the output shape is the same as these inputs.<br>
     * Note: supports broadcasting if x and y have different shapes and are broadcastable.<br>
     * Returns an array with values 1 where condition is satisfied, or value 0 otherwise.
     *
     * @param name Name of the output variable
     * @param x    Input 1
     * @param y    Input 2
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable xor(String name, SDVariable x, SDVariable y) {
        SDVariable result = f().xor(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise absolute value operation: out = abs(x)
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable abs(SDVariable x) {
        return abs(null, x);
    }

    /**
     * Elementwise absolute value operation: out = abs(x)
     *
     * @param name Name of the output variable
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable abs(String name, SDVariable x) {
        SDVariable result = f().abs(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise negative operation: out = -x
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable neg(SDVariable x) {
        return neg(null, x);
    }

    /**
     * Elementwise negative operation: out = -x
     *
     * @param name Name of the output variable
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable neg(String name, SDVariable x) {
        SDVariable result = functionFactory.neg(x);
        return updateVariableNameAndReference(result, name);
    }


    /**
     * Elementwise cosine operation: out = cos(x)
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable cos(SDVariable x) {
        return cos(null, x);
    }

    /**
     * Elementwise cosine operation: out = cos(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable cos(String name, SDVariable x) {
        SDVariable result = functionFactory.cos(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise sine operation: out = sin(x)
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable sin(SDVariable x) {
        return sin(null, x);
    }

    /**
     * Elementwise sine operation: out = sin(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable sin(String name, SDVariable x) {
        SDVariable result = functionFactory.sin(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise tangent operation: out = tan(x)
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable tan(SDVariable x) {
        return tan(null, x);
    }

    /**
     * Elementwise tangent operation: out = tan(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable tan(String name, SDVariable x) {
        SDVariable result = functionFactory.tan(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise identity operation: out = x
     *
     * @param input Input variable
     * @return Output variable
     */
    public SDVariable identity(SDVariable input) {
        return identity(null, input);
    }

    /**
     * Elementwise identity operation: out = x
     *
     * @param name  name of the output variable
     * @param input Input variable
     * @return Output variable
     */
    public SDVariable identity(String name, SDVariable input) {
        SDVariable s = f().identity(input);
        return updateVariableNameAndReference(s, name);
    }

    /**
     * Compute the inverse permutation indices for a permutation operation<br>
     * Example: if input is [2, 0, 1] then output is [1, 2, 0]<br>
     * The idea is that x.permute(input).permute(invertPermutation(input)) == x
     *
     * @param input 1D indices for permutation
     * @return 1D inverted permutation
     */
    public SDVariable invertPermutation(SDVariable input) {
        return invertPermutation(null, input);
    }

    /**
     * Compute the inverse permutation indices for a permutation operation<br>
     * Example: if input is [2, 0, 1] then output is [1, 2, 0]<br>
     * The idea is that x.permute(input).permute(invertPermutation(input)) == x
     *
     * @param name  name of the output variable
     * @param input 1D indices for permutation
     * @return 1D inverted permutation
     */
    public SDVariable invertPermutation(String name, SDVariable input) {
        SDVariable ret = f().invertPermutation(input, false);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Elementwise acos (arccosine, inverse cosine) operation: out = arccos(x)
     *
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable acos(SDVariable x) {
        return acos(null, x);
    }

    /**
     * Elementwise acos (arccosine, inverse cosine) operation: out = arccos(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable acos(String name, SDVariable x) {
        SDVariable result = functionFactory.acos(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise asin (arcsin, inverse sine) operation: out = arcsin(x)
     *
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable asin(SDVariable x) {
        return asin(null, x);
    }

    /**
     * Elementwise asin (arcsin, inverse sine) operation: out = arcsin(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable asin(String name, SDVariable x) {
        SDVariable result = functionFactory.asin(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise atan (arctangent, inverse tangent) operation: out = arctangent(x)
     *
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable atan(SDVariable x) {
        return atan(null, x);
    }

    /**
     * Elementwise atan (arctangent, inverse tangent) operation: out = arctangent(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable atan(String name, SDVariable x) {
        SDVariable result = functionFactory.atan(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise atan (arctangent, inverse tangent) operation: out = atan2(x,y).
     * Similar to atan(y/x) but sigts of x and y are used to determine the location of the result
     *
     * @param y Input Y variable
     * @param x Input X variable
     * @return Output variable
     */
    public SDVariable atan2(SDVariable y, SDVariable x) {
        return atan2(null, y, x);
    }

    /**
     * Elementwise atan (arctangent, inverse tangent) operation: out = atan2(x,y).
     * Similar to atan(y/x) but sigts of x and y are used to determine the location of the result
     *
     * @param name Name of the output variable
     * @param y    Input Y variable
     * @param x    Input X variable
     * @return Output variable
     */
    public SDVariable atan2(String name, SDVariable y, SDVariable x) {
        SDVariable ret = f().atan2(y, x);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Elementwise cosh (hyperbolic cosine) operation: out = cosh(x)
     *
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable cosh(SDVariable x) {
        return cosh(null, x);
    }

    /**
     * Elementwise cosh (hyperbolic cosine) operation: out = cosh(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable cosh(String name, SDVariable x) {
        SDVariable result = functionFactory.cosh(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise sinh (hyperbolic sine) operation: out = sinh(x)
     *
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable sinh(SDVariable x) {
        return sinh(null, x);
    }

    /**
     * Elementwise sinh (hyperbolic sine) operation: out = sinh(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable sinh(String name, SDVariable x) {
        SDVariable result = functionFactory.sinh(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise tanh (hyperbolic tangent) operation: out = tanh(x)
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable tanh(SDVariable x) {
        return tanh(null, x);
    }

    /**
     * Elementwise tanh (hyperbolic tangent) operation: out = tanh(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable tanh(String name, SDVariable x) {
        SDVariable result = functionFactory.tanh(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise step function:<br>
     * out(x) = 1 if x >= cutoff<br>
     * out(x) = 0 otherwise<br>
     *
     * @param in     Input variable
     * @param cutoff Cutoff value for step function
     * @return Output variable
     */
    public SDVariable step(SDVariable in, double cutoff) {
        return step(null, in, cutoff);
    }

    /**
     * Elementwise step function:<br>
     * out(x) = 1 if x >= cutoff<br>
     * out(x) = 0 otherwise<br>
     *
     * @param name   Name of the output variable
     * @param in     Input variable
     * @param cutoff Cutoff value for step function
     * @return Output variable
     */
    public SDVariable step(String name, SDVariable in, double cutoff) {
        SDVariable ret = f().step(in, cutoff);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Elementwise acosh (inverse hyperbolic cosine) function: out = acosh(x)
     *
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable acosh(SDVariable x) {
        return acosh(null, x);
    }

    /**
     * Elementwise acosh (inverse hyperbolic cosine) function: out = acosh(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable acosh(String name, SDVariable x) {
        SDVariable result = functionFactory.acosh(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise asinh (inverse hyperbolic sine) function: out = asinh(x)
     *
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable asinh(SDVariable x) {
        return asinh(null, x);
    }

    /**
     * Elementwise asinh (inverse hyperbolic sine) function: out = asinh(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable asinh(String name, SDVariable x) {
        SDVariable result = functionFactory.asinh(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise atanh (inverse hyperbolic tangent) function: out = atanh(x)
     *
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable atanh(SDVariable x) {
        return atanh(null, x);
    }

    /**
     * Elementwise atanh (inverse hyperbolic tangent) function: out = atanh(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable atanh(String name, SDVariable x) {
        SDVariable result = functionFactory.atanh(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise exponent function: out = exp(x) = 2.71828...^x
     *
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable exp(SDVariable x) {
        return exp(null, x);
    }

    /**
     * Elementwise exponent function: out = exp(x) = 2.71828...^x
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable exp(String name, SDVariable x) {
        SDVariable result = functionFactory.exp(x);
        return updateVariableNameAndReference(result, name);
    }


    /**
     * Element-wise reciprocal (inverse) of square root: out = 1.0 / sqrt(x)
     *
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable rsqrt(SDVariable x) {
        return rsqrt(null, x);
    }

    /**
     * Element-wise reciprocal (inverse) of square root: out = 1.0 / sqrt(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable rsqrt(String name, SDVariable x) {
        SDVariable result = functionFactory.rsqrt(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise 1.0 - exponent function: out = 1.0 - exp(x) = 1.0 - 2.71828...^x
     *
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable expm1(SDVariable x) {
        return expm1(null, x);
    }

    /**
     * Elementwise 1.0 - exponent function: out = 1.0 - exp(x) = 1.0 - 2.71828...^x
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable expm1(String name, SDVariable x) {
        SDVariable result = functionFactory.expm1(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise natural logarithm function: out = log_e (1 + x)
     *
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable log1p(SDVariable x) {
        return log1p(null, x);
    }

    /**
     * Elementwise natural logarithm function: out = log_e (1 + x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable log1p(String name, SDVariable x) {
        SDVariable result = functionFactory.log1p(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Elementwise round function: out = round(x).
     * Rounds (up or down depending on value) to the nearest integer value.
     *
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable round(SDVariable x) {
        return round(null, x);
    }

    /**
     * Element-wise round function: out = round(x).
     * Rounds (up or down depending on value) to the nearest integer value.
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable round(String name, SDVariable x) {
        SDVariable result = functionFactory.round(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Is infinite operation: elementwise isInfinite(x)<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param x Input array
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable isInfinite(SDVariable x) {
        return isInfinite(null, x);
    }

    /**
     * Is infinite operation: elementwise isInfinite(x)<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param name Output variable name
     * @param x   Input array
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable isInfinite(String name, SDVariable x) {
        SDVariable result = functionFactory.isInfinite(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Is Not a Number operation: elementwise isNaN(x)<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param x Input array
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable isNaN(SDVariable x) {
        return isNaN(null, x);
    }

    /**
     * Is Not a Number operation: elementwise isNaN(x)<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param name Output variable name
     * @param x   Input array
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable isNaN(String name, SDVariable x) {
        SDVariable result = functionFactory.isNaN(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Is finite operation: elementwise isFinite(x)<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param x Input array
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable isFinite(SDVariable x) {
        return isFinite(null, x);
    }

    /**
     * Is finite operation: elementwise isFinite(x)<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param name Output variable name
     * @param x   Input array
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable isFinite(String name, SDVariable x) {
        SDVariable result = functionFactory.isFinite(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Is maximum operation: elementwise x == max(x)<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param x Input array
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable isMax(SDVariable x) {
        return isMax(null, x);
    }

    /**
     * Is maximum operation: elementwise x == max(x)<br>
     * Returns an array with the same shape/size as the input, with values 1 where condition is satisfied, or
     * value 0 otherwise
     *
     * @param name Name of the output variable
     * @param x   Input array
     * @return Output SDVariable with values 0 and 1 based on where the condition is satisfied
     */
    public SDVariable isMax(String name, SDVariable x) {
        SDVariable ret = f().isMax(x);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Is the array non decreasing?<br>
     * An array is non-decreasing if for every valid i, x[i] <= x[i+1]. For Rank 2+ arrays, values are compared
     * in 'c' (row major) order
     *
     * @param x Input variable
     * @return Scalar variable with value 1 if non-decreasing, or 0 otherwise
     */
    public SDVariable isNonDecreasing(SDVariable x) {
        return isNonDecreasing(null, x);
    }

    /**
     * Is the array non decreasing?<br>
     * An array is non-decreasing if for every valid i, x[i] <= x[i+1]. For Rank 2+ arrays, values are compared
     * in 'c' (row major) order
     *
     * @param name Output name
     * @param x   Input variable
     * @return Scalar variable with value 1 if non-decreasing, or 0 otherwise
     */
    public SDVariable isNonDecreasing(String name, SDVariable x) {
        SDVariable result = functionFactory.isNonDecreasing(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Is the array strictly increasing?<br>
     * An array is strictly increasing if for every valid i, x[i] < x[i+1]. For Rank 2+ arrays, values are compared
     * in 'c' (row major) order
     *
     * @param x Input variable
     * @return Scalar variable with value 1 if strictly increasing, or 0 otherwise
     */
    public SDVariable isStrictlyIncreasing(SDVariable x) {
        return isStrictlyIncreasing(null, x);

    }

    /**
     * Is the array strictly increasing?<br>
     * An array is strictly increasing if for every valid i, x[i] < x[i+1]. For Rank 2+ arrays, values are compared
     * in 'c' (row major) order
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Scalar variable with value 1 if strictly increasing, or 0 otherwise
     */
    public SDVariable isStrictlyIncreasing(String name, SDVariable x) {
        SDVariable result = functionFactory.isStrictlyIncreasing(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Is the director a numeric tensor? In the current version of ND4J/SameDiff, this always returns true/1
     *
     * @param x Input variable
     * @return Scalar variable with value 1
     */
    public SDVariable isNumericTensor(SDVariable x) {
        return isNumericTensor(null, x);
    }

    /**
     * Is the director a numeric tensor? In the current version of ND4J/SameDiff, this always returns true/1
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Scalar variable with value 1
     */
    public SDVariable isNumericTensor(String name, SDVariable x) {
        SDVariable result = functionFactory.isNumericTensor(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise replace where condition:<br>
     * out[i] = from[i] if condition(update[i]) is satisfied, or<br>
     * out[i] = update[i] if condition(update[i]) is NOT satisfied
     *
     * @param update    Source array
     * @param from      Replacement values array (used conditionally). Must be same shape as 'update' array
     * @param condition Condition to check on update array elements
     * @return New array with values replaced where condition is satisfied
     */
    public SDVariable replaceWhere(SDVariable update, SDVariable from, Condition condition) {
        return replaceWhere(null, update, from, condition);
    }

    /**
     * Element-wise replace where condition:<br>
     * out[i] = from[i] if condition(update[i]) is satisfied, or<br>
     * out[i] = update[i] if condition(update[i]) is NOT satisfied
     *
     * @param name      Name of the output variable
     * @param update    Source array
     * @param from      Replacement values array (used conditionally). Must be same shape as 'update' array
     * @param condition Condition to check on update array elements
     * @return New array with values replaced where condition is satisfied
     */
    public SDVariable replaceWhere(String name, SDVariable update, SDVariable from, Condition condition) {
        SDVariable ret = f().replaceWhere(update, from, condition);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Element-wise replace where condition:<br>
     * out[i] = value if condition(update[i]) is satisfied, or<br>
     * out[i] = update[i] if condition(update[i]) is NOT satisfied
     *
     * @param update    Source array
     * @param value     Value to set at the output, if the condition is satisfied
     * @param condition Condition to check on update array elements
     * @return New array with values replaced where condition is satisfied
     */
    public SDVariable replaceWhere(SDVariable update, Number value, Condition condition) {
        return replaceWhere(null, update, value, condition);
    }

    /**
     * Element-wise replace where condition:<br>
     * out[i] = value if condition(update[i]) is satisfied, or<br>
     * out[i] = update[i] if condition(update[i]) is NOT satisfied
     *
     * @param name      Name of the output variable
     * @param update    Source array
     * @param value     Value to set at the output, if the condition is satisfied
     * @param condition Condition to check on update array elements
     * @return New array with values replaced where condition is satisfied
     */
    public SDVariable replaceWhere(String name, SDVariable update, Number value, Condition condition) {
        SDVariable ret = f().replaceWhere(update, value, condition);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Element-wise logarithm function (base e - natural logarithm): out = log(x)
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable log(SDVariable x) {
        return log(null, x);
    }

    /**
     * Element-wise logarithm function (base e - natural logarithm): out = log(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable log(String name, SDVariable x) {
        SDVariable result = functionFactory.log(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise logarithm function (with specified base): out = log_{base}(x)
     *
     * @param in   Input variable
     * @param base Logarithm base
     * @return Output variable
     */
    public SDVariable log(SDVariable in, double base) {
        return log(null, in, base);
    }

    /**
     * Element-wise logarithm function (with specified base): out = log_{base}(x)
     *
     * @param name Name of the output variable
     * @param in   Input variable
     * @param base Logarithm base
     * @return Output variable
     */
    public SDVariable log(String name, SDVariable in, double base) {
        SDVariable ret = f().log(in, base);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Log-sum-exp reduction (optionally along dimension).
     * Computes log(sum(exp(x))
     *
     * @param input      Input variable
     * @param dimensions Optional dimensions to reduce along
     * @return Output variable
     */
    public SDVariable logSumExp(SDVariable input, int... dimensions) {
        return logSumExp(null, input, dimensions);
    }

    /**
     * Log-sum-exp reduction (optionally along dimension).
     * Computes log(sum(exp(x))
     *
     * @param name       Name of the output variable
     * @param input      Input variable
     * @param dimensions Optional dimensions to reduce along
     * @return Output variable
     */
    public SDVariable logSumExp(String name, SDVariable input, int... dimensions) {
        SDVariable ret = f().logSumExp(input, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Element-wise cube function: out = x^3
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable cube(SDVariable x) {
        return cube(null, x);
    }

    /**
     * Element-wise cube function: out = x^3
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable cube(String name, SDVariable x) {
        SDVariable result = functionFactory.cube(x);
        return updateVariableNameAndReference(result, name);
    }


    /**
     * Element-wise power function: out = x^value
     *
     * @param x    Input variable
     * @param value Power to raise each element to
     * @return Output variable
     */
    public SDVariable pow(SDVariable x, double value) {
        return pow(null, x, value);
    }

    /**
     * Element-wise power function: out = x^value
     *
     * @param name  Output variable name
     * @param x    Input variable
     * @param value Power to raise each element to
     * @return Output variable
     */
    public SDVariable pow(String name, SDVariable x, double value) {
        SDVariable result = functionFactory.pow(x, value);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise (broadcastable) power function: out = x[i]^y[i]
     *
     * @param x    Input variable
     * @param y    Power
     * @return Output variable
     */
    public SDVariable pow(SDVariable x, SDVariable y) {
        return pow(null, x, y);
    }

    /**
     * Element-wise (broadcastable) power function: out = x[i]^y[i]
     *
     * @param name  Output variable name
     * @param x    Input variable
     * @param y    Power
     * @return Output variable
     */
    public SDVariable pow(String name, SDVariable x, SDVariable y) {
        SDVariable result = functionFactory.pow(x, y);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise square root function: out = sqrt(x)
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable sqrt(SDVariable x) {
        return sqrt(null, x);
    }

    /**
     * Element-wise square root function: out = sqrt(x)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable sqrt(String name, SDVariable x) {
        SDVariable result = functionFactory.sqrt(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise square function: out = x^2
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable square(SDVariable x) {
        return square(null, x);
    }

    /**
     * Element-wise square function: out = x^2
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable square(String name, SDVariable x) {
        SDVariable result = functionFactory.square(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise floor function: out = floor(x).
     * Rounds each value down to the nearest integer value (if not already an integer)
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable floor(SDVariable x) {
        return floor(null, x);
    }

    /**
     * Element-wise floor function: out = floor(x).
     * Rounds each value down to the nearest integer value (if not already an integer)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable floor(String name, SDVariable x) {
        SDVariable result = functionFactory.floor(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise ceiling function: out = ceil(x).
     * Rounds each value up to the nearest integer value (if not already an integer)
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable ceil(SDVariable x) {
        return ceil(null, x);
    }

    /**
     * Element-wise ceiling function: out = ceil(x).
     * Rounds each value up to the nearest integer value (if not already an integer)
     *
     * @param name Name of the output variable
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable ceil(String name, SDVariable x) {
        SDVariable ret = f().ceil(x);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Element-wise clipping function:<br>
     * out[i] = in[i] if in[i] >= clipValueMin and in[i] <= clipValueMax<br>
     * out[i] = clipValueMin if in[i] < clipValueMin<br>
     * out[i] = clipValueMax if in[i] > clipValueMax<br>
     * @param x            Input variable
     * @param clipValueMin Minimum value for clipping
     * @param clipValueMax Maximum value for clipping
     * @return Output variable
     */
    public SDVariable clipByValue(SDVariable x, double clipValueMin, double clipValueMax) {
        return clipByValue(null, x, clipValueMin, clipValueMax);
    }

    /**
     * Element-wise clipping function:<br>
     * out[i] = in[i] if in[i] >= clipValueMin and in[i] <= clipValueMax<br>
     * out[i] = clipValueMin if in[i] < clipValueMin<br>
     * out[i] = clipValueMax if in[i] > clipValueMax<br>
     *
     * @param name         Name of the output variable
     * @param x            Input variable
     * @param clipValueMin Minimum value for clipping
     * @param clipValueMax Maximum value for clipping
     * @return Output variable
     */
    public SDVariable clipByValue(String name, SDVariable x, double clipValueMin, double clipValueMax) {
        SDVariable ret = f().clipByValue(x, clipValueMin, clipValueMax);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Clipping by L2 norm<br>
     * if l2Norm(x) < clipValue, then input is returned unmodifed<br>
     * Otherwise, out[i] = in[i] * clipValue / l2Norm(in)
     *
     * @param x         Input variable
     * @param clipValue Clipping value (maximum l2 norm)
     * @return Output variable
     */
    public SDVariable clipByNorm(SDVariable x, double clipValue) {
        return clipByNorm(null, x, clipValue);
    }

    /**
     * Clipping by L2 norm<br>
     * if l2Norm(x) < clipValue, then input is returned unmodifed<br>
     * Otherwise, out[i] = in[i] * clipValue / l2Norm(in)
     *
     * @param name      Name of the output variable
     * @param x         Input variable
     * @param clipValue Clipping value (maximum l2 norm)
     * @return Output variable
     */
    public SDVariable clipByNorm(String name, SDVariable x, double clipValue) {
        SDVariable ret = f().clipByNorm(x, clipValue);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Clipping by L2 norm, optionally along dimension(s)<br>
     * if l2Norm(x,dimension) < clipValue, then input is returned unmodifed<br>
     * Otherwise, out[i] = in[i] * clipValue / l2Norm(in, dimensions) where each value is clipped according
     * to the corresponding l2Norm along the specified dimensions
     *
     * @param x          Input variable
     * @param clipValue  Clipping value (maximum l2 norm)
     * @param dimensions If not specified, all dimensions are used
     * @return Output variable
     */
    public SDVariable clipByNorm(SDVariable x, double clipValue, int... dimensions) {
        return clipByNorm(null, x, clipValue, dimensions);
    }

    /**
     * Clipping by L2 norm, optionally along dimension(s)<br>
     * if l2Norm(x,dimension) < clipValue, then input is returned unmodifed<br>
     * Otherwise, out[i] = in[i] * clipValue / l2Norm(in, dimensions) where each value is clipped according
     * to the corresponding l2Norm along the specified dimensions
     *
     * @param name       Output variable name
     * @param x          Input variable
     * @param clipValue  Clipping value (maximum l2 norm)
     * @param dimensions If not specified, all dimensions are used
     * @return Output variable
     */
    public SDVariable clipByNorm(String name, SDVariable x, double clipValue, int... dimensions) {
        SDVariable ret = f().clipByNorm(x, clipValue, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Element-wise rectified linear function with specified cutoff:<br>
     * out[i] = in[i] if in[i] >= cutoff
     * out[i] = 0 otherwise
     *
     * @param x     Input variable
     * @param cutoff Cutoff value. Usually 0
     * @return Output variable
     */
    public SDVariable relu(SDVariable x, double cutoff) {
        return relu(null, x, cutoff);
    }

    /**
     * Element-wise rectified linear function with specified cutoff:<br>
     * out[i] = in[i] if in[i] >= cutoff
     * out[i] = 0 otherwise
     *
     * @param name   Output variable name
     * @param x     Input variable
     * @param cutoff Cutoff value. Usually 0
     * @return Output variable
     */
    public SDVariable relu(String name, SDVariable x, double cutoff) {
        SDVariable result = functionFactory.relu(x, cutoff);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise "rectified linear 6" function with specified cutoff:<br>
     * out[i] = min(max(in, cutoff), 6)
     *
     * @param x     Input variable
     * @param cutoff Cutoff value. Usually 0
     * @return Output variable
     */
    public SDVariable relu6(SDVariable x, double cutoff) {
        return relu6(null, x, cutoff);
    }

    /**
     * Element-wise "rectified linear 6" function with specified cutoff:<br>
     * out[i] = min(max(in, cutoff), 6)
     *
     * @param name   Output variable name
     * @param x     Input variable
     * @param cutoff Cutoff value. Usually 0
     * @return Output variable
     */
    public SDVariable relu6(String name, SDVariable x, double cutoff) {
        SDVariable result = functionFactory.relu6(x, cutoff);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Softmax activation
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable softmax(SDVariable x) {
        return softmax(null, x);
    }

    /**
     * Softmax activation
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable softmax(String name, SDVariable x) {
        SDVariable result = functionFactory.softmax(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Log softmax activation
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable logSoftmax(SDVariable x) {
        return logSoftmax(null, x);
    }


    /**
     * Log softmax activation
     *
     * @param name Variable name
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable logSoftmax(String name, SDVariable x) {
        SDVariable ret = f().logSoftmax(x);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Element-wise SeLU function - Scaled exponential Lineal Unit: see <a href="https://arxiv.org/abs/1706.02515">Self-Normalizing Neural Networks</a>
     * <br>
     * out[i] = scale * alpha * (exp(in[i])-1) if in[i]>0, or 0 if in[i] <= 0<br>
     * Uses default lcale and alpha values.
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable selu(SDVariable x) {
        return selu(null, x);
    }

    /**
     * Element-wise SeLU function - Scaled exponential Lineal Unit: see <a href="https://arxiv.org/abs/1706.02515">Self-Normalizing Neural Networks</a>
     * <br>
     * out[i] = scale * alpha * (exp(in[i])-1) if in[i]>0, or 0 if in[i] <= 0<br>
     * Uses default lcale and alpha values.
     *
     * @param name Name of the output variable
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable selu(String name, SDVariable x) {
        SDVariable ret = f().selu(x);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Merge add function: merges an arbitrary number of equal shaped arrays using elementwise addition:
     * out = sum_i in[i]
     *
     * @param x Input variables
     * @return Output variable
     */
    public SDVariable mergeAdd(SDVariable... x) {
        return mergeAdd(null, x);
    }

    /**
     * Merge add function: merges an arbitrary number of equal shaped arrays using element-wise addition:
     * out = sum_i in[i]
     *
     * @param name   Name of the output variable
     * @param inputs Input variables
     * @return Output variable
     */
    public SDVariable mergeAdd(String name, SDVariable... inputs) {
        SDVariable ret = f().mergeAdd(inputs);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Merge max function: merges an arbitrary number of equal shaped arrays using element-wise maximum operation:
     * out = max_i in[i]
     *
     * @param x Input variables
     * @return Output variable
     */
    public SDVariable mergeMax(SDVariable... x) {
        return mergeMax(null, x);
    }

    /**
     * Merge max function: merges an arbitrary number of equal shaped arrays using element-wise maximum operation:
     * out = max_i in[i]
     *
     * @param inputs Input variables
     * @return Output variable
     */
    public SDVariable mergeMax(String name, SDVariable... inputs) {
        SDVariable ret = f().mergeMax(inputs);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Merge average function: merges an arbitrary number of equal shaped arrays using element-wise mean operation:
     * out = mean_i in[i]
     *
     * @param inputs Input variables
     * @return Output variable
     */
    public SDVariable mergeAvg(SDVariable... inputs) {
        return mergeAvg(null, inputs);
    }

    /**
     * Merge average function: merges an arbitrary number of equal shaped arrays using element-wise mean operation:
     * out = mean_i in[i]
     *
     * @param name   Name of the output variable
     * @param inputs Input variables
     * @return Output variable
     */
    public SDVariable mergeAvg(String name, SDVariable... inputs) {
        SDVariable ret = f().mergeAvg(inputs);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #batchToSpace(String, SDVariable, int[], int[][])
     */
    public SDVariable batchToSpace(SDVariable x, int[] blocks, int[][] crops) {
        return batchToSpace(null, x, blocks, crops);
    }

    /**
     * Convolution 2d layer batch to space operation on 4d input.
     * Reduces input batch dimension by rearranging data into a larger spatial dimensions
     *
     * @param name   Output variable name
     * @param x     Input variable. 4d input
     * @param blocks Block size, in the height/width dimension
     * @param crops  Optional 2d int[] array: values [[crop top, crop bottom], [crop left, crop right]]
     * @return Output variable
     * @see #spaceToBatch(String, SDVariable, int[], int[][])
     */
    public SDVariable batchToSpace(String name, SDVariable x, int[] blocks, int[][] crops) {
        SDVariable ret = f().batchToSpace(x, blocks, crops);
        return updateVariableNameAndReference(ret, name);
    }


    /**
     * Convolution 2d layer batch to space operation on 4d input.<br>
     * Reduces input channels dimension by rearranging data into a larger spatial dimensions<br>
     * Example: if input has shape [mb, 8, 2, 2] and block size is 2, then output size is [mb, 8/(2*2), 2*2, 2*2]
     * = [mb, 2, 4, 4]
     *
     * @param x         the input to depth to space pooling 2d operation - 4d activations in NCHW format
     *                   (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param blockSize  Block size, in the height/width dimension
     * @param dataFormat Data format: "NCHW" or "NHWC"
     * @return Output variable
     */
    public SDVariable depthToSpace(SDVariable x, int blockSize, String dataFormat) {
        return depthToSpace(null, x, blockSize, dataFormat);
    }

    /**
     * Convolution 2d layer batch to space operation on 4d input.<br>
     * Reduces input channels dimension by rearranging data into a larger spatial dimensions<br>
     * Example: if input has shape [mb, 8, 2, 2] and block size is 2, then output size is [mb, 8/(2*2), 2*2, 2*2]
     * = [mb, 2, 4, 4]
     *
     * @param name       Output variable name
     * @param x         the input to depth to space pooling 2d operation - 4d activations in NCHW format
     *                   (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param blockSize  Block size, in the height/width dimension
     * @param dataFormat Data format: "NCHW" or "NHWC"
     * @return Output variable
     * @see #depthToSpace(String, SDVariable, int, String)
     */
    public SDVariable depthToSpace(String name, SDVariable x, int blockSize, String dataFormat) {
        SDVariable ret = f().depthToSpace(x, blockSize, dataFormat);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #spaceToBatch(String, SDVariable, int[], int[][])
     */
    public SDVariable spaceToBatch(SDVariable x, int[] blocks, int[][] padding) {
        return spaceToBatch(null, x, blocks, padding);
    }

    /**
     * Convolution 2d layer space to batch operation on 4d input.
     * Increases input batch dimension by rearranging data from spatial dimensions into batch dimension
     *
     * @param name   Output variable name
     * @param x     Input variable. 4d input
     * @param blocks Block size, in the height/width dimension
     * @param padding Optional 2d int[] array for padding the result: values [[pad top, pad bottom], [pad left, pad right]]
     * @return Output variable
     * @see #batchToSpace(String, SDVariable, int[], int[][])
     */
    public SDVariable spaceToBatch(String name, SDVariable x, int[] blocks, int[][] padding) {
        SDVariable ret = f().spaceToBatch(x, blocks, padding);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #spaceToDepth(String, SDVariable, int, String)
     */
    public SDVariable spaceToDepth(SDVariable x, int blockSize, String dataFormat) {
        return spaceToDepth(null, x, blockSize, dataFormat);
    }

    /**
     * Convolution 2d layer space to depth operation on 4d input.<br>
     * Increases input channels (reduced spatial dimensions) by rearranging data into a larger channels dimension<br>
     * Example: if input has shape [mb, 2, 4, 4] and block size is 2, then output size is [mb, 8/(2*2), 2*2, 2*2]
     * = [mb, 2, 4, 4]
     *
     * @param name       Output variable name
     * @param x         the input to depth to space pooling 2d operation - 4d activations in NCHW format
     *                   (shape [minibatch, channels, height, width]) or NHWC format (shape [minibatch, height, width, channels])
     * @param blockSize  Block size, in the height/width dimension
     * @param dataFormat Data format: "NCHW" or "NHWC"
     * @return Output variable
     * @see #depthToSpace(String, SDVariable, int, String)
     */
    public SDVariable spaceToDepth(String name, SDVariable x, int blockSize, String dataFormat) {
        SDVariable ret = f().spaceToDepth(x, blockSize, dataFormat);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #dynamicPartition(String[], SDVariable, SDVariable, int)
     */
    public SDVariable[] dynamicPartition(SDVariable x, SDVariable partitions, int numPartitions) {
        return dynamicPartition(null, x, partitions, numPartitions);
    }

    /**
     * Dynamically partition the input variable values into the specified number of paritions, using the indices.<br>
     * Example:<br>
     * <pre>
     * {@code input = [1,2,3,4,5]
     * numPartitions = 2
     * partitions = [1,0,0,1,0]
     * out[0] = [2,3,5]
     * out[1] = [1,4] }
     * </pre>
     *
     * @param name          Names for the output variables. Length must be equal to numPartitions
     * @param x            Input variable
     * @param partitions    1D input with values 0 to numPartitions-1
     * @param numPartitions Number of partitions, >= 1
     * @return Output variables (equal in number to numPartitions)
     */
    public SDVariable[] dynamicPartition(String[] name, SDVariable x, SDVariable partitions, int numPartitions) {
        SDVariable[] ret = f().dynamicPartition(x, partitions, numPartitions);
        return updateVariableNamesAndReferences(ret, name);
    }

    /**
     * @see #dynamicStitch(String, SDVariable[], SDVariable[])
     */
    public SDVariable dynamicStitch(SDVariable[] indices, SDVariable[] x) {
        return dynamicStitch(null, indices, x);
    }

    /**
     * Dynamically merge the specified input arrays into a single array, using the specified indices
     *
     * @param name    Name of the output variable
     * @param indices Indices to use when merging. Must be >= 1, same length as input variables
     * @param x      Input variables.
     * @return Merged output variable
     */
    public SDVariable dynamicStitch(String name, SDVariable[] indices, SDVariable[] x) {
        SDVariable ret = f().dynamicStitch(indices, x);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #segmentMax(String, SDVariable, SDVariable)
     */
    public SDVariable segmentMax(SDVariable data, SDVariable segmentIds){
        return segmentMax(null, data, segmentIds);
    }

    /**
     * Segment max operation.<br>
     * If data =     [3, 6, 1, 4, 9, 2, 8]<br>
     * segmentIds =  [0, 0, 1, 1, 1, 2, 2]<br>
     * then output = [6, 9, 8] = [max(3,6), max(1,4,9), max(2,8)]<br>
     * Note that the segment IDs must be sorted from smallest to largest segment.
     * See {@link #unsortedSegmentMax(String, SDVariable, SDVariable, int)}
     * for the same op without this sorted requirement
     *
     * @param name       Name of the output variable. May be null
     * @param data       Data to perform segment max on
     * @param segmentIds Variable for the segment IDs
     * @return Segment max output
     */
    public SDVariable segmentMax(String name, SDVariable data, SDVariable segmentIds){
        SDVariable ret = f().segmentMax(data, segmentIds);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * See {@link #unsortedSegmentMax(String, SDVariable, SDVariable, int)}
     */
    public SDVariable unsortedSegmentMax(SDVariable data, SDVariable segmentIds, int numSegments){
        return unsortedSegmentMax(null, data, segmentIds, numSegments);
    }

    /**
     * Unsorted segment max operation. As per {@link #segmentMax(String, SDVariable, SDVariable)} but without
     * the requirement for the indices to be sorted.<br>
     * If data =     [1, 3, 2, 6, 4, 9, 8]<br>
     * segmentIds =  [1, 0, 2, 0, 1, 1, 2]<br>
     * then output = [6, 9, 8] = [max(3,6), max(1,4,9), max(2,8)]<br>
     *
     * @param name        Name of the output variable
     * @param data        Data (variable) to perform unsorted segment max on
     * @param segmentIds  Variable for the segment IDs
     * @param numSegments Number of segments
     * @return Unsorted segment max output
     */
    public SDVariable unsortedSegmentMax(String name, SDVariable data, SDVariable segmentIds, int numSegments){
        SDVariable ret = f().unsortedSegmentMax(data, segmentIds, numSegments);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #segmentMin(String, SDVariable, SDVariable)
     */
    public SDVariable segmentMin(SDVariable data, SDVariable segmentIds){
        return segmentMin(null, data, segmentIds);
    }

    /**
     * Segment min operation.<br>
     * If data =     [3, 6, 1, 4, 9, 2, 8]<br>
     * segmentIds =  [0, 0, 1, 1, 1, 2, 2]<br>
     * then output = [3, 1, 2] = [min(3,6), min(1,4,9), min(2,8)]<br>
     * Note that the segment IDs must be sorted from smallest to largest segment.
     * See {@link #unsortedSegmentMin(String, SDVariable, SDVariable, int)} for the same op without this sorted requirement
     *
     * @param name       Name of the output variable. May be null
     * @param data       Data to perform segment max on
     * @param segmentIds Variable for the segment IDs
     * @return Segment min output
     */
    public SDVariable segmentMin(String name, SDVariable data, SDVariable segmentIds){
        SDVariable ret = f().segmentMin(data, segmentIds);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * See {@link #unsortedSegmentMin(String, SDVariable, SDVariable, int)}
     */
    public SDVariable unsortedSegmentMin(SDVariable data, SDVariable segmentIds, int numSegments){
        return unsortedSegmentMin(null, data, segmentIds, numSegments);
    }

    /**
     * Unsorted segment min operation. As per {@link #segmentMin(String, SDVariable, SDVariable)} but without
     * the requirement for the indices to be sorted.<br>
     * If data =     [1, 3, 2, 6, 4, 9, 8]<br>
     * segmentIds =  [1, 0, 2, 0, 1, 1, 2]<br>
     * then output = [3, 1, 2] = [min(3,6), min(1,4,9), min(2,8)]<br>
     *
     * @param name        Name of the output variable
     * @param data        Data (variable) to perform unsorted segment min on
     * @param segmentIds  Variable for the segment IDs
     * @param numSegments Number of segments
     * @return Unsorted segment min output
     */
    public SDVariable unsortedSegmentMin(String name, SDVariable data, SDVariable segmentIds, int numSegments){
        SDVariable ret = f().unsortedSegmentMin(data, segmentIds, numSegments);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #segmentMean(String, SDVariable, SDVariable)
     */
    public SDVariable segmentMean(SDVariable data, SDVariable segmentIds){
        return segmentMean(null, data, segmentIds);
    }

    /**
     * Segment mean operation.<br>
     * If data =     [3, 6, 1, 4, 9, 2, 8]<br>
     * segmentIds =  [0, 0, 1, 1, 1, 2, 2]<br>
     * then output = [4.5, 4.666, 5] = [mean(3,6), mean(1,4,9), mean(2,8)]<br>
     * Note that the segment IDs must be sorted from smallest to largest segment.
     * See {@link #unsortedSegmentMean(String, SDVariable, SDVariable, int)} for the same op without this sorted requirement
     *
     * @param name       Name of the output variable. May be null
     * @param data       Data to perform segment max on
     * @param segmentIds Variable for the segment IDs
     * @return Segment mean output
     */
    public SDVariable segmentMean(String name, SDVariable data, SDVariable segmentIds){
        SDVariable ret = f().segmentMean(data, segmentIds);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * See {@link #unsortedSegmentMean(String, SDVariable, SDVariable, int)}
     */
    public SDVariable unsortedSegmentMean(SDVariable data, SDVariable segmentIds, int numSegments){
        return unsortedSegmentMean(null, data, segmentIds, numSegments);
    }

    /**
     * Unsorted segment mean operation. As per {@link #segmentMean(String, SDVariable, SDVariable)} but without
     * the requirement for the indices to be sorted.<br>
     * If data =     [1, 3, 2, 6, 4, 9, 8]<br>
     * segmentIds =  [1, 0, 2, 0, 1, 1, 2]<br>
     * then output = [4.5, 4.666, 5] = [mean(3,6), mean(1,4,9), mean(2,8)]<br>
     *
     * @param name        Name of the output variable
     * @param data        Data (variable) to perform unsorted segment mean on
     * @param segmentIds  Variable for the segment IDs
     * @param numSegments Number of segments
     * @return Unsorted segment mean output
     */
    public SDVariable unsortedSegmentMean(String name, SDVariable data, SDVariable segmentIds, int numSegments){
        SDVariable ret = f().unsortedSegmentMean(data, segmentIds, numSegments);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #segmentProd(String, SDVariable, SDVariable)
     */
    public SDVariable segmentProd(SDVariable data, SDVariable segmentIds){
        return segmentProd(null, data, segmentIds);
    }

    /**
     * Segment product operation.<br>
     * If data =     [3, 6, 1, 4, 9, 2, 8]<br>
     * segmentIds =  [0, 0, 1, 1, 1, 2, 2]<br>
     * then output = [18, 36, 16] = [prod(3,6), prod(1,4,9), prod(2,8)]<br>
     * Note that the segment IDs must be sorted from smallest to largest segment.
     * See {@link #unsortedSegmentProd(String, SDVariable, SDVariable, int)} for the same op without this sorted requirement
     *
     * @param name       Name of the output variable. May be null
     * @param data       Data to perform segment max on
     * @param segmentIds Variable for the segment IDs
     * @return Segment product output
     */
    public SDVariable segmentProd(String name, SDVariable data, SDVariable segmentIds){
        SDVariable ret = f().segmentProd(data, segmentIds);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * See {@link #unsortedSegmentProd(String, SDVariable, SDVariable, int)}
     */
    public SDVariable unsortedSegmentProd(SDVariable data, SDVariable segmentIds, int numSegments){
        return unsortedSegmentProd(null, data, segmentIds, numSegments);
    }

    /**
     * Unsorted segment product operation. As per {@link #segmentProd(String, SDVariable, SDVariable)} but without
     * the requirement for the indices to be sorted.<br>
     * If data =     [1, 3, 2, 6, 4, 9, 8]<br>
     * segmentIds =  [1, 0, 2, 0, 1, 1, 2]<br>
     * then output = [4.5, 4.666, 5] = [mean(3,6), mean(1,4,9), mean(2,8)]<br>
     *
     * @param name       Name of the output variable
     * @param data       Data (variable) to perform unsorted segment product on
     * @param segmentIds Variable for the segment IDs
     * @return Unsorted segment product output
     */
    public SDVariable unsortedSegmentProd(String name, SDVariable data, SDVariable segmentIds, int numSegments){
        SDVariable ret = f().unsortedSegmentProd(data, segmentIds, numSegments);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #segmentSum(String, SDVariable, SDVariable)
     */
    public SDVariable segmentSum(SDVariable data, SDVariable segmentIds){
        return segmentSum(null, data, segmentIds);
    }

    /**
     * Segment sum operation.<br>
     * If data =     [3, 6, 1, 4, 9, 2, 8]<br>
     * segmentIds =  [0, 0, 1, 1, 1, 2, 2]<br>
     * then output = [9, 14, 10] = [sum(3,6), sum(1,4,9), sum(2,8)]<br>
     * Note that the segment IDs must be sorted from smallest to largest segment.
     * See {@link #unsortedSegmentSum(String, SDVariable, SDVariable, int)} for the same op without this sorted requirement
     *
     * @param name       Name of the output variable. May be null
     * @param data       Data to perform segment max on
     * @param segmentIds Variable for the segment IDs
     * @return Segment sum output
     */
    public SDVariable segmentSum(String name, SDVariable data, SDVariable segmentIds){
        SDVariable ret = f().segmentSum(data, segmentIds);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * See {@link #unsortedSegmentSum(String, SDVariable, SDVariable, int)}
     */
    public SDVariable unsortedSegmentSum(SDVariable data, SDVariable segmentIds, int numSegments){
        return unsortedSegmentSum(null, data, segmentIds, numSegments);
    }

    /**
     * Unsorted segment sum operation. As per {@link #segmentSum(String, SDVariable, SDVariable)} but without
     * the requirement for the indices to be sorted.<br>
     * If data =     [1, 3, 2, 6, 4, 9, 8]<br>
     * segmentIds =  [1, 0, 2, 0, 1, 1, 2]<br>
     * then output = [9, 14, 10] = [sum(3,6), sum(1,4,9), sum(2,8)]<br>
     *
     * @param name        Name of the output variable
     * @param data        Data (variable) to perform unsorted segment sum on
     * @param segmentIds  Variable for the segment IDs
     * @param numSegments Number of segments
     * @return Unsorted segment sum output
     */
    public SDVariable unsortedSegmentSum(String name, SDVariable data, SDVariable segmentIds, int numSegments){
        SDVariable ret = f().unsortedSegmentSum(data, segmentIds, numSegments);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * See {@link #unsortedSegmentSqrtN(String, SDVariable, SDVariable, int)}
     */
    public SDVariable unsortedSegmentSqrtN(SDVariable data, SDVariable segmentIds, int numSegments){
        return unsortedSegmentSqrtN(null, data, segmentIds, numSegments);
    }

    /**
     * Unsorted segment sqrtN operation. Simply returns the sqrt of the count of the number of values in each segment<br>
     * If data =     [1, 3, 2, 6, 4, 9, 8]<br>
     * segmentIds =  [1, 0, 2, 0, 1, 1, 2]<br>
     * then output = [1.414, 1.732, 1.414] = [sqrt(2), sqrtN(3), sqrtN(2)]<br>
     *
     * @param name       Name of the output variable
     * @param data       Data (variable) to perform unsorted segment sqrtN on
     * @param segmentIds Variable for the segment IDs
     * @return Unsorted segment sqrtN output
     */
    public SDVariable unsortedSegmentSqrtN(String name, SDVariable data, SDVariable segmentIds, int numSegments){
        SDVariable ret = f().unsortedSegmentSqrtN(data, segmentIds, numSegments);
        return updateVariableNameAndReference(ret, name);
    }


    /**
     * TODO doc string
     *
     * @param df
     * @param weights
     * @param strides
     * @param rates
     * @param isSameMode
     * @return
     */
    public SDVariable dilation2D(SDVariable df, SDVariable weights, int[] strides,
                                 int[] rates, boolean isSameMode) {
        return dilation2D(null, df, weights, strides, rates, isSameMode);
    }

    /**
     * TODO doc string
     *
     * @param name
     * @param df
     * @param weights
     * @param strides
     * @param rates
     * @param isSameMode
     * @return
     */
    public SDVariable dilation2D(String name, SDVariable df, SDVariable weights, int[] strides,
                                 int[] rates, boolean isSameMode) {
        SDVariable ret = f().dilation2D(df, weights, strides, rates, isSameMode);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Returns the shape of the specified SDVariable as a 1D SDVariable
     *
     * @param input Input variable
     * @return 1D output variable with contents equal to the shape of the input
     */
    public SDVariable shape(SDVariable input) {
        return shape(null, input);
    }

    /**
     * Returns the shape of the specified SDVariable as a 1D SDVariable
     *
     * @param name  Name of the output variable
     * @param input Input variable
     * @return 1D output variable with contents equal to the shape of the input
     */
    public SDVariable shape(String name, SDVariable input) {
        SDVariable ret = f().shape(input);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Returns the size (number of elements, i.e., prod(shape)) of the specified SDVariable as a 0D scalar variable
     *
     * @param in Input variable
     * @return 0D (scalar) output variable with value equal to the number of elements in the specified array
     */
    public SDVariable size(SDVariable in){
        return size(null, in);
    }

    /**
     * Returns the size (number of elements, i.e., prod(shape)) of the specified SDVariable as a 0D scalar variable
     *
     * @param name Name of the output variable
     * @param in   Input variable
     * @return 0D (scalar) output variable with value equal to the number of elements in the specified array
     */
    public SDVariable size(String name, SDVariable in){
        SDVariable ret = f().size(in);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Returns the rank (number of dimensions, i.e., length(shape)) of the specified SDVariable as a 0D scalar variable
     *
     * @param in Input variable
     * @return 0D (scalar) output variable with value equal to the rank of the input variable
     */
    public SDVariable rank(SDVariable in) {
        return rank(null, in);
    }

    /**
     * Returns the rank (number of dimensions, i.e., length(shape)) of the specified SDVariable as a 0D scalar variable
     *
     * @param name Name of the output variable
     * @param in   Input variable
     * @return 0D (scalar) output variable with value equal to the rank of the input variable
     */
    public SDVariable rank(String name, SDVariable in) {
        SDVariable ret = f().rank(in);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #sizeAt(String, SDVariable, int)
     */
    public SDVariable sizeAt(SDVariable in, int dimension){
        return sizeAt(null, in, dimension);
    }

    /**
     * Returns a rank 0 (scalar) variable for the size of the specified dimension.
     * For example, if X has shape [10,20,30] then sizeAt(X,1)=20. Similarly, sizeAt(X,-1)=30
     *
     * @param name      Name of the output variable
     * @param in        Input variable
     * @param dimension Dimension to get size of
     * @return Scalar SDVariable for size at specified variable
     */
    public SDVariable sizeAt(String name, SDVariable in, int dimension){
        SDVariable ret = f().sizeAt(in, dimension);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #cross(String, SDVariable, SDVariable)
     */
    public SDVariable cross(SDVariable a, SDVariable b) {
        return cross(null, a, b);
    }

    /**
     * Returns the pair-wise cross product of equal size arrays a and b: a x b = ||a||x||b|| sin(theta).
     * Can take rank 1 or above inputs (of equal shapes), but note that the last dimension must have dimension 3
     *
     * @param a First input
     * @param b Second input
     * @return Element-wise cross product
     */
    public SDVariable cross(String name, SDVariable a, SDVariable b) {
        SDVariable ret = f().cross(a, b);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #gather(String, SDVariable, int[], int)
     */
    public SDVariable gather(SDVariable df, int[] indices, int axis) {
        return gather(null, df, indices, axis);
    }

    /**
     * Gather slices from the input variable where the indices are specified as fixed int[] values.<br>
     * Output shape is same as input shape, except for axis dimension, which has size equal to indices.length.
     *
     * @param name    name of the output variable
     * @param df      Input variable
     * @param indices Indices to get
     * @param axis    Axis that the indices refer to
     * @return Output variable with slices pulled from the specified axis
     */
    public SDVariable gather(String name, SDVariable df, int[] indices, int axis) {
        SDVariable ret = f().gather(df, indices, axis);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #gather(String, SDVariable, SDVariable, int)
     */
    public SDVariable gather(SDVariable df, SDVariable indices, int axis) {
        return gather(null, df, indices, axis);
    }

    /**
     * Gather slices from the input variable where the indices are specified as dynamic SDVariable values.<br>
     * Output shape is same as input shape, except for axis dimension, which has size equal to indices.length.
     *
     * @param name    name of the output variable
     * @param df      Input variable
     * @param indices Indices to get slices for. Rank 0 or 1 input
     * @param axis    Axis that the indices refer to
     * @return Output variable with slices pulled from the specified axis
     */
    public SDVariable gather(String name, SDVariable df, SDVariable indices, int axis) {
        SDVariable ret = f().gather(df, indices, axis);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * TODO doc string
     *
     * @param df
     * @param indices
     * @return
     */
    public SDVariable gatherNd(SDVariable df, SDVariable indices) {
        return gatherNd(null, df, indices);
    }

    /**
     * TODO doc string
     *
     * @param name
     * @param df
     * @param indices
     * @return
     */
    public SDVariable gatherNd(String name, SDVariable df, SDVariable indices) {
        SDVariable ret = f().gatherNd(df, indices);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #repeat(String, SDVariable, int)
     */
    public SDVariable repeat(SDVariable df, int axis) {
        return repeat(null, df, axis);
    }

    /**
     * @see #repeat(String, SDVariable, int)
     */
    public SDVariable repeat(String name, SDVariable df, int axis) {
        SDVariable ret = f().repeat(df, axis);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #stack(String, int, SDVariable...)
     */
    public SDVariable stack(int axis, SDVariable... values) {
        return stack(null, axis, values);
    }

    /**
     * Stack a set of N SDVariables of rank X into one rank X+1 variable.
     * If inputs have shape [a,b,c] then output has shape:<br>
     * axis = 0: [N,a,b,c]<br>
     * axis = 1: [a,N,b,c]<br>
     * axis = 2: [a,b,N,c]<br>
     * axis = 3: [a,b,c,N]<br>
     *
     * @param name   Name of the output variable
     * @param axis   Axis to stack on
     * @param values Input variables to stack. Must have the same shape for all inputs
     * @return Output variable
     * @see #unstack(String[], SDVariable, int, int)
     */
    public SDVariable stack(String name, int axis, SDVariable... values) {
        SDVariable ret = f().stack(values, axis);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #stack(String, int, SDVariable...)
     */
    public SDVariable parallel_stack(SDVariable[] values) {
        return parallel_stack(null, values);
    }

    /**
     * @see #stack(String, int, SDVariable...)
     */
    public SDVariable parallel_stack(String name, SDVariable[] values) {
        SDVariable ret = f().parallel_stack(values);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #unstack(String[], SDVariable, int, int)
     */
    public SDVariable[] unstack(SDVariable value, int axis) {
        return unstack(null, value, axis);
    }

    /**
     * @see #unstack(String[], SDVariable, int, int)
     */
    public SDVariable[] unstack(String[] names, SDVariable value, int axis) {
        SDVariable[] ret = f().unstack(value, axis);
        return updateVariableNamesAndReferences(ret, names);
    }

    /**
     * @see #unstack(String[], SDVariable, int, int)
     */
    public SDVariable[] unstack(SDVariable value, int axis, int num) {
        return unstack(null, value, axis, num);
    }

    /**
     * Unstack a variable of rank X into N rank X-1 variables by taking slices along the specified axis.
     * If input has shape [a,b,c] then output has shape:
     * axis = 0: [b,c]<br>
     * axis = 1: [a,c]<br>
     * axis = 2: [a,b]<br>
     *
     * @param names Output variable names. May be null
     * @param value Input variable to unstack
     * @param axis  Axis to unstack on
     * @param num   Number of output variables
     * @return Output variables
     * @see #stack(String, int, SDVariable...)
     */
    public SDVariable[] unstack(String[] names, SDVariable value, int axis, int num) {
        SDVariable[] ret = f().unstack(value, axis, num);
        return updateVariableNamesAndReferences(ret, names);
    }

    /**
     * Element-wise Gaussian error function - out = erf(in)
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable erf(SDVariable x) {
        return erf(null, x);
    }

    /**
     * Element-wise Gaussian error function - out = erf(in)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable erf(String name, SDVariable x) {
        SDVariable ret = f().erf(x);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Element-wise complementary Gaussian error function - out = erfc(in) = 1 - erf(in)
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable erfc(SDVariable x) {
        return erfc(null, x);
    }

    /**
     * Element-wise complementary Gaussian error function - out = erfc(in) = 1 - erf(in)
     *
     * @param name Name of the output variable
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable erfc(String name, SDVariable x) {
        SDVariable ret = f().erfc(x);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #diag(String, SDVariable)
     */
    public SDVariable diag(SDVariable x) {
        return diag(null, x);
    }

    /**
     * Returns an output variable with diagonal values equal to the specified values; off-diagonal values will be set to 0<br>
     * For example, if input = [1,2,3], then output is given by:<br>
     * [ 1, 0, 0]<br>
     * [ 0, 2, 0]<br>
     * [ 0, 0, 3]<br>
     * <br>
     * Higher input ranks are also supported: if input has shape [a,...,R-1] then output[i,...,k,i,...,k] = input[i,...,k].
     * i.e., for input rank R, output has rank 2R
     *
     * @param name Name of the output variable
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable diag(String name, SDVariable x) {
        SDVariable ret = f().diag(x);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #diagPart(String, SDVariable)
     */
    public SDVariable diagPart(SDVariable x) {
        return diagPart(null, x);
    }

    /**
     * Extract the diagonal part from the input array.<br>
     * If input is<br>
     * [ 1, 0, 0]<br>
     * [ 0, 2, 0]<br>
     * [ 0, 0, 3]<br>
     * then output is [1, 2, 3].<br>
     * Supports higher dimensions: in general, out[i,...,k] = in[i,...,k,i,...,k]
     *
     * @param x Input variable
     * @return Diagonal part of the input
     * @see #diag(String, SDVariable)
     */
    public SDVariable diagPart(String name, SDVariable x) {
        SDVariable ret = f().diagPart(x);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #setDiag(String, SDVariable, SDVariable)
     */
    public SDVariable setDiag(SDVariable in, SDVariable diag) {
        return setDiag(null, in, diag);
    }

    /**
     * Set the diagonal value to the specified values<br>
     * If input is<br>
     * [ a, b, c]<br>
     * [ d, e, f]<br>
     * [ g, h, i]<br>
     * and diag = [ 1, 2, 3] then output is<br>
     * [ 1, b, c]<br>
     * [ d, 2, f]<br>
     * [ g, h, 3]<br>
     *
     * @param name Name of the output variable
     * @param in   Input variable
     * @param diag Diagonal
     * @return Output variable
     */
    public SDVariable setDiag(String name, SDVariable in, SDVariable diag) {
        SDVariable ret = f().setDiag(in, diag);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #oneHot(String, SDVariable, int)
     */
    public SDVariable oneHot(SDVariable indices, int depth) {
        return oneHot(null, indices, depth, -1, 1.00, 0.00);
    }

    /**
     * @see #oneHot(String, SDVariable, int, int, double, double)
     */
    public SDVariable oneHot(SDVariable indices, int depth, int axis, double on, double off) {
        return oneHot(null, indices, depth, axis, on, off);
    }

    /**
     * Convert the array to a one-hot array with walues 0 and 1 for each entry<br>
     * If input has shape [ a, ..., n] then output has shape [ a, ..., n, depth],
     * with out[i, ..., j, in[i,...,j]] = 1 with other values being set to 0
     *
     * @param name    Output variable name
     * @param indices Indices - value 0 to depth-1
     * @param depth   Number of classes
     * @return Output variable
     * @see #oneHot(SDVariable, int, int, double, double)
     */
    public SDVariable oneHot(String name, SDVariable indices, int depth) {
        return oneHot(name, indices, depth, -1, 1.00, 0.00);
    }

    /**
     * Convert the array to a one-hot array with walues {@code on} and {@code off} for each entry<br>
     * If input has shape [ a, ..., n] then output has shape [ a, ..., n, depth],
     * with {@code out[i, ..., j, in[i,...,j]] = on} with other values being set to {@code off}
     *
     * @param name    Output variable name
     * @param indices Indices - value 0 to depth-1
     * @param depth   Number of classes
     * @return Output variable
     */
    public SDVariable oneHot(String name, SDVariable indices, int depth, int axis, double on, double off) {
        SDVariable ret = f().onehot(indices, depth, axis, on, off);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Element-wise reciprocal (inverse) function: out[i] = 1 / in[i]
     *
     * @param a Input variable
     * @return Output variable
     */
    public SDVariable reciprocal(SDVariable a) {
        return reciprocal(null, a);
    }

    /**
     * Element-wise reciprocal (inverse) function: out[i] = 1 / in[i]
     *
     * @param name Name of the output variable
     * @param a    Input variable
     * @return Output variable
     */
    public SDVariable reciprocal(String name, SDVariable a) {
        SDVariable ret = f().reciprocal(a);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Intended for internal/developer use
     */
    public SDVariable gradientBackwardsMarker(SDVariable x) {
        return gradientBackwardsMarker(generateNewVarName(new GradientBackwardsMarker().opName(), 0), x);
    }

    /**
     * Intended for internal/developer use
     */
    public SDVariable gradientBackwardsMarker(String name, SDVariable x) {
        SDVariable result = functionFactory.gradientBackwardsMarker(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise hard tanh function:<br>
     * out[i] = -1 if in[i] <= -1<br>
     * out[1] = in[i] if -1 < in[i] < 1<br>
     * out[i] = 1 if in[i] >= 1<br>
     *
     * @param in Input variable
     * @return Output variable
     */
    public SDVariable hardTanh(SDVariable in) {
        return hardTanh(null, in);
    }

    /**
     * Element-wise hard tanh function:<br>
     * out[i] = -1 if in[i] <= -1<br>
     * out[1] = in[i] if -1 < in[i] < 1<br>
     * out[i] = 1 if in[i] >= 1<br>
     *
     * @param name Output variable name
     * @param in   Input variable
     * @return Output variable
     */
    public SDVariable hardTanh(String name, SDVariable in) {
        SDVariable result = functionFactory.hardTanh(in);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise hard sigmoid function:<br>
     * out[i] = 0 if in[i] <= -2.5<br>
     * out[1] = 0.2*in[i]+0.5 if -2.5 < in[i] < 2.5<br>
     * out[i] = 1 if in[i] >= 2.5<br>
     *
     * @param in Input variable
     * @return Output variable
     */
    public SDVariable hardSigmoid(SDVariable in) {
        return hardSigmoid(null, in);
    }

    /**
     * Element-wise hard sigmoid function:<br>
     * out[i] = 0 if in[i] <= -2.5<br>
     * out[1] = 0.2*in[i]+0.5 if -2.5 < in[i] < 2.5<br>
     * out[i] = 1 if in[i] >= 2.5<br>
     *
     * @param name Name of the output variable
     * @param in    Input variable
     * @return Output variable
     */
    public SDVariable hardSigmoid(String name, SDVariable in) {
        SDVariable ret = f().hardSigmoid(in);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Derivative (dOut/dIn) of the element-wise hard Tanh function - {@link #hardTanh(SDVariable)}
     *
     * @param x Input
     * @return Output variable
     */
    public SDVariable hardTanhDerivative(SDVariable x) {
        return hardTanhDerivative(null, x);
    }

    /**
     * Derivative (dOut/dIn) of the element-wise hard Tanh function - {@link #hardTanh(SDVariable)}
     *
     * @param name Output variable name
     * @param x   Input
     * @return Output variable
     */
    public SDVariable hardTanhDerivative(String name, SDVariable x) {
        SDVariable result = functionFactory.hardTanhDerivative(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise sigmoid function: out[i] = 1.0/(1+exp(-in[i]))
     *
     * @param x Input Variable
     * @return Output variable
     */
    public SDVariable sigmoid(SDVariable x) {
        return sigmoid(null, x);
    }

    /**
     * Element-wise sigmoid function: out[i] = 1.0/(1+exp(-in[i]))
     *
     * @param name Output variable name
     * @param x   Input Variable
     * @return Output variable
     */
    public SDVariable sigmoid(String name, SDVariable x) {
        SDVariable result = functionFactory.sigmoid(x);
        return updateVariableNameAndReference(result, name);
    }


    /**
     * Element-wise sigmoid function derivative: dL/dIn given input and dL/dOut
     *
     * @param x  Input Variable
     * @param wrt Gradient at the output - dL/dOut. Must have same shape as the input
     * @return Output variable
     */
    public SDVariable sigmoidDerivative(SDVariable x, SDVariable wrt) {
        return sigmoidDerivative(null, x, wrt);
    }

    /**
     * Element-wise sigmoid function derivative: dL/dIn given input and dL/dOut
     *
     * @param name Output variable name
     * @param x   Input Variable
     * @param wrt  Gradient at the output - dL/dOut. Must have same shape as the input
     * @return Output variable
     */
    public SDVariable sigmoidDerivative(String name, SDVariable x, SDVariable wrt) {
        SDVariable result = functionFactory
                .sigmoidDerivative(x, wrt);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise sigmoid function: out[i] = log(sigmoid(in[i]))
     *
     * @param x Input Variable
     * @return Output variable
     */
    public SDVariable logSigmoid(SDVariable x) {
        return logSigmoid(null, x);
    }

    /**
     * Element-wise sigmoid function: out[i] = log(sigmoid(in[i]))
     *
     * @param name Name of the output variable
     * @param x   Input Variable
     * @return Output variable
     */
    public SDVariable logSigmoid(String name, SDVariable x) {
        SDVariable ret = f().logSigmoid(x);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Element-wise sign (signum) function:<br>
     * out = -1 if in < 0<br>
     * out = 0 if in = 0<br>
     * out = 1 if in > 0
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable sign(SDVariable x) {
        return sign(null, x);
    }

    /**
     * Element-wise sign (signum) function:<br>
     * out = -1 if in < 0<br>
     * out = 0 if in = 0<br>
     * out = 1 if in > 0
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable sign(String name, SDVariable x) {
        SDVariable result = functionFactory.sign(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise softsign function: out = x / (abs(x) + 1)
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable softsign(SDVariable x) {
        return softsign(null, x);
    }

    /**
     * Element-wise softsign function: out = x / (abs(x) + 1)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable softsign(String name, SDVariable x) {
        SDVariable result = functionFactory.softsign(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise derivative (dOut/dIn) of the softsign function {@link #softsign(SDVariable)}
     *
     * @param x Input variable
     * @return Output varible
     */
    public SDVariable softsignDerivative(SDVariable x) {
        return softsignDerivative(null, x);
    }

    /**
     * Element-wise derivative (dOut/dIn) of the softsign function {@link #softsign(SDVariable)}
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output varible
     */
    public SDVariable softsignDerivative(String name, SDVariable x) {
        SDVariable result = functionFactory.softsignDerivative(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise softplus function: out = log(exp(x) + 1)
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable softplus(SDVariable x) {
        return softplus(null, x);
    }

    /**
     * Element-wise softplus function: out = log(exp(x) + 1)
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable softplus(String name, SDVariable x) {
        SDVariable result = functionFactory.softplus(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise "swish" function: out = x * sigmoid(b*x) with b=1.0<br>
     * See: <a href="https://arxiv.org/abs/1710.05941">https://arxiv.org/abs/1710.05941</a>
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable swish(SDVariable x) {
        return swish(null, x);
    }

    /**
     * Element-wise "swish" function: out = x * sigmoid(b*x) with b=1.0<br>
     * See: <a href="https://arxiv.org/abs/1710.05941">https://arxiv.org/abs/1710.05941</a>
     *
     * @param name Name of the output variable
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable swish(String name, SDVariable x) {
        SDVariable ret = f().swish(x);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Element-wise exponential linear unit (ELU) function:<br>
     * out = x if x > 0<br>
     * out = a * (exp(x) - 1) if x <= 0<br>
     * with constant a = 1.0
     * <p>
     * See: <a href="http://arxiv.org/abs/1511.07289">http://arxiv.org/abs/1511.07289</a>
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable elu(SDVariable x) {
        return elu(null, x);
    }

    /**
     * Element-wise exponential linear unit (ELU) function:<br>
     * out = x if x > 0<br>
     * out = a * (exp(x) - 1) if x <= 0<br>
     * with constant a = 1.0
     * <p>
     * See: <a href="http://arxiv.org/abs/1511.07289">http://arxiv.org/abs/1511.07289</a>
     *
     * @param name Output variable name
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable elu(String name, SDVariable x) {
        SDVariable result = functionFactory.elu(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise derivative exponential linear unit (ELU) function, dOut/dIn given input.
     * {@link #elu(SDVariable)}
     *
     * @param x Input variable
     * @return Output variable
     */
    public SDVariable eluDerivative(SDVariable x) {
        return eluDerivative(null, x);
    }

    /**
     * Element-wise derivative exponential linear unit (ELU) function, dOut/dIn given input.
     * {@link #elu(SDVariable)}
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable
     */
    public SDVariable eluDerivative(String name, SDVariable x) {
        SDVariable result = functionFactory.eluDerivative(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise leaky ReLU function:<br>
     * out = x if x >= 0.0<br>
     * out = alpha * x if x < cutoff<br>
     * Alpha value is most commonly set to 0.01
     *
     * @param x    Input variable
     * @param alpha Cutoff - usually 0.0
     * @return Output variable
     */
    public SDVariable leakyRelu(SDVariable x, double alpha) {
        return leakyRelu(null, x, alpha);
    }

    /**
     * Element-wise leaky ReLU function:<br>
     * out = x if x >= 0.0<br>
     * out = alpha * x if x < cutoff<br>
     * Alpha value is most commonly set to 0.01
     *
     * @param x    Input variable
     * @param alpha Cutoff - usually 0.0
     * @return Output variable
     */
    public SDVariable leakyRelu(String name, SDVariable x, double alpha) {
        SDVariable result = functionFactory.leakyRelu(x, alpha);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Leaky ReLU derivative: dOut/dIn given input.<br>
     * See {@link #leakyRelu(String, SDVariable, double)}
     *
     * @param x    Input variable
     * @param alpha Alpha value
     * @return Output variable
     */
    public SDVariable leakyReluDerivative(String name, SDVariable x, double alpha) {
        SDVariable result = functionFactory.leakyReluDerivative(x, alpha);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Full array mean reduction operation
     * @param x Input variable
     * @return Output variable - scalar
     */
    public SDVariable mean(SDVariable x) {
        return mean(null, x);
    }


    /**
     * Mean (average) array reduction operation, optionally along specified dimensions
     *
     * @param x        Input variable
     * @param dimension Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable mean(SDVariable x, int... dimension) {
        return mean(null, x, dimension);
    }

    /**
     * Mean (average) array reduction operation, optionally along specified dimensions
     *
     * @param name      Output variable name
     * @param x        Input variable
     * @param dimension Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable mean(String name, SDVariable x, int... dimension) {
        return mean(name, x, false, dimension);
    }

    /**
     * Mean (average) array reduction operation, optionally along specified dimensions<br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name      Output variable name
     * @param x        Input variable
     * @param keepDims  If true: keep the dimensions that are reduced on (as size 1). False: remove the reduction dimensions
     * @param dimension Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable mean(String name, SDVariable x, boolean keepDims, int... dimension) {
        SDVariable result = functionFactory.mean(x, keepDims, dimension);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @see #standardDeviation(String, SDVariable, boolean, int...)
     */
    public SDVariable standardDeviation(SDVariable x, boolean biasCorrected, int... dimensions) {
        return standardDeviation(null, x, biasCorrected, dimensions);
    }

    /**
     * Stardard deviation array reduction operation, optionally along specified dimensions
     *
     * @param name          Output variable name
     * @param x            Input variable
     * @param biasCorrected If true: divide by (N-1) (i.e., sample stdev). If false: divide by N (population stdev)
     * @param dimensions    Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Output variable: reduced array of rank (input rank - num dimensions)
     */
    public SDVariable standardDeviation(String name, SDVariable x, boolean biasCorrected, int... dimensions) {
        return standardDeviation(name, x, biasCorrected, false, dimensions);
    }

    /**
     * Stardard deviation array reduction operation, optionally along specified dimensions<br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param x            Input variable
     * @param biasCorrected If true: divide by (N-1) (i.e., sample stdev). If false: divide by N (population stdev)
     * @param keepDims      If true: keep the dimensions that are reduced on (as size 1). False: remove the reduction dimensions
     * @param dimensions    Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Output variable: reduced array of rank (input rank - num dimensions)
     */
    public SDVariable standardDeviation(String name, SDVariable x, boolean biasCorrected, boolean keepDims, int... dimensions) {
        SDVariable result = functionFactory.std(x, biasCorrected, keepDims, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @see #variance(String, SDVariable, boolean, int...)
     */
    public SDVariable variance(SDVariable x, boolean biasCorrected, int... dimensions) {
        return variance(null, x, biasCorrected, dimensions);
    }

    /**
     * Variance array reduction operation, optionally along specified dimensions
     *
     * @param name          Output variable name
     * @param x            Input variable
     * @param biasCorrected If true: divide by (N-1) (i.e., sample variable). If false: divide by N (population variance)
     * @param dimensions    Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Output variable: reduced array of rank (input rank - num dimensions)
     */
    public SDVariable variance(String name, SDVariable x, boolean biasCorrected, int... dimensions) {
        return variance(name, x, biasCorrected, false, dimensions);
    }

    /**
     * Variance array reduction operation, optionally along specified dimensions<br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name          Output variable name
     * @param x            Input variable
     * @param biasCorrected If true: divide by (N-1) (i.e., sample variable). If false: divide by N (population variance)
     * @param keepDims      If true: keep the dimensions that are reduced on (as size 1). False: remove the reduction dimensions
     * @param dimensions    Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Output variable: reduced array of rank (input rank - num dimensions)
     */
    public SDVariable variance(String name, SDVariable x, boolean biasCorrected, boolean keepDims, int... dimensions) {
        SDVariable result = functionFactory.variance(x, biasCorrected, keepDims, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Entropy reduction: -sum(x * log(x))
     *
     * @param in         Input variable
     * @param dimensions Dimensions to reduce on (null/empty for full array)
     * @return Output variable
     */
    public SDVariable entropy(SDVariable in, int... dimensions) {
        return entropy(null, in, dimensions);
    }

    /**
     * Entropy reduction: -sum(x * log(x))
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param dimensions Dimensions to reduce on (null/empty for full array)
     * @return Output variable: reduced array of rank (input rank - num dimensions)
     */
    public SDVariable entropy(String name, SDVariable in, int... dimensions) {
        SDVariable ret = f().entropy(in, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Log entropy reduction: log(-sum(x * log(x)))
     *
     * @param in         Input variable
     * @param dimensions Dimensions to reduce on (null for full array)
     * @return Output variable: reduced array of rank (input rank - num dimensions)
     */
    public SDVariable logEntropy(SDVariable in, int... dimensions) {
        return logEntropy(null, in, dimensions);
    }

    /**
     * Log entropy reduction: log(-sum(x * log(x)))
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param dimensions Dimensions to reduce on (null for full array)
     * @return Output variable: reduced array of rank (input rank - num dimensions)
     */
    public SDVariable logEntropy(String name, SDVariable in, int... dimensions) {
        SDVariable ret = f().logEntropy(in, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Shannon Entropy reduction: -sum(x * log2(x))
     *
     * @param in         Input variable
     * @param dimensions Dimensions to reduce on (null/empty for full array)
     * @return Output variable
     */
    public SDVariable shannonEntropy(SDVariable in, int... dimensions) {
        return shannonEntropy(null, in, dimensions);
    }

    /**
     * Shannon Entropy reduction: -sum(x * log2(x))
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param dimensions Dimensions to reduce on (null/empty for full array)
     * @return Output variable: reduced array of rank (input rank - num dimensions)
     */
    public SDVariable shannonEntropy(String name, SDVariable in, int... dimensions) {
        SDVariable ret = f().shannonEntropy(in, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Sum array reduction operation, optionally along specified dimensions
     *
     * @param x         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Output variable: reduced array of rank (input rank - num dimensions)
     */
    public SDVariable sum(SDVariable x, int... dimensions) {
        return sum(null, x, dimensions);
    }

    /**
     * Sum array reduction operation, optionally along specified dimensions
     *
     * @param x         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Output variable: reduced array of rank (input rank - num dimensions) if keepDims = false, or
     * of rank (input rank) if keepdims = true
     */
    public SDVariable sum(String name, SDVariable x, int... dimensions) {
        return sum(name, x, false, dimensions);
    }

    /**
     * Sum array reduction operation, optionally along specified dimensions.<br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name       Output variable name
     * @param x         Input variable
     * @param keepDims   If true: keep the dimensions that are reduced on (as length 1). False: remove the reduction dimensions
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Output variable: reduced array of rank (input rank - num dimensions) if keepDims = false, or
     * of rank (input rank) if keepdims = true
     */
    public SDVariable sum(String name, SDVariable x, boolean keepDims, int... dimensions) {
        SDVariable result = functionFactory.sum(x, keepDims, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @see #sum(String, SDVariable, boolean, int...)
     */
    public SDVariable sum(SDVariable x, boolean keepDims, int... dimensions) {
        return sum(null, x, keepDims, dimensions);
    }


    /**
     * Product array reduction operation, optionally along specified dimensions
     *
     * @param x         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Output variable: reduced array of rank (input rank - num dimensions)
     */
    public SDVariable prod(SDVariable x, int... dimensions) {
        return prod(null, x, dimensions);
    }

    /**
     * Product array reduction operation, optionally along specified dimensions
     *
     * @param name       Output variable name
     * @param x         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Output variable: reduced array of rank (input rank - num dimensions)
     */
    public SDVariable prod(String name, SDVariable x, int... dimensions) {
        return prod(name, x, false, dimensions);
    }

    /**
     * Product array reduction operation, optionally along specified dimensions<br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name       Output variable name
     * @param x         Input variable
     * @param keepDims   If true: keep the dimensions that are reduced on (as length 1). False: remove the reduction dimensions
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Output variable: reduced array of rank (input rank - num dimensions)
     */
    public SDVariable prod(String name, SDVariable x, boolean keepDims, int... dimensions) {
        SDVariable result = functionFactory.prod(x, keepDims, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise scalar maximum operation: out = max(in, value)
     *
     * @param in    Input variable
     * @param value Scalar value to compare
     * @return Output variable
     */
    public SDVariable scalarMax(SDVariable in, Number value) {
        return scalarMax(null, in, value);
    }

    /**
     * Element-wise scalar maximum operation: out = max(in, value)
     *
     * @param name  Name of the output variable
     * @param in    Input variable
     * @param value Scalar value to compare
     * @return Output variable
     */
    public SDVariable scalarMax(String name, SDVariable in, Number value) {
        SDVariable ret = f().scalarMax(in, value);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Element-wise scalar minimum operation: out = min(in, value)
     *
     * @param in    Input variable
     * @param value Scalar value to compare
     * @return Output variable
     */
    public SDVariable scalarMin(SDVariable in, Number value) {
        return scalarMin(null, in, value);
    }

    /**
     * Element-wise scalar minimum operation: out = min(in, value)
     *
     * @param name  Name of the output variable
     * @param in    Input variable
     * @param value Scalar value to compare
     * @return Output variable
     */
    public SDVariable scalarMin(String name, SDVariable in, Number value) {
        SDVariable ret = f().scalarMin(in, value);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Element-wise scalar floor modulus operation: out = floorMod(in, value).
     * i.e., returns the remainder after division by 'value'
     *
     * @param in    Input variable
     * @param value Scalar value to compare
     * @return Output variable
     */
    public SDVariable scalarFloorMod(SDVariable in, Number value) {
        return scalarFloorMod(null, in, value);
    }

    /**
     * Element-wise scalar floor modulus operation: out = floorMod(in, value).
     * i.e., returns the remainder after division by 'value'
     *
     * @param name  Name of the output variable
     * @param in    Input variable
     * @param value Scalar value to compare
     * @return Output variable
     */
    public SDVariable scalarFloorMod(String name, SDVariable in, Number value) {
        SDVariable ret = f().scalarFloorMod(in, value);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Return an array with equal shape to the input, but all elements set to value 'set'
     *
     * @param in  Input variable
     * @param set Value to set
     * @return Output variable
     */
    public SDVariable scalarSet(SDVariable in, Number set) {
        return scalarSet(null, in, set);
    }

    /**
     * Return a variable with equal shape to the input, but all elements set to value 'set'
     *
     * @param name Name of the output variable
     * @param in   Input variable
     * @param set  Value to set
     * @return Output variable
     */
    public SDVariable scalarSet(String name, SDVariable in, Number set) {
        SDVariable ret = f().scalarSet(in, set);
        return updateVariableNameAndReference(ret, name);
    }


    /**
     * Max array reduction operation, optionally along specified dimensions
     *
     * @param x         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable max(SDVariable x, int... dimensions) {
        return max(null, x, dimensions);
    }

    /**
     * Max array reduction operation, optionally along specified dimensions
     *
     * @param name       Output variable name
     * @param x         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable max(String name, SDVariable x, int... dimensions) {
        return max(name, x, false, dimensions);
    }

    /**
     * Max array reduction operation, optionally along specified dimensions<br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name       Output variable name
     * @param x         Input variable
     * @param keepDims   If true: keep the dimensions that are reduced on (as size 1). False: remove the reduction dimensions
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable max(String name, SDVariable x, boolean keepDims, int... dimensions) {
        SDVariable result = functionFactory.max(x, keepDims, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Element-wise maximum operation: out[i] = max(first[i], second[i])<br>
     * Supports broadcasting
     *
     * @param first  First input array
     * @param second Second input array
     * @return Output variable
     */
    public SDVariable max(SDVariable first, SDVariable second) {
        return max(null, first, second);
    }

    /**
     * Element-wise maximum operation: out[i] = max(first[i], second[i])<br>
     * Supports broadcasting
     *
     * @param name   Name of the output variable
     * @param first  First input array
     * @param second Second input array
     * @return Output variable
     */
    public SDVariable max(String name, SDVariable first, SDVariable second) {
        SDVariable result = f().max(first, second);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Absolute max array reduction operation, optionally along specified dimensions: out = max(abs(x))
     *
     * @param in         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable amax(SDVariable in, int... dimensions) {
        return amax(null, in, dimensions);
    }

    /**
     * Absolute max array reduction operation, optionally along specified dimensions: out = max(abs(x))
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable amax(String name, SDVariable in, int... dimensions) {
        SDVariable ret = f().amax(in, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Absolute min array reduction operation, optionally along specified dimensions: out = min(abs(x))
     *
     * @param in         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable amin(SDVariable in, int... dimensions) {
        return amin(null, in, dimensions);
    }

    /**
     * Absolute min array reduction operation, optionally along specified dimensions: out = min(abs(x))
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable amin(String name, SDVariable in, int... dimensions) {
        SDVariable ret = f().amin(in, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Absolute mean array reduction operation, optionally along specified dimensions: out = mean(abs(x))
     *
     * @param in         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable amean(SDVariable in, int... dimensions) {
        return amean(null, in, dimensions);
    }

    /**
     * Absolute mean array reduction operation, optionally along specified dimensions: out = mean(abs(x))
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable amean(String name, SDVariable in, int... dimensions) {
        SDVariable ret = f().amean(in, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Absolute sum array reduction operation, optionally along specified dimensions: out = sum(abs(x))
     *
     * @param in         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable asum(SDVariable in, int... dimensions) {
        return asum(null, in, dimensions);
    }

    /**
     * Absolute sum array reduction operation, optionally along specified dimensions: out = sum(abs(x))
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable asum(String name, SDVariable in, int... dimensions) {
        SDVariable ret = f().asum(in, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Count zero array reduction operation, optionally along specified dimensions: out = count(x == 0)
     *
     * @param input      Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable countZero(SDVariable input, int... dimensions) {
        return countZero(null, input, dimensions);
    }

    /**
     * Count zero array reduction operation, optionally along specified dimensions: out = count(x == 0)
     *
     * @param name       Name of the output variable
     * @param input      Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable countZero(String name, SDVariable input, int... dimensions) {
        SDVariable res = f().countZero(input, dimensions);
        return updateVariableNameAndReference(res, name);
    }

    /**
     * Full array zero fraction array reduction operation, optionally along specified dimensions: out = (count(x == 0) / length(x))
     *
     * @param input Input variable
     * @return Reduced array of rank 0 (scalar)
     */
    public SDVariable zeroFraction(SDVariable input) {
        return zeroFraction(null, input);
    }

    /**
     * Full array zero fraction array reduction operation, optionally along specified dimensions: out = (count(x == 0) / length(x))
     *
     * @param name  Name of the output variable
     * @param input Input variable
     * @return Reduced array of rank 0 (scalar)
     */
    public SDVariable zeroFraction(String name, SDVariable input) {
        SDVariable res = f().zeroFraction(input);
        return updateVariableNameAndReference(res, name);
    }

    /**
     * Count non zero array reduction operation, optionally along specified dimensions: out = count(x != 0)
     *
     * @param input      Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable countNonZero(SDVariable input, int... dimensions) {
        return countNonZero(null, input, dimensions);
    }

    /**
     * Count non zero array reduction operation, optionally along specified dimensions: out = count(x != 0)
     *
     * @param name       Name of the output variable
     * @param input      Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable countNonZero(String name, SDVariable input, int... dimensions) {
        SDVariable res = f().countNonZero(input, dimensions);
        return updateVariableNameAndReference(res, name);
    }

    /**
     * Minimum array reduction operation, optionally along specified dimensions. out = min(in)
     *
     * @param x         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable min(SDVariable x, int... dimensions) {
        return min(null, x, dimensions);
    }

    /**
     * Minimum array reduction operation, optionally along specified dimensions. out = min(in)
     *
     * @param name       Output variable name
     * @param x         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable min(String name, SDVariable x, int... dimensions) {
        return min(name, x, false, dimensions);
    }

    /**
     * Minimum array reduction operation, optionally along specified dimensions. out = min(in)<br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name       Output variable name
     * @param x         Input variable
     * @param keepDims   If true: keep the dimensions that are reduced on (as size 1). False: remove the reduction dimensions
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable min(String name, SDVariable x, boolean keepDims, int... dimensions) {
        SDVariable result = functionFactory.min(x, keepDims, dimensions);
        return updateVariableNameAndReference(result, name);

    }

    /**
     * Element-wise minimum operation: out[i] = min(first[i], second[i])<br>
     * Supports broadcasting
     *
     * @param first  First input array
     * @param second Second input array
     * @return Output variable
     */
    public SDVariable min(SDVariable first, SDVariable second) {
        return min(null, first, second);
    }

    /**
     * Element-wise minimum operation: out[i] = min(first[i], second[i])<br>
     * Supports broadcasting
     *
     * @param name   Name of the output variable
     * @param first  First input array
     * @param second Second input array
     * @return Output variable
     */
    public SDVariable min(String name, SDVariable first, SDVariable second) {
        SDVariable result = f().min(first, second);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Argmax array reduction operation, optionally along specified dimensions.<br>
     * Output values are the index of the maximum value of each slice along the specified dimension
     *
     * @param in         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable argmax(SDVariable in, int... dimensions) {
        return argmax(null, in, false, dimensions);
    }

    /**
     * @see #argmax(String, SDVariable, boolean, int...)
     */
    public SDVariable argmax(SDVariable in, boolean keepDims, int... dimensions) {
        return argmax(null, in, keepDims, dimensions);
    }

    /**
     * Argmax array reduction operation, optionally along specified dimensions.<br>
     * Output values are the index of the maximum value of each slice along the specified dimension
     *
     * @param in         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable argmax(String name, SDVariable in, int... dimensions) {
        return argmax(name, in, false, dimensions);
    }

    /**
     * Argmax array reduction operation, optionally along specified dimensions.<br>
     * Output values are the index of the maximum value of each slice along the specified dimension.<br>
     * <br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param keepDims   If true: keep the dimensions that are reduced on (as size 1). False: remove the reduction dimensions
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Output variable: reduced array of rank (input rank - num dimensions) if keepDims = false, or
     * of rank (input rank) if keepdims = true
     */
    public SDVariable argmax(String name, SDVariable in, boolean keepDims, int... dimensions) {
        SDVariable ret = f().argmax(in, keepDims, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Argmin array reduction operation, optionally along specified dimensions.<br>
     * Output values are the index of the minimum value of each slice along the specified dimension
     *
     * @param in         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable argmin(SDVariable in, int... dimensions) {
        return argmin(null, in, dimensions);
    }

    /**
     * @see #argmin(String, SDVariable, boolean, int...)
     */
    public SDVariable argmin(SDVariable in, boolean keepDims, int... dimensions) {
        return argmin(null, in, keepDims, dimensions);
    }

    /**
     * Argmin array reduction operation, optionally along specified dimensions.<br>
     * Output values are the index of the minimum value of each slice along the specified dimension
     *
     * @param in         Input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable argmin(String name, SDVariable in, int... dimensions) {
        return argmin(name, in, false, dimensions);
    }

    /**
     * Argmin array reduction operation, optionally along specified dimensions.<br>
     * Output values are the index of the minimum value of each slice along the specified dimension.<br>
     * <br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param keepDims   If true: keep the dimensions that are reduced on (as length 1). False: remove the reduction dimensions
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Output variable: reduced array of rank (input rank - num dimensions) if keepDims = false, or
     * of rank (input rank) if keepdims = true
     */
    public SDVariable argmin(String name, SDVariable in, boolean keepDims, int... dimensions) {
        SDVariable ret = f().argmin(in, keepDims, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Index of the max absolute value: argmax(abs(in))
     * @see #argmax(SDVariable, int...)
     */
    public SDVariable iamax(SDVariable in, int... dimensions) {
        return iamax(null, in, dimensions);
    }

    /**
     * Index of the max absolute value: argmax(abs(in))
     * @see #argmax(String, SDVariable, boolean, int...)
     */
    public SDVariable iamax(SDVariable in, boolean keepDims, int... dimensions) {
        return iamax(null, in, keepDims, dimensions);
    }

    /**
     * Index of the max absolute value: argmax(abs(in))
     * @see #argmax(String, SDVariable, boolean, int...)
     */
    public SDVariable iamax(String name, SDVariable in, int... dimensions) {
        return iamax(name, in, false, dimensions);
    }

    /**
     * Index of the max absolute value: argmax(abs(in))
     * @see #argmax(String, SDVariable, boolean, int...)
     */
    public SDVariable iamax(String name, SDVariable in, boolean keepDims, int... dimensions) {
        SDVariable ret = f().iamax(in, keepDims, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Index of the min absolute value: argmin(abs(in))
     * @see #argmin(String, SDVariable, boolean, int...)
     */
    public SDVariable iamin(SDVariable in, int... dimensions) {
        return iamin(null, in, dimensions);
    }

    /**
     * Index of the min absolute value: argmin(abs(in))
     * @see #argmin(String, SDVariable, boolean, int...)
     */
    public SDVariable iamin(SDVariable in, boolean keepDims, int... dimensions) {
        return iamin(null, in, keepDims, dimensions);
    }

    /**
     * Index of the min absolute value: argmin(abs(in))
     * @see #argmin(String, SDVariable, boolean, int...)
     */
    public SDVariable iamin(String name, SDVariable in, int... dimensions) {
        return iamin(name, in, false, dimensions);
    }

    /**
     * Index of the min absolute value: argmin(abs(in))
     * @see #argmin(String, SDVariable, boolean, int...)
     */
    public SDVariable iamin(String name, SDVariable in, boolean keepDims, int... dimensions) {
        SDVariable ret = f().iamin(in, keepDims, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #firstIndex(String, SDVariable, Condition, int...)
     */
    public SDVariable firstIndex(SDVariable in, Condition condition, int... dimensions) {
        return firstIndex(null, in, condition, dimensions);
    }

    /**
     * @see #firstIndex(String, SDVariable, Condition, boolean, int...)
     */
    public SDVariable firstIndex(SDVariable in, Condition condition, boolean keepDims, int... dimensions){
        return firstIndex(null, in, condition, keepDims, dimensions);
    }

    /**
     * First index reduction operation.<br>
     * Returns a variable that contains the index of the first element that matches the specified condition (for each
     * slice along the specified dimensions)
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param condition  Condition to check on input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable firstIndex(String name, SDVariable in, Condition condition, int... dimensions) {
        return firstIndex(name, in, condition, false, dimensions);
    }

    /**
     * First index reduction operation.<br>
     * Returns a variable that contains the index of the first element that matches the specified condition (for each
     * slice along the specified dimensions)<br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param condition  Condition to check on input variable
     * @param keepDims   If true: keep the dimensions that are reduced on (as length 1). False: remove the reduction dimensions
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable firstIndex(String name, SDVariable in, Condition condition, boolean keepDims, int... dimensions) {
        SDVariable ret = f().firstIndex(in, condition, keepDims, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #lastIndex(String, SDVariable, Condition, int...)
     */
    public SDVariable lastIndex(SDVariable in, Condition condition, int... dimensions) {
        return lastIndex(null, in, condition, dimensions);
    }

    /**
     * @see #lastIndex(String, SDVariable, Condition, boolean, int...)
     */
    public SDVariable lastIndex(SDVariable in, Condition condition, boolean keepDims, int... dimensions){
        return lastIndex(null, in, condition, keepDims, dimensions);
    }

    /**
     * Last index reduction operation.<br>
     * Returns a variable that contains the index of the last element that matches the specified condition (for each
     * slice along the specified dimensions)
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param condition  Condition to check on input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable lastIndex(String name, SDVariable in, Condition condition, int... dimensions) {
        return lastIndex(name, in, condition, false, dimensions);
    }

    /**
     * Last index reduction operation.<br>
     * Returns a variable that contains the index of the last element that matches the specified condition (for each
     * slice along the specified dimensions)<br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param condition  Condition to check on input variable
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Reduced array of rank (input rank - num dimensions)
     */
    public SDVariable lastIndex(String name, SDVariable in, Condition condition, boolean keepDims, int... dimensions){
        SDVariable ret = f().lastIndex(in, condition, keepDims, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Returns a count of the number of elements that satisfy the condition
     * @param in        Input
     * @param condition Condition
     * @return          Number of elements that the condition is satisfied for
     */
    public SDVariable matchConditionCount(SDVariable in, Condition condition) {
        return matchConditionCount(null, in, condition);
    }

    /**
     * Returns a count of the number of elements that satisfy the condition
     * @param name      Name of the output variable
     * @param in        Input
     * @param condition Condition
     * @return          Number of elements that the condition is satisfied for
     */
    public SDVariable matchConditionCount(String name, SDVariable in, Condition condition) {
        return matchConditionCount(name, in, condition, false);
    }

    /**
     * Returns a count of the number of elements that satisfy the condition (for each slice along the specified dimensions)<br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name       Name of the output variable
     * @param in         Input variable
     * @param condition  Condition
     * @param keepDim    If true: keep the dimensions that are reduced on (as size 1). False: remove the reduction dimensions
     * @param dimensions Dimensions to reduce over. If dimensions are not specified, full array reduction is performed
     * @return Number of elements that the condition is satisfied for
     */
    public SDVariable matchConditionCount(String name, SDVariable in, Condition condition, boolean keepDim, int... dimensions) {
        SDVariable ret = f().matchConditionCount(in, condition, keepDim, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Returns a boolean mask of equal shape to the input, where the condition is satisfied - value 1 where satisfied, 0 otherwise
     *
     * @param in        Input variable
     * @param condition Condition
     * @return Boolean mask mariable
     */
    public SDVariable matchCondition(SDVariable in, Condition condition) {
        return matchCondition(null, in, condition);
    }

    /**
     * Returns a boolean mask of equal shape to the input, where the condition is satisfied - value 1 where satisfied, 0 otherwise
     *
     * @param in        Input
     * @param condition Condition
     * @return Boolean mask
     */
    public SDVariable matchCondition(String name, SDVariable in, Condition condition){
        SDVariable ret = f().matchCondition(in, condition);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #cumsum(String, SDVariable, boolean, boolean, int...)
     */
    public SDVariable cumsum(SDVariable in, boolean exclusive, boolean reverse, int... axis) {
        return cumsum(null, in, exclusive, reverse, axis);
    }

    /**
     * Cumulative sum operation.<br>
     * For input: [ a, b, c], output is:<br>
     * exclusize=false, reverse=false: [a, a+b, a+b+c]<br>
     * exclusive=true, reverse=false, [0, a, a+b]<br>
     * exclusive=false, reverse=true: [a+b+c, b+c, c]<br>
     * exclusive=true, reverse=true: [b+c, c, 0]<br><br>
     *
     * @param name      Name of the output variable
     * @param in        Input variable
     * @param axis      Scalar axis argument for dimension to perform cumululative sum operations along
     * @param exclusive If true: exclude the first value
     * @param reverse   If true: reverse the direction of the accumulation
     * @return Output variable
     */
    public SDVariable cumsum(String name, SDVariable in, boolean exclusive, boolean reverse, int... axis) {
        SDVariable ret = f().cumsum(in, exclusive, reverse, axis);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #cumprod(String, SDVariable, boolean, boolean, int...)
     */
    public SDVariable cumprod(SDVariable in, boolean exclusive, boolean reverse, int... axis) {
        return cumprod(null, in, exclusive, reverse, axis);
    }

    /**
     * Cumulative product operation.<br>
     * For input: [ a, b, c], output is:<br>
     * exclusize=false, reverse=false: [a, a*b, a*b*c]<br>
     * exclusive=true, reverse=false, [0, a, a*b]<br>
     * exclusive=false, reverse=true: [a*b*c, b*c, c]<br>
     * exclusive=true, reverse=true: [b*c, c, 0]<br><br>
     *
     * @param name      Name of the output variable
     * @param in        Input variable
     * @param axis      Scalar axis argument for dimension to perform cumululative sum operations along
     * @param exclusive If true: exclude the first value
     * @param reverse   If true: reverse the direction of the accumulation
     * @return Output variable
     */
    public SDVariable cumprod(String name, SDVariable in, boolean exclusive, boolean reverse, int... axis) {
        SDVariable ret = f().cumprod(in, exclusive, reverse, axis);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #biasAdd(String, SDVariable, SDVariable)
     */
    public SDVariable biasAdd(SDVariable input, SDVariable bias) {
        return biasAdd(null, input, bias);
    }

    /**
     * Bias addition operation: a special case of addition, typically used with CNN 4D activations and a 1D bias vector
     * @param name  Name of the output variable
     * @param input 4d input variable
     * @param bias  1d bias
     * @return Output variable
     */
    public SDVariable biasAdd(String name, SDVariable input, SDVariable bias) {
        SDVariable ret = f().biasAdd(input, bias);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Reshape the input variable to the specified (fixed) shape. The output variable will have the same values as the
     * input, but with the specified shape.<br>
     * Note that prod(shape) must match length(input) == prod(input.shape)
     *
     * @param x    Input variable
     * @param shape New shape for variable
     * @return Output variable
     * @see #reshape(SDVariable, SDVariable)
     */
    public SDVariable reshape(SDVariable x, long... shape) {
        return reshape(null, x, shape);
    }

    /**
     * Reshape the input variable to the specified (fixed) shape. The output variable will have the same values as the
     * input, but with the specified shape.<br>
     * Note that prod(shape) must match length(input) == prod(input.shape)
     *
     * @param name  Output variable name
     * @param x    Input variable
     * @param shape New shape for variable
     * @return Output variable
     * @see #reshape(SDVariable, SDVariable)
     */
    public SDVariable reshape(String name, SDVariable x, long... shape) {
        SDVariable result = functionFactory .reshape(x, shape);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Reshape the input variable to the specified (fixed) shape. The output variable will have the same values as the
     * input, but with the specified shape.<br>
     * Note that prod(shape) must match length(input) == prod(input.shape)
     *
     * @param x    Input variable
     * @param shape New shape for variable
     * @return Output variable
     * @see #reshape(SDVariable, SDVariable)
     */
    public SDVariable reshape(SDVariable x, int... shape) {
        return reshape(null, x, shape);
    }

    /**
     * Reshape the input variable to the specified (fixed) shape. The output variable will have the same values as the
     * input, but with the specified shape.<br>
     * Note that prod(shape) must match length(input) == prod(input.shape)
     *
     * @param name  Output variable name
     * @param x    Input variable
     * @param shape New shape for variable
     * @return Output variable
     * @see #reshape(SDVariable, SDVariable)
     */
    public SDVariable reshape(String name, SDVariable x, int... shape) {
        SDVariable result = functionFactory .reshape(x, shape);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Reshape the input variable to the specified (dynamic) shape. The output variable will have the same values as the
     * input, but with the specified shape.<br>
     * Note that prod(shape) must match length(input) == prod(input.shape)
     *
     * @param x    Input variable
     * @param shape New shape for variable
     * @return Output variable
     * @see #reshape(SDVariable, int[])
     */
    public SDVariable reshape(SDVariable x, SDVariable shape) {
        return reshape(null, x, shape);
    }

    /**
     * Reshape the input variable to the specified (dynamic) shape. The output variable will have the same values as the
     * input, but with the specified shape.<br>
     * Note that prod(shape) must match length(input) == prod(input.shape)
     *
     * @param name  Output variable name
     * @param x    Input variable
     * @param shape New shape for variable
     * @return Output variable
     * @see #reshape(SDVariable, int[])
     */
    public SDVariable reshape(String name, SDVariable x,SDVariable shape) {
        SDVariable result = functionFactory.reshape(x, shape);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @see #reverse(String, SDVariable, int...)
     */
    public SDVariable reverse(SDVariable x, int... dimensions) {
        return reverse(null, x, dimensions);
    }

    /**
     * Reverse the values of an array for the specified dimensions<br>
     * If input is:<br>
     * [ 1, 2, 3]<br>
     * [ 4, 5, 6]<br>
     * then<br>
     * reverse(in, 0):<br>
     * [3, 2, 1]<br>
     * [6, 5, 4]<br>
     * reverse(in, 0):<br>
     * [4, 5, 6]<br>
     * [1, 2 3]<br>
     *
     * @param x          Input variable
     * @param dimensions Dimensions
     * @return Output variable
     */
    public SDVariable reverse(String name, SDVariable x, int... dimensions) {
        SDVariable ret = f().reverse(x, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Reverse sequence op: for each slice along dimension seqDimension, the first seqLength values are reversed
     *
     * @param name        Name of the output variable
     * @param x           Input variable
     * @param seq_lengths Length of the sequences
     * @param seqDim      Sequence dimension
     * @param batchDim    Batch dimension
     * @return Reversed sequences
     */
    public SDVariable reverseSequence(String name, SDVariable x, SDVariable seq_lengths, int seqDim, int batchDim) {
        SDVariable ret = f().reverseSequence(x, seq_lengths, seqDim, batchDim);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #reverseSequence(String, SDVariable, SDVariable, int, int)
     */
    public SDVariable reverseSequence(String name, SDVariable x, SDVariable seq_lengths) {
        SDVariable ret = f().reverseSequence(x, seq_lengths);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #reverseSequence(String, SDVariable, SDVariable, int, int)
     */
    public SDVariable reverseSequence(SDVariable x, SDVariable seq_lengths, int seqDim, int batchDim) {
        return reverseSequence(null, x, seq_lengths, seqDim, batchDim);
    }

    /**
     * @see #reverseSequence(String, SDVariable, SDVariable, int, int)
     */
    public SDVariable reverseSequence(SDVariable x, SDVariable seq_lengths) {
        return reverseSequence(null, x, seq_lengths);
    }

    /**
     * Generate a sequence mask (with values 0 or 1) based on the specified lengths<br>
     * Specifically, out[i, ..., k, j] = (j < lengths[i, ..., k] ? 1.0 : 0.0)
     *
     * @param name    Name of the output variable
     * @param lengths Lengths of the sequences
     * @param maxLen  Maximum sequence length
     * @return Output variable
     */
    public SDVariable sequenceMask(String name, SDVariable lengths, SDVariable maxLen) {
        SDVariable ret = f().sequenceMask(lengths, maxLen);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #sequenceMask(String, SDVariable, SDVariable)
     */
    public SDVariable sequenceMask(SDVariable lengths, SDVariable maxLen) {
        return sequenceMask(null, lengths, maxLen);
    }

    /**
     * @see #sequenceMask(String, SDVariable, SDVariable)
     */
    public SDVariable sequenceMask(String name, SDVariable lengths, int maxLen) {
        SDVariable ret = f().sequenceMask(lengths, maxLen);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #sequenceMask(String, SDVariable, SDVariable)
     */
    public SDVariable sequenceMask(SDVariable lengths, int maxLen) {
        return sequenceMask(null, lengths, maxLen);
    }

    /**
     * @see #sequenceMask(String, SDVariable, SDVariable)
     */
    public SDVariable sequenceMask(String name, SDVariable lengths) {
        SDVariable ret = f().sequenceMask(lengths);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #sequenceMask(String, SDVariable, SDVariable)
     */
    public SDVariable sequenceMask(SDVariable lengths) {
        SDVariable ret = f().sequenceMask(lengths);
        return updateVariableNameAndReference(ret, null);
    }

    /**
     * @see #expandDims(String, SDVariable, int)
     */
    public SDVariable expandDims(SDVariable x, int axis) {
        return expandDims(null, x, axis);
    }

    /**
     * Reshape the input by adding a 1 at the specified location.<br>
     * For example, if input has shape [a, b], then output shape is:<br>
     * axis = 0: [1, a, b]<br>
     * axis = 1: [a, 1, b]<br>
     * axis = 2: [a, b, 1]<br>
     *
     * @param name Name of the output variable
     * @param x   Input variable
     * @param axis Axis to expand
     * @return Output variable
     * @see #squeeze(String, SDVariable, int)
     */
    public SDVariable expandDims(String name, SDVariable x, int axis) {
        SDVariable result = f().expandDims(x, axis);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @see #squeeze(String, SDVariable, int)
     */
    public SDVariable squeeze(SDVariable x, int axis) {
        return squeeze(null, x, axis);
    }

    /**
     * Remove a single dimension of size 1.
     * For example, if input has shape [a,b,1,c] then squeeze(input, 2) returns an array of shape [a,b,c]
     *
     * @param name Name of the output variable
     * @param x   Input variable
     * @param axis Size 1 dimension to remove
     * @return Output variable
     */
    public SDVariable squeeze(String name, SDVariable x, int axis) {
        SDVariable result = f().squeeze(x, axis);
        return updateVariableNameAndReference(result, name);
    }


    /**
     * Assign/copy op: out = x.assign(y). Supports broadcasting
     *
     * @param x Input variable x
     * @param y Input variable y
     * @return Output variable
     */
    public SDVariable assign(SDVariable x, SDVariable y) {
        return assign(null, x, y);
    }

    /**
     * Assign/copy op: out = x.assign(y). Supports broadcasting
     *
     * @param name Name of the output variable
     * @param x    Input variable x
     * @param y    Input variable y
     * @return Output variable
     */
    public SDVariable assign(String name, SDVariable x, SDVariable y) {
        SDVariable ret = f().assign(x, y);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Return an array with equal shape to the input, but all elements set to 'value'
     *
     * @param in    Input variable
     * @param value Value to set
     * @return Output variable
     */
    public SDVariable assign(SDVariable in, Number value) {
        return assign(null, in, value);
    }

    /**
     * Return an array with equal shape to the input, but all elements set to 'value'
     *
     * @param name Name of the output variable
     * @param in    Input variable
     * @param value Value to set
     * @return Output variable
     */
    public SDVariable assign(String name, SDVariable in, Number value) {
        SDVariable ret = f().assign(in, value);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Matrix transpose operation: If input has shape [a,b] output has shape [b,a]
     *
     * @param x Input variable
     * @return Output variable (transposed input)
     */
    public SDVariable transpose(SDVariable x) {
        return transpose(null, x);
    }

    /**
     * Matrix transpose operation: If input has shape [a,b] output has shape [b,a]
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable (transposed input)
     */
    public SDVariable transpose(String name, SDVariable x) {
        SDVariable result = functionFactory.transpose(x);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Array permutation operation: permute the dimensions according to the specified permutation indices.<br>
     * Example: if input has shape [a,b,c] and dimensions = [2,0,1] the output has shape [c,a,b]
     *
     * @param x Input variable
     * @return Output variable (permuted input)
     */
    public SDVariable permute(SDVariable x, int... dimensions) {
        return permute(null, x, dimensions);
    }

    /**
     * Array permutation operation: permute the dimensions according to the specified permutation indices.<br>
     * Example: if input has shape [a,b,c] and dimensions = [2,0,1] the output has shape [c,a,b]
     *
     * @param name Output variable name
     * @param x   Input variable
     * @return Output variable (permuted input)
     */
    public SDVariable permute(String name, SDVariable x, int... dimensions) {
        SDVariable result = functionFactory.permute(x, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @see #concat(String, int, SDVariable...)
     */
    public SDVariable concat(int dimension, SDVariable... inputs) {
        return concat(null, dimension, inputs);
    }

    /**
     * Concatenate a set of inputs along the specified dimension.<br>
     * Note that inputs must have identical rank and identical dimensions, other than the dimension to stack on.<br>
     * For example, if 2 inputs have shape [a, x, c] and [a, y, c] and dimension = 1, then the output has shape [a, x+y, c]
     *
     * @param name      Name of the output variable
     * @param dimension Dimension to concatenate on
     * @param inputs    Input variables
     * @return Output variable
     * @see #stack(String, int, SDVariable...)
     */
    public SDVariable concat(String name, int dimension, SDVariable... inputs) {
        SDVariable result = functionFactory.concat(dimension, inputs);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @see #moments(String[], SDVariable, int...)
     */
    public SDVariable[] moments(SDVariable input, int... axes) {
        return moments(null, input, axes);
    }

    /**
     * Calculate the mean and (population) variance for the input variable, for the specified axis
     *
     * @param name  Name of the output variables. Can be null; if non-null, must be length 2
     * @param input Input to calculate moments for
     * @param axes  Dimensions to perform calculation over
     * @return Mean and variance variables
     */
    public SDVariable[] moments(String[] name, SDVariable input, int... axes) {
        SDVariable[] res = f().moments(input, axes);
        return updateVariableNamesAndReferences(res, name);
    }

    /**
     * @see #normalizeMoments(String[], SDVariable, SDVariable, SDVariable, double)
     */
    public SDVariable[] normalizeMoments(SDVariable counts, SDVariable means, SDVariable variances, double shift) {
        return normalizeMoments(null, counts, means, variances, shift);
    }

    /**
     * Calculate the mean and variance from the sufficient statistics
     *
     * @param name      Name of the output variables. Can be null; if non-null, must be length 2
     * @param counts    Rank 0 (scalar) value with the total number of values used to calculate the sufficient statistics
     * @param means     Mean-value sufficient statistics: this is the SUM of all data values
     * @param variances Variaance sufficient statistics: this is the squared sum of all data values
     * @param shift     Shift value, possibly 0, used when calculating the sufficient statistics (for numerical stability)
     * @return Output variables: mean and population variance
     */
    public SDVariable[] normalizeMoments(String[] name, SDVariable counts, SDVariable means, SDVariable variances,
                                         double shift) {
        SDVariable[] res = f().normalizeMoments(counts, means, variances, shift);
        return updateVariableNamesAndReferences(res, name);
    }

    /**
     * @see #matrixDeterminant(String, SDVariable)
     */
    public SDVariable matrixDeterminant(SDVariable in){
        return matrixDeterminant(null, in);
    }

    /**
     * Matrix determinant op. For 2D input, this returns the standard matrix determinant.
     * For higher dimensional input with shape [..., m, m] the matrix determinant is returned for each
     * shape [m,m] sub-matrix.
     * @param name Name of the output variable
     * @param in   Input
     * @return Matrix determinant variable
     */
    public SDVariable matrixDeterminant(String name, SDVariable in){
        SDVariable ret = f().matrixDeterminant(in);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #matrixInverse(String, SDVariable)
     */
    public SDVariable matrixInverse(SDVariable in){
        return matrixInverse(null, in);
    }

    /**
     * Matrix inverse op. For 2D input, this returns the standard matrix inverse.
     * For higher dimensional input with shape [..., m, m] the matrix inverse is returned for each
     * shape [m,m] sub-matrix.
     * @param name Name of the output variable
     * @param in   Input
     * @return Matrix inverse variable
     */
    public SDVariable matrixInverse(String name, SDVariable in){
        SDVariable ret = f().matrixInverse(in);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #confusionMatrix(String, SDVariable, SDVariable)
     */
    public SDVariable confusionMatrix(SDVariable labels, SDVariable predictions) {
        return confusionMatrix((String) null, labels, predictions);
    }

    /**
     * Compute the 2d confusion matrix of size [numClasses, numClasses] from a pair of labels and predictions, both of
     * which are represented as integer values. This version assumes the number of classes is 1 + max(max(labels), max(pred))<br>
     * For example, if labels = [0, 1, 1] and predicted = [0, 2, 1] then output is:<br>
     * [1, 0, 0]<br>
     * [0, 1, 1]<br>
     * [0, 0, 0]<br>
     *
     * @param name   Name of the output variable
     * @param labels Labels - 1D array of integer values representing label values
     * @param pred   Predictions - 1D array of integer values representing predictions. Same length as labels
     * @return Output variable (2D, shape [numClasses, numClasses})
     */
    public SDVariable confusionMatrix(String name, SDVariable labels, SDVariable pred) {
        SDVariable result = f().confusionMatrix(labels, pred);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @see #confusionMatrix(String, SDVariable, SDVariable, Integer)
     */
    public SDVariable confusionMatrix(SDVariable labels, SDVariable pred, Integer numClasses) {
        return confusionMatrix(null, labels, pred, numClasses);
    }

    /**
     * Compute the 2d confusion matrix of size [numClasses, numClasses] from a pair of labels and predictions, both of
     * which are represented as integer values.<br>
     * For example, if labels = [0, 1, 1], predicted = [0, 2, 1], and numClasses=4 then output is:<br>
     * [1, 0, 0, 0]<br>
     * [0, 1, 1, 0]<br>
     * [0, 0, 0, 0]<br>
     * [0, 0, 0, 0]<br>
     *
     * @param name       Name of the output variable
     * @param labels     Labels - 1D array of integer values representing label values
     * @param pred       Predictions - 1D array of integer values representing predictions. Same length as labels
     * @param numClasses Number of classes
     * @return Output variable (2D, shape [numClasses, numClasses})
     */
    public SDVariable confusionMatrix(String name, SDVariable labels, SDVariable pred, Integer numClasses) {
        SDVariable result = f().confusionMatrix(labels, pred, numClasses);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @see #confusionMatrix(String, SDVariable, SDVariable, SDVariable)
     */
    public SDVariable confusionMatrix(SDVariable labels, SDVariable pred, SDVariable weights) {
        return confusionMatrix(null, labels, pred, weights);
    }

    /**
     * Compute the 2d confusion matrix of size [numClasses, numClasses] from a pair of labels and predictions, both of
     * which are represented as integer values. This version assumes the number of classes is 1 + max(max(labels), max(pred))<br>
     * For example, if labels = [0, 1, 1], predicted = [0, 2, 1] and weights = [1, 2, 3]
     * [1, 0, 0]<br>
     * [0, 3, 2]<br>
     * [0, 0, 0]<br>
     *
     * @param name    Name of the output variable
     * @param labels  Labels - 1D array of integer values representing label values
     * @param pred    Predictions - 1D array of integer values representing predictions. Same length as labels
     * @param weights Weights - 1D array of values (may be real/decimal) representing the weight/contribution of
     *                each prediction. Must be same length as both labels and predictions arrays
     * @return Output variable (2D, shape [numClasses, numClasses})
     */
    public SDVariable confusionMatrix(String name, SDVariable labels, SDVariable pred, SDVariable weights) {
        SDVariable result = f().confusionMatrix(labels, pred, weights);
        return updateVariableNameAndReference(result, name);
    }


    /**
     * @see #confusionMatrix(String, SDVariable, SDVariable, Integer, SDVariable)
     */
    public SDVariable confusionMatrix(SDVariable labels, SDVariable pred, Integer numClasses, SDVariable weights) {
        return confusionMatrix(null, labels, pred, numClasses, weights);
    }

    /**
     * Compute the 2d confusion matrix of size [numClasses, numClasses] from a pair of labels and predictions, both of
     * which are represented as integer values.<br>
     * For example, if labels = [0, 1, 1], predicted = [0, 2, 1], numClasses = 4, and weights = [1, 2, 3]
     * [1, 0, 0, 0]<br>
     * [0, 3, 2, 0]<br>
     * [0, 0, 0, 0]<br>
     * [0, 0, 0, 0]<br>
     *
     * @param name    Name of the output variable
     * @param labels  Labels - 1D array of integer values representing label values
     * @param pred    Predictions - 1D array of integer values representing predictions. Same length as labels
     * @param weights Weights - 1D array of values (may be real/decimal) representing the weight/contribution of
     *                each prediction. Must be same length as both labels and predictions arrays
     * @return Output variable (2D, shape [numClasses, numClasses})
     */
    public SDVariable confusionMatrix(String name, SDVariable labels, SDVariable pred, Integer numClasses, SDVariable weights) {
        SDVariable result = f().confusionMatrix(labels, pred, numClasses, weights);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @see #tile(String, SDVariable, int[])
     */
    public SDVariable tile(SDVariable x, int[] repeat) {
        return tile(null, x, repeat);
    }

    /**
     * Repeat (tile) the input tensor the specified number of times.<br>
     * For example, if input is<br>
     * [1, 2]<br>
     * [3, 4]<br>
     * and repeat is [2, 3]<br>
     * then output is<br>
     * [1, 2, 1, 2, 1, 2]<br>
     * [3, 4, 3, 4, 3, 4]<br>
     * [1, 2, 1, 2, 1, 2]<br>
     * [3, 4, 3, 4, 3, 4]<br>
     * <br>
     *
     * @param name   Output variable name
     * @param x     Input variable
     * @param repeat Number of times to repeat in each axis. Must have length equal to the rank of the input array
     * @return Output variable
     */
    public SDVariable tile(String name, SDVariable x, int[] repeat) {
        SDVariable result = functionFactory.tile(x, repeat);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Generate an output variable with the specified (dynamic) shape with all elements set to the specified value
     *
     * @param shape Shape: must be a 1D array/variable
     * @param value Value to set all elements to
     * @return Output variable
     */
    public SDVariable fill(SDVariable shape, org.nd4j.linalg.api.buffer.DataType dataType, double value) {
        return fill(null, shape, dataType, value);
    }

    /**
     * Generate an output variable with the specified (dynamic) shape with all elements set to the specified value
     *
     * @param name  Name of the output variable
     * @param shape Shape: must be a 1D array/variable
     * @param value Value to set all elements to
     * @return Output variable
     */
    public SDVariable fill(String name, SDVariable shape, org.nd4j.linalg.api.buffer.DataType dataType, double value) {
        SDVariable result = functionFactory.fill(shape, dataType, value);
        return updateVariableNameAndReference(result, name);
    }


    /**
     *
     * @param input                  Input
     * @param inputRetainProbability Probability of retaining an input (set to 0 with probability 1-p)
     * @return
     */
    public SDVariable dropout(SDVariable input, double inputRetainProbability) {
        return dropout(null, input, inputRetainProbability);
    }

    /**
     *
     * @param input                  Input
     * @param inputRetainProbability Probability of retaining an input (set to 0 with probability 1-p)
     * @return
     */
    public SDVariable dropout(String name, SDVariable input, double inputRetainProbability) {
        SDVariable res = f().dropout(input, inputRetainProbability);
        return updateVariableNameAndReference(res, name);
    }

    /**
     * @see #linear(String, SDVariable, SDVariable, SDVariable)
     */
    public SDVariable linear(SDVariable input, SDVariable weights, SDVariable bias) {
        return linear(null, input, weights, bias);
    }

    /**
     * Linear layer operation: out = mmul(in,w) + bias<br>
     * Note that bias array is optional
     *
     * @param name    Name of the output variable
     * @param input   Input data
     * @param weights Weights variable
     * @param bias    Optional bias variable (may be null)
     * @return Output variable
     */
    public SDVariable linear(String name, SDVariable input, SDVariable weights, SDVariable bias) {
        SDVariable res = f().xwPlusB(input, weights, bias);
        return updateVariableNameAndReference(res, name);
    }

    /**
     * @see #reluLayer(String, SDVariable, SDVariable, SDVariable)
     */
    public SDVariable reluLayer(SDVariable input, SDVariable weights, SDVariable bias) {
        return reluLayer(null, input, weights, bias);
    }

    /**
     * ReLU (Rectified Linear Unit) layer operation: out = relu(mmul(in,w) + bias)<br>
     * Note that bias array is optional
     *
     * @param name    Name of the output variable
     * @param input   Input data
     * @param weights Weights variable
     * @param bias    Optional bias variable (may be null)
     * @return Output variable
     */
    public SDVariable reluLayer(String name, SDVariable input, SDVariable weights, SDVariable bias) {
        SDVariable res = f().reluLayer(input, weights, bias);
        return updateVariableNameAndReference(res, name);
    }

    /**
     * Matrix multiplication: out = mmul(x,y)<br>
     * Supports specifying a {@link MMulTranspose} argument to perform operation such as mmul(a^T, b), etc.
     *
     * @param x         First input variable
     * @param y         Second input variable
     * @param transpose Transpose arguments
     * @return Output variable
     */
    public SDVariable mmul(SDVariable x, SDVariable y, MMulTranspose transpose) {
        return mmul(null, x, y, transpose);

    }

    /**
     * Matrix multiplication: out = mmul(x,y)
     *
     * @param x First input variable
     * @param y Second input variable
     * @return Output variable
     */
    public SDVariable mmul(SDVariable x, SDVariable y) {
        return mmul(null, x, y);
    }

    /**
     * Matrix multiplication: out = mmul(x,y)<br>
     * Supports specifying a {@link MMulTranspose} argument to perform operation such as mmul(a^T, b), etc.
     *
     * @param name      Output variable name
     * @param x         First input variable
     * @param y         Second input variable
     * @param transpose Transpose arguments
     * @return Output variable
     */
    public SDVariable mmul(String name, SDVariable x, SDVariable y, MMulTranspose transpose) {
        SDVariable result = functionFactory.mmul(x, y, transpose);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Matrix multiplication: out = mmul(x,y)
     *
     * @param name Output variable name
     * @param x    First input variable
     * @param y    Second input variable
     * @return Output variable
     */
    public SDVariable mmul(String name, SDVariable x, SDVariable y) {
        return mmul(name, x, y, MMulTranspose.allFalse());
    }

    /**
     * Matrix multiply a batch of matrices. matricesA and matricesB have to be arrays of same
     * length and each pair taken from these sets has to have dimensions (M, N) and (N, K),
     * respectively. If transposeA is true, matrices from matricesA will have shape (N, M) instead.
     * Likewise, if transposeB is true, matrices from matricesB will have shape (K, N).
     *
     *
     * The result of this operation will be a batch of multiplied matrices. The
     * result has the same length as both input batches and each output matrix is of shape (M, K).
     *
     * @param matricesA First array of input matrices, all of shape (M, N) or (N, M)
     * @param matricesB Second array of input matrices, all of shape (N, K) or (K, N)
     * @param transposeA whether first batch of matrices is transposed.
     * @param transposeB whether second batch of matrices is transposed.
     * @param names names for all provided SDVariables
     *
     * @return Array of multiplied SDVariables of shape (M, K)
     */
    public SDVariable[] batchMmul(String[] names, SDVariable[] matricesA, SDVariable[] matricesB,
                                  boolean transposeA, boolean transposeB) {
        SDVariable[] result = functionFactory.batchMmul(matricesA, matricesB, transposeA, transposeB);
        return updateVariableNamesAndReferences(result, names);
    }


    /**
     * Matrix multiply a batch of matrices. matricesA and matricesB have to be arrays of same
     * length and each pair taken from these sets has to have dimensions (M, N) and (N, K),
     * respectively. If transposeA is true, matrices from matricesA will have shape (N, M) instead.
     * Likewise, if transposeB is true, matrices from matricesB will have shape (K, N).
     *
     *
     * The result of this operation will be a batch of multiplied matrices. The
     * result has the same length as both input batches and each output matrix is of shape (M, K).
     *
     * @param matricesA First array of input matrices, all of shape (M, N) or (N, M)
     * @param matricesB Second array of input matrices, all of shape (N, K) or (K, N)
     * @param transposeA whether first batch of matrices is transposed.
     * @param transposeB whether second batch of matrices is transposed.
     *
     * @return Array of multiplied SDVariables of shape (M, K)
     */
    public SDVariable[] batchMmul(SDVariable[] matricesA, SDVariable[] matricesB,
                                  boolean transposeA, boolean transposeB) {
        return batchMmul(null, matricesA, matricesB, transposeA, transposeB);
    }

    /**
     * Matrix multiply a batch of matrices. matricesA and matricesB have to be arrays of same
     * length and each pair taken from these sets has to have dimensions (M, N) and (N, K),
     * respectively. The result of this operation will be a batch of multiplied matrices. The
     * result has the same length as both input batches and each output matrix is of shape (M, K).
     *
     * @param matricesA First array of input matrices, all of shape (M, N)
     * @param matricesB Second array of input matrices, all of shape (N, K)
     * @return Array of multiplied SDVariables of shape (M, K)
     */
    public SDVariable[] batchMmul(SDVariable[] matricesA, SDVariable[] matricesB) {
        return batchMmul(null, matricesA, matricesB, false, false);
    }


    /**
     * @param x
     * @param y
     * @param dimensions
     * @return
     */
    public SDVariable tensorMmul(SDVariable x,
                                 SDVariable y,
                                 int[][] dimensions) {
        return tensorMmul(null, x, y, dimensions);
    }


    /**
     * TODO doc string
     *
     * @param x
     * @param y
     * @param dimensions
     * @return
     */
    public SDVariable dot(SDVariable x, SDVariable y, int... dimensions) {
        return dot(null, x, y, dimensions);
    }

    /**
     * TODO doc string
     *
     * @param name
     * @param x
     * @param y
     * @param dimensions
     * @return
     */
    public SDVariable dot(String name, SDVariable x, SDVariable y, int... dimensions) {
        SDVariable ret = f().dot(x, y, dimensions);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * Norm1 (L1 norm) reduction operation: The output contains the L1 norm for each tensor/subset along the specified dimensions:<br>
     * out = sum_i abs(x[i])
     *
     * @param name       Output variable name
     * @param x         Input variable
     * @param dimensions dimensions to reduce over
     * @return Output variable
     */
    public SDVariable norm1(String name, SDVariable x, int... dimensions) {
        return norm1(name, x, false, dimensions);
    }

    /**
     * Norm1 (L1 norm) reduction operation: The output contains the L1 norm for each tensor/subset along the specified dimensions:<br>
     * out = sum_i abs(x[i])<br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name       Output variable name
     * @param x         Input variable
     * @param keepDims   If true: keep the dimensions that are reduced on (as size 1). False: remove the reduction dimensions
     * @param dimensions dimensions to reduce over
     * @return Output variable
     */
    public SDVariable norm1(String name, SDVariable x, boolean keepDims, int... dimensions) {
        SDVariable result = f().norm1(x, keepDims, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Norm2 (L2 norm) reduction operation: The output contains the L2 norm for each tensor/subset along the specified dimensions:<br>
     * out = sqrt(sum_i x[i]^2)
     *
     * @param name       Output variable name
     * @param x         Input variable
     * @param dimensions dimensions to reduce over
     * @return Output variable
     */
    public SDVariable norm2(String name, SDVariable x, int... dimensions) {
        return norm2(name, x, false, dimensions);
    }

    /**
     * Norm2 (L2 norm) reduction operation: The output contains the L2 norm for each tensor/subset along the specified dimensions:<br>
     * out = sqrt(sum_i x[i]^2)<br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name       Output variable name
     * @param x         Input variable
     * @param keepDims   If true: keep the dimensions that are reduced on (as size 1). False: remove the reduction dimensions
     * @param dimensions dimensions to reduce over
     * @return Output variable
     */
    public SDVariable norm2(String name, SDVariable x, boolean keepDims, int... dimensions) {
        SDVariable result = f().norm2(x, keepDims, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Squared L2 norm: see {@link #norm2(String, SDVariable, int...)}
     */
    public SDVariable squaredNorm(SDVariable x, int... dimensions) {
        return squaredNorm(null, x, false, dimensions);
    }

    /**
     * Squared L2 norm: see {@link #norm2(String, SDVariable, int...)}
     */
    public SDVariable squaredNorm(String name, SDVariable x, int... dimensions) {
        return squaredNorm(name, x, false, dimensions);
    }

    /**
     * Squared L2 norm: see {@link #norm2(String, SDVariable, boolean, int...)}
     */
    public SDVariable squaredNorm(SDVariable x, boolean keepDims, int... dimensions) {
        return squaredNorm(null, x, keepDims, dimensions);
    }

    /**
     * Squared L2 norm: see {@link #norm2(String, SDVariable, boolean, int...)}
     */
    public SDVariable squaredNorm(String name, SDVariable x, boolean keepDims, int... dimensions) {
        SDVariable result = f().squaredNorm(x, keepDims, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Max norm (infinity norm) reduction operation: The output contains the max norm for each tensor/subset along the
     * specified dimensions
     *
     * @param name       Output variable name
     * @param x         Input variable
     * @param dimensions dimensions to reduce over
     * @return Output variable
     */
    public SDVariable normmax(String name, SDVariable x, int... dimensions) {
        return normmax(name, x, false, dimensions);
    }

    /**
     * Max norm (infinity norm) reduction operation: The output contains the max norm for each tensor/subset along the
     * specified dimensions:<br>
     * out = max(abs(x[i]))<br>
     * Note that if keepDims = true, the output variable has the same rank as the input variable,
     * with the reduced dimensions having size 1. This can be useful for later broadcast operations (such as subtracting
     * the mean along a dimension).<br>
     * Example: if input has shape [a,b,c] and dimensions=[1] then output has shape:
     * keepDims = true: [a,1,c]<br>
     * keepDims = false: [a,c]
     *
     * @param name       Output variable name
     * @param x         Input variable
     * @param keepDims   If true: keep the dimensions that are reduced on (as size 1). False: remove the reduction dimensions
     * @param dimensions dimensions to reduce over
     * @return Output variable
     */
    public SDVariable normmax(String name, SDVariable x, boolean keepDims, int... dimensions) {
        SDVariable result = f().normmax(x, keepDims, dimensions);
        return updateVariableNameAndReference(result, name);
    }


    /**
     * @see #cosineSimilarity(String, SDVariable, SDVariable, int...)
     */
    public SDVariable cosineSimilarity(SDVariable x, SDVariable y, int... dimensions) {
        return cosineSimilarity(generateNewVarName(CosineSimilarity.OP_NAME, 0), x, y, dimensions);
    }

    /**
     * Cosine similarity pairwise reduction operation. The output contains the cosine similarity for each tensor/subset
     * along the specified dimensions:<br>
     * out = (sum_i x[i] * y[i]) / ( sqrt(sum_i x[i]^2) * sqrt(sum_i y[i]^2)
     *
     * @param x          Input variable x
     * @param y          Input variable y
     * @param dimensions Dimensions to calculate cosine similarity over
     * @return Output variable
     */
    public SDVariable cosineSimilarity(String name, SDVariable x, SDVariable y, int... dimensions) {
        SDVariable cosim = functionFactory.cosineSimilarity(x, y, dimensions);
        return updateVariableNameAndReference(cosim, name);
    }

    /**
     * @see #euclideanDistance(String, SDVariable, SDVariable, int...)
     */
    public SDVariable euclideanDistance(SDVariable x, SDVariable y, int... dimensions) {
        return euclideanDistance(generateNewVarName(EuclideanDistance.OP_NAME, 0), x, y, dimensions);
    }

    /**
     * Euclidean distance (l2 norm, l2 distance) reduction operation. The output contains the Euclidean distance for each
     * tensor/subset along the specified dimensions:<br>
     * out = sqrt( sum_i (x[i] - y[i])^2 )
     *
     * @param x          Input variable x
     * @param y          Input variable y
     * @param dimensions Dimensions to calculate cosine similarity over
     * @return Output variable
     */
    public SDVariable euclideanDistance(String name, SDVariable x, SDVariable y, int... dimensions) {
        SDVariable result = functionFactory.euclideanDistance(x, y, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @see #manhattanDistance(String, SDVariable, SDVariable, int...)
     */
    public SDVariable manhattanDistance(SDVariable x, SDVariable y, int... dimensions) {
        return manhattanDistance(generateNewVarName(ManhattanDistance.OP_NAME, 0), x, y, dimensions);
    }

    /**
     * Manhattan distance (l1 norm, l1 distance) reduction operation. The output contains the Manhattan distance for each
     * tensor/subset along the specified dimensions:<br>
     * out = sum_i abs(x[i]-y[i])
     *
     * @param name       Name of the output variable
     * @param x          Input variable x
     * @param y          Input variable y
     * @param dimensions Dimensions to calculate cosine similarity over
     * @return Output variable
     */
    public SDVariable manhattanDistance(String name, SDVariable x, SDVariable y, int... dimensions) {
        SDVariable result = functionFactory.manhattanDistance(x, y, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @see #cosineDistance(String, SDVariable, SDVariable, int...)
     */
    public SDVariable cosineDistance(SDVariable x, SDVariable y, int... dimensions) {
        return cosineDistance(null, x, y, dimensions);
    }

    /**
     * Cosine distance reduction operation. The output contains the cosine distance for each
     * tensor/subset along the specified dimensions:<br>
     * out = 1.0 - cosineSimilarity(x,y)<br>
     * See {@link #cosineSimilarity(String, SDVariable, SDVariable, int...)}
     *
     * @param name       Name of the output variable
     * @param x          Input variable x
     * @param y          Input variable y
     * @param dimensions Dimensions to calculate cosine similarity over
     * @return Output variable
     */
    public SDVariable cosineDistance(String name, SDVariable x, SDVariable y, int... dimensions) {
        SDVariable result = functionFactory.cosineDistance(x, y, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @see #hammingDistance(String, SDVariable, SDVariable, int...)
     */
    public SDVariable hammingDistance(SDVariable x, SDVariable y, int... dimensions) {
        return hammingDistance(null, x, y, dimensions);
    }

    /**
     * Hamming distance reduction operation. The output contains the cosine distance for each
     * tensor/subset along the specified dimensions:<br>
     * out = count( x[i] != y[i] )
     *
     * @param name       Name of the output variable
     * @param x          Input variable x
     * @param y          Input variable y
     * @param dimensions Dimensions to calculate cosine similarity over
     * @return Output variable
     */
    public SDVariable hammingDistance(String name, SDVariable x, SDVariable y, int... dimensions) {
        SDVariable result = functionFactory.hammingDistance(x, y, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * Jaccard similarity reduction operation. The output contains the Jaccard distance for each
     * tensor along the specified dimensions.
     *
     * @param x          Input variable x
     * @param y          Input variable y
     * @param dimensions Dimensions to calculate Jaccard similarity over
     * @return Output variable
     */
    public SDVariable jaccardDistance(SDVariable x, SDVariable y, int... dimensions) {
        return jaccardDistance(null, x, y, dimensions);
    }

    /**
     * Jaccard similarity reduction operation. The output contains the Jaccard distance for each
     * tensor along the specified dimensions.
     *
     * @param name       Name of the output variable
     * @param x          Input variable x
     * @param y          Input variable y
     * @param dimensions Dimensions to calculate Jaccard similarity over
     * @return Output variable
     */
    public SDVariable jaccardDistance(String name, SDVariable x, SDVariable y, int... dimensions) {
        SDVariable result = functionFactory.jaccardDistance(x, y, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @param x
     * @return
     */
    public SDVariable softmaxDerivative(String name, SDVariable x, SDVariable wrt) {
        return softmaxDerivative(name, x, wrt, null);
    }

    public SDVariable softmaxDerivative(String name, SDVariable x, SDVariable wrt, Integer dimension) {
        SDVariable result = functionFactory.softmaxDerivative(x, wrt, dimension);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * @param x          Input variable x
     * @param y          Input variable y
     * @param dimensions dimensions
     * @return Output variable
     */
    public SDVariable tensorMmul(String name,
                                 SDVariable x,
                                 SDVariable y,
                                 int[][] dimensions) {
        SDVariable result = functionFactory.tensorMmul(x, y, dimensions);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * L2 loss: 1/2 * sum(x^2)
     * @param var  Variable to calculate L2 loss of
     * @return L2 loss
     */
    public SDVariable lossL2(@NonNull SDVariable var){
        return lossL2(null, var);
    }

    /**
     * L2 loss: 1/2 * sum(x^2)
     * @param name Name of the output variable
     * @param var  Variable to calculate L2 loss of
     * @return L2 loss
     */
    public SDVariable lossL2(String name, @NonNull SDVariable var){
        SDVariable ret = f().lossL2(var);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * See {@link #lossAbsoluteDifference(String, SDVariable, SDVariable, SDVariable, LossReduce)}.
     */
    public SDVariable lossAbsoluteDifference(String name, @NonNull SDVariable label, @NonNull SDVariable predictions) {
        return lossAbsoluteDifference(name, label, predictions, null, LossReduce.MEAN_BY_NONZERO_WEIGHT_COUNT);
    }

    /**
     * See {@link #lossAbsoluteDifference(String, SDVariable, SDVariable, SDVariable, LossReduce)}.
     */
    public SDVariable lossAbsoluteDifference(String name, @NonNull SDVariable label, @NonNull SDVariable predictions, @NonNull LossReduce lossReduce) {
        return lossAbsoluteDifference(name, label, predictions, null, lossReduce);
    }

    /**
     * Absolute difference loss: {@code sum_i abs( label[i] - predictions[i] )
     *
     * @param name        Name of the operation
     * @param label       Label array
     * @param predictions Predictions array
     * @param weights     Weights array. May be null. If null, a weight of 1.0 is used
     * @param lossReduce  Reduction type for the loss. See {@link LossReduce} for more details. Default: {@link LossReduce#MEAN_BY_NONZERO_WEIGHT_COUNT}
     * @return Loss variable
     */
    public SDVariable lossAbsoluteDifference(String name, @NonNull SDVariable label, @NonNull SDVariable predictions,
                                             SDVariable weights, @NonNull LossReduce lossReduce) {
        if(weights == null)
            weights = this.scalar(null, 1.0);
        SDVariable result = functionFactory.lossAbsoluteDifference(label, predictions, weights, lossReduce);
        return updateVariableNameAndReference(result, name);
    }


    /**
     * See {@link #lossCosineDistance(String, SDVariable, SDVariable, SDVariable, LossReduce, int)}.
     */
    public SDVariable lossCosineDistance(String name, @NonNull SDVariable label, @NonNull SDVariable predictions, int dimension) {
        return lossCosineDistance(name, label, predictions, null, LossReduce.MEAN_BY_NONZERO_WEIGHT_COUNT, dimension);
    }

    /**
     * See {@link #lossCosineDistance(String, SDVariable, SDVariable, SDVariable, LossReduce, int)}.
     */
    public SDVariable lossCosineDistance(String name, @NonNull SDVariable label, @NonNull SDVariable predictions,
                                         @NonNull LossReduce lossReduce, int dimension) {
        return lossCosineDistance(name, label, predictions, null, lossReduce, dimension);
    }

    /**
     *
     * Cosine distance loss: {@code 1 - cosineSimilarity(x,y)} or {@code 1 - sum_i label[i] * prediction[i]}, which is
     * equivalent to cosine distance when both the predictions and labels are normalized.<br>
     * <b>Note</b>: This loss function assumes that both the predictions and labels are normalized to have unit l2 norm.
     * If this is not the case, you should normalize them first by dividing by {@link #norm2(String, SDVariable, boolean, int...)}
     * along the cosine distance dimension (with keepDims=true).
     *
     * @param name        Name of the operation
     * @param label       Label array
     * @param predictions Predictions array
     * @param weights     Weights array. May be null. If null, a weight of 1.0 is used
     * @param lossReduce  Reduction type for the loss. See {@link LossReduce} for more details. Default: {@link LossReduce#MEAN_BY_NONZERO_WEIGHT_COUNT}
     * @param dimension   Dimension to perform the cosine distance over
     * @return Cosine distance loss variable
     */
    public SDVariable lossCosineDistance(String name, @NonNull SDVariable label, @NonNull SDVariable predictions,
                                         SDVariable weights, @NonNull LossReduce lossReduce, int dimension) {
        if(weights == null)
            weights = this.scalar(null, 1.0);
        SDVariable result = functionFactory.lossCosineDistance(label, predictions, weights, lossReduce, dimension);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * See {@link #lossHinge(String, SDVariable, SDVariable, SDVariable, LossReduce)}.
     */
    public SDVariable lossHinge(String name, @NonNull SDVariable label, @NonNull SDVariable predictions) {
        return lossHinge(name, label, predictions, null, LossReduce.MEAN_BY_NONZERO_WEIGHT_COUNT);
    }

    /**
     * See {@link #lossHinge(String, SDVariable, SDVariable, SDVariable, LossReduce)}.
     */
    public SDVariable lossHinge(String name, @NonNull SDVariable label, @NonNull SDVariable predictions, @NonNull LossReduce lossReduce) {
        return lossHinge(name, label, predictions, null, lossReduce);
    }

    /**
     * Hinge loss: a loss function used for training classifiers.
     * Implements {@code L = max(0, 1 - t * predictions)} where t is the label values after internally converting to {-1,1}
     * from the user specified {0,1}. Note that Labels should be provided with values {0,1}.
     *
     * @param name        Name of the operation
     * @param label       Label array. Each value should be 0.0 or 1.0 (internally -1 to 1 is used)
     * @param predictions Predictions array
     * @param weights     Weights array. May be null. If null, a weight of 1.0 is used
     * @param lossReduce  Reduction type for the loss. See {@link LossReduce} for more details. Default: {@link LossReduce#MEAN_BY_NONZERO_WEIGHT_COUNT}
     * @return Loss variable
     */
    public SDVariable lossHinge(String name, @NonNull SDVariable label, @NonNull SDVariable predictions,
                                SDVariable weights, @NonNull LossReduce lossReduce) {
        if(weights == null)
            weights = this.scalar(null, 1.0);
        SDVariable result = functionFactory.lossHinge(label, predictions, weights, lossReduce);
        return updateVariableNameAndReference(result, name);
    }


    /**
     * See {@link #lossHuber(String, SDVariable, SDVariable, SDVariable, LossReduce, double)}.
     */
    public SDVariable lossHuber(String name, @NonNull SDVariable label, @NonNull SDVariable predictions, double delta) {
        return lossHuber(name, label, predictions, null, LossReduce.MEAN_BY_NONZERO_WEIGHT_COUNT, delta);
    }

    /**
     * See {@link #lossHuber(String, SDVariable, SDVariable, SDVariable, LossReduce, double)}.
     */
    public SDVariable lossHuber(String name, @NonNull SDVariable label, @NonNull SDVariable predictions, @NonNull LossReduce lossReduce, double delta) {
        return lossHuber(name, label, predictions, null, lossReduce, delta);
    }

    /**
     * Huber loss function, used for robust regression. It is similar both squared error loss and absolute difference loss,
     * though is less sensitive to outliers than squared error.<br>
     * Huber loss implements:
     * <pre>
     *{@code L = 0.5 * (label[i] - predictions[i])^2 if abs(label[i] - predictions[i]) < delta
     *  L = delta * abs(label[i] - predictions[i]) - 0.5 * delta^2 otherwise
     *     }
     * </pre>
     *
     * @param name        Name of the operation
     * @param label       Label array
     * @param predictions Predictions array
     * @param weights     Weights array. May be null. If null, a weight of 1.0 is used
     * @param lossReduce  Reduction type for the loss. See {@link LossReduce} for more details. Default: {@link LossReduce#MEAN_BY_NONZERO_WEIGHT_COUNT}
     * @param delta       Loss function delta value
     * @return Huber loss variable
     */
    public SDVariable lossHuber(String name, @NonNull SDVariable label, @NonNull SDVariable predictions,
                                SDVariable weights, @NonNull LossReduce lossReduce, double delta) {
        if(weights == null)
            weights = this.scalar(null, 1.0);
        SDVariable result = functionFactory.lossHuber(label, predictions, weights, lossReduce, delta);
        return updateVariableNameAndReference(result, name);
    }


    /**
     * See {@link #lossLog(String, SDVariable, SDVariable, SDVariable, LossReduce, double)}.
     */
    public SDVariable lossLog(String name, @NonNull SDVariable label, @NonNull SDVariable predictions) {
        return lossLog(name, label, predictions, null, LossReduce.MEAN_BY_NONZERO_WEIGHT_COUNT, LogLoss.DEFAULT_EPSILON);
    }

    /**
     * See {@link #lossLog(String, SDVariable, SDVariable, SDVariable, LossReduce, double)}.
     */
    public SDVariable lossLog(String name, @NonNull SDVariable label, @NonNull SDVariable predictions, @NonNull LossReduce lossReduce) {
        return lossLog(name, label, predictions, null, lossReduce, LogLoss.DEFAULT_EPSILON);
    }

    /**
     * Log loss, i.e., binary cross entropy loss, usually used for binary multi-label classification. Implements:
     * {@code -1/numExamples * sum_i (labels[i] * log(predictions[i] + epsilon) + (1-labels[i]) * log(1-predictions[i] + epsilon))}
     *
     * @param name        Name of the operation
     * @param label       Label array
     * @param predictions Predictions array
     * @param weights     Weights array. May be null. If null, a weight of 1.0 is used
     * @param lossReduce  Reduction type for the loss. See {@link LossReduce} for more details. Default: {@link LossReduce#MEAN_BY_NONZERO_WEIGHT_COUNT}
     * @return Log loss variable
     */
    public SDVariable lossLog(String name, @NonNull SDVariable label, @NonNull SDVariable predictions,
                              SDVariable weights, @NonNull LossReduce lossReduce, double epsilon) {
        if(weights == null)
            weights = this.scalar(null, 1.0);
        SDVariable result = functionFactory.lossLog(label, predictions, weights, lossReduce, epsilon);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * See {@link #lossMeanPairwiseSquaredError(String, SDVariable, SDVariable, SDVariable)}.
     */
    public SDVariable lossMeanPairwiseSquaredError(String name, @NonNull SDVariable label, @NonNull SDVariable predictions) {
        return lossMeanPairwiseSquaredError(name, label, predictions, null);
    }

    /**
     * Mean pairwise squared error.<br>
     * MPWSE loss calculates the difference between pairs of consecutive elements in the predictions and labels arrays.
     * For example, if predictions = [p0, p1, p2] and labels are [l0, l1, l2] then MPWSE is:
     * {@code [((p0-p1) - (l0-l1))^2 + ((p0-p2) - (l0-l2))^2 + ((p1-p2) - (l1-l2))^2] / 3}<br>
     *
     * @param name        Name of the operation
     * @param label       Label array
     * @param predictions Predictions array
     * @param weights     Weights array. May be null. If null, a weight of 1.0 is used. Must be either null, scalar, or have shape [batchSize]
     * @return Loss variable, scalar output
     */
    public SDVariable lossMeanPairwiseSquaredError(String name, @NonNull SDVariable label, @NonNull SDVariable predictions, SDVariable weights) {
        if(weights == null)
            weights = this.scalar(null, 1.0);
        SDVariable result = functionFactory.lossMeanPairwiseSquaredError(label, predictions, weights);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * See {@link #lossMeanSquaredError(String, SDVariable, SDVariable, SDVariable, LossReduce)}.
     */
    public SDVariable lossMeanSquaredError(String name, @NonNull SDVariable label, @NonNull SDVariable predictions) {
        return lossMeanSquaredError(name, label, predictions, null, LossReduce.MEAN_BY_NONZERO_WEIGHT_COUNT);
    }

    /**
     * See {@link #lossMeanSquaredError(String, SDVariable, SDVariable, SDVariable, LossReduce)}.
     */
    public SDVariable lossMeanSquaredError(String name, @NonNull SDVariable label, @NonNull SDVariable predictions, @NonNull LossReduce lossReduce) {
        return lossMeanSquaredError(name, label, predictions, null, lossReduce);
    }

    /**
     * Mean squared error loss function. Implements {@code (label[i] - prediction[i])^2} - i.e., squared error on a per-element basis.
     * When averaged (using {@link LossReduce#MEAN_BY_WEIGHT} or {@link LossReduce#MEAN_BY_NONZERO_WEIGHT_COUNT} (the default))
     * this is the mean squared error loss function.
     * @param name        Name of the operation
     * @param label       Label array
     * @param predictions Predictions array
     * @param weights     Weights array. May be null. If null, a weight of 1.0 is used
     * @param lossReduce  Reduction type for the loss. See {@link LossReduce} for more details. Default: {@link LossReduce#MEAN_BY_NONZERO_WEIGHT_COUNT}
     * @return Loss variable
     */
    public SDVariable lossMeanSquaredError(String name, @NonNull SDVariable label, @NonNull SDVariable predictions,
                                           SDVariable weights, @NonNull LossReduce lossReduce) {
        if(weights == null)
            weights = this.scalar(null, 1.0);
        SDVariable result = functionFactory.lossMeanSquaredError(label, predictions, weights, lossReduce);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * See {@link #lossSigmoidCrossEntropy(String, SDVariable, SDVariable, SDVariable, LossReduce, double)}.
     */
    public SDVariable lossSigmoidCrossEntropy(String name, @NonNull SDVariable label, @NonNull SDVariable predictions) {
        return lossSigmoidCrossEntropy(name, label, predictions, null, LossReduce.MEAN_BY_NONZERO_WEIGHT_COUNT, SigmoidCrossEntropyLoss.DEFAULT_LABEL_SMOOTHING);
    }

    /**
     * See {@link #lossSigmoidCrossEntropy(String, SDVariable, SDVariable, SDVariable, LossReduce, double)}.
     */
    public SDVariable lossSigmoidCrossEntropy(String name, @NonNull SDVariable label, @NonNull SDVariable predictions, @NonNull LossReduce lossReduce) {
        return lossSigmoidCrossEntropy(name, label, predictions, null, lossReduce, SigmoidCrossEntropyLoss.DEFAULT_LABEL_SMOOTHING);
    }

    /**
     * Sigmoid cross entropy: applies the sigmoid activation function on the input logits (input "pre-sigmoid preductions")
     * and implements the binary cross entropy loss function. This implementation is numerically more stable than using
     * standard (but separate) sigmoid activation function and log loss (binary cross entropy) loss function.<br>
     * Implements:
     * {@code -1/numExamples * sum_i (labels[i] * log(sigmoid(logits[i])) + (1-labels[i]) * log(1-sigmoid(logits[i])))}
     * though this is done in a mathematically equivalent but more numerical stable form.<br>
     * <br>
     * When label smoothing is > 0, the following label smoothing is used:<br>
     * <pre>
     * {@code numClasses = labels.size(1);
     * label = (1.0 - labelSmoothing) * label + 0.5 * labelSmoothing}
     * </pre>
     *
     * @param name        Name of the operation
     * @param label       Label array
     * @param predictionLogits Predictions array
     * @param weights     Weights array. May be null. If null, a weight of 1.0 is used
     * @param lossReduce  Reduction type for the loss. See {@link LossReduce} for more details. Default: {@link LossReduce#MEAN_BY_NONZERO_WEIGHT_COUNT}
     * @return Loss variable
     */
    public SDVariable lossSigmoidCrossEntropy(String name, @NonNull SDVariable label, @NonNull SDVariable predictionLogits,
                                              SDVariable weights, @NonNull LossReduce lossReduce, double labelSmoothing) {
        if(weights == null)
            weights = this.scalar(null, 1.0);
        SDVariable result = functionFactory.lossSigmoidCrossEntropy(label, predictionLogits, weights, lossReduce, labelSmoothing);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * See {@link #lossSoftmaxCrossEntropy(String, SDVariable, SDVariable, SDVariable, LossReduce, double)}.
     */
    public SDVariable lossSoftmaxCrossEntropy(String name, @NonNull SDVariable label, @NonNull SDVariable predictions) {
        return lossSoftmaxCrossEntropy(name, label, predictions, null, LossReduce.MEAN_BY_NONZERO_WEIGHT_COUNT, SoftmaxCrossEntropyLoss.DEFAULT_LABEL_SMOOTHING);
    }

    /**
     * See {@link #lossSoftmaxCrossEntropy(String, SDVariable, SDVariable, SDVariable, LossReduce, double)}.
     */
    public SDVariable lossSoftmaxCrossEntropy(String name, @NonNull SDVariable label, @NonNull SDVariable predictions, @NonNull LossReduce lossReduce) {
        return lossSoftmaxCrossEntropy(name, label, predictions, null, lossReduce, SoftmaxCrossEntropyLoss.DEFAULT_LABEL_SMOOTHING);
    }

    /**
     * Applies the softmax activation function to the input, then implement multi-class cross entropy:<br>
     * {@code -sum_classes label[i] * log(p[c])} where {@code p = softmax(logits)}<br>
     * If {@link LossReduce#NONE} is used, returned shape is [numExamples] out for [numExamples, numClasses] predicitons/labels;
     * otherwise, the output is a scalar.<br>
     * <p>
     * When label smoothing is > 0, the following label smoothing is used:<br>
     * <pre>
     * {@code numClasses = labels.size(1);
     * oneHotLabel = (1.0 - labelSmoothing) * oneHotLabels + labelSmoothing/numClasses}
     * </pre>
     *
     * @param name             Name of the operation
     * @param oneHotLabels     Label array. Should be one-hot per example and same shape as predictions (for example, [mb, nOut])
     * @param logitPreductions Predictions array (pre-softmax)
     * @param weights          Weights array. May be null. If null, a weight of 1.0 is used
     * @param lossReduce       Reduction type for the loss. See {@link LossReduce} for more details. Default: {@link LossReduce#MEAN_BY_NONZERO_WEIGHT_COUNT}
     * @param labelSmoothing   Label smoothing value. Default value: 0
     * @return Loss variable
     */
    public SDVariable lossSoftmaxCrossEntropy(String name, @NonNull SDVariable oneHotLabels, @NonNull SDVariable logitPreductions,
                                              SDVariable weights, @NonNull LossReduce lossReduce, double labelSmoothing) {
        if(weights == null)
            weights = this.scalar(null, 1.0);
        SDVariable result = functionFactory.lossSoftmaxCrossEntropy(oneHotLabels, logitPreductions, weights, lossReduce, labelSmoothing);
        return updateVariableNameAndReference(result, name);
    }

    /**
     * See {@link #lossSparseSoftmaxCrossEntropy(String, SDVariable, SDVariable)}
     */
    public SDVariable lossSparseSoftmaxCrossEntropy(@NonNull SDVariable logits, @NonNull SDVariable labels) {
        return lossSparseSoftmaxCrossEntropy(null, logits, labels);
    }

    /**
     * As per {@link #lossSoftmaxCrossEntropy(String, SDVariable, SDVariable, LossReduce)} but the labels variable
     * is represented as an integer array instead of the equivalent one-hot array.<br>
     * i.e., if logits are rank N, then labels have rank N-1
     *
     * @param name       Name of the output variable. May be null
     * @param logits     Logits array ("pre-softmax activations")
     * @param labels     Labels array. Must be an integer type.
     * @return Softmax cross entropy
     */
    public SDVariable lossSparseSoftmaxCrossEntropy(String name, @NonNull SDVariable logits, @NonNull SDVariable labels) {
        Preconditions.checkState(labels.dataType().isIntType(), "Labels variable must be an integer type: got %s", logits);
        SDVariable ret = f().lossSparseSoftmaxCrossEntropy(logits, labels);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * TODO
     *
     * @param targets
     * @param inputs
     * @param weights
     * @return
     */
    public SDVariable weightedCrossEntropyWithLogits(SDVariable targets, SDVariable inputs,
                                                     SDVariable weights) {
        return weightedCrossEntropyWithLogits(null, targets, inputs, weights);
    }

    /**
     * TODO
     *
     * @param name
     * @param targets
     * @param inputs
     * @param weights
     * @return
     */
    public SDVariable weightedCrossEntropyWithLogits(String name, SDVariable targets, SDVariable inputs,
                                                     SDVariable weights) {
        SDVariable res = f().weightedCrossEntropyWithLogits(targets, inputs, weights);
        return updateVariableNameAndReference(res, name);
    }

    /**
     * Add the specified variable to this SameDiff instance
     * @param variable Variable to add
     */
    public SDVariable addVariable(SDVariable variable) {
        Preconditions.checkState(variable.getSameDiff() == this, "Samediff instance must be the same.");

        if (variables.containsKey(variable.getVarName()) && !variables.get(variable.getVarName()).getVariable().equals(variable)) {
            throw new IllegalArgumentException("Variable already found with variable opName " + variable.getVarName());
        }

        Preconditions.checkState(variable.getSameDiff() == this, "Same diff instance for variable must be the same!");
        variables.put(variable.getVarName(), Variable.builder().name(variable.getVarName()).variable(variable).build());
        return variable;
    }


    /**
     * Generate a new variable name based on the uniqueness of the base name and arg index<br>
     * For example, if baseName = "X" will return:<br>
     * "X" if "X" does not already exist, or "X:argIndex" if argIndex > 0<br>
     * "X_1" if "X" already exists, or "X_1:argIndex" if argIndex > 0<br>
     * "X_2" if "X" and "X_1" already exists, or "X_2:argIndex" if argIndex > 0<br>
     * And so on, until an unused name is found
     *
     * @param baseName the base name to use (use function.opName() where function is a {@link DifferentialFunction}
     * @param argIndex the arg index
     * @return the new generated name
     */
    public String generateNewVarName(String baseName, int argIndex) {
        if (!variables.containsKey(baseName) && argIndex == 0) {
            return baseName;
        }

        //need to find a new name
        int count = 0;
        String name = baseName + (count == 0 ? "" : "_" + count) + (argIndex > 0 ? ":" + argIndex : "");
        while (getVariable(name) != null) {
            name = baseName + "_" + (++count) + (argIndex > 0 ? ":" + argIndex : "");
        }

        if (getVariable(name) != null) {
            throw new ND4JIllegalStateException("Converged on already generated variable!");
        }
        return name;
    }


    /**
     * LSTM unit
     *
     * @param baseName      the base name for outputs
     * @param configuration the configuration to use
     * @return
     */
    public SDVariable lstm(String baseName, LSTMCellConfiguration configuration) {
        return new LSTMCell(this, configuration).outputVariables(baseName)[0];
    }


    /**
     * An sru cell
     *
     * @param configuration the configuration for the sru cell
     * @return
     */
    public SDVariable sruCell(SRUCellConfiguration configuration) {
        return new SRUCell(this, configuration).outputVariables()[0];
    }


    /**
     * Simple recurrent unit
     *
     * @param configuration the configuration for the sru
     * @return
     */
    public SDVariable sru(SRUConfiguration configuration) {
        return new SRU(this, configuration).outputVariables()[0];
    }

    /**
     * The gru cell
     *
     * @param configuration teh configuration to use
     * @return
     */
    public SDVariable gru(GRUCellConfiguration configuration) {
        return new GRUCell(this, configuration).outputVariables()[0];
    }


    /**
     * An sru cell
     *
     * @param baseName      the base name to  use for the output variables
     * @param configuration the configuration for the sru cell
     * @return
     */
    public SDVariable sruCell(String baseName, SRUCellConfiguration configuration) {
        return new SRUCell(this, configuration).outputVariables(baseName)[0];
    }


    /**
     * Simiple recurrent unit
     *
     * @param baseName      the base name to use for output variables
     * @param configuration the configuration for the sru
     * @return
     */
    public SDVariable sru(String baseName, SRUConfiguration configuration) {
        return new SRU(this, configuration).outputVariables(baseName)[0];
    }

    /**
     * The gru cell
     *
     * @param baseName      the base name for the gru cell
     * @param configuration teh configuration to use
     * @return
     */
    public SDVariable gru(String baseName, GRUCellConfiguration configuration) {
        return new GRUCell(this, configuration).outputVariables(baseName)[0];
    }

    /**
     * @see #slice(String, SDVariable, int[], int[])
     */
    public SDVariable slice(SDVariable input, int[] begin, int[] size) {
        return slice(null, input, begin, size);
    }

    /**
     * Get a subset of the specified input, by specifying the first element and the size of the array.<br>
     * For example, if input is:<br>
     * [a, b, c]<br>
     * [d, e, f]<br>
     * then slice(input, begin=[0,1], size=[2,1] will return:<br>
     * [b]<br>
     * [e]<br>
     * <br>
     * Note that for each dimension i, begin[i] + size[i] <= input.size(i)
     *
     * @param name  Output variable name
     * @param input Variable to get subset of
     * @param begin Beginning index. Must be same length as rank of input array
     * @param size  Size of the output array. Must be same length as rank of input array
     * @return Subset of the input
     */
    public SDVariable slice(String name, SDVariable input, int[] begin, int[] size) {
        SDVariable ret = f().slice(input, begin, size);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #stridedSlice(String, SDVariable, long[], long[], long[])
     */
    public SDVariable stridedSlice(SDVariable input, int[] begin, int[] end, int[] strides) {
        return stridedSlice(null, input, begin, end, strides);
    }

    /**
     * @see #stridedSlice(String, SDVariable, long[], long[], long[])
     */
    public SDVariable stridedSlice(String name, SDVariable input, int[] begin, int[] end, int[] strides) {
        return stridedSlice(name, input, begin, end, strides, 0, 0, 0, 0, 0);
    }

    /**
     * @see #stridedSlice(String, SDVariable, long[], long[], long[])
     */
    public SDVariable stridedSlice(SDVariable input, long[] begin, long[] end, long[] strides) {
        return stridedSlice(null, input, begin, end, strides);
    }

    /**
     * Get a subset of the specified input, by specifying the first element, last element, and the strides.<br>
     * For example, if input is:<br>
     * [a, b, c]<br>
     * [d, e, f]<br>
     * [g, h, i]<br>
     * then stridedSlice(input, begin=[0,1], end=[2,2], strides=[2,1]) will return:<br>
     * [b, c]<br>
     * [h, i]<br>
     * <br>
     *
     * @param name    Output variable name
     * @param input   Variable to get subset of
     * @param begin   Beginning index. Must be same length as rank of input array
     * @param end     End index. Must be same length as the rank of the array
     * @param strides Stride ("step size") for each dimension. Must be same length as the rank of the array. For example,
     *                stride of 2 means take every second element.
     * @return Subset of the input
     */
    public SDVariable stridedSlice(String name, SDVariable input, long[] begin, long[] end, long[] strides) {
        return stridedSlice(name, input, begin, end, strides, 0, 0, 0, 0, 0);
    }

    /**
     * Get a subset of the specified input, by specifying the first element, last element, and the strides.<br>
     * Operates as described in {@link #stridedSlice(SDVariable, long[], long[], long[])} with some extra mask arrays
     * as described below.
     *
     * @param name           Output variable name
     * @param in             Variable to get subset of
     * @param begin          Beginning index
     * @param end            End index
     * @param strides        Stride ("step size") for each dimension. For example,
     *                       stride of 2 means take every second element.
     * @param beginMask      Bit mask: If the ith bit is set to 1, then the value in the begin long[] is ignored,
     *                       and a value of 0 is used instead for the beginning index for that dimension
     * @param endMask        Bit mask: If the ith bit is set to 1, then the value in the end long[] is ignored,
     *                       and a value of size(i)-1 is used instead for the end index for that dimension
     * @param ellipsisMask   Bit mask: only one non-zero value is allowed here. If a non-zero value is set, then other
     *                       dimensions are inserted as required at the specified position
     * @param newAxisMask    Bit mask: if the ith bit is set to 1, then the begin/end/stride values are ignored, and
     *                       a size 1 dimension is inserted at this point
     * @param shrinkAxisMask Bit mask: if the ith bit is set to 1, then the begin/end/stride values are ignored, and
     *                       a size 1 dimension is removed at this point. Note that begin/end/stride values must
     *                       result in a size 1 output for these dimensions
     * @return A subset of the input array
     */
    public SDVariable stridedSlice(String name, SDVariable in, long[] begin, long[] end, long[] strides, int beginMask,
                                   int endMask, int ellipsisMask, int newAxisMask, int shrinkAxisMask) {
        SDVariable ret = f().stridedSlice(in, begin, end, strides, beginMask, endMask, ellipsisMask, newAxisMask, shrinkAxisMask);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #stridedSlice(String, SDVariable, long[], long[], long[], int, int, int, int, int)
     */
    public SDVariable stridedSlice(SDVariable in, int[] begin, int[] end, int[] strides, int beginMask,
                                   int endMask, int ellipsisMask, int newAxisMask, int shrinkAxisMask) {
        return stridedSlice(null, in, begin, end, strides, beginMask, endMask, ellipsisMask, newAxisMask, shrinkAxisMask);
    }

    /**
     * @see #stridedSlice(String, SDVariable, long[], long[], long[], int, int, int, int, int)
     */
    public SDVariable stridedSlice(String name, SDVariable in, int[] begin, int[] end, int[] strides, int beginMask,
                                   int endMask, int ellipsisMask, int newAxisMask, int shrinkAxisMask) {
        SDVariable ret = f().stridedSlice(in, begin, end, strides, beginMask, endMask, ellipsisMask, newAxisMask, shrinkAxisMask);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #stridedSlice(String, SDVariable, long[], long[], long[], int, int, int, int, int)
     */
    public SDVariable stridedSlice(SDVariable in, long[] begin, long[] end, long[] strides, int beginMask,
                                   int endMask, int ellipsisMask, int newAxisMask, int shrinkAxisMask) {
        return stridedSlice(null, in, begin, end, strides, beginMask, endMask, ellipsisMask, newAxisMask, shrinkAxisMask);
    }

    /**
     * @see #scatterAdd(String, SDVariable, SDVariable, SDVariable)
     */
    public SDVariable scatterAdd(SDVariable ref, SDVariable indices, SDVariable updates) {
        return scatterAdd(null, ref, indices, updates);
    }

    /**
     * Scatter addition operation.<br>
     * If indices is rank 0 (a scalar), then out[index, ...] += updates[...]<br>
     * If indices is rank 1 (a vector), then for each position i, out[indices[i], ...] += updates[i, ...]<br>
     * If indices is rank 2+, then for each position (i,...,k), out[indices[i], ..., indices[k], ...] += updates[i, ..., k, ...]<br>
     * Note that if multiple indices refer to the same location, the contributions from each is handled correctly.
     *
     * @param name    Name of the output variable
     * @param ref     Initial/source variable
     * @param indices Indices array
     * @param updates Updates to add to the initial/source array
     * @return The updated variable
     */
    public SDVariable scatterAdd(String name, SDVariable ref, SDVariable indices, SDVariable updates) {
        SDVariable ret = f().scatterAdd(ref, indices, updates);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #scatterMul(String, SDVariable, SDVariable, SDVariable)
     */
    public SDVariable scatterMul(SDVariable ref, SDVariable indices, SDVariable updates) {
        return scatterMul(null, ref, indices, updates);
    }

    /**
     * Scatter multiplication operation.<br>
     * If indices is rank 0 (a scalar), then out[index, ...] *= updates[...]<br>
     * If indices is rank 1 (a vector), then for each position i, out[indices[i], ...] *= updates[i, ...]<br>
     * If indices is rank 2+, then for each position (i,...,k), out[indices[i], ..., indices[k], ...] *= updates[i, ..., k, ...]<br>
     * Note that if multiple indices refer to the same location, the contributions from each is handled correctly.
     *
     * @param name    Name of the output variable
     * @param ref     Initial/source variable
     * @param indices Indices array
     * @param updates Updates to add to the initial/source array
     * @return The updated variable
     */
    public SDVariable scatterMul(String name, SDVariable ref, SDVariable indices, SDVariable updates) {
        SDVariable ret = f().scatterMul(ref, indices, updates);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #scatterSub(String, SDVariable, SDVariable, SDVariable)
     */
    public SDVariable scatterSub(SDVariable ref, SDVariable indices, SDVariable updates) {
        return scatterSub(null, ref, indices, updates);
    }

    /**
     * Scatter subtraction operation.<br>
     * If indices is rank 0 (a scalar), then out[index, ...] -= updates[...]<br>
     * If indices is rank 1 (a vector), then for each position i, out[indices[i], ...] -= updates[i, ...]<br>
     * If indices is rank 2+, then for each position (i,...,k), out[indices[i], ..., indices[k], ...] -= updates[i, ..., k, ...]<br>
     * Note that if multiple indices refer to the same location, the contributions from each is handled correctly.
     *
     * @param name    Name of the output variable
     * @param ref     Initial/source variable
     * @param indices Indices array
     * @param updates Updates to add to the initial/source array
     * @return The updated variable
     */
    public SDVariable scatterSub(String name, SDVariable ref, SDVariable indices, SDVariable updates) {
        SDVariable ret = f().scatterSub(ref, indices, updates);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #scatterDiv(String, SDVariable, SDVariable, SDVariable)
     */
    public SDVariable scatterDiv(SDVariable ref, SDVariable indices, SDVariable updates) {
        return scatterDiv(null, ref, indices, updates);
    }

    /**
     * Scatter division operation.<br>
     * If indices is rank 0 (a scalar), then out[index, ...] /= updates[...]<br>
     * If indices is rank 1 (a vector), then for each position i, out[indices[i], ...] /= updates[i, ...]<br>
     * If indices is rank 2+, then for each position (i,...,k), out[indices[i], ..., indices[k], ...] /= updates[i, ..., k, ...]<br>
     * Note that if multiple indices refer to the same location, the contributions from each is handled correctly.
     *
     * @param name    Name of the output variable
     * @param ref     Initial/source variable
     * @param indices Indices array
     * @param updates Updates to add to the initial/source array
     * @return The updated variable
     */
    public SDVariable scatterDiv(String name, SDVariable ref, SDVariable indices, SDVariable updates) {
        SDVariable ret = f().scatterDiv(ref, indices, updates);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #scatterMax(String, SDVariable, SDVariable, SDVariable)
     */
    public SDVariable scatterMax(SDVariable ref, SDVariable indices, SDVariable updates) {
        return scatterMax(null, ref, indices, updates);
    }

    /**
     * Scatter max operation.<br>
     * If indices is rank 0 (a scalar), then out[index, ...] = max(updates[...], in[index,...])<br>
     * If indices is rank 1 (a vector), then for each position i, out[indices[i], ...] = max(updates[i,...], in[indices[i],...])<br>
     * If indices is rank 2+, then for each position (i,...,k), out[indices[i], ..., indices[k], ...] = max(updates[i, ..., k, ...], in[indices[i], ..., indices[k], ...]<br>
     * Note that if multiple indices refer to the same location, the contributions from each is handled correctly.
     *
     * @param name    Name of the output variable
     * @param ref     Initial/source variable
     * @param indices Indices array
     * @param updates Updates to add to the initial/source array
     * @return The updated variable
     */
    public SDVariable scatterMax(String name, SDVariable ref, SDVariable indices, SDVariable updates) {
        SDVariable ret = f().scatterMax(ref, indices, updates);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #scatterMin(String, SDVariable, SDVariable, SDVariable)
     */
    public SDVariable scatterMin(SDVariable ref, SDVariable indices, SDVariable updates) {
        return scatterMin(null, ref, indices, updates);
    }

    /**
     * Scatter min operation.<br>
     * If indices is rank 0 (a scalar), then out[index, ...] = min(updates[...], in[index,...])<br>
     * If indices is rank 1 (a vector), then for each position i, out[indices[i], ...] = min(updates[i,...], in[indices[i],...])<br>
     * If indices is rank 2+, then for each position (i,...,k), out[indices[i], ..., indices[k], ...] = min(updates[i, ..., k, ...], in[indices[i], ..., indices[k], ...]<br>
     * Note that if multiple indices refer to the same location, the contributions from each is handled correctly.
     *
     * @param name    Name of the output variable
     * @param ref     Initial/source variable
     * @param indices Indices array
     * @param updates Updates to add to the initial/source array
     * @return The updated variable
     */
    public SDVariable scatterMin(String name, SDVariable ref, SDVariable indices, SDVariable updates) {
        SDVariable ret = f().scatterMin(ref, indices, updates);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #scatterUpdate(String, SDVariable, SDVariable, SDVariable)
     */
    public SDVariable scatterUpdate(SDVariable ref, SDVariable indices, SDVariable updates) {
        return scatterUpdate(null, ref, indices, updates);
    }

    /**
     * Scatter update operation.<br>
     * If indices is rank 0 (a scalar), then out[index, ...] = updates[...]<br>
     * If indices is rank 1 (a vector), then for each position i, out[indices[i], ...] = updates[i, ...]<br>
     * If indices is rank 2+, then for each position (i,...,k), out[indices[i], ..., indices[k], ...] = updates[i, ..., k, ...]<br>
     * Note that if multiple indices refer to the same location, the output at those locations is undefined - different
     * updates may occur in different orders
     *
     * @param name    Name of the output variable
     * @param ref     Initial/source variable
     * @param indices Indices array
     * @param updates Updates to add to the initial/source array
     * @return The updated variable
     */
    public SDVariable scatterUpdate(String name, SDVariable ref, SDVariable indices, SDVariable updates) {
        SDVariable ret = f().scatterUpdate(ref, indices, updates);
        return updateVariableNameAndReference(ret, name);
    }

    /**
     * @see #trace(String, SDVariable)
     */
    public SDVariable trace(SDVariable in){
        return trace(null, in);
    }

    /**
     * Matrix trace operation
     * For rank 2 matrices, the output is a scalar vith the trace - i.e., sum of the main diagonal.<br>
     * For higher rank inputs, output[a,b,c] = trace(in[a,b,c,:,:])
     *
     * @param name Name of the output variable. May be null.
     * @param in   Input variable
     * @return Trace
     */
    public SDVariable trace(String name, SDVariable in){
        SDVariable ret = f().trace(in);
        return updateVariableNameAndReference(ret, name);
    }


    /**
     * Generate the variables based on the given input op and return the output variable names.
     *
     * @param function the function to generate the output
     *                 variable names for
     * @return the set of names generated for each output of the function.
     */
    public SDVariable[] generateOutputVariableForOp(DifferentialFunction function, String baseName, boolean isImport) {
        //xyz ops only have 1 output
        //if there is already a base name defined, use that
        if (baseName == null || baseName.isEmpty() && getBaseNameForFunction(function) != null)
            baseName = getBaseNameForFunction(function);

        if (baseName == null)
            baseName = function.opName();

        //First: calculate output data types. We can always calculate output data types, even if the input arrays
        //are not available - *except for sometimes during import, until all ops/variables have been added*
        List<org.nd4j.linalg.api.buffer.DataType> outputDataTypes = null;

        if(!isImport) {
            List<org.nd4j.linalg.api.buffer.DataType> inputDataTypes = new ArrayList<>();
            List<String> fnInputs = ops.get(function.getOwnName()).getInputsToOp();
            if (fnInputs != null) {
                for (String var : fnInputs) {
                    inputDataTypes.add(variables.get(var).getVariable().dataType());
                }
            }
            outputDataTypes = function.calculateOutputDataTypes(inputDataTypes);
        }

        val outputShape = function.calculateOutputShape();
        if (outputShape == null || outputShape.isEmpty()) {
            if (function instanceof CustomOp) {
                CustomOp customOp = (CustomOp) function;
                //can't guess number of outputs, variable
                int num_outputs = function.getNumOutputs(); //Use this in preference - if set. Descriptor might specify 2, but it can sometimes be 2+
                if (num_outputs <= 0) {
                    val descriptor = customOp.getDescriptor();
                    if (descriptor != null) {
                        num_outputs = descriptor.getNumOutputs();
                    }
                    if (num_outputs <= 0) {
                        throw new ND4UnresolvedOutputVariables("Could not determine number of output variables for op "
                                + function.getOwnName() + " - " + function.getClass().getSimpleName() + ". Ops can override" +
                                " getNumOutputs() to specify number of outputs if required");
                    }
                }
                char ordering = 'c';
                SDVariable[] args = function.args();
                if (args != null && args.length > 0 && args[0].getArr() != null) {  //Args may be null or length 0 for some ops, like eye
                    ordering = function.args()[0].getArr().ordering();
                }
                SDVariable[] ret = new SDVariable[num_outputs];

                //Infer the output types: we can always determine datatype but not always shapes
                Preconditions.checkState(isImport || num_outputs == 0 || (outputDataTypes != null && outputDataTypes.size() == num_outputs),
                        "Incorrect number of output datatypes: got %s but expected datatypes for %s outputs - %s (op: %s)",
                        (outputDataTypes == null ? null : outputDataTypes.size()), num_outputs, outputDataTypes, function.getClass().getSimpleName());

                //dynamic shapes
                //When importing from TF: convention is "unstack", "unstack:1", "unstack:2", ...
                for (int i = 0; i < ret.length; i++) {
                    SDVariable var = (i == 0 ? getVariable(baseName) : getVariable(baseName + ":" + i));
                    if (var == null) {
                        //Generate new variable name if one with the specified name doesn't exist
                        //Note: output of an op is ARRAY type - activations, not a trainable parameter. Thus has no weight init scheme

                        org.nd4j.linalg.api.buffer.DataType dataType  = isImport ? null : outputDataTypes.get(i);
                        var = var(generateNewVarName(baseName, i), VariableType.ARRAY, null, dataType, (long[])null);
                    }
                    var.setOutputIndex(i);
                    var.setCreator(function);
                    ret[i] = var;
                }

                //Update the internal state: outgoing variables for function
                if (getOutputsForFunction(function) == null)
                    addOutgoingFor(ret, function);

                return ret;
            }

            //this is for unresolved shapes, we know xyz is always 1 output
            else if (function instanceof BaseOp && outputShape.isEmpty()) {
                SDVariable[] ret = new SDVariable[1];
                SDVariable checkGet = getVariable(baseName);
                char ordering = 'c';
                SDVariable[] args = function.args();
                if (args != null && args.length > 0 && function.args()[0].getArr() != null) { //Args may be null or length 0 for some ops, like eye
                    ordering = function.args()[0].getArr().ordering();
                }
                if (checkGet == null) {
                    //Note: output of an op is ARRAY type - activations, not a trainable parameter. Thus has no weight init scheme
                    org.nd4j.linalg.api.buffer.DataType dataType  = outputDataTypes.get(0);
                    checkGet = var(baseName, VariableType.ARRAY, null, dataType, (long[])null);
                }

                if (checkGet == null) {
                    //Note: output of an op is ARRAY type - activations, not a trainable parameter. Thus has no weight init scheme
                    org.nd4j.linalg.api.buffer.DataType dataType  = outputDataTypes.get(0);
                    checkGet = var(baseName, VariableType.ARRAY, null, dataType, (long[])null);
                }

                checkGet.setOutputIndex(0);
                checkGet.setCreator(function);
                ret[0] = checkGet;


                //Update the internal state: outgoing variables for function
                if (getOutputsForFunction(function) == null)
                    addOutgoingFor(ret, function);

                return ret;
            }
        }

        //Check that output shapes and output dtypes actually match (they should)
        if(!isImport) {
            for (int i = 0; i < outputShape.size(); i++) {
                org.nd4j.linalg.api.buffer.DataType shapeDataType = outputShape.get(i).dataType();
                org.nd4j.linalg.api.buffer.DataType calcType = outputDataTypes.get(i);
                Preconditions.checkState(calcType == shapeDataType, "Calculated output data types do not match for shape calculation vs. datatype calculation:" +
                        " %s vs %s for op %s output %s", shapeDataType, calcType, function.getClass().getName(), i);
            }
        }

        char ordering = 'c';
        if (function.args() != null && function.args().length > 0 && function.args()[0].getArr() != null) {
            ordering = function.args()[0].getArr().ordering();
        }

        SDVariable[] ret = new SDVariable[outputShape.size()];

        // ownName/baseName will be used to get variables names
        val ownName = function.getOwnName();
        val rootName = baseName;
        for (int i = 0; i < ret.length; i++) {
            LongShapeDescriptor shape = outputShape.get(i);
            // it should be: rootName:index. i.e.: split:1, split:2, split:3, split:4 etc
            baseName = rootName + (i > 0 ? ":" + i : "");
            SDVariable checkGet = getVariable(baseName);
            if (checkGet == null) {
                // obviously - there's no such var, just add it
                //Note: output of an op is ARRAY type - activations, not a trainable parameter. Thus has no weight init scheme


                checkGet = var(baseName, VariableType.ARRAY, null, shape.dataType(), shape.getShape());
            } else if (shape != null && !shapeAlreadyExistsForVarName(checkGet.getVarName())) {
                // var exists, let's update its shape
                putShapeForVarName(checkGet.getVarName(), shape);
            } else if (shape != null && shapeAlreadyExistsForVarName(checkGet.getVarName())) {
                // no-op.
                // TODO: maybe we should check shapes equality here?
                // it's either var that already exist, or something bad happening
            }

            if (checkGet == null) {
                org.nd4j.linalg.api.buffer.DataType dataType = org.nd4j.linalg.api.buffer.DataType.FLOAT;     //TODO FIX THIS
                checkGet = var(baseName + (i > 0 ? ":" + i : ""), new ZeroInitScheme(ordering), dataType, shape.getShape());
            }

            checkGet.setOutputIndex(i);
            checkGet.setCreator(function);
            ret[i] = checkGet;
        }

        return ret;
    }

    /**
     * Generate the variables based on the given input op
     * and return the output variable names.
     *
     * @param function the function to generate the output
     *                 variable names for
     * @return the set of names generated for each output of the function.
     */
    public SDVariable[] generateOutputVariableForOp(DifferentialFunction function) {
        return generateOutputVariableForOp(function, function.opName(), false);
    }

    /**
     * Get a SameDiff function instance given the name of the function
     *
     * @param functionName the name of the function
     * @return the same diff function instance defined for the given name
     */
    public SameDiff getFunction(String functionName) {
        return sameDiffFunctionInstances.get(functionName);
    }


    /**
     * Creates a while statement
     *
     * @param sameDiffConditional
     * @param loopBody
     * @return
     */
    public While whileStatement(SameDiffConditional sameDiffConditional,
                                SameDiffFunctionDefinition conditionBody,
                                SameDiffFunctionDefinition loopBody
            , SDVariable[] inputVars) {
        return While.builder()
                .inputVars(inputVars)
                .condition(conditionBody)
                .predicate(sameDiffConditional)
                .trueBody(loopBody)
                .parent(this)
                .blockName("while-" + UUID.randomUUID().toString())
                .build();
    }

    /**
     * @param conditional
     * @param trueBody
     * @param falseBody
     * @return
     */
    public If ifStatement(SameDiffConditional conditional,
                          SameDiffFunctionDefinition conditionBody,
                          SameDiffFunctionDefinition trueBody,
                          SameDiffFunctionDefinition falseBody
            , SDVariable[] inputVars) {
        return If.builder()
                .conditionBody(conditionBody)
                .falseBody(falseBody)
                .trueBody(trueBody)
                .predicate(conditional)
                .inputVars(inputVars)
                .parent(this)
                .blockName("if-" + UUID.randomUUID().toString())
                .build();
    }


    public TensorArray tensorArray(DataType dataType) {
        TensorArray ta = new TensorArray(this, dataType);
        SDVariable[] outVars = ta.outputVariables();
        return ta;
    }

    /**
     * @param functionName
     * @param with
     */

    public SDVariable invokeFunctionOn(String functionName, SameDiff with) {
        SameDiff instance = sameDiffFunctionInstances.get(functionName);
        SDVariable ret = instance.invokeGraphOn(with);

        return ret;
    }


    /**
     * @param function
     */
    public SameDiff defineFunction(String function, SameDiffFunctionDefinition functionDefinition, SDVariable[] variables) {
        if (!sameDiffFunctionInstances.containsKey(function)) {
            SameDiff sub = SameDiff.create();
            this.child = sub;
            sub.parent = this;
            //setup subgraph
            //re execute to populate subgraph
            SDVariable[] ret = new SDVariable[variables.length];
            for (int i = 0; i < ret.length; i++) {
                ret[i] = sub.var(variables[i]);
            }

            functionDefinition.define(sub, null, ret);
            sameDiffFunctionInstances.put(function, sub);
        }
        this.child = null;
        return sameDiffFunctionInstances.get(function);
    }


    /**
     * @param function
     */
    public void defineFunction(String function, SameDiffFunctionDefinition functionDefinition) {
        defineFunction(function, functionDefinition, new LinkedHashMap<String, INDArray>());
    }

    /**
     * @param function
     * @param functionDefinition
     * @param inputs
     */
    public void defineFunction(String function,
                               SameDiffFunctionDefinition functionDefinition,
                               Map<String, INDArray> inputs) {
        if (!sameDiffFunctionInstances.containsKey(function)) {
            SameDiff sub = SameDiff.create();
            //setup subgraph
            //re execute to populate subgraph
            functionDefinition.define(sub, inputs, null);

            sameDiffFunctionInstances.put(function, sub);
        }

    }

    @Deprecated
    public INDArray execAndEndResult(){
        List<String> outputs = outputs();
        Preconditions.checkState(outputs.size() == 1, "Method can only be used with SameDiff instances with a single output");
        long tid = Thread.currentThread().getId();
        Map<String,INDArray> placeholders = placeholdersPerThread.get(tid);
        return execSingle(placeholders, outputs.get(0));
    }

    /**
     * Execute the gradient (backward pass) function on this graph.<br>
     * Constructs a backwards graph (differentiating the defined graph) if it does not already exist, and the executes
     * the operations on that graph, calculating gradients for all variables.<br>
     * Note that after execBackwards() has completed, the gradient arrays for a each variable can be accessed using
     * {@link SDVariable#getGradient()} followed by  {@link SDVariable#getArr()} or by using {@link #getGradForVariable(String)}
     */
    public void execBackwards(Map<String,INDArray> placeholders){
        if (getFunction("grad") == null) {
            createGradFunction();
        }

        //Collect (unique) list of gradient names...
        Set<String> varGradNames = new HashSet<>();
        for(Variable v : variables.values()){
            if(v.getVariable().getVariableType() == VariableType.VARIABLE){
                SDVariable g = v.getVariable().gradient();
                varGradNames.add(g.getVarName());
            }
        }

        //Edge case: if no variables, no variable gradients to calculate...
        if(varGradNames.isEmpty()){
            log.trace("Skipping gradient execution - no variables to be calculated (variableGradNamesList is empty)");
            return;
        }

        List<String> vargradNamesList = new ArrayList<>(varGradNames);
        execBackwards(placeholders, vargradNamesList);
    }

    public void execBackwards(Map<String,INDArray> placeholders, List<String> variableGradNamesList){
        if (getFunction("grad") == null) {
            createGradFunction();
        }

        log.trace("About to execute backward function");

        //Edge case: if no variables, no variable gradients to calculate...
        if(variableGradNamesList.isEmpty()){
            log.trace("Skipping gradient execution - no variables to be calculated (variableGradNamesList is empty)");
            return;
        }


        sameDiffFunctionInstances.get("grad").exec(placeholders, variableGradNamesList);
    }

    /**
     * Create the gradient function (for calculating gradients via {@link #execBackwards()}) if it is not already defined.
     * Users do not usually need to call this function manually, as it is called as required in the aforementioned method.
     * <br><br>
     * If the gradient function already exists, this method is a no-op.<br>
     * After this method returns, the SameDiff function instance for the gradient can be accessed using {@link #getFunction(String)}
     * with name "grad" as the argument.
     */
    public void createGradFunction() {
        if (log.isTraceEnabled()) {
            log.trace("Defining function \"grad\"");
        }

        //First thing: check that there's only one output... throw an exception if so
        //A variable is an output if it's eithen an input, or if it's the output of a function, but not an input
        Set<String> variablesNotAsFunctionInput = new HashSet<>();
        for(SDVariable s : variables()){
            variablesNotAsFunctionInput.add(s.getVarName());
        }
        for(SameDiffOp op : ops.values()){
            List<String> fnInputs = op.getInputsToOp();
            for(String s : fnInputs) {
                variablesNotAsFunctionInput.remove(s);
            }
        }
        if(variablesNotAsFunctionInput.size() > 1){
            List<String> outputs = new ArrayList<>(variablesNotAsFunctionInput);
            Collections.sort(outputs);
            throw new IllegalStateException("Cannot create gradient function for graph with multiple outputs.\n" +
                    "Gradient calculation assumes a single output which defines a scalar loss function value.\n" +
                    "An output is any variable that is not used as the input to a function in the graph.\n" +
                    "In the case of multiple outputs that are components of an additive loss function, simply add the" +
                    "component variables to create a scalar output.\nAll outputs for graph: "
                    + outputs);
        }

        final SameDiff outer = this;
        defineFunction("grad", new SameDiffFunctionDefinition() {

            @Override
            public SDVariable[] define(SameDiff sameDiff, Map<String, INDArray> inputs, SDVariable[] variableInputs) {
                //propagate graph to this samediff instance
                //which will also contain the backward
                if (SameDiff.this.debugMode) {
                    sameDiff.enableDebugMode();
                }

                outer.invokeGraphOn(sameDiff);
                if (debugMode) {
                    //Expect incoming args and outgoing args to be the same
                    Preconditions.checkState(sameDiff.ops.keySet().equals(ops.keySet()), "ops keysets not equal");
                }

                List<SameDiffOp> allFunctions = new ArrayList<>(sameDiff.ops.values());
                if (allFunctions.isEmpty()) {
                    throw new ND4JIllegalStateException("No ops found!");
                }

                for (SameDiffOp op : allFunctions) {
                    DifferentialFunction func = op.getOp();
                    if (func instanceof SDVariable) {
                        continue;
                    }

                    val args = func.args();
                    for (val arg : args)
                        arg.setSameDiff(sameDiff);
                    val outputs = func.outputVariables();
                    for (val output : outputs)
                        output.setSameDiff(sameDiff);
                    func.setSameDiff(sameDiff);
                }

                //Find final outputs - these are SDVariables that are output of a function that are not inputs to anything else
                // i.e., ArrayType - not constant, variable, placeholder
                //Also should be a floating point type, to contribute to score
                List<SDVariable> finalOutputs = new ArrayList<>();
                for(Variable v : sameDiff.variables.values()){
                    String outputOfOp = v.getOutputOfOp();
                    boolean isExternalGrad = false;
                    if(outputOfOp != null && getOps().get(outputOfOp).getOp() instanceof ExternalErrorsFunction){
                        isExternalGrad = true;
                    }
                    if(!isExternalGrad && (v.getVariable().getVariableType() != VariableType.ARRAY ||
                            (v.getInputsForOp() != null && ! v.getInputsForOp().isEmpty())
                            || !v.getVariable().dataType().isFPType())){
                        continue;
                    }
                    finalOutputs.add(v.getVariable());
                }

                Preconditions.checkState(!finalOutputs.isEmpty(), "Could not infer final network outputs to begin differentiation");

                if (log.isTraceEnabled()) {
                    String[] initialOutputsStr = allFunctions.get(allFunctions.size() - 1).getOp().outputVariablesNames();
                    String s = initialOutputsStr == null ? "null" : Arrays.toString(initialOutputsStr);
                    log.trace("Defining backward function: initial outputs {}", s);
                }

                //Differentiate ops in any valid reverse topological order to ensure the parent gradient nodes are
                // available before we try to differentiate the op
                Queue<DifferentialFunction> availableForDiff = new LinkedList<>();
                Set<String> seenOps = new HashSet<>();
                //start with scalar backprop
                SDVariable initialGrad = sameDiff.var("one-var", Nd4j.trueScalar(1.0));
                for(SDVariable v : finalOutputs) {
                    if(v.dataType() == initialGrad.dataType()){
                        sameDiff.setGradientForVariableName(v.getVarName(), initialGrad);
                    } else {
                        sameDiff.setGradientForVariableName(v.getVarName(), initialGrad.castTo(v.dataType()));
                    }
                    SDVariable gradientBackwardsMarker = sameDiff.gradientBackwardsMarker(v);
                    DifferentialFunction df = sameDiff.getVariableOutputFunction(gradientBackwardsMarker.getVarName());
                    if(!seenOps.contains(df.getOwnName())) {
                        availableForDiff.add(df);
                        seenOps.add(df.getOwnName());
                    }
                }

                int numProcessed = 0;
                while(availableForDiff.size() > 0){
                    DifferentialFunction df = availableForDiff.remove();

                    //Get the inputs and outputs of the op
                    List<String> inputsToOp;
                    List<String> outputsOfOp;
                    if(df instanceof GradientBackwardsMarker){
                        SameDiffOp op = sameDiff.ops.get(df.getOwnName());
                        inputsToOp = op.getInputsToOp();
                        outputsOfOp = Collections.emptyList();
                    } else {
                        inputsToOp = sameDiff.ops.get(df.getOwnName()).getInputsToOp();
                        outputsOfOp = sameDiff.ops.get(df.getOwnName()).getOutputsOfOp();
                        numProcessed++;
                    }


                    //Get gradients for all output variables:
                    List<SDVariable> grads = new ArrayList<>();
                    for(String s : outputsOfOp){
                        SDVariable g = sameDiff.getVariable(s).gradient();
                        Preconditions.checkNotNull(g, "Could not get gradient for variable %s as output of op %s", g.getVarName(), df.getOwnName());
                        grads.add(g);
                    }

                    //Differentiate:
                    List<SDVariable> currFnGrads = df.diff(grads);

                    //Check the inputs, see if we can differentiate those ops now (and if so: add to queue)
                    for(String s : inputsToOp){
                        Variable v = sameDiff.variables.get(s);
                        String opName = v.getOutputOfOp();
                        if(opName == null){
                            //Skip placeholder/constant etc
                            continue;
                        }

                        //We can differentiate this op if output variables all have gradients defined
                        //TODO what about control ops? Need to be handled differently?
                        boolean allAvaliable = true;
                        SameDiffOp o = sameDiff.ops.get(opName);
                        for(String opOutputs : o.getOutputsOfOp()){
                            SDVariable g = sameDiff.getVariable(opOutputs).getGradient();
                            allAvaliable &= (g != null);
                            if(g == null)
                                break;
                        }

                        if(allAvaliable && !seenOps.contains(o.getOp().getOwnName())) {
                            availableForDiff.add(o.getOp());
                            seenOps.add(o.getOp().getOwnName());
                        }
                    }
                }

                Preconditions.checkState(numProcessed == ops.size(), "Only differentiated %s of %s ops", numProcessed, ops.size());

                return new SDVariable[]{sameDiff.var("grad", org.nd4j.linalg.api.buffer.DataType.FLOAT, 1)};
            }
        });

        associateSameDiffWithOpsAndVariables();
    }


    /**
     * Set the original shape for a given place holder.<br>
     * This is used to track original shapes of place holder variables.<br>
     * The reason we track original shapes is to validate possible candidate arrays coming in (especially with -1
     * as the expected shapes).
     * <p>
     * Note that if {@link #isPlaceHolder(String)}
     * returns false for the passed in vertex id,
     * a {@link ND4JIllegalStateException} is thrown.
     * <p>
     * A vertex id must be added first. You can
     * do this with {@link #addAsPlaceHolder(String)}
     *
     * @param variableName the vertex id for the original shape
     * @param shape        the shape of the place holder
     */
    public void setOriginalPlaceHolderShape(String variableName, long[] shape) {
        if (!isPlaceHolder(variableName)) {
            throw new ND4JIllegalStateException("Vertex id " + variableName + " does not appear to be a place holder. Did you forget to call addPlaceHolder?");
        }

        if (shape == null) {
            throw new ND4JIllegalStateException("Null and 0 length shape arrays not allowed");
        }


        if (placeHolderOriginalShapes.containsKey(variableName) && !Arrays.equals(placeHolderOriginalShapes.get(variableName), shape)) {
            throw new ND4JIllegalStateException("Unable to add a new shape for vertex id " + variableName);
        }

        //after validation now only set once
        placeHolderOriginalShapes.put(variableName, shape);

    }


    /**
     * Get the original shape for the vertex id if one was set (other wise returns null).<br>
     * This is mainly for use in validating passed in arrays as arguments to {@link #resolveVariablesWith(Map)}
     * usually when executing using {@link #execWithPlaceHolder(Map)}
     *
     * @param varName the vertex id to get the original shape for.
     * @return the set vertex
     */
    public long[] getOriginalShapeForPlaceHolder(String varName) {
        return placeHolderOriginalShapes.get(varName);
    }

    /**
     * Returns true if this vertex id is a place holder variable or not<br>
     * A place holder variable is one where the array shape(s) are currently known and can't yet be calculated
     *
     * @param varName the vertex id to test
     * @return True if the variable is a placeholder, false otherwise
     */
    public boolean isPlaceHolder(String varName) {
        Preconditions.checkState(variables.containsKey(varName), "No variable present in SameDiff instance with name \"%s\"", varName);
        return variables.get(varName).getVariable().isPlaceHolder();
    }


    /**
     * Resolve all ndarrays by updating the variables for each array specified in the given map.
     * An {@link IllegalStateException} will be thrown if not all arrays are specified for resolution.
     *
     * @param arrays the arrays to resolve.
     */
    public void resolveVariablesWith(Map<String, INDArray> arrays) {
        for (Map.Entry<String,INDArray> e : arrays.entrySet()) {
            SDVariable varForName = getVariable(e.getKey());
            if (varForName == null) {
                throw new ND4JIllegalStateException("No variable name found for " + e.getKey());
            }

            Variable v = variables.get(e.getKey());
            if(varForName.getVariableType() == VariableType.PLACEHOLDER){
                //Check shape:
                long[] shape = varForName.placeholderShape();
                long[] newShape = e.getValue().shape();
                Preconditions.checkState(shape.length == newShape.length, "Placeholder shape not compatible (mismatched rank): placeholder \"%s\" " +
                        "shape %s, got incompatible shape %s", e.getKey(), shape, newShape);
            }
        }


        for (val entry : arrays.entrySet()) {
            if (!variables.get(entry.getKey()).getVariable().isPlaceHolder()) {
                throw new ND4JIllegalStateException("Illegal variable " + entry.getKey() + " passed in. Variable found not to be a place holder variable");
            }

            val specifiedShape = getOriginalShapeForPlaceHolder(entry.getKey());
            //whole shape was specified: validate whether the input array shape is equal
            if (!Shape.isPlaceholderShape(specifiedShape)) {
                if (!Shape.shapeEquals(specifiedShape, entry.getValue().shape())) {
                    throw new ND4JIllegalStateException("Place holder shape specified was " + Arrays.toString(specifiedShape) + " but array shape was " + Arrays.toString(entry.getValue().shape()));
                }
            }

            associateArrayWithVariable(entry.getValue(), getVariable(entry.getKey()));
            setArrayForVariable(entry.getKey(), entry.getValue());
        }

        //declare resolved
        resolvedVariables = true;
    }

    /**
     * Updates the variable name property on the passed in variable, the reference in samediff, and returns the variable.
     * <p>
     * Note that if null for the new variable is passed in, it will just return the original input variable.
     *
     * @param varToUpdate the variable to update
     * @param newVarName  the new variable name
     * @return the passed in variable
     */
    public SDVariable updateVariableNameAndReference(SDVariable varToUpdate, String newVarName) {
        if (varToUpdate == null) {
            throw new NullPointerException("Null input: No variable found for updating!");
        }

        if(newVarName != null && variables.containsKey(newVarName) && varToUpdate != variables.get(newVarName).getVariable()){
            throw new IllegalStateException("Variable name \"" + newVarName + "\" already exists for a different SDVariable");
        }

        if (newVarName == null && variables.containsKey(varToUpdate.getVarName())) {
            //Edge case: suppose we do m1=sd.mean(in), m2=sd.mean(m1) -> both initially have the name
            // "mean" and consequently a new variable name needs to be generated
            newVarName = generateNewVarName(varToUpdate.getVarName(), 0);
        }

        if (newVarName == null || varToUpdate.getVarName().equals(newVarName)) {
            return varToUpdate;
        }

        val oldVarName = varToUpdate.getVarName();
        varToUpdate.setVarName(newVarName);
        updateVariableName(oldVarName, newVarName);
        return varToUpdate;
    }


    /**
     * Updates the variable name property on the passed in variables, its reference in samediff, and returns the variable.
     *
     * @param variablesToUpdate the variable to update
     * @param newVariableNames  the new variable name
     * @return the updated, passed in variables
     */
    public SDVariable[] updateVariableNamesAndReferences(SDVariable[] variablesToUpdate, String[] newVariableNames) {

        int numVariables = variablesToUpdate.length;
        SDVariable[] updatedVariables = new SDVariable[numVariables];

        for (int i = 0; i < numVariables; i++) {
            SDVariable varToUpdate = variablesToUpdate[i];
            String name = newVariableNames == null ? null : newVariableNames[i];
            updatedVariables[i] = updateVariableNameAndReference(varToUpdate, name);
        }

        return updatedVariables;
    }

    /**
     * Associate the current SameDiff instance with all ops and variables.
     * This is necessary to ensure that when dealing with shared state (usually with a SameDiff function such
     * as "grad" - the backward function) we have the correct SameDiff instance set for all ops/SDVariables.<br>
     * If this is not done, arrays and shapes could be fetched from the incorrect SameDiff instance for some methods
     */
    protected void associateSameDiffWithOpsAndVariables(){
        for(SDVariable var : variableMap().values()){
            var.setSameDiff(this);
        }
//        for(DifferentialFunction df : functionInstancesById.values()){
        for(SameDiffOp op : ops.values()){
            DifferentialFunction df = op.getOp();
            df.setSameDiff(this);

            //TODO: This is ugly but seemingly necessary
            //Finally, also set the SDVariable for each op
            //Otherwise: could have an op pointing to this SameDiff instance, but op's SDVariable's sameDiff field pointing
            // to another SameDiff instance. At which point, they could fetch shapes and arrays from some other instance
            // (i.e., not from this one that is currently executing)
            SDVariable[] args = df.args();
            if(args != null){
                for(SDVariable arg : args){
                    arg.setSameDiff(this);
                }
            }

            SDVariable[] outputs = df.outputVariables();
            if(outputs != null){
                for(SDVariable out : outputs){
                    out.setSameDiff(this);
                }
            }
        }
    }

    public Map<String,INDArray> execAll(Map<String,INDArray> placeholders){
        List<String> allVars = new ArrayList<>();
        for(Variable v : variables.values()){
            allVars.add(v.getName());
        }
        return exec(placeholders, allVars.toArray(new String[allVars.size()]));
    }

    public INDArray execSingle(Map<String,INDArray> placeholders, String output){
        return exec(placeholders, output).get(output);
    }

    public Map<String,INDArray> exec(Map<String,INDArray> placeholders, List<String> outputs){
        return exec(placeholders, outputs.toArray(new String[outputs.size()]));
    }

    public Map<String,INDArray> exec(Map<String,INDArray> placeholders, String... outputs){
        Preconditions.checkState(outputs != null && outputs.length > 0, "No outputs were specified");
        long threadId = Thread.currentThread().getId();
        if(!sessions.containsKey(threadId)){
            log.info("Creating new InferenceSession for thread {}", threadId);
            sessions.put(threadId, new InferenceSession(this));
        }

        List<String> phNames = inputs();
        if(placeholders == null && phNames != null){
            //Maybe user set placeholders before calling exec method?
            placeholders = placeholdersPerThread.get(Thread.currentThread().getId());
        }

        //Check that all placeholders are provided
        if(phNames != null && phNames.size() > 0) {
            Preconditions.checkNotNull(placeholders, "No placeholders were provided. Network has placeholders: %s", phNames);
            for (String s : phNames) {
                Preconditions.checkState(placeholders.containsKey(s), "No placeholder variable was provided for variable \"%s\"." +
                        " Cannot execute without all placeholders set", s);
            }
        }

        InferenceSession is = sessions.get(threadId);
        Map<String,INDArray> ret = is.output(Arrays.asList(outputs), placeholders);
        return ret;
    }

    /**
     * Print the given function for debugging (will not print functions)
     *
     * @param differentialFunction the function to print
     */
    public void printFunction(DifferentialFunction differentialFunction) {
        if (!logExecution)
            return;
        if (differentialFunction instanceof SDVariable)
            return;

        StringBuilder argShapes = new StringBuilder();
        for (val arg : differentialFunction.args()) {
            argShapes.append(" Variable " + arg.getVarName() +
                    " Shape for " + Arrays.toString(arg.getShape()));
        }

        for (val func : differentialFunction.outputVariables()) {
            argShapes.append("  Output variable " + func.getVarName() + " is " +
                    Arrays.toString(func.getShape()));
        }


        StringBuilder realShapes = new StringBuilder();
        for (val arg : differentialFunction.args()) {
            realShapes.append(" Input shape for " + arg.getVarName() + " is  " + Arrays.
                    toString(getShapeForVarName(arg.getVarName())));
        }

        for (val arg : differentialFunction.outputVariables()) {
            realShapes.append(" Output shape for " + arg.getVarName() + " is  " + Arrays.
                    toString(getShapeForVarName(arg.getVarName())));
        }
    }


    /**
     * Permute indices for the samediff/dl4j format.
     * Due to the dl4j format being NCHW, this is a
     * simple routine for returning permute indices.
     * This is typically used for model import.
     *
     * @param dataFormat the data format to permute
     * @return the permuted indices
     */
    @Deprecated //TODO MOVE TO UTILITY METHOD - or delete entirely
    public static int[] permuteDataFormatForSameDiff(String dataFormat, boolean weights) {
        val dl4jFormat = "NCHW";
        dataFormat = dataFormat.toUpperCase();
        //TF: filter_height, filter_width, in_channels, out_channels
        /**
         * N: filter_height
         * H: filter_width
         * W: in_channels
         * C: out_channels
         */


        /**
         *
         *
         */
        //DL4J: filter_height,out_channels,filter_width,in_channels
        // Weights should be: out channels, in channels, height,width
        int[] ret = new int[4];
        if (weights) {
            ret[0] = dataFormat.indexOf('W');
            ret[1] = dataFormat.indexOf('C');
            ret[2] = dataFormat.indexOf('N');
            ret[3] = dataFormat.indexOf('H');
            return ret;
        }


        //NHWC
        //DL4J: NCHW
        for (int i = 0; i < dataFormat.length(); i++) {
            if (dl4jFormat.indexOf(dataFormat.charAt(i)) < 0) {
                throw new ND4JIllegalStateException("Illegal convolution data format string passed in " + dataFormat + " must be some variant of NCHW");
            }
        }

        for (int i = 0; i < dl4jFormat.length(); i++) {
            ret[i] = dl4jFormat.indexOf(dataFormat.charAt(i));
        }

        return ret;
    }


    protected int asFlatNode(String name, @NonNull SameDiff scope, @NonNull FlatBufferBuilder bufferBuilder) {
        int scopeName = bufferBuilder.createString(name);

        int flatNode = FlatNode.createFlatNode(bufferBuilder,
                scopeName,
                scopeName,
                OpType.LOGIC,
                10, // hardcoded value
                0,
                0,
                0,
                (byte) 0,
                0,
                0,
                0,
                0,
                -1,
                0, 0, 0, 0,0, 0);

        return flatNode;
    }

    /**
     * Note: INTENDED FOR DEVELOPER USE<br>
     * This method extract base variable name and output index (if exists) from raw variable name.
     * I.e:
     * - if variable name is "Unstack_2", result will be Pair("Unstack_2", 0)
     * - if variable name is "Unstack_2:12", result will be Pair("Unstack_2", 12)
     *
     * @param varName
     * @return
     */
    public static Pair<String, Integer> parseVariable(@NonNull String varName) {
        if (!varName.contains(":")) {
            return Pair.pairOf(varName, 0);
        } else {
            val split = varName.split(":");
            val index = Integer.valueOf(split[split.length - 1]);
            if (split.length == 2)
                return Pair.pairOf(split[0], index);
            else {
                val builder = new StringBuilder();
                for (int e = 0; e < split.length - 1; e++) {
                    builder.append(split[e]);

                    if (e < split.length - 2)
                        builder.append(":");
                }

                return Pair.pairOf(builder.toString(), index);
            }
        }
    }

    protected int asFlatNode(@NonNull DifferentialFunction node, @NonNull FlatBufferBuilder bufferBuilder, List<SDVariable> variables,
                             Map<String, Integer> reverseMap, Map<String, Integer> forwardMap, Map<String, Integer> framesMap, AtomicInteger idCounter, Integer id) {
        val opName = node.opName();
        val hash = FlatBuffersMapper.getOpNum(node.opName(), node.opType());
        //log.info("Exporting node: [{}:<{}> ; OpType: {}; Hash/opNum: {}]", node.opName(), node.tensorflowName(), node.opType(), hash);

        double[] extras;
        if(node.opType() == Op.Type.CUSTOM){
            CustomOp op = (CustomOp)node;
            extras = op.tArgs();
        } else {
            extras = node.getExtraArgs() != null ? new double[node.getExtraArgs().length] : new double[0];
            for (int e = 0; e < extras.length; e++) {
                extras[e] = ((Number) node.getExtraArgs()[e]).doubleValue();
            }
        }

        boolean[] boolArgs = null;
        long[] extraBits = null;
        if (node.opType() == Op.Type.CUSTOM) {
            DynamicCustomOp dynamicCustomOp = (DynamicCustomOp) node;
            extraBits = dynamicCustomOp.iArgs();
            boolArgs = dynamicCustomOp.bArgs();
        } else if (node instanceof Enter) {
            // in case of Enter node we'll be storing unique frame reference
            val frameName = ((Enter) node).getFrameName();
            if (!framesMap.containsKey(frameName))
                framesMap.put(frameName, idCounter.incrementAndGet());

            extraBits = new long[]{framesMap.get(frameName).intValue()};
        } else
            extraBits = new long[]{};

        if (node.opType() == Op.Type.REDUCE_BOOL || node.opType() == Op.Type.REDUCE_SAME || node.opType() == Op.Type.REDUCE_FLOAT || node.opType() == Op.Type.REDUCE_LONG) {
            val op = (ReduceOp) node;

            boolArgs = new boolean[2];
            boolArgs[0] = op.isKeepDims();
            boolArgs[1] = true; // always new format
        } else if (node.opType() == Op.Type.INDEXREDUCE) {
            val op = (IndexAccumulation) node;

            boolArgs = new boolean[2];
            boolArgs[0] = op.isKeepDims();
            boolArgs[1] = true; // always new format
        }

        val inPaired = new ArrayList<Integer>();

        int[] outputIds = null;
        SDVariable[] outputVertexId = null;

        try {
            outputVertexId = node.outputVariables();
            outputIds = new int[outputVertexId.length];
            for (int i = 0; i < outputIds.length; i++) {
                outputIds[i] = variables.indexOf(outputVertexId[i]);
            }
        } catch (ND4UnresolvedOutputVariables e) {

            outputIds = new int[0];
            outputVertexId = null;
        } catch (Exception e) {
            throw new ND4JIllegalStateException(e);
        }


        SDVariable[] inputs = node.args();
        for (SDVariable input : inputs) {
            String varName = input.getVarName();
            int outIdx;
            if(this.variables.get(varName).getOutputOfOp() != null){
                DifferentialFunction df = ops.get(this.variables.get(varName).getOutputOfOp()).getOp();
                outIdx = ops.get(df.getOwnName()).getOutputsOfOp().indexOf(varName);
            } else {
                outIdx = 0;
            }

            if (!reverseMap.containsKey(varName)) {
                if (varName.contains("NextIteration")) {
                    // forward declaration: Merge node in case of loop will be referring to NextIteration node, which wasn't announced yet
                    int fwdNodeId = idCounter.incrementAndGet();
                    forwardMap.put(varName, fwdNodeId);
                    reverseMap.put(varName, fwdNodeId);
                } else {
                    throw new ND4JIllegalStateException("Unknown variable used in input: [" + varName + "]");
                }
            }

            int nodeId = reverseMap.get(varName);
            inPaired.add(IntPair.createIntPair(bufferBuilder, nodeId, outIdx));
        }

        log.trace("Own Name: {}", node.getOwnName());
        int ownId = id != null ? id : idCounter.incrementAndGet();  //forwardMap.containsKey(node.getOwnName()) ? forwardMap.get(node.getOwnName()) : idCounter.incrementAndGet();
        String[] outNames = node.outputVariablesNames();
        for(String s : outNames){
            if(!reverseMap.containsKey(s)){
                reverseMap.put(s, ownId);
            }
        }

        int[] dims;
        if(node.opType() == Op.Type.REDUCE_FLOAT || node.opType() == Op.Type.REDUCE_SAME || node.opType() == Op.Type.REDUCE_BOOL || node.opType() == Op.Type.REDUCE_LONG || node.opType() == Op.Type.INDEXREDUCE || node.opType() == Op.Type.REDUCE3){
            dims = node.getDimensions();
            if(dims == null)
                dims = new int[0];
        } else {
            dims = new int[0];
        }
        Map<String,Object> fnProps = node.propertiesForFunction();
        int[] flatProperties = FlatBuffersMapper.mapFunctionPropertiesToFlatProperties(bufferBuilder, fnProps);
        int propIdx = FlatNode.createPropertiesVector(bufferBuilder, flatProperties);

        int nodesIn = FlatNode.createInputVector(bufferBuilder, new int[]{});
        int nodesInPaired = FlatNode.createInputPairedVector(bufferBuilder, Ints.toArray(inPaired));
        int nodesOut = FlatNode.createOutputVector(bufferBuilder, outputIds);
        int extraz = FlatNode.createExtraParamsVector(bufferBuilder, extras);
        int integerArgs = FlatNode.createExtraIntegerVector(bufferBuilder, extraBits);
        int bArgs = FlatNode.createExtraBoolsVector(bufferBuilder, boolArgs != null ? boolArgs : new boolean[0]);
        int dimensions = FlatNode.createDimensionsVector(bufferBuilder, dims);
        int fname = bufferBuilder.createString(node.getOwnName());
        int scopeName = bufferBuilder.createString("");
        int scalar = 0;
        if(node instanceof ScalarOp){
            ScalarOp sOp = (ScalarOp)node;
            INDArray s = sOp.scalar();
            if(s != null){
                scalar = s.toFlatArray(bufferBuilder);
            }
        }


        if (node.opType() == null)
            log.warn("Null-op node: {}", node);


        List<String> outVarNames = node.getSameDiff().ops.get(node.getOwnName()).getOutputsOfOp();
        int[] outVarNamesStringsOffsets = new int[outVarNames == null ? 0 : outVarNames.size()];
        for( int i=0; i<outVarNamesStringsOffsets.length; i++ ){
            outVarNamesStringsOffsets[i] = bufferBuilder.createString(outVarNames.get(i));
        }
        int outVarNamesOffset = FlatNode.createOutputNamesVector(bufferBuilder, outVarNamesStringsOffsets);

        int opNameOffset = bufferBuilder.createString(opName);

        byte[] outTypes = new byte[outVarNames.size()];
        int i=0;
        for(String s : outVarNames){
            SDVariable v = getVariable(s);
            outTypes[i++] = FlatBuffersMapper.getDataTypeAsByte(v.dataType());
        }
        int outTypesOffset = FlatNode.createOutputTypesVector(bufferBuilder, outTypes);

        int flatNode = FlatNode.createFlatNode(
                bufferBuilder,
                ownId,
                fname,
                FlatBuffersMapper.getFlatOpType(node.opType()),
                hash,
                propIdx,
                nodesIn,
                nodesInPaired,
                nodesOut,
                extraz,
                integerArgs,
                bArgs,
                dimensions,
                -1,     //Device
                0,      //Scope ID
                scopeName,      //Scope name
                outVarNamesOffset,
                opNameOffset,
                outTypesOffset,   //Output types
                scalar
        );

        return flatNode;
    }

    /**
     * This method exports the current SameDiff instance into FlatBuffers format, returning the array ops and
     * all arrays as a ByteBuffer containing the FlatBuffers format data
     *
     * @param configuration - ExecutorConfiguration to be embedded into serialized graph
     * @return a ByteBuffer holding the exported FlatBuffers representation of the graph
     */
    public ByteBuffer asFlatBuffers(@NonNull ExecutorConfiguration configuration) {
        return asFlatBuffers(0, configuration);
    }

    /**
     * This method exports the current SameDiff instance into FlatBuffers format, returning the array ops and
     * all arrays as a ByteBuffer containing the FlatBuffers format data
     *
     * @param configuration - ExecutorConfiguration to be embedded into serialized graph
     * @return a ByteBuffer holding the exported FlatBuffers representation of the graph
     */
    public ByteBuffer asFlatBuffers(long graphId, @NonNull ExecutorConfiguration configuration) {
        Nd4j.getExecutioner().commit();
        val bufferBuilder = new FlatBufferBuilder(1024);
        val idCounter = new AtomicInteger(0);

        val flatVariables = new ArrayList<Integer>();
        val flatOffsets = new ArrayList<Integer>();
        val flatNodes = new ArrayList<Integer>();

        // first of all we build VariableSpace dump
        val variableList = new ArrayList<SDVariable>(variables());
        val reverseMap = new LinkedHashMap<String, Integer>();
        val forwardMap = new LinkedHashMap<String, Integer>();
        val framesMap = new LinkedHashMap<String, Integer>();

        int idx = 0;
        val idxForOps = new IdentityHashMap<DifferentialFunction,Integer>();
        List<SDVariable> allVars = variables();
        for (SDVariable variable : allVars) {
            INDArray arr = variable.getArr();
            log.trace("Exporting variable: [{}]", variable.getVarName());

            //If variable is the output of some op - let's use the ONE index for exporting, and properly track the output
            // numbers. For example, unstack(x) -> y0, y1, y2 -> the y's should be say (3,0), (3,1), (3,2) NOT (4,0), (5,0), (6,0)
            String varName = variable.getVarName();
            int varIdx;
            int outputNum;
            if(variables.get(varName).getOutputOfOp() != null){
                //This variable is the output of a node
                DifferentialFunction df = ops.get(variables.get(varName).getOutputOfOp()).getOp();
                if(!idxForOps.containsKey(df)){
                    varIdx = idCounter.incrementAndGet();
                    idxForOps.put(df, varIdx);
                } else {
                    varIdx = idxForOps.get(df);
                }
                String[] outNames = df.outputVariablesNames();
                outputNum = ArrayUtils.indexOf(outNames, varName);
                Preconditions.checkState(outputNum >= 0, "Variable name \"%s\" not found in list of outputs: %s", varName, outNames);
            } else {
                varIdx = idCounter.incrementAndGet();
                outputNum = 0;
            }


            reverseMap.put(variable.getVarName(), varIdx);
            log.trace("Adding [{}] as [{}]", variable.getVarName(), varIdx);

//            val arr = variable.getArr();

            int shape = 0;
            int name = bufferBuilder.createString(variable.getVarName());
            int array = arr == null ? 0 : arr.toFlatArray(bufferBuilder);
            int id = IntPair.createIntPair(bufferBuilder, varIdx, outputNum);
            byte varType = (byte)variable.getVariableType().ordinal();

            if (variable.getVariableType() == VariableType.PLACEHOLDER) {
                val shp = variable.getShape();
                shape = FlatVariable.createShapeVector(bufferBuilder, shp);
            }

            int flatVariable = FlatVariable.createFlatVariable(bufferBuilder, id, name,  FlatBuffersMapper.getDataTypeAsByte(variable.dataType()), shape, array, -1, varType);
            flatVariables.add(flatVariable);
        }

        //add functions
        for(SameDiffOp op : ops.values()){
            DifferentialFunction func = op.getOp();
            Integer fnId = idxForOps.get(func);
            flatNodes.add(asFlatNode(func, bufferBuilder, variableList, reverseMap, forwardMap, framesMap, idCounter, fnId));
        }

        // we're dumping scopes now
        for (Map.Entry<String, SameDiff> scope : sameDiffFunctionInstances.entrySet()) {
            if(scope.getKey().equalsIgnoreCase("grad")){
                //Skip the gradient function for export
                continue;
            }

            flatNodes.add(asFlatNode(scope.getKey(), scope.getValue(), bufferBuilder));
            val currVarList = new ArrayList<SDVariable>(scope.getValue().variables());
            // converting all ops from node
            for (val node : scope.getValue().variables()) {
                INDArray arr = node.getArr();
                if (arr == null) {
                    continue;
                }

                int name = bufferBuilder.createString(node.getVarName());
                int array = arr.toFlatArray(bufferBuilder);
                int id = IntPair.createIntPair(bufferBuilder, ++idx, 0);

                val pair = parseVariable(node.getVarName());
                reverseMap.put(pair.getFirst(), idx);

                log.trace("Adding [{}] as [{}]", pair.getFirst(), idx);

                byte varType = (byte)node.getVariableType().ordinal();
                int flatVariable = FlatVariable.createFlatVariable(bufferBuilder, id, name, FlatBuffersMapper.getDataTypeAsByte(arr.dataType()),0, array, -1, varType);
                flatVariables.add(flatVariable);
            }

            //add functions
            for(SameDiffOp op : scope.getValue().ops.values()){
                DifferentialFunction func = op.getOp();
                flatNodes.add(asFlatNode(func, bufferBuilder, currVarList, reverseMap, forwardMap, framesMap, idCounter, null));
            }
        }

        int outputsOffset = FlatGraph.createVariablesVector(bufferBuilder, Ints.toArray(flatOffsets));
        int variablesOffset = FlatGraph.createVariablesVector(bufferBuilder, Ints.toArray(flatVariables));
        int nodesOffset = FlatGraph.createNodesVector(bufferBuilder, Ints.toArray(flatNodes));

        int numPlaceholders = 0;
        for(SDVariable v : variables()){
            if(v.isPlaceHolder()){
                numPlaceholders++;
            }
        }

        int[] placeholderOffsets = new int[numPlaceholders];
        if(numPlaceholders > 0){
            int i=0;
            for(SDVariable v : variables()){
                if(!v.isPlaceHolder())
                    continue;
                placeholderOffsets[i++] = bufferBuilder.createString(v.getVarName());
            }
        }
        int placeholdersOffset = FlatGraph.createPlaceholdersVector(bufferBuilder, placeholderOffsets);

        int fg = FlatGraph.createFlatGraph(bufferBuilder, graphId, variablesOffset, nodesOffset, outputsOffset, configuration.getFlatConfiguration(bufferBuilder), placeholdersOffset);
        bufferBuilder.finish(fg);

        synchronized (this) {
            for(Map.Entry<String,Integer> e : reverseMap.entrySet()){
                this.variables.get(e.getKey()).setVariableIndex(e.getValue());
            }
        }

        return bufferBuilder.dataBuffer();
    }

    public FlatGraph asFlatGraph() {
        return FlatGraph.getRootAsFlatGraph(this.asFlatBuffers());
    }

    /**
     * This method returns FlatGraph structure
     *
     * @param configuration
     * @return
     */
    public FlatGraph asFlatGraph(long graphId, ExecutorConfiguration configuration) {
        return FlatGraph.getRootAsFlatGraph(asFlatBuffers(graphId, configuration));
    }

    /**
     * This method exports the current SameDiff instance into FlatBuffers format, returning the array ops and
     * all arrays as a ByteBuffer containing the FlatBuffers format data
     *
     * @return a ByteBuffer holding the exported FlatBuffers representation of the graph
     */
    public ByteBuffer asFlatBuffers() {
        val configuration = ExecutorConfiguration.builder()
                .outputMode(OutputMode.VARIABLE_SPACE)
                .executionMode(org.nd4j.autodiff.execution.conf.ExecutionMode.SEQUENTIAL)
                .profilingMode(OpExecutioner.ProfilingMode.DISABLED)
                .gatherTimings(true)
                .build();

        return asFlatBuffers(configuration);
    }


    /**
     * Save this samediff instance with its training config.
     * Note that if a training configuration is not defined,
     * an {@link IllegalStateException} is thrown.
     *
     * @param outputStream the output stream to write to
     * @throws IOException
     */
    public void saveWithTrainingConfig(OutputStream outputStream) throws IOException {
        if(this.trainingConfig == null) {
            throw new IllegalStateException("No training configuration found!");
        }

        saveWithTrainingConfig(this.trainingConfig,outputStream);
    }



    /**
     * Save this samediff instance with its training config.
     * Note that if a training configuration is not defined,
     * an {@link IllegalStateException} is thrown.
     *
     * @param outputFile the output stream to write to
     * @throws IOException
     */
    public void saveWithTrainingConfig(File outputFile) throws IOException {
        if(this.trainingConfig == null) {
            throw new IllegalStateException("No training configuration found!");
        }

        try(BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            saveWithTrainingConfig(this.trainingConfig, bufferedOutputStream);
            bufferedOutputStream.flush();
        }

    }


    /**
     * Save this samediff instance as a zip file
     * with the training configuration
     * @param trainingConfig the training configuration to save
     * @param outputStream the output stream to write to
     * @throws IOException
     */
    public void saveWithTrainingConfig(TrainingConfig trainingConfig,OutputStream outputStream) throws  IOException {
        ObjectMapper objectMapper = ObjectMapperHolder.getJsonMapper();
        String configJson = objectMapper.writeValueAsString(trainingConfig);
        ZipOutputStream zipfile = new ZipOutputStream(new CloseShieldOutputStream(outputStream));
        ZipEntry config = new ZipEntry(TRAINING_CONFIG_JSON_ZIP_ENTRY_NAME);
        zipfile.putNextEntry(config);
        zipfile.write(configJson.getBytes());

        ZipEntry sameDiff = new ZipEntry(SAMEDIFF_FILE_ENTRY_NAME);
        zipfile.putNextEntry(sameDiff);

        val fb = asFlatBuffers();
        val offset = fb.position();

        val array = fb.array();

        try (BufferedOutputStream zipFileOutputStream = new BufferedOutputStream(zipfile);
             val dos = new DataOutputStream(zipFileOutputStream)) {
            dos.write(array, offset, array.length - offset);
        }
    }


    /**
     * Restore a {@link SameDiff}
     * instance from a configuration
     * zip file
     * @param file the file to restore from
     * @return the associated samediff instance
     * @throws IOException
     */
    public static SameDiff restoreFromTrainingConfigZip(File file) throws IOException {
        ZipFile zipFile = new ZipFile(file);
        ZipEntry config = zipFile.getEntry(TRAINING_CONFIG_JSON_ZIP_ENTRY_NAME);
        TrainingConfig trainingConfig = null;
        try(InputStream stream = zipFile.getInputStream(config)) {
            byte[] read = IOUtils.toByteArray(stream);
            trainingConfig = ObjectMapperHolder.getJsonMapper().readValue(read,TrainingConfig.class);
        }

        SameDiff ret = null;

        ZipEntry sameDiffFile = zipFile.getEntry(SAMEDIFF_FILE_ENTRY_NAME);
        try(InputStream stream = zipFile.getInputStream(sameDiffFile)) {
            byte[] read = IOUtils.toByteArray(stream);
            ret = SameDiff.fromFlatBuffers(ByteBuffer.wrap(read));
        }


        ret.setTrainingConfig(trainingConfig);
        ret.initializeTraining();
        return ret;
    }

    /**
     * This method converts SameDiff instance to
     * FlatBuffers and saves it to file which
     * can be restored later
     *
     * @param file File to save the FlatBuffers serialized graph (including arrays) to
     */
    public void asFlatFile(@NonNull File file) throws IOException {
        val fb = asFlatBuffers();
        val offset = fb.position();

        val array = fb.array();

        try (val fos = new FileOutputStream(file); val bos = new BufferedOutputStream(fos); val dos = new DataOutputStream(bos)) {
            dos.write(array, offset, array.length - offset);
        }
    }

    /**
     * This method converts SameDiff instance to FlatBuffers and saves it to file which can be restored later
     *
     * @param file File to save the FlatBuffers serialized graph (including arrays) to
     */
    public void asFlatFile(@NonNull File file, @NonNull ExecutorConfiguration configuration) throws IOException {
        val fb = asFlatBuffers(configuration);
        val offset = fb.position();

        val array = fb.array();

        try (val fos = new FileOutputStream(file); val bos = new BufferedOutputStream(fos); val dos = new DataOutputStream(bos)) {
            dos.write(array, offset, array.length - offset);
        }
    }


    /**
     * Create a {@link SameDiff}
     * instance from a file.
     * The method to save the file is
     * {@link #asFlatFile(File)}
     * @param file the file to load from
     * @return the loaded same diff instance
     * @throws IOException
     */
    public static SameDiff fromFlatFile(@NonNull File file) throws IOException {
        byte[] bytes;
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            bytes = IOUtils.toByteArray(is);
        }

        ByteBuffer bbIn = ByteBuffer.wrap(bytes);
        return fromFlatBuffers(bbIn);
    }

    /**
     * Create a {@link SameDiff}
     * instance from a byte buffers
     * instance.
     * @param bbIn the input byte buffer
     * @return the created samediff instance
     * @throws IOException
     */
    public static SameDiff fromFlatBuffers(ByteBuffer bbIn) throws IOException {

        FlatGraph fg = FlatGraph.getRootAsFlatGraph(bbIn);

        int numOps = fg.nodesLength();
        int numVars = fg.variablesLength();
        List<FlatNode> ops = new ArrayList<>(numOps);
        for( int i=0; i<numOps; i++ ){
            ops.add(fg.nodes(i));
        }
        List<FlatVariable> vars = new ArrayList<>(numVars);
        for( int i = 0; i < numVars; i++) {
            vars.add(fg.variables(i));
        }

        FlatConfiguration conf = fg.configuration();

        /* Reconstruct the graph
        We'll do the reconstruction manually here, rather than using sd.var(...), so that we have more control
        over the final result.
         */

        SameDiff sd = SameDiff.create();

        //Reconstruct placeholders
        int numPlaceholders = fg.placeholdersLength();
        Set<String> ph = new LinkedHashSet<>();
        for(int i=0; i<numPlaceholders; i++ ){
            ph.add(fg.placeholders(i));
        }

        //Reconstruct variables:
        Map<Integer,SDVariable> varNodeIds = new HashMap<>();
        Map<Pair<Integer,Integer>, SDVariable> variablesByNodeAndOutNum = new HashMap<>();
        Map<String,List<SDVariable>> variablesByName = new HashMap<>();
        for(FlatVariable v : vars){
            int shapeLength = v.shapeLength();
            long[] shape = new long[shapeLength];
            for( int i = 0; i < shapeLength; i++) {
                shape[i] = v.shape(i);
            }

            String n = v.name();

            byte dtypeByte = v.dtype();
            org.nd4j.linalg.api.buffer.DataType dtype = FlatBuffersMapper.getDataTypeFromByte(dtypeByte);

            //TODO Infer this properly! Could be constant, etc.
            VariableType vt = VariableType.values()[v.variabletype()];
            SDVariable var = new SDVariable(n, vt, sd, shape, dtype, null);
            sd.variables.put(n, Variable.builder().name(n).variable(var).build());
            sd.variableNameToShape.put(n, shape);


            FlatArray fa = v.ndarray();
            if(fa != null && vt != VariableType.ARRAY){
                INDArray arr = Nd4j.createFromFlatArray(fa);
                sd.setArrayForVariable(n, arr);
            }

            IntPair id = v.id();    //First value: node (op) id. Second: output number
            variablesByNodeAndOutNum.put(new Pair<>(id.first(), id.second()), var);

            if(!variablesByName.containsKey(n)){
                variablesByName.put(n, new ArrayList<SDVariable>());
            }

            List<SDVariable> list = variablesByName.get(n);
            list.add(var);
        }

        //Reconstruct ops:
        for(FlatNode fn : ops){
            DifferentialFunction df = FlatBuffersMapper.fromFlatNode(fn);
            String name = fn.name();
            df.setSameDiff(sd);
            df.setOwnName(name);
            if(sd.ops.containsKey(name)){
                sd.ops.get(name).setOp(df);
            } else {
                sd.ops.put(name, SameDiffOp.builder().name(name).op(df).build());
            }

            int outLength = fn.outputLength();
            int[] outs = new int[outLength];
            for( int i=0; i<outLength; i++ ){
                outs[i] = fn.output(i);
            }

            int opId = fn.id();

            //Work out inputs and outputs:
            int[] output = new int[fn.outputLength()];
            for (int i = 0; i < output.length; i++) {
                output[i] = fn.output(i);
            }
            int[] input = new int[fn.inputLength()];
            for (int i = 0; i < input.length; i++) {
                input[i] = fn.input(i);
            }
            IntPair[] inputPaired = new IntPair[fn.inputPairedLength()];
            List<Pair<Integer,Integer>> intPairList = new ArrayList<>();
            for (int i = 0; i < inputPaired.length; i++) {
                inputPaired[i] = fn.inputPaired(i);
                intPairList.add(new Pair<>(inputPaired[i].first(), inputPaired[i].second()));
            }

            String[] inputNames = new String[inputPaired.length];
            for(int i=0; i<inputPaired.length; i++ ){
                int nodeId = inputPaired[i].first();
                int nodeOutNum = inputPaired[i].second();
                SDVariable varIn = variablesByNodeAndOutNum.get(new Pair<>(nodeId, nodeOutNum));
                if(varIn == null){
                    //The variable corresponding to this op was not
                }
                inputNames[i] = varIn.getVarName();
            }
            sd.ops.get(df.getOwnName()).setInputsToOp(Arrays.asList(inputNames));

            //Record that input variables are input to this op
            for(String inName : inputNames) {
                Variable v = sd.getVariables().get(inName);
                if(v.getInputsForOp() == null){
                    v.setInputsForOp(new ArrayList<String>());
                }
                if(!v.getInputsForOp().contains(df.getOwnName())){
                    v.getInputsForOp().add(df.getOwnName());
                }
            }

            List<SDVariable> varsForOp = variablesByName.get(name);

            //Can't assume that variables for the op have all been defined. For example, if we export before execution in SameDiff
            //In theory, we can reconstruct the output variables (minus names) if we know the number of op outputs
            //And we can calculate the op outputs - in most cases - after the op has been created and parameters set
            int numOutputs = df.getNumOutputs();
            if(numOutputs <= 0){
                numOutputs = fn.outputLength();
            }

            String[] varNames = null;
            if(varsForOp != null && varsForOp.size() == numOutputs){
                varNames = new String[varsForOp.size()];
                for( int i=0; i<varNames.length; i++ ){
                    varNames[i] = varsForOp.get(i).getVarName();
                    sd.getVariables().get(varNames[i]).setOutputOfOp(df.getOwnName());
                }
                sd.ops.get(df.getOwnName()).setOutputsOfOp(Arrays.asList(varNames));
            } else {
                //We're missing some variables...
                int outputNamesLength = fn.outputNamesLength();
                varNames = new String[outputNamesLength];
                for( int i=0; i<outputNamesLength; i++ ){
                    String n = fn.outputNames(i);
                    varNames[i] = n;
                    if(!sd.variables.containsKey(n)){
                        //Need to create the variable - perhaps it wasn't exported. Note output of node -> can only be VARIABLE type
                        SDVariable var = new SDVariable(n, VariableType.VARIABLE, sd, null, null, null);
                        sd.variables.put(n, Variable.builder().name(n).variable(var).build());
                        variablesByNodeAndOutNum.put(new Pair<>(opId, i), var);
                    }
                    sd.getVariables().get(varNames[i]).setOutputOfOp(df.getOwnName());
                }
                sd.ops.get(df.getOwnName()).setOutputsOfOp(Arrays.asList(varNames));
            }

            //Check the op mapping int he variablesByNodeAndOutputNum
            //For multi-output ops, variables will have their own index, not related to the op index
            for( int i=0; i<varNames.length; i++ ){
                Pair<Integer,Integer> p = new Pair<>(opId, i);
                if(!variablesByNodeAndOutNum.containsKey(p)){
                    variablesByNodeAndOutNum.put(p, sd.getVariable(varNames[i]));
                }
            }
        }

        return sd;
    }

    /**
     * This method returns a text representation of the "flattened" graph.
     *
     * @return String representation of the graph
     * @see #summary()
     */
    public String asFlatPrint() {
        val sb = new StringBuilder();
        val fb = asFlatBuffers();

        val graph = FlatGraph.getRootAsFlatGraph(fb);

        sb.append("\nExternal variables:\n\n");
        for (int e = 0; e < graph.variablesLength(); e++) {
            val var = graph.variables(e);
            val ndarray = Nd4j.createFromFlatArray(var.ndarray());

            sb.append(var.id().first())
                    .append(":<").append(var.name()).append("> ")
                    .append(Arrays.toString(ndarray.shapeInfoDataBuffer().asInt())).append("; Values: ").append(Arrays.toString(ndarray.data().asFloat())).append(";\n");
        }

        val map = Nd4j.getExecutioner().getCustomOperations();


        sb.append("\nOps sequence:\n\n");
        for (int e = 0; e < graph.nodesLength(); e++) {
            val node = graph.nodes(e);

            log.info("{}:<{}>", node.id(), node.name());
            sb.append(node.id())
                    .append(":<").append(node.name()).append("> ").append(FlatBuffersMapper.getTypeFromByte(node.opType()));

            if (FlatBuffersMapper.getTypeFromByte(node.opType()) != Op.Type.CUSTOM)
                sb.append(": ").append(node.opNum());
            else {
                val keys = map.keySet();
                String opName = null;
                for (val k : keys) {
                    val d = map.get(k);
                    if (d.getHash() == node.opNum())
                        opName = k;
                }

                if (opName == null)
                    opName = "unknown";

                sb.append(": ").append(opName);
            }

            sb.append("; Inputs: {");

            for (int i = 0; i < node.inputPairedLength(); i++) {
                val pair = node.inputPaired(i);

                sb.append("[").append(pair.first()).append(":").append(pair.second()).append("]");

                if (i < node.inputPairedLength() - 1)
                    sb.append(", ");
            }

            sb.append("};");
            sb.append(" OpNum: {").append(node.opNum()).append("};");

            sb.append("\n");
        }


        return sb.toString();
    }

    /**
     * Generate and return a String representation of the current SameDiff instance<br>
     * Reports variables, ops, SameDiff function instances, and (where possible) array shapes.<br>
     * For ops, the input and output variables are reported.<br>
     * For variables, the ops that they are inputs to - or outputs of - are also reported
     *
     * @return A String representation of the SameDiff instance
     */
    public String summary() {

        Map<String, SDVariable> varMap = variableMap();
        DifferentialFunction[] functions = functions();


        int countVarsWithArrays = 0;
        for (String s : varMap.keySet()) {
            if (getArrForVarName(s) != null) {
                countVarsWithArrays++;
            }
        }

        StringBuilder sb = new StringBuilder();
        String format = "%-25s%-20s";
        sb.append("--- Summary ---\n");
        sb.append(String.format(format, "Variables:", varMap.size())).append(" (").append(countVarsWithArrays).append(" with arrays)").append("\n")
                .append(String.format(format, "Functions:", functions.length)).append("\n")
                .append(String.format(format, "SameDiff Function Defs:", sameDiffFunctionInstances.size()))
                .append("\n\n");

        sb.append("--- Variables ---\n");
        //Work out which function - if any - this arg is an output of...
        Map<String, String> outputOfFn = new HashMap<>();
        int maxLengthOutputOf = 22;     //Length of "- Output Of Function -"
        int maxLengthOfName = 8;       //Length of "- Name -"
        for (String s : varMap.keySet()) {
            String outputOf = null;
//            for (Map.Entry<String, String[]> dfToArgs : outgoingArgsReverse.entrySet()) {
            for(SameDiffOp op : ops.values()){
                List<String> outputsOfOp = op.getOutputsOfOp();
                if (outputsOfOp != null && outputsOfOp.contains(s)) {
                    outputOf = op.getName();
                    break;
                }
            }

            if (outputOf == null) {
                outputOf = "<none>";
            } else {
                DifferentialFunction d = getFunctionById(outputOf);
                outputOf = d.getOwnName() + "(" + d.opName() + ")";
            }
            outputOfFn.put(s, outputOf);
            maxLengthOutputOf = Math.max(maxLengthOutputOf, outputOf.length());
            maxLengthOfName = Math.max(maxLengthOfName, s.length());
        }
        maxLengthOutputOf += 2;
        maxLengthOfName += 2;

        //Create the output for values:
        format = "%-" + maxLengthOfName + "s%-20s%-" + maxLengthOutputOf + "s%-20s";
        sb.append(String.format(format, "- Name -", "- Array Shape -", "- Output Of Function -", "- Inputs To Functions -")).append("\n");
        for (String s : varMap.keySet()) {
            INDArray arr = getArrForVarName(s);
            String arrayShape = "-";
            if (arr != null) {
                arrayShape = Arrays.toString(arr.shape());
            }

            List<String> argNames = variables.get(s).getInputsForOp();
            String dfArrStr = "";
            if (argNames != null) {
                dfArrStr = argNames.toString();
            }

            String outputOfStr = outputOfFn.get(s);

            sb.append(String.format(format, s, arrayShape, outputOfStr, dfArrStr)).append("\n");
        }

        sb.append("\n\n--- Functions ---\n");

        //First: work out the amount of space we need for inputs and outputs...
        List<String> dfInputStr = new ArrayList<>();
        List<String> dfOutputStr = new ArrayList<>();
        int maxInLength = 10;       //Length of "- Inputs -"
        int maxOutLength = 11;      //Length of "- Outputs -"
        int maxOpNameLength = 17;   //Default to min of 17 - length of "- Function Name -"
        int maxDfClassNameLength = 10;  //Default to min of 10
        for (DifferentialFunction df : functions) {
            String[] argNames = df.argNames();
            String[] outNames = df.outputVariablesNames();

            String argStr = Arrays.toString(argNames);
            String outStr = Arrays.toString(outNames);

            maxInLength = Math.max(maxInLength, argStr.length());
            maxOutLength = Math.max(maxOutLength, outStr.length());

            dfInputStr.add(argStr);
            dfOutputStr.add(outStr);

            String name = df.getOwnName() == null ? df.opName() : df.getOwnName();
            maxOpNameLength = Math.max(maxOpNameLength, name.length());
            maxDfClassNameLength = Math.max(maxDfClassNameLength, df.getClass().getSimpleName().length());
        }
        //Extra padding space
        maxInLength += 2;
        maxOutLength += 2;
        maxOpNameLength += 2;
        maxDfClassNameLength += 2;


        format = "%-5s%-" + maxOpNameLength + "s%-" + maxDfClassNameLength + "s%-" + maxInLength + "s%-" + maxOutLength + "s";
        sb.append(String.format(format, "", "- Function Name -", "- Op -", "- Inputs -", "- Outputs -")).append("\n");
        for (int i = 0; i < functions.length; i++) {
            DifferentialFunction df = functions[i];
            String fnName = df.getOwnName() == null ? df.opName() : df.getOwnName();

            sb.append(String.format(format, String.valueOf(i), fnName, df.getClass().getSimpleName(), dfInputStr.get(i), dfOutputStr.get(i))).append("\n");
        }

        if (sameDiffFunctionInstances.size() > 0) {
            sb.append("\n\n--- SameDiff Defined Functions ---\n");
            format = "%-20s%-15s%-15s%-15s";
            sb.append(String.format(format, "- Name -", "- Variables -", "- Functions -", "- Fn Defs -")).append("\n");
            for (Map.Entry<String, SameDiff> e : sameDiffFunctionInstances.entrySet()) {
                SameDiff sd = e.getValue();
                int vars = sd.variableMap().size();
                int fns = (sd.functions() == null ? 0 : sd.functions().length);
                int defFns = sd.definedFunctionNames().size();

                sb.append(String.format(format, e.getKey(), String.valueOf(vars), String.valueOf(fns), String.valueOf(defFns))).append("\n");
            }
        }

        return sb.toString();
    }


    public Map<String,org.nd4j.linalg.api.buffer.DataType> calculateOutputDataTypes(){
        List<String> allVars = new ArrayList<>(variables.keySet());
        DataTypesSession session = new DataTypesSession(this);
        Map<String,org.nd4j.linalg.api.buffer.DataType> phValues = new HashMap<>();
        for(Variable v : variables.values()){
            if(v.getVariable().isPlaceHolder()){
                org.nd4j.linalg.api.buffer.DataType dt = v.getVariable().dataType();
                Preconditions.checkNotNull(dt, "Placeholder variable %s has null datatype", v.getName());
                phValues.put(v.getName(), dt);
            }
        }
        Map<String, org.nd4j.linalg.api.buffer.DataType> out = session.output(allVars, phValues);
        return out;
    }
}

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

package org.deeplearning4j.spark.parameterserver.training;

import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.RandomUtils;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaRDDLike;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.storage.StorageLevel;
import org.datavec.spark.util.BroadcastHadoopConfigHolder;
import org.datavec.spark.util.SerializableHadoopConfig;
import org.deeplearning4j.api.loader.DataSetLoader;
import org.deeplearning4j.api.loader.MultiDataSetLoader;
import org.deeplearning4j.api.loader.impl.SerializedDataSetLoader;
import org.deeplearning4j.api.loader.impl.SerializedMultiDataSetLoader;
import org.deeplearning4j.api.storage.Persistable;
import org.deeplearning4j.api.storage.StatsStorageRouter;
import org.deeplearning4j.api.storage.StorageMetaData;
import org.deeplearning4j.config.DL4JEnvironmentVars;
import org.deeplearning4j.exception.DL4JInvalidConfigException;
import org.deeplearning4j.optimize.api.TrainingListener;
import org.deeplearning4j.optimize.solvers.accumulation.encoding.ResidualPostProcessor;
import org.deeplearning4j.optimize.solvers.accumulation.encoding.ThresholdAlgorithm;
import org.deeplearning4j.optimize.solvers.accumulation.encoding.residual.ResidualClippingPostProcessor;
import org.deeplearning4j.optimize.solvers.accumulation.encoding.threshold.AdaptiveThresholdAlgorithm;
import org.deeplearning4j.spark.api.*;
import org.deeplearning4j.spark.api.stats.SparkTrainingStats;
import org.deeplearning4j.spark.api.worker.NetBroadcastTuple;
import org.deeplearning4j.spark.impl.graph.SparkComputationGraph;
import org.deeplearning4j.spark.impl.multilayer.SparkDl4jMultiLayer;
import org.deeplearning4j.spark.impl.paramavg.BaseTrainingMaster;
import org.deeplearning4j.spark.impl.paramavg.stats.ParameterAveragingTrainingMasterStats;
import org.deeplearning4j.spark.impl.repartitioner.DefaultRepartitioner;
import org.deeplearning4j.spark.parameterserver.accumulation.SharedTrainingAccumulationFunction;
import org.deeplearning4j.spark.parameterserver.accumulation.SharedTrainingAccumulationTuple;
import org.deeplearning4j.spark.parameterserver.accumulation.SharedTrainingAggregateFunction;
import org.deeplearning4j.spark.parameterserver.conf.SharedTrainingConfiguration;
import org.deeplearning4j.spark.parameterserver.functions.SharedFlatMapDataSet;
import org.deeplearning4j.spark.parameterserver.functions.SharedFlatMapMultiDataSet;
import org.deeplearning4j.spark.parameterserver.functions.SharedFlatMapPaths;
import org.deeplearning4j.spark.parameterserver.functions.SharedFlatMapPathsMDS;
import org.deeplearning4j.spark.parameterserver.networking.v1.SilentTrainingDriver;
import org.deeplearning4j.spark.parameterserver.networking.v2.UpdatesConsumer;
import org.deeplearning4j.spark.util.SparkUtils;
import org.deeplearning4j.util.UIDProvider;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.parameterserver.distributed.conf.VoidConfiguration;
import org.nd4j.parameterserver.distributed.enums.ExecutionMode;
import org.nd4j.parameterserver.distributed.enums.NodeRole;
import org.nd4j.parameterserver.distributed.enums.TransportType;
import org.nd4j.parameterserver.distributed.util.NetworkOrganizer;
import org.nd4j.parameterserver.distributed.v2.ModelParameterServer;
import org.nd4j.parameterserver.distributed.v2.transport.Transport;
import org.nd4j.parameterserver.distributed.v2.transport.impl.AeronUdpTransport;
import org.nd4j.shade.jackson.core.JsonProcessingException;
import org.nd4j.shade.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SharedTrainingMaster implements distributed training of neural networks using a compressed quantized gradient (update)
 * sharing implementation based on the Strom 2015 paper "Scalable Distributed DNN Training Using Commodity GPU Cloud Computing":
 * <a href="https://s3-us-west-2.amazonaws.com/amazon.jobs-public-documents/strom_interspeech2015.pdf">https://s3-us-west-2.amazonaws.com/amazon.jobs-public-documents/strom_interspeech2015.pdf</a>.
 * The Deeplearning4j implementation makes a number of modifications, such as having the option to use a parameter-server
 * based implementation for fault tolerance and execution where multicast networking support is not available.
 *
 * @author raver119@gmail.com
 */
@Slf4j
@Data
public class SharedTrainingMaster extends BaseTrainingMaster<SharedTrainingResult, SharedTrainingWorker>
                implements TrainingMaster<SharedTrainingResult, SharedTrainingWorker> {
    //Static counter/id fields used to determine which training master last set up the singleton param servers, etc
    protected static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();
    protected static final AtomicInteger LAST_TRAINING_INSTANCE = new AtomicInteger(-1);

    protected List<TrainingHook> trainingHooks;
    protected VoidConfiguration voidConfiguration;

    protected Integer numWorkers;
    protected Integer numWorkersPerNode;
    protected int workerPrefetchBatches;
    protected RDDTrainingApproach rddTrainingApproach;
    protected StorageLevel storageLevel;
    protected Repartitioner repartitioner;

    protected boolean collectTrainingStats;
    protected int rddDataSetNumExamples;
    protected long debugLongerIterations = 0L;
    protected boolean logMinibatchesPerWorker = false;
    protected boolean encodingDebugMode = false;

    protected ThresholdAlgorithm thresholdAlgorithm;
    protected ResidualPostProcessor residualPostProcessor;

    protected Repartition repartition;
    protected RepartitionStrategy repartitionStrategy;

    protected ParameterAveragingTrainingMasterStats.ParameterAveragingTrainingMasterStatsHelper stats;

    protected Random rng;

    protected AtomicBoolean isFirstRun;

    // better ignore
    protected final transient int instanceId;
    protected transient Broadcast<NetBroadcastTuple> broadcastModel;
    protected transient Broadcast<SharedTrainingConfiguration> broadcastConfiguration;
    protected transient Transport transport;
    protected transient SilentTrainingDriver trainingDriver;

    protected transient UpdatesConsumer updatesConsumer;

    protected boolean setupDone;

    protected SharedTrainingMaster() {
        // just a stub for ser/de
        instanceId = INSTANCE_COUNTER.getAndIncrement();
    }

    public SharedTrainingMaster(@NonNull VoidConfiguration voidConfiguration, Integer numWorkers,
                    RDDTrainingApproach rddTrainingApproach, StorageLevel storageLevel, boolean collectTrainingStats,
                    RepartitionStrategy repartitionStrategy, Repartition repartition,
                    ThresholdAlgorithm thresholdAlgorithm, ResidualPostProcessor residualPostProcessor,
                    int rddDataSetNumExamples,
                    int batchSizePerWorker, long debugLongerIterations, int numWorkersPerNode, int workerPrefetchBatches,
                    Repartitioner repartitioner, Boolean workerTogglePeriodicGC, Integer workerPeriodicGCFrequency,
                    boolean encodingDebugMode) {
        this.voidConfiguration = voidConfiguration;
        this.numWorkers = numWorkers;
        this.thresholdAlgorithm = thresholdAlgorithm;
        this.residualPostProcessor = residualPostProcessor;
        this.rddTrainingApproach = rddTrainingApproach;
        this.repartitionStrategy = repartitionStrategy;
        this.repartition = repartition;
        this.storageLevel = storageLevel;
        this.collectTrainingStats = collectTrainingStats;
        this.isFirstRun = new AtomicBoolean(false);
        this.batchSizePerWorker = batchSizePerWorker;
        this.rddDataSetNumExamples = rddDataSetNumExamples;
        this.debugLongerIterations = debugLongerIterations;
        this.numWorkersPerNode = numWorkersPerNode;
        this.workerPrefetchBatches = workerPrefetchBatches;
        this.repartitioner = repartitioner;
        this.workerTogglePeriodicGC = workerTogglePeriodicGC;
        this.workerPeriodicGCFrequency = workerPeriodicGCFrequency;
        this.encodingDebugMode = encodingDebugMode;


        if (collectTrainingStats)
            stats = new ParameterAveragingTrainingMasterStats.ParameterAveragingTrainingMasterStatsHelper();


        String jvmuid = UIDProvider.getJVMUID();
        this.trainingMasterUID =
                        System.currentTimeMillis() + "_" + (jvmuid.length() <= 8 ? jvmuid : jvmuid.substring(0, 8));
        instanceId = INSTANCE_COUNTER.getAndIncrement();
    }

    @Override
    public void removeHook(TrainingHook trainingHook) {
        if (trainingHooks != null)
            trainingHooks.remove(trainingHook);
    }

    @Override
    public void addHook(@NonNull TrainingHook trainingHook) {
        if (trainingHooks == null)
            trainingHooks = new ArrayList<>();

        trainingHooks.add(trainingHook);
    }

    @Override
    public String toJson() {
        ObjectMapper om = getJsonMapper();

        try {
            return om.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error producing JSON representation for ParameterAveragingTrainingMaster", e);
        }
    }

    @Override
    public String toYaml() {
        ObjectMapper om = getYamlMapper();

        try {
            return om.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error producing YAML representation for ParameterAveragingTrainingMaster", e);
        }
    }

    /**
     * Create a SharedTrainingMaster instance by deserializing a JSON string that has been serialized with
     * {@link #toJson()}
     *
     * @param jsonStr SharedTrainingMaster configuration serialized as JSON
     */
    public static SharedTrainingMaster fromJson(String jsonStr) {
        ObjectMapper om = getJsonMapper();
        try {
            return om.readValue(jsonStr, SharedTrainingMaster.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not parse JSON", e);
        }
    }

    /**
     * Create a SharedTrainingMaster instance by deserializing a YAML string that has been serialized with
     * {@link #toYaml()}
     *
     * @param yamlStr SharedTrainingMaster configuration serialized as YAML
     */
    public static SharedTrainingMaster fromYaml(String yamlStr) {
        ObjectMapper om = getYamlMapper();
        try {
            return om.readValue(yamlStr, SharedTrainingMaster.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not parse YAML", e);
        }
    }

    @Override
    public SharedTrainingWorker getWorkerInstance(SparkDl4jMultiLayer network) {
        /*
            Here we're going create our worker, which will be passed into corresponding FlatMapFunction
         */
        NetBroadcastTuple tuple = new NetBroadcastTuple(network.getNetwork().getLayerWiseConfigurations(),
                        network.getNetwork().params(), network.getNetwork().getUpdater().getStateViewArray());

        voidConfiguration.setUnicastControllerPort(voidConfiguration.getPortSupplier().getPort());

        SharedTrainingConfiguration configuration = SharedTrainingConfiguration.builder()
                .thresholdAlgorithm(thresholdAlgorithm)
                .residualPostProcessor(residualPostProcessor)
                .voidConfiguration(voidConfiguration)
                .debugLongerIterations(debugLongerIterations)
                .numberOfWorkersPerNode(numWorkersPerNode)
                .encodingDebugMode(encodingDebugMode).build();

        if (collectTrainingStats)
            stats.logBroadcastStart();

        if (broadcastModel == null)
            broadcastModel = network.getSparkContext().broadcast(tuple);

        if (broadcastConfiguration == null)
            broadcastConfiguration = network.getSparkContext().broadcast(configuration);

        if (collectTrainingStats)
            stats.logBroadcastEnd();

        SharedTrainingWorker worker = new SharedTrainingWorker(instanceId, broadcastModel, broadcastConfiguration, listeners,
                statsStorage, workerTogglePeriodicGC, workerPeriodicGCFrequency);

        return worker;
    }

    @Override
    public SharedTrainingWorker getWorkerInstance(SparkComputationGraph graph) {
        NetBroadcastTuple tuple = new NetBroadcastTuple(graph.getNetwork().getConfiguration(),
                        graph.getNetwork().params(), graph.getNetwork().getUpdater().getStateViewArray());

        SharedTrainingConfiguration configuration = SharedTrainingConfiguration.builder()
                .thresholdAlgorithm(thresholdAlgorithm)
                .residualPostProcessor(residualPostProcessor)
                .voidConfiguration(voidConfiguration).debugLongerIterations(debugLongerIterations)
                .numberOfWorkersPerNode(numWorkersPerNode)
                .prefetchSize(workerPrefetchBatches)
                .encodingDebugMode(encodingDebugMode)
                .build();

        if (collectTrainingStats)
            stats.logBroadcastStart();

        if (broadcastModel == null)
            broadcastModel = graph.getSparkContext().broadcast(tuple);

        if (broadcastConfiguration == null)
            broadcastConfiguration = graph.getSparkContext().broadcast(configuration);

        if (collectTrainingStats)
            stats.logBroadcastEnd();

        SharedTrainingWorker worker = new SharedTrainingWorker(instanceId, broadcastModel, broadcastConfiguration, listeners,
                statsStorage, workerTogglePeriodicGC, workerPeriodicGCFrequency);

        return worker;
    }

    protected int numObjectsEachWorker(int numExamplesEachRddObject) {
        return batchSizePerWorker / numExamplesEachRddObject;
    }

    protected <T, Repr extends JavaRDDLike<T, Repr>> long getTotalDataSetObjectCount(
                    JavaRDDLike<T, Repr> trainingData) {
        if (collectTrainingStats)
            stats.logCountStart();

        long totalDataSetObjectCount = trainingData.count();

        if (collectTrainingStats)
            stats.logCountEnd();

        return totalDataSetObjectCount;
    }

    protected void executeTrainingDirect(SparkDl4jMultiLayer network, JavaRDD<DataSet> trainingData) {
        if (collectTrainingStats)
            stats.logFitStart();

        //For "vanilla" parameter averaging training, we need to split the full data set into batches of size N, such that we can process the specified
        // number of minibatches between averagings
        //But to do that, wee need to know: (a) the number of examples, and (b) the number of workers
        if (storageLevel != null)
            trainingData.persist(storageLevel);

        long totalDataSetObjectCount = getTotalDataSetObjectCount(trainingData);

        // since this is real distributed training, we don't need to split data
        doIteration(network, trainingData, 1, 1);

        if (collectTrainingStats)
            stats.logFitEnd((int) totalDataSetObjectCount);
    }

    protected void executeTrainingDirectMDS(SparkComputationGraph network, JavaRDD<MultiDataSet> trainingData) {
        if (collectTrainingStats)
            stats.logFitStart();

        //For "vanilla" parameter averaging training, we need to split the full data set into batches of size N, such that we can process the specified
        // number of minibatches between averagings
        //But to do that, wee need to know: (a) the number of examples, and (b) the number of workers
        if (storageLevel != null)
            trainingData.persist(storageLevel);

        long totalDataSetObjectCount = getTotalDataSetObjectCount(trainingData);

        // since this is real distributed training, we don't need to split data
        doIterationMDS(network, trainingData, 1, 1);

        if (collectTrainingStats)
            stats.logFitEnd((int) totalDataSetObjectCount);
    }

    protected void executeTrainingDirect(SparkComputationGraph network, JavaRDD<DataSet> trainingData) {
        if (collectTrainingStats)
            stats.logFitStart();

        //For "vanilla" parameter averaging training, we need to split the full data set into batches of size N, such that we can process the specified
        // number of minibatches between averagings
        //But to do that, wee need to know: (a) the number of examples, and (b) the number of workers
        if (storageLevel != null)
            trainingData.persist(storageLevel);

        long totalDataSetObjectCount = getTotalDataSetObjectCount(trainingData);

        // since this is real distributed training, we don't need to split data
        doIteration(network, trainingData, 1, 1);

        if (collectTrainingStats)
            stats.logFitEnd((int) totalDataSetObjectCount);
    }


    @Override
    public void executeTrainingPaths(SparkDl4jMultiLayer network, SparkComputationGraph graph, JavaRDD<String> trainingDataPaths,
                                              DataSetLoader dsLoader, MultiDataSetLoader mdsLoader) {
        prepareNetworkAndStuff(network, graph);
        executeTrainingPathsHelper(network, graph, trainingDataPaths, dsLoader, mdsLoader, rddDataSetNumExamples);
    }

    protected void executeTrainingPathsHelper(SparkDl4jMultiLayer network, SparkComputationGraph graph, JavaRDD<String> trainingDataPaths,
                                              DataSetLoader dsLoader, MultiDataSetLoader mdsLoader, int dataSetObjectsNumExamples) {

        if (numWorkers == null) {
            if(network != null){
                numWorkers = network.getSparkContext().defaultParallelism();
            } else {
                numWorkers = graph.getSparkContext().defaultParallelism();
            }
        }

        if (collectTrainingStats)
            stats.logFitStart();

        if (storageLevelStreams != null)
            trainingDataPaths.persist(storageLevelStreams);

        long totalDataSetObjectCount = getTotalDataSetObjectCount(trainingDataPaths);

        doIterationPaths(network, graph, trainingDataPaths, 1, 1, dsLoader, mdsLoader, dataSetObjectsNumExamples);

        if (collectTrainingStats)
            stats.logFitEnd((int) totalDataSetObjectCount);
    }

    protected void prepareNetworkAndStuff(SparkDl4jMultiLayer network, SparkComputationGraph graph) {
        if (network == null && graph == null)
            throw new IllegalStateException("Both MLN & CG are undefined");

        //Get the port for communicating with the master/driver - and add it to the configuration for use from each machine
        //Note that each machine will allocate their own port for inbound communications according to what the PortSupplier
        //returns on each worker machine.
        voidConfiguration.setUnicastControllerPort(voidConfiguration.getPortSupplier().getPort());

        // if streamId has default value - generate random one
        if (voidConfiguration.getStreamId() < 1)
            voidConfiguration.setStreamId(RandomUtils.nextInt(119, Integer.MAX_VALUE - 1));

        // first of all, we're instantiating ParameterServer shard here\
        if (numWorkers == null)
            numWorkers = network != null ? network.getSparkContext().defaultParallelism()
                            : graph.getSparkContext().defaultParallelism();

        // set current box as controller, if field is unset - switch to next step
        if (voidConfiguration.getControllerAddress() == null) {
            try {
                val e = System.getenv("SPARK_PUBLIC_DNS");
                log.info("Trying {SPARK_PUBLIC_DNS}: [{}]", e);
                if (e != null) {
                    String sparkIp = InetAddress.getByName(e).getHostAddress();
                    voidConfiguration.setControllerAddress(sparkIp);
                }
            } catch (UnknownHostException e) {
            }
        }

        // next step - is to get ip address that matches specific network mask
        if (voidConfiguration.getControllerAddress() == null && voidConfiguration.getNetworkMask() != null) {
            NetworkOrganizer organizer = new NetworkOrganizer(voidConfiguration.getNetworkMask());
            val s = organizer.getMatchingAddress();
            log.info("Trying auto-detected address: [{}]", s);

            voidConfiguration.setControllerAddress(s);
        }

        if (voidConfiguration.getControllerAddress() == null)
            voidConfiguration.setControllerAddress(DL4JEnvironmentVars.DL4J_VOID_IP);

        if (voidConfiguration.getControllerAddress() == null)
            throw new DL4JInvalidConfigException(
                            "Can't get Spark Master local address. Please specify it manually using VoidConfiguration.setControllerAddress(String) method or VoidConfiguration.setNetworkMask(String) method");

        // we're forcing proper defaults
        log.info("Setting controller address to {}:{}", voidConfiguration.getControllerAddress(),
                        voidConfiguration.getUnicastControllerPort());
        voidConfiguration.setShardAddresses(voidConfiguration.getControllerAddress());
        voidConfiguration.setNumberOfShards(1);

        if (network != null)
            network.getNetwork().init();
        else
            graph.getNetwork().init();

        // this instance will be SilentWorker - it'll accept and apply messages, but won't contribute to training. And we init it only once
        if (isFirstRun.compareAndSet(false, true) || LAST_TRAINING_INSTANCE.get() != instanceId) {
            if(LAST_TRAINING_INSTANCE.get() >= 0 && LAST_TRAINING_INSTANCE.get() != instanceId){
                log.debug("Detected changed training instance - setting up new parameter server - old instance {}, new instance {}",
                        LAST_TRAINING_INSTANCE, instanceId);

                ModelParameterServer.getInstance().shutdown();
                try{    //TODO is this required?
                    Thread.sleep(3000);
                } catch (Exception e){
                    throw new RuntimeException(e);
                }
            }

            val transport = voidConfiguration.getTransportType() == TransportType.ROUTED_UDP
                    ? new AeronUdpTransport(voidConfiguration.getControllerAddress(), voidConfiguration.getUnicastControllerPort(), voidConfiguration)
                    : null;

            if (transport == null)
                throw new DL4JInvalidConfigException("No Transport implementation was defined for this training session!");

            val params = network != null ? network.getNetwork().params() : graph.getNetwork().params();

            updatesConsumer = UpdatesConsumer.builder()
                    .params(params)
                    .updates(Nd4j.create(params.shape(), params.ordering()))
                    .stepFunction(network != null ? network.getNetwork().getOptimizer().getStepFunction() : graph.getNetwork().getOptimizer().getStepFunction())
                    .build();

            // apply configuration
            ModelParameterServer.getInstance().configure(voidConfiguration, transport, true);

            // and attach our consumer
            ModelParameterServer.getInstance().addUpdatesSubscriber(updatesConsumer);


            // and start actual server
            if (!ModelParameterServer.getInstance().isInitialized())
                ModelParameterServer.getInstance().launch();

            LAST_TRAINING_INSTANCE.set(instanceId);
        }

        setupDone = true;
    }

    protected void finalizeTraining() {
        /*
            Here we basically want to do few things:
            1) update statistics, if any
            2) finalize updates of silent worker
            3) pull back gradients, maybe?
         */

        // applying non-applied updates, if any :)
        if (trainingDriver != null) {
            trainingDriver.finishTraining(0L, 0L);
        }

        // the same, but v2 impl
        if (updatesConsumer != null)
            updatesConsumer.flush();
    }

    @Override
    public void executeTraining(SparkDl4jMultiLayer network, JavaRDD<DataSet> trainingData) {
        /*
            This method (and other similar methods) is basically one of our entry points, here we'll spawn our training process:
            1) broadcast everything needed: initial model params, updaters state, conf. Useful for uptraining
            2) shuffle, if needed
            3) repartition, if needed
            4) EXECUTE SILENT WORKER
            5) invoke training function via mapPartitions
            6) wait till finished
            7) do something with final model, i.e. export it somewhere :)
         */

        prepareNetworkAndStuff(network, null);

        // at this moment we have coordinator server up (master works as coordinator)
        if (rddTrainingApproach == RDDTrainingApproach.Direct) {
            executeTrainingDirect(network, trainingData);
        } else if (rddTrainingApproach == RDDTrainingApproach.Export) {
            //Export data if required (or, use cached export)
            JavaRDD<String> paths = exportIfRequired(network.getSparkContext(), trainingData);
            executeTrainingPathsHelper(network, null, paths, new SerializedDataSetLoader(), null, batchSizePerWorker);
        } else
            throw new DL4JInvalidConfigException(
                            "Unknown RDDtrainingApproach [" + rddTrainingApproach + "] was specified!");
    }

    @Override
    public void executeTraining(SparkComputationGraph graph, JavaRDD<DataSet> trainingData) {
        prepareNetworkAndStuff(null, graph);

        // at this moment we have coordinator server up (master works as coordinator)
        if (rddTrainingApproach == RDDTrainingApproach.Direct) {
            executeTrainingDirect(graph, trainingData);
        } else if (rddTrainingApproach == RDDTrainingApproach.Export) {
            //Export data if required (or, use cached export)
            JavaRDD<String> paths = exportIfRequired(graph.getSparkContext(), trainingData);
            executeTrainingPathsHelper(null, graph, paths, new SerializedDataSetLoader(), null, batchSizePerWorker);
        } else
            throw new DL4JInvalidConfigException(
                            "Unknown RDDtrainingApproach [" + rddTrainingApproach + "] was specified!");
    }

    @Override
    public void executeTrainingMDS(SparkComputationGraph graph, JavaRDD<MultiDataSet> trainingData) {
        prepareNetworkAndStuff(null, graph);

        // at this moment we have coordinator server up (master works as coordinator)
        if (rddTrainingApproach == RDDTrainingApproach.Direct) {
            executeTrainingDirectMDS(graph, trainingData);
        } else if (rddTrainingApproach == RDDTrainingApproach.Export) {
            //Export data if required (or, use cached export)
            JavaRDD<String> paths = exportIfRequiredMDS(graph.getSparkContext(), trainingData);
            executeTrainingPathsHelper(null, graph, paths, null, new SerializedMultiDataSetLoader(), batchSizePerWorker);
        } else
            throw new DL4JInvalidConfigException(
                            "Unknown RDDtrainingApproach [" + rddTrainingApproach + "] was specified!");
    }

    @Override
    public void setCollectTrainingStats(boolean collectTrainingStats) {
        this.collectTrainingStats = collectTrainingStats;
    }

    @Override
    public boolean getIsCollectTrainingStats() {
        return collectTrainingStats;
    }

    @Override
    public SparkTrainingStats getTrainingStats() {
        return null;
    }

    @Override
    public void setListeners(Collection<TrainingListener> listeners) {
        setListeners(null, listeners);
    }

    @Override
    public void setListeners(StatsStorageRouter router, Collection<TrainingListener> listeners) {
        this.statsStorage = router;
        this.listeners = (listeners == null ? null : new ArrayList<>(listeners));
    }


    protected void processResults(SparkDl4jMultiLayer network, SparkComputationGraph graph,
                    JavaRDD<SharedTrainingResult> results) {
        Preconditions.checkState(network != null || graph != null, "Both MLN & CG are null");
        Preconditions.checkState(setupDone, "Setup was not completed before trying to process results");



        if (collectTrainingStats)
            stats.logAggregateStartTime();

        SharedTrainingAccumulationTuple finalResult = results.treeAggregate(null, new SharedTrainingAggregateFunction(),
                        new SharedTrainingAccumulationFunction(), 4);
        SparkTrainingStats aggregatedStats = finalResult.getSparkTrainingStats();
        if (collectTrainingStats)
            stats.logAggregationEndTime();

        //finalizeTraining has to be *after* training has completed, otherwise the RDD (via tree aggregate)
        finalizeTraining();


        if (collectTrainingStats)
            stats.logProcessParamsUpdaterStart();

        if (finalResult.getUpdaterStateArray() != null) {

            if (finalResult.getAggregationsCount() > 1) {
                finalResult.getUpdaterStateArray().divi(finalResult.getAggregationsCount());
            }

            if (network != null) {
                if (network.getNetwork().getUpdater() != null
                                && network.getNetwork().getUpdater().getStateViewArray() != null)
                    network.getNetwork().getUpdater().getStateViewArray().assign(finalResult.getUpdaterStateArray());
            } else {
                if (graph.getNetwork().getUpdater() != null
                                && graph.getNetwork().getUpdater().getStateViewArray() != null)
                    graph.getNetwork().getUpdater().getStateViewArray().assign(finalResult.getUpdaterStateArray());
            }
        }


        double score = finalResult.getScoreSum() / Math.max(1, finalResult.getAggregationsCount());

        if (network != null) {
            network.getNetwork().setScore(score);
        } else {
            graph.getNetwork().setScore(score);
        }

        if (collectTrainingStats)
            stats.logProcessParamsUpdaterEnd();


        if (collectTrainingStats) {
            stats.logProcessParamsUpdaterEnd();
            stats.addWorkerStats(aggregatedStats);
        }

        if (statsStorage != null) {
            Collection<StorageMetaData> meta = finalResult.getListenerMetaData();
            if (meta != null && !meta.isEmpty()) {
                statsStorage.putStorageMetaData(meta);
            }

            Collection<Persistable> staticInfo = finalResult.getListenerStaticInfo();
            if (staticInfo != null && !staticInfo.isEmpty()) {
                statsStorage.putStaticInfo(staticInfo);
            }

            Collection<Persistable> updates = finalResult.getListenerUpdates();
            if (updates != null && !updates.isEmpty()) {
                statsStorage.putUpdate(updates);
            }
        }

        if (logMinibatchesPerWorker){
            if(finalResult.getMinibatchesPerExecutor() != null){
                List<String> l = new ArrayList<>(finalResult.getMinibatchesPerExecutor().keySet());
                Collections.sort(l);
                Map<String,Integer> linkedMap = new LinkedHashMap<>();
                for(String s : l){
                    linkedMap.put(s, finalResult.getMinibatchesPerExecutor().get(s));
                }
                log.info("Number of minibatches processed per JVM/executor: {}", linkedMap);
            }
        }

        if(finalResult.getThresholdAlgorithmReducer() != null){
            //Store the final threshold algorithm after aggregation
            //Some threshold algorithms contain state/history, used to adapt the threshold algorithm
            //The idea is we want to keep this history/state for next epoch, rather than simply throwing it away
            // and starting the threshold adaption process from scratch on each epoch
            ThresholdAlgorithm ta = finalResult.getThresholdAlgorithmReducer().getFinalResult();
            this.thresholdAlgorithm = ta;
        }

        Nd4j.getExecutioner().commit();
    }

    protected void doIteration(SparkDl4jMultiLayer network, JavaRDD<DataSet> split, int splitNum, int numSplits) {
        log.info("Starting training of split {} of {}. workerMiniBatchSize={}, thresholdAlgorithm={}, Configured for {} workers",
                        splitNum, numSplits, batchSizePerWorker, thresholdAlgorithm, numWorkers);

        if (collectTrainingStats)
            stats.logMapPartitionsStart();

        JavaRDD<DataSet> splitData = split;

        if (collectTrainingStats)
            stats.logRepartitionStart();

        if(repartitioner != null){
            log.info("Repartitioning training data using repartitioner: {}", repartitioner);
            int minPerWorker = Math.max(1, batchSizePerWorker/rddDataSetNumExamples);
            splitData = repartitioner.repartition(splitData, minPerWorker, numWorkers);
        } else {
            log.info("Repartitioning training data using SparkUtils repartitioner");
            splitData = SparkUtils.repartitionEqually(splitData, repartition, numWorkers);
        }
        int nPartitions = splitData.partitions().size();

        if (collectTrainingStats && repartition != Repartition.Never)
            stats.logRepartitionEnd();


        FlatMapFunction<Iterator<DataSet>, SharedTrainingResult> function =
                        new SharedFlatMapDataSet<>(getWorkerInstance(network));

        JavaRDD<SharedTrainingResult> result = splitData.mapPartitions(function);

        processResults(network, null, result);

        if (collectTrainingStats)
            stats.logMapPartitionsEnd(nPartitions);
    }

    protected void doIterationMDS(SparkComputationGraph network, JavaRDD<MultiDataSet> split, int splitNum,
                    int numSplits) {
        log.info("Starting training of split {} of {}. workerMiniBatchSize={}, thresholdAlgorithm={}, Configured for {} workers",
                        splitNum, numSplits, batchSizePerWorker, thresholdAlgorithm, numWorkers);

        if (collectTrainingStats)
            stats.logMapPartitionsStart();

        JavaRDD<MultiDataSet> splitData = split;

        if (collectTrainingStats)
            stats.logRepartitionStart();

        if(repartitioner != null){
            log.info("Repartitioning training data using repartitioner: {}", repartitioner);
            int minPerWorker = Math.max(1, batchSizePerWorker/rddDataSetNumExamples);
            splitData = repartitioner.repartition(splitData, minPerWorker, numWorkers);
        } else {
            log.info("Repartitioning training data using SparkUtils repartitioner");
            splitData = SparkUtils.repartitionEqually(splitData, repartition, numWorkers);
        }
        int nPartitions = splitData.partitions().size();

        if (collectTrainingStats && repartition != Repartition.Never)
            stats.logRepartitionEnd();


        FlatMapFunction<Iterator<MultiDataSet>, SharedTrainingResult> function =
                        new SharedFlatMapMultiDataSet<>(getWorkerInstance(network));

        JavaRDD<SharedTrainingResult> result = splitData.mapPartitions(function);

        processResults(null, network, result);

        if (collectTrainingStats)
            stats.logMapPartitionsEnd(nPartitions);
    }

    protected void doIteration(SparkComputationGraph network, JavaRDD<DataSet> data, int splitNum, int numSplits) {
        log.info("Starting training of split {} of {}. workerMiniBatchSize={}, thresholdAlgorithm={}, Configured for {} workers",
                        splitNum, numSplits, batchSizePerWorker, thresholdAlgorithm, numWorkers);

        if (collectTrainingStats)
            stats.logMapPartitionsStart();

        if (collectTrainingStats)
            stats.logRepartitionStart();

        if(repartitioner != null){
            log.info("Repartitioning training data using repartitioner: {}", repartitioner);
            int minPerWorker = Math.max(1, batchSizePerWorker/rddDataSetNumExamples);
            data = repartitioner.repartition(data, minPerWorker, numWorkers);
        } else {
            log.info("Repartitioning training data using SparkUtils repartitioner");
            data = SparkUtils.repartitionEqually(data, repartition, numWorkers);
        }
        int nPartitions = data.partitions().size();

        if (collectTrainingStats && repartition != Repartition.Never)
            stats.logRepartitionEnd();


        FlatMapFunction<Iterator<DataSet>, SharedTrainingResult> function =
                        new SharedFlatMapDataSet<>(getWorkerInstance(network));

        JavaRDD<SharedTrainingResult> result = data.mapPartitions(function);

        processResults(null, network, result);

        if (collectTrainingStats)
            stats.logMapPartitionsEnd(nPartitions);
    }

    protected void doIterationPaths(SparkDl4jMultiLayer network, SparkComputationGraph graph, JavaRDD<String> data,
                    int splitNum, int numSplits, DataSetLoader dsLoader, MultiDataSetLoader mdsLoader, int dataSetObjectNumExamples) {
        if (network == null && graph == null)
            throw new DL4JInvalidConfigException("Both MLN & CompGraph are NULL");

        log.info("Starting training of split {} of {}. workerMiniBatchSize={}, thresholdAlgorithm={}, Configured for {} workers",
                        splitNum, numSplits, batchSizePerWorker, thresholdAlgorithm, numWorkers);

        if (collectTrainingStats)
            stats.logMapPartitionsStart();

        if (collectTrainingStats)
            stats.logRepartitionStart();

        if(repartitioner != null){
            log.info("Repartitioning training data using repartitioner: {}", repartitioner);
            int minPerWorker = Math.max(1, batchSizePerWorker/dataSetObjectNumExamples);
            data = repartitioner.repartition(data, minPerWorker, numWorkers);
        } else {
            log.info("Repartitioning training data using SparkUtils repartitioner");
            data = SparkUtils.repartitionEqually(data, repartition, numWorkers);
        }

        int nPartitions = data.partitions().size();
        if (collectTrainingStats && repartition != Repartition.Never)
            stats.logRepartitionEnd();

        JavaSparkContext sc = (network != null ? network.getSparkContext() : graph.getSparkContext());
        FlatMapFunction<Iterator<String>, SharedTrainingResult> function;
        if(dsLoader != null){
            function = new SharedFlatMapPaths<>(
                    network != null ? getWorkerInstance(network) : getWorkerInstance(graph), dsLoader, BroadcastHadoopConfigHolder.get(sc));
        } else {
            function = new SharedFlatMapPathsMDS<>(
                    network != null ? getWorkerInstance(network) : getWorkerInstance(graph), mdsLoader, BroadcastHadoopConfigHolder.get(sc));
        }


        JavaRDD<SharedTrainingResult> result = data.mapPartitions(function);

        processResults(network, graph, result);

        if (collectTrainingStats)
            stats.logMapPartitionsEnd(nPartitions);
    }


    public static class Builder {
        protected ThresholdAlgorithm thresholdAlgorithm = new AdaptiveThresholdAlgorithm();
        protected ResidualPostProcessor residualPostProcessor = new ResidualClippingPostProcessor(5.0, 5);
        protected int rddDataSetNumExamples = 1;
        @Deprecated
        protected Repartition repartition = Repartition.Always;
        @Deprecated
        protected RepartitionStrategy repartitionStrategy = RepartitionStrategy.Balanced;
        protected StorageLevel storageLevel = StorageLevel.MEMORY_ONLY_SER();
        protected VoidConfiguration voidConfiguration;
        protected RDDTrainingApproach rddTrainingApproach = RDDTrainingApproach.Export;
        protected long rngSeed;
        protected String exportDirectory = null;
        protected Integer numWorkers;
        protected boolean collectTrainingStats;
        protected Transport transport;
        protected int batchSize;
        protected long debugLongerIterations = 0L;
        protected int numWorkersPerNode = -1;
        protected int workerPrefetchNumBatches = 2;
        protected Repartitioner repartitioner = new DefaultRepartitioner();
        protected Boolean workerTogglePeriodicGC = new Boolean(true);
        protected Integer workerPeriodicGCFrequency = new Integer(5000);
        protected boolean encodingDebugMode = false;

        /**
         * Create a SharedTrainingMaster with defaults other than the RDD number of examples
         * @param rddDataSetNumExamples When fitting from an {@code RDD<DataSet>} how many examples are in each dataset?
         */
        public Builder(int rddDataSetNumExamples) {
            this(new AdaptiveThresholdAlgorithm(), rddDataSetNumExamples);
        }

        /**
         * Create a SharedTrainingMaster with defaults other than the RDD number of examples
         * @param voidConfiguration     Configuration bean for the SharedTrainingMaster parameter server
         * @param rddDataSetNumExamples When fitting from an {@code RDD<DataSet>} how many examples are in each dataset?
         */
        public Builder(@NonNull VoidConfiguration voidConfiguration, int rddDataSetNumExamples) {
            this(voidConfiguration, new AdaptiveThresholdAlgorithm(), rddDataSetNumExamples);
        }

        /**
         * Create a SharedTrainingMaster with defaults other than the RDD number of examples
         * @param thresholdAlgorithm    Threshold algorithm for the sparse update encoding
         * @param rddDataSetNumExamples When fitting from an {@code RDD<DataSet>} how many examples are in each dataset?
         */
        public Builder(ThresholdAlgorithm thresholdAlgorithm, int rddDataSetNumExamples) {
            this(VoidConfiguration.builder().executionMode(ExecutionMode.MANAGED).forcedRole(NodeRole.SHARD)
                            // we're setting controller to Spark Master, if it's null - that's ok for now.
                            .controllerAddress(System.getenv("SPARK_PUBLIC_DNS")).build(), thresholdAlgorithm,
                            rddDataSetNumExamples);
        }

        /**
         * @param voidConfiguration     Configuration bean for the SharedTrainingMaster parameter server
         * @param numWorkers            No longer used/required
         * @param threshold             Encoding threshold
         * @param rddDataSetNumExamples When fitting from an {@code RDD<DataSet>} how many examples are in each dataset?
         * @deprecated This constructor is deprecated - use {@link #Builder(VoidConfiguration, int)} or {@link #Builder(VoidConfiguration, ThresholdAlgorithm, int)}
         */
        @Deprecated
        public Builder(@NonNull VoidConfiguration voidConfiguration, Integer numWorkers, double threshold, int rddDataSetNumExamples) {
            this(voidConfiguration, new AdaptiveThresholdAlgorithm(threshold), rddDataSetNumExamples);
        }

        /**
         * @param voidConfiguration     Configuration bean for the SharedTrainingMaster parameter server
         * @param thresholdAlgorithm    Update sharing threshold algorithm
         * @param rddDataSetNumExamples
         */
        public Builder(@NonNull VoidConfiguration voidConfiguration, ThresholdAlgorithm thresholdAlgorithm, int rddDataSetNumExamples) {
            this.thresholdAlgorithm = thresholdAlgorithm;
            this.voidConfiguration = voidConfiguration;
            this.rddDataSetNumExamples = rddDataSetNumExamples;

            // we're enforcing managed mode in all cases here
            this.voidConfiguration.setExecutionMode(ExecutionMode.MANAGED);
        }

        public Builder(@NonNull VoidConfiguration voidConfiguration, Integer numWorkers, ThresholdAlgorithm thresholdAlgorithm, int rddDataSetNumExamples) {
            this.thresholdAlgorithm = thresholdAlgorithm;
            this.voidConfiguration = voidConfiguration;
            this.rddDataSetNumExamples = rddDataSetNumExamples;
            this.numWorkers = numWorkers;

            // we're enforcing managed mode in all cases here
            this.voidConfiguration.setExecutionMode(ExecutionMode.MANAGED);
        }

        /**
         * Enable/disable collection of training statistics
         * @param enable Enable
         * @return
         */
        public Builder collectTrainingStats(boolean enable) {
            this.collectTrainingStats = enable;
            return this;
        }

        /**
         * This parameter defines when repartition is applied (if applied).
         * @param repartition Repartition setting
         * @deprecated Use {@link #repartitioner(Repartitioner)}
         */
        @Deprecated
        public Builder repartitionData(Repartition repartition) {
            this.repartition = repartition;
            return this;
        }

        /**
         * Used in conjunction with {@link #repartitionData(Repartition)} (which defines <i>when</i> repartitioning should be
         * conducted), repartitionStrategy defines <i>how</i> the repartitioning should be done. See {@link RepartitionStrategy}
         * for details
         *
         * @param repartitionStrategy Repartitioning strategy to use
         * @deprecated Use {@link #repartitioner(Repartitioner)}
         */
        @Deprecated
        public Builder repartitionStrategy(RepartitionStrategy repartitionStrategy) {
            this.repartitionStrategy = repartitionStrategy;
            return this;
        }

        /**
         * Set the storage level for {@code RDD<DataSet>}s.<br>
         * Default: StorageLevel.MEMORY_ONLY_SER() - i.e., store in memory, in serialized form<br>
         * To use no RDD persistence, use {@code null}<br>
         * Note that this only has effect when {@code RDDTrainingApproach.Direct} is used (which is not the default),
         * and when fitting from an {@code RDD<DataSet>}.
         * <p>
         * <b>Note</b>: Spark's StorageLevel.MEMORY_ONLY() and StorageLevel.MEMORY_AND_DISK() can be problematic when
         * it comes to off-heap data (which DL4J/ND4J uses extensively). Spark does not account for off-heap memory
         * when deciding if/when to drop blocks to ensure enough free memory; consequently, for DataSet RDDs that are
         * larger than the total amount of (off-heap) memory, this can lead to OOM issues. Put another way: Spark counts
         * the on-heap size of DataSet and INDArray objects only (which is negligible) resulting in a significant
         * underestimate of the true DataSet object sizes. More DataSets are thus kept in memory than we can really afford.<br>
         * <br>
         * Note also that fitting directly from an {@code RDD<DataSet>} is discouraged - it is better to export your
         * prepared data once and call (for example} {@code SparkDl4jMultiLayer.fit(String savedDataDirectory)}.
         * See DL4J's Spark website documentation for details.<br>
         *
         * @param storageLevel Storage level to use for DataSet RDDs
         */
        public Builder storageLevel(StorageLevel storageLevel) {
            this.storageLevel = storageLevel;
            return this;
        }

        /**
         * The approach to use when training on a {@code RDD<DataSet>} or {@code RDD<MultiDataSet>}.
         * Default: {@link RDDTrainingApproach#Export}, which exports data to a temporary directory first.<br>
         * The default cluster temporary directory is used, though can be configured using {@link #exportDirectory(String)}
         * Note also that fitting directly from an {@code RDD<DataSet>} is discouraged - it is better to export your
         * prepared data once and call (for example} {@code SparkDl4jMultiLayer.fit(String savedDataDirectory)}.
         * See DL4J's Spark website documentation for details.<br>
         *
         * @param rddTrainingApproach Training approach to use when training from a {@code RDD<DataSet>} or {@code RDD<MultiDataSet>}
         */
        public Builder rddTrainingApproach(RDDTrainingApproach rddTrainingApproach) {
            this.rddTrainingApproach = rddTrainingApproach;
            return this;
        }

        /**
         * When {@link #rddTrainingApproach(RDDTrainingApproach)} is set to {@link RDDTrainingApproach#Export} (as it is by default)
         * the data is exported to a temporary directory first.
         * <p>
         * Default: null. -> use {hadoop.tmp.dir}/dl4j/. In this case, data is exported to {hadoop.tmp.dir}/dl4j/SOME_UNIQUE_ID/<br>
         * If you specify a directory, the directory {exportDirectory}/SOME_UNIQUE_ID/ will be used instead.
         *
         * @param exportDirectory Base directory to export data
         */
        public Builder exportDirectory(String exportDirectory) {
            this.exportDirectory = exportDirectory;
            return this;
        }

        /**
         * Random number generator seed, used mainly for enforcing repeatable splitting/repartitioning on RDDs
         * Default: no seed set (i.e., random seed)
         *
         * @param rngSeed RNG seed
         */
        public Builder rngSeed(long rngSeed) {
            this.rngSeed = rngSeed;
            return this;
        }

        /**
         * @deprecated Use {@link #thresholdAlgorithm(ThresholdAlgorithm)} with (for example) {@link AdaptiveThresholdAlgorithm}
         */
        @Deprecated
        public Builder updatesThreshold(double updatesThreshold){
            return thresholdAlgorithm(new AdaptiveThresholdAlgorithm(updatesThreshold));
        }

        /**
         * Algorithm to use to determine the threshold for updates encoding. Lower values might improve convergence, but
         * increase amount of network communication<br>
         * Values that are too low may also impact network convergence. If convergence problems are observed, try increasing
         * or decreasing this by a factor of 10 - say 1e-4 and 1e-2.<br>
         * For technical details, see the paper <a href="https://s3-us-west-2.amazonaws.com/amazon.jobs-public-documents/strom_interspeech2015.pdf">
         * Scalable Distributed DNN Training Using Commodity GPU Cloud Computing</a><br>
         * See also {@link ThresholdAlgorithm}<br><br>
         * Default: {@link AdaptiveThresholdAlgorithm} with default parameters
         * @param thresholdAlgorithm Threshold algorithm to use to determine encoding threshold
         */
        public Builder thresholdAlgorithm(ThresholdAlgorithm thresholdAlgorithm){
            this.thresholdAlgorithm = thresholdAlgorithm;
            return this;
        }

        /**
         * Residual post processor. See {@link ResidualPostProcessor} for details.
         *
         * Default: {@code new ResidualClippingPostProcessor(5.0, 5)} - i.e., a {@link ResidualClippingPostProcessor}
         * that clips the residual to +/- 5x current threshold, every 5 iterations.
         *
         * @param residualPostProcessor Residual post processor to use
         */
        public Builder residualPostProcessor(ResidualPostProcessor residualPostProcessor){
            this.residualPostProcessor = residualPostProcessor;
            return this;
        }

        /**
         * Minibatch size to use when training workers. In principle, the source data (i.e., {@code RDD<DataSet>} etc)
         * can have a different number of examples in each {@code DataSet} than we want to use when training.
         * i.e., we can split or combine DataSets if required.
         *
         * @param batchSize Minibatch size to use when fitting each worker
         */
        public Builder batchSizePerWorker(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * This method allows to configure number of network training threads per cluster node.<br>
         * Default value: -1, which defines automated number of workers selection, based on hardware present in system
         * (i.e., number of GPUs, if training on a GPU enabled system).
         * <br>
         * When training on GPUs, you should use 1 worker per GPU (which is the default). For CPUs, 1 worker per
         * node is usually preferred, though multi-CPU (i.e., multiple physical CPUs) or CPUs with large core counts
         * may have better throughput (i.e., more examples per second) when increasing the number of workers,
         * at the expense of more memory consumed. Note that if you increase the number of workers on a CPU system,
         * you should set the number of OpenMP threads using the {@code OMP_NUM_THREADS} property - see
         * {@link org.nd4j.config.ND4JEnvironmentVars#OMP_NUM_THREADS} for more details.
         * For example, a machine with 32 physical cores could use 4 workers with {@code OMP_NUM_THREADS=8}
         *
         * @param numWorkers Number of workers on each node.
         */
        public Builder workersPerNode(int numWorkers) {
            if (numWorkers < 1)
                numWorkers = -1;

            this.numWorkersPerNode = numWorkers;
            return this;
        }

        /**
         * This method allows you to artificially extend iteration time using Thread.sleep() for a given time.
         *
         * PLEASE NOTE: Never use that option in production environment. It's suited for debugging purposes only.
         *
         * @param timeMs
         * @return
         */
        @Deprecated
        public Builder debugLongerIterations(long timeMs) {
            if (timeMs < 0)
                timeMs = 0L;
            this.debugLongerIterations = timeMs;
            return this;
        }

        /**
         * Optional method: Transport implementation to be used as TransportType.CUSTOM for VoidParameterAveraging method<br>
         * Generally not used by users
         *
         * @param transport Transport to use
         * @return
         */
        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Number of minibatches to asynchronously prefetch on each worker when training. Default: 2, which is usually suitable
         * in most cases. Increasing this might help in some cases of ETL (data loading) bottlenecks, at the expense
         * of greater memory consumption
         * @param prefetchNumBatches Number of batches to prefetch
         */
        public Builder workerPrefetchNumBatches(int prefetchNumBatches){
            this.workerPrefetchNumBatches = prefetchNumBatches;
            return this;
        }

        /**
         * Repartitioner to use to repartition data before fitting.<br>
         * DL4J performs a MapPartitions operation for training, hence how the data is partitioned can matter a lot for
         * performance - too few partitions (or very imbalanced partitions can result in poor cluster utilization, due to
         * some workers being idle. A larger number of smaller partitions can help to avoid so-called "end-of-epoch"
         * effects where training can only complete once the last/slowest worker finishes it's partition.<br>
         * Default repartitioner is {@link DefaultRepartitioner}, which repartitions equally up to a maximum of 5000
         * partitions, and is usually suitable for most purposes. In the worst case, the "end of epoch" effect
         * when using the partitioner should be limited to a maximum of the amount of time required to process a single partition.
         *
         * @param repartitioner Repartitioner to use
         */
        public Builder repartitioner(Repartitioner repartitioner){
            this.repartitioner = repartitioner;
            return this;
        }

        /**
         * Used to disable the periodic garbage collection calls on the workers.<br>
         * Equivalent to {@code Nd4j.getMemoryManager().togglePeriodicGc(workerTogglePeriodicGC);}<br>
         * Pass false to disable periodic GC on the workers or true (equivalent to the default, or not setting it) to keep it enabled.
         * 
         * @param workerTogglePeriodicGC Worker periodic garbage collection setting
         */
        public Builder workerTogglePeriodicGC(boolean workerTogglePeriodicGC){
            this.workerTogglePeriodicGC = workerTogglePeriodicGC;
            return this;
        }

        /**
         * Used to set the periodic garbage collection frequency on the workers.<br>
         * Equivalent to calling {@code Nd4j.getMemoryManager().setAutoGcWindow(workerPeriodicGCFrequency);} on each worker<br>
         * Does not have any effect if {@link #workerTogglePeriodicGC(boolean)} is set to false
         * 
         * @param workerPeriodicGCFrequency The periodic GC frequency to use on the workers
         */
        public Builder workerPeriodicGCFrequency(int workerPeriodicGCFrequency){
            this.workerPeriodicGCFrequency = workerPeriodicGCFrequency;
            return this;
        }

        /**
         * Enable debug mode for threshold encoding. When enabled, various statistics for the threshold and the residual
         * will be calculated and logged on each worker (at info log level).<br>
         * This information can be used to check if the encoding threshold is too big (for example, virtually all updates
         * are much smaller than the threshold) or too big (majority of updates are much larger than the threshold).<br>
         * encodingDebugMode is disabled by default.<br>
         * <b>IMPORTANT</b>: enabling this has a performance overhead, and should not be enabled unless the debug information is actually required.<br>
         *
         * @param enabled True to enable
         */
        public Builder encodingDebugMode(boolean enabled){
            this.encodingDebugMode = enabled;
            return this;
        }

        public SharedTrainingMaster build() {
            SharedTrainingMaster master = new SharedTrainingMaster(voidConfiguration, numWorkers, rddTrainingApproach,
                            storageLevel, collectTrainingStats, repartitionStrategy, repartition,
                        thresholdAlgorithm, residualPostProcessor, rddDataSetNumExamples, batchSize,
                            debugLongerIterations, numWorkersPerNode, workerPrefetchNumBatches, repartitioner, workerTogglePeriodicGC,
                    workerPeriodicGCFrequency, encodingDebugMode);
            if (transport != null)
                master.transport = this.transport;

            return master;
        }
    }
}

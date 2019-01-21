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

package org.deeplearning4j.spark.util;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.spark.HashPartitioner;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.serializer.SerializerInstance;
import org.deeplearning4j.spark.api.Repartition;
import org.deeplearning4j.spark.api.RepartitionStrategy;
import org.deeplearning4j.spark.data.BatchDataSetsFunction;
import org.deeplearning4j.spark.data.shuffle.SplitDataSetExamplesPairFlatMapFunction;
import org.deeplearning4j.spark.impl.common.CountPartitionsFunction;
import org.deeplearning4j.spark.impl.common.SplitPartitionsFunction;
import org.deeplearning4j.spark.impl.common.SplitPartitionsFunction2;
import org.deeplearning4j.spark.impl.common.repartition.BalancedPartitioner;
import org.deeplearning4j.spark.impl.common.repartition.EqualPartitioner;
import org.deeplearning4j.spark.impl.common.repartition.HashingBalancedPartitioner;
import org.deeplearning4j.spark.impl.common.repartition.MapTupleToPairFlatMap;
import org.deeplearning4j.spark.impl.repartitioner.EqualRepartitioner;
import org.deeplearning4j.util.UIDProvider;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.util.MathUtils;
import org.slf4j.Logger;
import scala.Tuple2;

import java.io.*;
import java.lang.reflect.Array;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Various utilities for Spark
 *
 * @author Alex Black
 */
@Slf4j
public class SparkUtils {

    private static final String KRYO_EXCEPTION_MSG = "Kryo serialization detected without an appropriate registrator "
                    + "for ND4J INDArrays.\nWhen using Kryo, An appropriate Kryo registrator must be used to avoid"
                    + " serialization issues (NullPointerException) with off-heap data in INDArrays.\n"
                    + "Use nd4j-kryo_2.10 or _2.11 artifact, with sparkConf.set(\"spark.kryo.registrator\", \"org.nd4j.Nd4jRegistrator\");\n"
                    + "See https://deeplearning4j.org/spark#kryo for more details";

    private static String sparkExecutorId;

    private SparkUtils() {}

    /**
     * Check the spark configuration for incorrect Kryo configuration, logging a warning message if necessary
     *
     * @param javaSparkContext Spark context
     * @param log              Logger to log messages to
     * @return True if ok (no kryo, or correct kryo setup)
     */
    public static boolean checkKryoConfiguration(JavaSparkContext javaSparkContext, Logger log) {
        //Check if kryo configuration is correct:
        String serializer = javaSparkContext.getConf().get("spark.serializer", null);
        if (serializer != null && serializer.equals("org.apache.spark.serializer.KryoSerializer")) {
            String kryoRegistrator = javaSparkContext.getConf().get("spark.kryo.registrator", null);
            if (kryoRegistrator == null || !kryoRegistrator.equals("org.nd4j.Nd4jRegistrator")) {

                //It's probably going to fail later due to Kryo failing on the INDArray deserialization (off-heap data)
                //But: the user might be using a custom Kryo registrator that can handle ND4J INDArrays, even if they
                // aren't using the official ND4J-provided one
                //Either way: Let's test serialization now of INDArrays now, and fail early if necessary
                SerializerInstance si;
                ByteBuffer bb;
                try {
                    si = javaSparkContext.env().serializer().newInstance();
                    bb = si.serialize(Nd4j.linspace(1, 5, 5), null);
                } catch (Exception e) {
                    //Failed for some unknown reason during serialization - should never happen
                    throw new RuntimeException(KRYO_EXCEPTION_MSG, e);
                }

                if (bb == null) {
                    //Should probably never happen
                    throw new RuntimeException(
                                    KRYO_EXCEPTION_MSG + "\n(Got: null ByteBuffer from Spark SerializerInstance)");
                } else {
                    //Could serialize successfully, but still may not be able to deserialize if kryo config is wrong
                    boolean equals;
                    INDArray deserialized;
                    try {
                        deserialized = (INDArray) si.deserialize(bb, null);
                        //Equals method may fail on malformed INDArrays, hence should be within the try-catch
                        equals = Nd4j.linspace(1, 5, 5).equals(deserialized);
                    } catch (Exception e) {
                        throw new RuntimeException(KRYO_EXCEPTION_MSG, e);
                    }
                    if (!equals) {
                        throw new RuntimeException(KRYO_EXCEPTION_MSG + "\n(Error during deserialization: test array"
                                        + " was not deserialized successfully)");
                    }

                    //Otherwise: serialization/deserialization was successful using Kryo
                    return true;
                }
            }
        }
        return true;
    }

    /**
     * Write a String to a file (on HDFS or local) in UTF-8 format
     *
     * @param path    Path to write to
     * @param toWrite String to write
     * @param sc      Spark context
     */
    public static void writeStringToFile(String path, String toWrite, JavaSparkContext sc) throws IOException {
        writeStringToFile(path, toWrite, sc.sc());
    }

    /**
     * Write a String to a file (on HDFS or local) in UTF-8 format
     *
     * @param path    Path to write to
     * @param toWrite String to write
     * @param sc      Spark context
     */
    public static void writeStringToFile(String path, String toWrite, SparkContext sc) throws IOException {
        FileSystem fileSystem = FileSystem.get(sc.hadoopConfiguration());
        try (BufferedOutputStream bos = new BufferedOutputStream(fileSystem.create(new Path(path)))) {
            bos.write(toWrite.getBytes("UTF-8"));
        }
    }

    /**
     * Read a UTF-8 format String from HDFS (or local)
     *
     * @param path Path to write the string
     * @param sc   Spark context
     */
    public static String readStringFromFile(String path, JavaSparkContext sc) throws IOException {
        return readStringFromFile(path, sc.sc());
    }

    /**
     * Read a UTF-8 format String from HDFS (or local)
     *
     * @param path Path to write the string
     * @param sc   Spark context
     */
    public static String readStringFromFile(String path, SparkContext sc) throws IOException {
        FileSystem fileSystem = FileSystem.get(sc.hadoopConfiguration());
        try (BufferedInputStream bis = new BufferedInputStream(fileSystem.open(new Path(path)))) {
            byte[] asBytes = IOUtils.toByteArray(bis);
            return new String(asBytes, "UTF-8");
        }
    }

    /**
     * Write an object to HDFS (or local) using default Java object serialization
     *
     * @param path    Path to write the object to
     * @param toWrite Object to write
     * @param sc      Spark context
     */
    public static void writeObjectToFile(String path, Object toWrite, JavaSparkContext sc) throws IOException {
        writeObjectToFile(path, toWrite, sc.sc());
    }

    /**
     * Write an object to HDFS (or local) using default Java object serialization
     *
     * @param path    Path to write the object to
     * @param toWrite Object to write
     * @param sc      Spark context
     */
    public static void writeObjectToFile(String path, Object toWrite, SparkContext sc) throws IOException {
        FileSystem fileSystem = FileSystem.get(sc.hadoopConfiguration());
        try (BufferedOutputStream bos = new BufferedOutputStream(fileSystem.create(new Path(path)))) {
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(toWrite);
        }
    }

    /**
     * Read an object from HDFS (or local) using default Java object serialization
     *
     * @param path File to read
     * @param type Class of the object to read
     * @param sc   Spark context
     * @param <T>  Type of the object to read
     */
    public static <T> T readObjectFromFile(String path, Class<T> type, JavaSparkContext sc) throws IOException {
        return readObjectFromFile(path, type, sc.sc());
    }

    /**
     * Read an object from HDFS (or local) using default Java object serialization
     *
     * @param path File to read
     * @param type Class of the object to read
     * @param sc   Spark context
     * @param <T>  Type of the object to read
     */
    public static <T> T readObjectFromFile(String path, Class<T> type, SparkContext sc) throws IOException {
        FileSystem fileSystem = FileSystem.get(sc.hadoopConfiguration());
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(fileSystem.open(new Path(path))))) {
            Object o;
            try {
                o = ois.readObject();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            return (T) o;
        }
    }

    /**
     * Repartition the specified RDD (or not) using the given {@link Repartition} and {@link RepartitionStrategy} settings
     *
     * @param rdd                 RDD to repartition
     * @param repartition         Setting for when repartiting is to be conducted
     * @param repartitionStrategy Setting for how repartitioning is to be conducted
     * @param objectsPerPartition Desired number of objects per partition
     * @param numPartitions       Total number of partitions
     * @param <T>                 Type of the RDD
     * @return Repartitioned RDD, or original RDD if no repartitioning was conducted
     */
    public static <T> JavaRDD<T> repartition(JavaRDD<T> rdd, Repartition repartition,
                    RepartitionStrategy repartitionStrategy, int objectsPerPartition, int numPartitions) {
        if (repartition == Repartition.Never)
            return rdd;

        switch (repartitionStrategy) {
            case SparkDefault:
                if (repartition == Repartition.NumPartitionsWorkersDiffers && rdd.partitions().size() == numPartitions)
                    return rdd;

                //Either repartition always, or workers/num partitions differs
                return rdd.repartition(numPartitions);
            case Balanced:
                return repartitionBalanceIfRequired(rdd, repartition, objectsPerPartition, numPartitions);
            case ApproximateBalanced:
                return repartitionApproximateBalance(rdd, repartition, numPartitions);
            default:
                throw new RuntimeException("Unknown repartition strategy: " + repartitionStrategy);
        }
    }

    public static <T> JavaRDD<T> repartitionApproximateBalance(JavaRDD<T> rdd, Repartition repartition,
                    int numPartitions) {
        int origNumPartitions = rdd.partitions().size();
        switch (repartition) {
            case Never:
                return rdd;
            case NumPartitionsWorkersDiffers:
                if (origNumPartitions == numPartitions)
                    return rdd;
            case Always:
                // Count each partition...
                List<Integer> partitionCounts =
                                rdd.mapPartitionsWithIndex(new Function2<Integer, Iterator<T>, Iterator<Integer>>() {
                                    @Override
                                    public Iterator<Integer> call(Integer integer, Iterator<T> tIterator)
                                                    throws Exception {
                                        int count = 0;
                                        while (tIterator.hasNext()) {
                                            tIterator.next();
                                            count++;
                                        }
                                        return Collections.singletonList(count).iterator();
                                    }
                                }, true).collect();

                Integer totalCount = 0;
                for (Integer i : partitionCounts)
                    totalCount += i;
                List<Double> partitionWeights = new ArrayList<>(Math.max(numPartitions, origNumPartitions));
                Double ideal = (double) totalCount / numPartitions;
                // partitions in the initial set and not in the final one get -1 => elements always jump
                // partitions in the final set not in the initial one get 0 => aim to receive the average amount
                for (int i = 0; i < Math.min(origNumPartitions, numPartitions); i++) {
                    partitionWeights.add((double) partitionCounts.get(i) / ideal);
                }
                for (int i = Math.min(origNumPartitions, numPartitions); i < Math.max(origNumPartitions,
                                numPartitions); i++) {
                    // we shrink the # of partitions
                    if (i >= numPartitions)
                        partitionWeights.add(-1D);
                    // we enlarge the # of partitions
                    else
                        partitionWeights.add(0D);
                }

                // this method won't trigger a spark job, which is different from {@link org.apache.spark.rdd.RDD#zipWithIndex}

                JavaPairRDD<Tuple2<Long, Integer>, T> indexedRDD = rdd.zipWithUniqueId()
                                .mapToPair(new PairFunction<Tuple2<T, Long>, Tuple2<Long, Integer>, T>() {
                                    @Override
                                    public Tuple2<Tuple2<Long, Integer>, T> call(Tuple2<T, Long> tLongTuple2) {
                                        return new Tuple2<>(
                                                        new Tuple2<Long, Integer>(tLongTuple2._2(), 0),
                                                        tLongTuple2._1());
                                    }
                                });

                HashingBalancedPartitioner hbp =
                                new HashingBalancedPartitioner(Collections.singletonList(partitionWeights));
                JavaPairRDD<Tuple2<Long, Integer>, T> partitionedRDD = indexedRDD.partitionBy(hbp);

                return partitionedRDD.map(new Function<Tuple2<Tuple2<Long, Integer>, T>, T>() {
                    @Override
                    public T call(Tuple2<Tuple2<Long, Integer>, T> indexNPayload) {
                        return indexNPayload._2();
                    }
                });
            default:
                throw new RuntimeException("Unknown setting for repartition: " + repartition);
        }
    }

    /**
     * Repartition a RDD (given the {@link Repartition} setting) such that we have approximately
     * {@code numPartitions} partitions, each of which has {@code objectsPerPartition} objects.
     *
     * @param rdd RDD to repartition
     * @param repartition Repartitioning setting
     * @param objectsPerPartition Number of objects we want in each partition
     * @param numPartitions Number of partitions to have
     * @param <T> Type of RDD
     * @return Repartitioned RDD, or the original RDD if no repartitioning was performed
     */
    public static <T> JavaRDD<T> repartitionBalanceIfRequired(JavaRDD<T> rdd, Repartition repartition,
                    int objectsPerPartition, int numPartitions) {
        int origNumPartitions = rdd.partitions().size();
        switch (repartition) {
            case Never:
                return rdd;
            case NumPartitionsWorkersDiffers:
                if (origNumPartitions == numPartitions)
                    return rdd;
            case Always:
                //Repartition: either always, or origNumPartitions != numWorkers

                //First: count number of elements in each partition. Need to know this so we can work out how to properly index each example,
                // so we can in turn create properly balanced partitions after repartitioning
                //Because the objects (DataSets etc) should be small, this should be OK

                //Count each partition...
                List<Tuple2<Integer, Integer>> partitionCounts =
                                rdd.mapPartitionsWithIndex(new CountPartitionsFunction<T>(), true).collect();
                int totalObjects = 0;
                int initialPartitions = partitionCounts.size();

                boolean allCorrectSize = true;
                int x = 0;
                for (Tuple2<Integer, Integer> t2 : partitionCounts) {
                    int partitionSize = t2._2();
                    allCorrectSize &= (partitionSize == objectsPerPartition);
                    totalObjects += t2._2();
                }

                if (numPartitions * objectsPerPartition < totalObjects) {
                    allCorrectSize = true;
                    for (Tuple2<Integer, Integer> t2 : partitionCounts) {
                        allCorrectSize &= (t2._2() == objectsPerPartition);
                    }
                }

                if (initialPartitions == numPartitions && allCorrectSize) {
                    //Don't need to do any repartitioning here - already in the format we want
                    return rdd;
                }

                //Index each element for repartitioning (can only do manual repartitioning on a JavaPairRDD)
                JavaPairRDD<Integer, T> pairIndexed = indexedRDD(rdd);

                int remainder = (totalObjects - numPartitions * objectsPerPartition) % numPartitions;
                log.info("Amount to rebalance: numPartitions={}, objectsPerPartition={}, remainder={}", numPartitions, objectsPerPartition, remainder);
                pairIndexed = pairIndexed
                                .partitionBy(new BalancedPartitioner(numPartitions, objectsPerPartition, remainder));
                return pairIndexed.values();
            default:
                throw new RuntimeException("Unknown setting for repartition: " + repartition);
        }
    }

    public static <T> JavaPairRDD<Integer, T> indexedRDD(JavaRDD<T> rdd) {
        return rdd.zipWithIndex().mapToPair(new PairFunction<Tuple2<T, Long>, Integer, T>() {
            @Override
            public Tuple2<Integer, T> call(Tuple2<T, Long> elemIdx) {
                return new Tuple2<>(elemIdx._2().intValue(), elemIdx._1());
            }
        });
    }

    public static <T> JavaRDD<T> repartitionEqually(JavaRDD<T> rdd, Repartition repartition, int numPartitions){
        int origNumPartitions = rdd.partitions().size();
        switch (repartition) {
            case Never:
                return rdd;
            case NumPartitionsWorkersDiffers:
                if (origNumPartitions == numPartitions)
                    return rdd;
            case Always:
                return new EqualRepartitioner().repartition(rdd, -1, numPartitions);
            default:
                throw new RuntimeException("Unknown setting for repartition: " + repartition);
        }
    }

    /**
     * Random split the specified RDD into a number of RDDs, where each has {@code numObjectsPerSplit} in them.
     * <p>
     * This similar to how RDD.randomSplit works (i.e., split via filtering), but this should result in more
     * equal splits (instead of independent binomial sampling that is used there, based on weighting)
     * This balanced splitting approach is important when the number of DataSet objects we want in each split is small,
     * as random sampling variance of {@link JavaRDD#randomSplit(double[])} is quite large relative to the number of examples
     * in each split. Note however that this method doesn't <i>guarantee</i> that partitions will be balanced
     * <p>
     * Downside is we need total object count (whereas {@link JavaRDD#randomSplit(double[])} does not). However, randomSplit
     * requires a full pass of the data anyway (in order to do filtering upon it) so this should not add much overhead in practice
     *
     * @param totalObjectCount   Total number of objects in the RDD to split
     * @param numObjectsPerSplit Number of objects in each split
     * @param data               Data to split
     * @param <T>                Generic type for the RDD
     * @return The RDD split up (without replacement) into a number of smaller RDDs
     */
    public static <T> JavaRDD<T>[] balancedRandomSplit(int totalObjectCount, int numObjectsPerSplit, JavaRDD<T> data) {
        return balancedRandomSplit(totalObjectCount, numObjectsPerSplit, data, new Random().nextLong());
    }

    /**
     * Equivalent to {@link #balancedRandomSplit(int, int, JavaRDD)} with control over the RNG seed
     */
    public static <T> JavaRDD<T>[] balancedRandomSplit(int totalObjectCount, int numObjectsPerSplit, JavaRDD<T> data,
                    long rngSeed) {
        JavaRDD<T>[] splits;
        if (totalObjectCount <= numObjectsPerSplit) {
            splits = (JavaRDD<T>[]) Array.newInstance(JavaRDD.class, 1);
            splits[0] = data;
        } else {
            int numSplits = totalObjectCount / numObjectsPerSplit; //Intentional round down
            splits = (JavaRDD<T>[]) Array.newInstance(JavaRDD.class, numSplits);
            for (int i = 0; i < numSplits; i++) {
                splits[i] = data.mapPartitionsWithIndex(new SplitPartitionsFunction<T>(i, numSplits, rngSeed), true);
            }

        }
        return splits;
    }

    /**
     * Equivalent to {@link #balancedRandomSplit(int, int, JavaRDD)} but for Pair RDDs
     */
    public static <T, U> JavaPairRDD<T, U>[] balancedRandomSplit(int totalObjectCount, int numObjectsPerSplit,
                    JavaPairRDD<T, U> data) {
        return balancedRandomSplit(totalObjectCount, numObjectsPerSplit, data, new Random().nextLong());
    }

    /**
     * Equivalent to {@link #balancedRandomSplit(int, int, JavaRDD)} but for pair RDDs, and with control over the RNG seed
     */
    public static <T, U> JavaPairRDD<T, U>[] balancedRandomSplit(int totalObjectCount, int numObjectsPerSplit,
                    JavaPairRDD<T, U> data, long rngSeed) {
        JavaPairRDD<T, U>[] splits;
        if (totalObjectCount <= numObjectsPerSplit) {
            splits = (JavaPairRDD<T, U>[]) Array.newInstance(JavaPairRDD.class, 1);
            splits[0] = data;
        } else {
            int numSplits = totalObjectCount / numObjectsPerSplit; //Intentional round down

            splits = (JavaPairRDD<T, U>[]) Array.newInstance(JavaPairRDD.class, numSplits);
            for (int i = 0; i < numSplits; i++) {

                //What we really need is a .mapPartitionsToPairWithIndex function
                //but, of course Spark doesn't provide this
                //So we need to do a two-step process here...

                JavaRDD<Tuple2<T, U>> split = data.mapPartitionsWithIndex(
                                new SplitPartitionsFunction2<T, U>(i, numSplits, rngSeed), true);
                splits[i] = split.mapPartitionsToPair(new MapTupleToPairFlatMap<T, U>(), true);
            }
        }
        return splits;
    }

    /**
     * List of the files in the given directory (path), as a {@code JavaRDD<String>}
     *
     * @param sc      Spark context
     * @param path    Path to list files in
     * @return        Paths in the directory
     * @throws IOException If error occurs getting directory contents
     */
    public static JavaRDD<String> listPaths(JavaSparkContext sc, String path) throws IOException {
        return listPaths(sc, path, false);
    }

    /**
     * List of the files in the given directory (path), as a {@code JavaRDD<String>}
     *
     * @param sc        Spark context
     * @param path      Path to list files in
     * @param recursive Whether to walk the directory tree recursively (i.e., include subdirectories)
     * @return Paths in the directory
     * @throws IOException If error occurs getting directory contents
     */
    public static JavaRDD<String> listPaths(JavaSparkContext sc, String path, boolean recursive) throws IOException {
        //NativeImageLoader.ALLOWED_FORMATS
        return listPaths(sc, path, recursive, (Set<String>)null);
    }

    /**
     * List of the files in the given directory (path), as a {@code JavaRDD<String>}
     *
     * @param sc                Spark context
     * @param path              Path to list files in
     * @param recursive         Whether to walk the directory tree recursively (i.e., include subdirectories)
     * @param allowedExtensions If null: all files will be accepted. If non-null: only files with the specified extension will be allowed.
     *                          Exclude the extension separator - i.e., use "txt" not ".txt" here.
     * @return Paths in the directory
     * @throws IOException If error occurs getting directory contents
     */
    public static JavaRDD<String> listPaths(JavaSparkContext sc, String path, boolean recursive, String[] allowedExtensions) throws IOException {
        return listPaths(sc, path, recursive, (allowedExtensions == null ? null : new HashSet<>(Arrays.asList(allowedExtensions))));
    }

    /**
     * List of the files in the given directory (path), as a {@code JavaRDD<String>}
     *
     * @param sc                Spark context
     * @param path              Path to list files in
     * @param recursive         Whether to walk the directory tree recursively (i.e., include subdirectories)
     * @param allowedExtensions If null: all files will be accepted. If non-null: only files with the specified extension will be allowed.
     *                          Exclude the extension separator - i.e., use "txt" not ".txt" here.
     * @return Paths in the directory
     * @throws IOException If error occurs getting directory contents
     */
    public static JavaRDD<String> listPaths(JavaSparkContext sc, String path, boolean recursive, Set<String> allowedExtensions) throws IOException {
        return listPaths(sc, path, recursive, allowedExtensions, sc.hadoopConfiguration());
    }

    /**
     * List of the files in the given directory (path), as a {@code JavaRDD<String>}
     *
     * @param sc                Spark context
     * @param path              Path to list files in
     * @param recursive         Whether to walk the directory tree recursively (i.e., include subdirectories)
     * @param allowedExtensions If null: all files will be accepted. If non-null: only files with the specified extension will be allowed.
     *                          Exclude the extension separator - i.e., use "txt" not ".txt" here.
     * @param config            Hadoop configuration to use. Must not be null.
     * @return Paths in the directory
     * @throws IOException If error occurs getting directory contents
     */
    public static JavaRDD<String> listPaths(@NonNull JavaSparkContext sc, String path, boolean recursive,
                                            Set<String> allowedExtensions, @NonNull Configuration config) throws IOException {
        List<String> paths = new ArrayList<>();
        FileSystem hdfs = FileSystem.get(URI.create(path), config);
        RemoteIterator<LocatedFileStatus> fileIter = hdfs.listFiles(new org.apache.hadoop.fs.Path(path), recursive);

        while (fileIter.hasNext()) {
            String filePath = fileIter.next().getPath().toString();
            if(allowedExtensions == null){
                paths.add(filePath);
            } else {
                String ext = FilenameUtils.getExtension(path);
                if(allowedExtensions.contains(ext)){
                    paths.add(filePath);
                }
            }
        }
        return sc.parallelize(paths);
    }


    /**
     * Randomly shuffle the examples in each DataSet object, and recombine them into new DataSet objects
     * with the specified BatchSize
     *
     * @param rdd DataSets to shuffle/recombine
     * @param newBatchSize New batch size for the DataSet objects, after shuffling/recombining
     * @param numPartitions Number of partitions to use when splitting/recombining
     * @return A new {@link JavaRDD<DataSet>}, with the examples shuffled/combined in each
     */
    public static JavaRDD<DataSet> shuffleExamples(JavaRDD<DataSet> rdd, int newBatchSize, int numPartitions) {
        //Step 1: split into individual examples, mapping to a pair RDD (random key in range 0 to numPartitions)

        JavaPairRDD<Integer, DataSet> singleExampleDataSets =
                        rdd.flatMapToPair(new SplitDataSetExamplesPairFlatMapFunction(numPartitions));

        //Step 2: repartition according to the random keys
        singleExampleDataSets = singleExampleDataSets.partitionBy(new HashPartitioner(numPartitions));

        //Step 3: Recombine
        return singleExampleDataSets.values().mapPartitions(new BatchDataSetsFunction(newBatchSize));
    }

    /**
     * Get the Spark executor ID<br>
     * The ID is parsed from the JVM launch args. If that is not specified (or can't be obtained) then the value
     * from {@link UIDProvider#getJVMUID()} is returned
     * @return
     */
    public static String getSparkExecutorId(){
        if(sparkExecutorId != null)
            return sparkExecutorId;

        synchronized (SparkUtils.class){
            //re-check, in case some other thread set it while waiting for lock
            if(sparkExecutorId != null)
                return sparkExecutorId;

            String s = System.getProperty("sun.java.command");
            if(s == null || s.isEmpty() || !s.contains("executor-id")){
                sparkExecutorId = UIDProvider.getJVMUID();
                return sparkExecutorId;
            }

            int idx = s.indexOf("executor-id");
            String sub = s.substring(idx);
            String[] split = sub.split(" ");
            if(split.length < 2){
                sparkExecutorId = UIDProvider.getJVMUID();
                return sparkExecutorId;
            }
            sparkExecutorId = split[1];
            return sparkExecutorId;
        }
    }

    public static Broadcast<byte[]> asByteArrayBroadcast(JavaSparkContext sc, INDArray array){
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Nd4j.write(array, new DataOutputStream(baos));
        } catch (IOException e){
            throw new RuntimeException(e);  //Should never happen
        }
        byte[] paramBytes = baos.toByteArray();       //See docs in EvaluationRunner for why we use byte[] instead of INDArray (thread locality etc)
        return sc.broadcast(paramBytes);
    }
}

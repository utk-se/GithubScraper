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

package org.deeplearning4j.spark.data;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.broadcast.Broadcast;
import org.datavec.spark.util.DefaultHadoopConfig;
import org.datavec.spark.util.SerializableHadoopConfig;
import org.deeplearning4j.util.UIDProvider;
import org.nd4j.linalg.dataset.api.MultiDataSet;

import java.net.URI;
import java.util.Iterator;

/**
 * A function (used in forEachPartition) to save MultiDataSet objects to disk/HDFS. Each MultiDataSet object is given a random and
 * (probably) unique name, starting with "mds_" and ending  with ".bin".<br>
 * Use with {@code JavaRDD<MultiDataSet>.foreachPartition()}
 *
 * @author Alex Black
 */
public class MultiDataSetExportFunction implements VoidFunction<Iterator<MultiDataSet>> {
    private final URI outputDir;
    private final Broadcast<SerializableHadoopConfig> conf;
    private String uid = null;

    private int outputCount;

    public MultiDataSetExportFunction(URI outputDir) {
        this(outputDir, null);
    }

    public MultiDataSetExportFunction(URI outputDir, Broadcast<SerializableHadoopConfig> configuration) {
        this.outputDir = outputDir;
        this.conf = configuration;
    }

    @Override
    public void call(Iterator<MultiDataSet> iter) throws Exception {
        String jvmuid = UIDProvider.getJVMUID();
        uid = Thread.currentThread().getId() + jvmuid.substring(0, Math.min(8, jvmuid.length()));

        Configuration c = conf == null ? DefaultHadoopConfig.get() : conf.getValue().getConfiguration();

        while (iter.hasNext()) {
            MultiDataSet next = iter.next();

            String filename = "mds_" + uid + "_" + (outputCount++) + ".bin";

            String path = outputDir.getPath();
            URI uri = new URI(path + (path.endsWith("/") || path.endsWith("\\") ? "" : "/") + filename);
            FileSystem file = FileSystem.get(uri, c);
            try (FSDataOutputStream out = file.create(new Path(uri))) {
                next.save(out);
            }
        }
    }
}

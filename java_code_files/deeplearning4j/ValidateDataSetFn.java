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

package org.deeplearning4j.spark.util.data.validation;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.broadcast.Broadcast;
import org.datavec.spark.util.DefaultHadoopConfig;
import org.datavec.spark.util.SerializableHadoopConfig;
import org.deeplearning4j.spark.util.data.ValidationResult;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;

import java.io.EOFException;
import java.net.URI;

/**
 * Function used to validate DataSets on HDFS - see {@link org.deeplearning4j.spark.util.data.SparkDataValidation} for
 * further details
 *
 * @author Alex Black
 */
public class ValidateDataSetFn implements Function<String, ValidationResult> {
    public static final int BUFFER_SIZE = 4194304; //4 MB

    private final boolean deleteInvalid;
    private final int[] featuresShape;
    private final int[] labelsShape;
    private final Broadcast<SerializableHadoopConfig> conf;
    private transient FileSystem fileSystem;

    public ValidateDataSetFn(boolean deleteInvalid, int[] featuresShape, int[] labelsShape) {
        this(deleteInvalid, featuresShape, labelsShape, null);
    }

    public ValidateDataSetFn(boolean deleteInvalid, int[] featuresShape, int[] labelsShape, Broadcast<SerializableHadoopConfig> configuration) {
        this.deleteInvalid = deleteInvalid;
        this.featuresShape = featuresShape;
        this.labelsShape = labelsShape;
        this.conf = configuration;
    }

    @Override
    public ValidationResult call(String path) throws Exception {
        if (fileSystem == null) {
            Configuration c = conf == null ? DefaultHadoopConfig.get() : conf.getValue().getConfiguration();
            try {
                fileSystem = FileSystem.get(new URI(path), c);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        ValidationResult ret = new ValidationResult();
        ret.setCountTotal(1);

        boolean shouldDelete = false;
        boolean loadSuccessful = false;
        DataSet ds = new DataSet();
        Path p = new Path(path);

        if(fileSystem.isDirectory(p)){
            ret.setCountTotal(0);
            return ret;
        }

        if (!fileSystem.exists(p)) {
            ret.setCountMissingFile(1);
            return ret;
        }

        try (FSDataInputStream inputStream = fileSystem.open(p, BUFFER_SIZE)) {
            ds.load(inputStream);
            loadSuccessful = true;
        } catch (RuntimeException t) {
            shouldDelete = deleteInvalid;
            ret.setCountLoadingFailure(1);
        }

        boolean isValid = loadSuccessful;
        if (loadSuccessful) {
            //Validate
            if (ds.getFeatures() == null) {
                ret.setCountMissingFeatures(1);
                isValid = false;
            } else {
                if(featuresShape != null && !validateArrayShape(featuresShape, ds.getFeatures())){
                    ret.setCountInvalidFeatures(1);
                    isValid = false;
                }
            }

            if(ds.getLabels() == null){
                ret.setCountMissingLabels(1);
                isValid = false;
            } else {
                if(labelsShape != null && !validateArrayShape(labelsShape, ds.getLabels())){
                    ret.setCountInvalidLabels(1);
                    isValid = false;
                }
            }

            if(!isValid && deleteInvalid){
                shouldDelete = true;
            }
        }

        if (isValid) {
            ret.setCountTotalValid(1);
        } else {
            ret.setCountTotalInvalid(1);
        }

        if (shouldDelete) {
            fileSystem.delete(p, false);
            ret.setCountInvalidDeleted(1);
        }

        return ret;
    }

    protected static boolean validateArrayShape(int[] featuresShape, INDArray array){
        if(featuresShape == null){
            return true;
        }

        if(featuresShape.length != array.rank()){
            return false;
        } else {
            for( int i=0; i<featuresShape.length; i++ ){
                if(featuresShape[i] <= 0)
                    continue;
                if(featuresShape[i] != array.size(i)){
                    return false;
                }
            }
        }
        return true;
    }
}

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

package org.deeplearning4j.datasets.fetchers;

import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.image.loader.BaseImageLoader;
import org.datavec.image.recordreader.objdetect.ObjectDetectionRecordReader;
import org.datavec.image.transform.ImageTransform;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * The Street View House Numbers (SVHN) Dataset is a real-world image dataset for developing machine learning
 * and object recognition algorithms with minimal requirement on data preprocessing and formatting.
 *
 * The SVHN datasets contain 10 classes (digits) with 73257 digits for training, 26032 digits for testing, and 531131 extra.
 *
 * Datasets in "Format 1: Full Numbers" are fetched.
 *
 * See: <a href="http://ufldl.stanford.edu/housenumbers/">http://ufldl.stanford.edu/housenumbers/</a>
 *
 * @author saudet
 */
public class SvhnDataFetcher extends CacheableExtractableDataSetFetcher {

    private static String BASE_URL = "http://ufldl.stanford.edu/";

    public static void setBaseUrl(String baseUrl){
        BASE_URL = baseUrl;
    }

    public static int NUM_LABELS = 10;

    @Override
    public String remoteDataUrl(DataSetType set) {
        switch (set) {
            case TRAIN:
                return BASE_URL + "housenumbers/train.tar.gz";
            case TEST:
                return BASE_URL + "housenumbers/test.tar.gz";
            case VALIDATION:
                return BASE_URL + "housenumbers/extra.tar.gz";
            default:
                 throw new IllegalArgumentException("Unknown DataSetType:" + set);
        }
    }

    @Override
    public String localCacheName() {
        return "SVHN";
    }

    @Override
    public String dataSetName(DataSetType set) {
        switch (set) {
            case TRAIN:
                return "train";
            case TEST:
                return "test";
            case VALIDATION:
                return "extra";
            default:
                throw new IllegalArgumentException("Unknown DataSetType:" + set);
        }
    }

    @Override
    public long expectedChecksum(DataSetType set) {
        switch (set) {
            case TRAIN:
                return 979655493L;
            case TEST:
                return 1629515343L;
            case VALIDATION:
                return 132781169L;
            default:
                 throw new IllegalArgumentException("Unknown DataSetType:" + set);
        }
    }

    public File getDataSetPath(DataSetType set) throws IOException {
        File localCache = getLocalCacheDir();
        // check empty cache
        deleteIfEmpty(localCache);

        File datasetPath;
        switch (set) {
            case TRAIN:
                datasetPath = new File(localCache, "/train/");
                break;
            case TEST:
                datasetPath = new File(localCache, "/test/");
                break;
            case VALIDATION:
                datasetPath = new File(localCache, "/extra/");
                break;
            default:
                datasetPath = null;
        }

        if (!datasetPath.exists()) {
            downloadAndExtract(set);
        }
        return datasetPath;
    }

    @Override
    public RecordReader getRecordReader(long rngSeed, int[] imgDim, DataSetType set, ImageTransform imageTransform) {
        try {
            Random rng = new Random(rngSeed);
            File datasetPath = getDataSetPath(set);

            FileSplit data = new FileSplit(datasetPath, BaseImageLoader.ALLOWED_FORMATS, rng);
            ObjectDetectionRecordReader recordReader = new ObjectDetectionRecordReader(imgDim[1], imgDim[0], imgDim[2],
                            imgDim[4], imgDim[3], null);

            recordReader.initialize(data);
            return recordReader;
        } catch (IOException e) {
            throw new RuntimeException("Could not download SVHN", e);
        }
    }
}
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

package org.nd4j.linalg.dataset.api.iterator;

import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;

import java.util.List;

/**
 * A wrapper for a dataset to sample from.
 * This will randomly sample from the given dataset.
 *
 * @author Adam Gibson
 */
public class SamplingDataSetIterator implements DataSetIterator {

    /**
     *
     */
    private static final long serialVersionUID = -2700563801361726914L;
    private DataSet sampleFrom;
    private int batchSize;
    private int totalNumberSamples;
    private int numTimesSampled;
    private boolean replace = false;
    private DataSetPreProcessor preProcessor;

    /**
     * @param sampleFrom         the dataset to sample from
     * @param batchSize          the batch size to sample
     * @param totalNumberSamples the sample size
     */
    public SamplingDataSetIterator(DataSet sampleFrom, int batchSize, int totalNumberSamples, boolean replace) {
        super();
        this.sampleFrom = sampleFrom;
        this.batchSize = batchSize;
        this.totalNumberSamples = totalNumberSamples;
        this.replace = replace;
    }


    /**
     * @param sampleFrom         the dataset to sample from
     * @param batchSize          the batch size to sample
     * @param totalNumberSamples the sample size
     */
    public SamplingDataSetIterator(DataSet sampleFrom, int batchSize, int totalNumberSamples) {
        super();
        this.sampleFrom = sampleFrom;
        this.batchSize = batchSize;
        this.totalNumberSamples = totalNumberSamples;
    }

    @Override
    public boolean hasNext() {
        return numTimesSampled < totalNumberSamples;
    }

    @Override
    public DataSet next() {
        DataSet ret = sampleFrom.sample(batchSize, replace);
        numTimesSampled += batchSize;

        if (preProcessor != null) {
            preProcessor.preProcess(ret);
        }

        return ret;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int inputColumns() {
        return sampleFrom.numInputs();
    }

    @Override
    public int totalOutcomes() {
        return sampleFrom.numOutcomes();
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public boolean asyncSupported() {
        //Aleady in memory -> async prefetching doesn't make sense here
        return false;
    }

    @Override
    public void reset() {
        numTimesSampled = 0;
    }

    @Override
    public int batch() {
        return batchSize;
    }

    /**
     * Set a pre processor
     *
     * @param preProcessor a pre processor to set
     */
    @Override
    public void setPreProcessor(DataSetPreProcessor preProcessor) {
        this.preProcessor = preProcessor;
    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        return preProcessor;
    }

    @Override
    public List<String> getLabels() {
        return null;
    }

    @Override
    public DataSet next(int num) {
        DataSet ret = sampleFrom.sample(num);
        numTimesSampled++;
        return ret;
    }


}

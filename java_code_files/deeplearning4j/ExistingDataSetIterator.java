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

package org.deeplearning4j.datasets.iterator;


import lombok.Getter;
import lombok.NonNull;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

import java.util.Iterator;
import java.util.List;

/**
 * This wrapper provides DataSetIterator interface to existing java {@code Iterable<DataSet>} and {@code Iterator<DataSet>}.
 * Note that when using {@code Iterator<DataSet>}, resetting of the DataSetIterator is not supported
 *
 * @author raver119@gmail.com
 */
public class ExistingDataSetIterator implements DataSetIterator {
    @Getter
    private DataSetPreProcessor preProcessor;

    private transient Iterable<DataSet> iterable;
    private transient Iterator<DataSet> iterator;
    private int totalExamples = 0;
    private int numFeatures = 0;
    private int numLabels = 0;
    private List<String> labels;

    /**
     * Note that when using this constructor, resetting is not supported
     * @param iterator Iterator to wrap
     */
    public ExistingDataSetIterator(@NonNull Iterator<DataSet> iterator) {
        this.iterator = iterator;
    }

    /**
     * Note that when using this constructor, resetting is not supported
     * @param iterator Iterator to wrap
     * @param labels   String labels. May be null.
     */
    public ExistingDataSetIterator(@NonNull Iterator<DataSet> iterator, @NonNull List<String> labels) {
        this(iterator);
        this.labels = labels;
    }

    /**
     * Wraps the specified iterable. Supports resetting
     * @param iterable Iterable to wrap
     */
    public ExistingDataSetIterator(@NonNull Iterable<DataSet> iterable) {
        this.iterable = iterable;
        this.iterator = iterable.iterator();
    }

    /**
     * Wraps the specified iterable. Supports resetting
     * @param iterable Iterable to wrap
     * @param labels   Labels list. May be null
     */
    public ExistingDataSetIterator(@NonNull Iterable<DataSet> iterable, @NonNull List<String> labels) {
        this(iterable);
        this.labels = labels;
    }


    public ExistingDataSetIterator(@NonNull Iterable<DataSet> iterable, int totalExamples, int numFeatures,
                    int numLabels) {
        this(iterable);

        this.totalExamples = totalExamples;
        this.numFeatures = numFeatures;
        this.numLabels = numLabels;
    }

    @Override
    public DataSet next(int num) {
        // TODO: this might be changed
        throw new UnsupportedOperationException("next(int) isn't supported");
    }

    @Override
    public int inputColumns() {
        return numFeatures;
    }

    @Override
    public int totalOutcomes() {
        if (labels != null)
            return labels.size();

        return numLabels;
    }

    @Override
    public boolean resetSupported() {
        return iterable != null;
    }

    @Override
    public boolean asyncSupported() {
        //No need to asynchronously prefetch here: already in memory
        return false;
    }

    @Override
    public void reset() {
        if (iterable != null)
            this.iterator = iterable.iterator();
        else
            throw new IllegalStateException(
                            "To use reset() method you need to provide Iterable<DataSet>, not Iterator");
    }

    @Override
    public int batch() {
        return 0;
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor preProcessor) {
        this.preProcessor = preProcessor;
    }

    @Override
    public List<String> getLabels() {
        return labels;
    }

    @Override
    public boolean hasNext() {
        if (iterator != null)
            return iterator.hasNext();

        return false;
    }

    @Override
    public DataSet next() {
        if (preProcessor != null) {
            DataSet ds = iterator.next();
            if (!ds.isPreProcessed()) {
                preProcessor.preProcess(ds);
                ds.markAsPreProcessed();
            }
            return ds;
        } else
            return iterator.next();
    }

    @Override
    public void remove() {
        // no-op
    }
}

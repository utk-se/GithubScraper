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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * A dataset iterator for doing multiple passes over a dataset
 */
@Deprecated
public class MultipleEpochsIterator implements DataSetIterator {
    private static final Logger log = LoggerFactory.getLogger(MultipleEpochsIterator.class);
    private int numPasses;
    private int batch = 0;
    private DataSetIterator iter;
    private int passes = 0;
    private DataSetPreProcessor preProcessor;

    public MultipleEpochsIterator(int numPasses, DataSetIterator iter) {
        this.numPasses = numPasses;
        this.iter = iter;
    }

    /**
     * Like the standard next method but allows a
     * customizable number of examples returned
     *
     * @param num the number of examples
     * @return the next data applyTransformToDestination
     */
    @Override
    public DataSet next(int num) {
        if (!iter.hasNext() && passes < numPasses) {
            passes++;
            batch = 0;
            log.info("Epoch " + passes + " batch " + batch);
            iter.reset();
        }
        batch++;

        DataSet next = iter.next(num);
        if (preProcessor != null)
            preProcessor.preProcess(next);
        return next;
    }
    /**
     * Input columns for the dataset
     *
     * @return
     */
    @Override
    public int inputColumns() {
        return iter.inputColumns();
    }

    /**
     * The number of labels for the dataset
     *
     * @return
     */
    @Override
    public int totalOutcomes() {
        return iter.totalOutcomes();
    }

    @Override
    public boolean resetSupported() {
        return iter.resetSupported();
    }

    @Override
    public boolean asyncSupported() {
        return iter.asyncSupported();
    }

    /**
     * Resets the iterator back to the beginning
     */
    @Override
    public void reset() {
        passes = 0;
        batch = 0;
        iter.reset();
    }

    /**
     * Batch size
     *
     * @return
     */
    @Override
    public int batch() {
        return iter.batch();
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

    /**
     * Returns {@code true} if the iteration has more elements.
     * (In other words, returns {@code true} if {@link #next} would
     * return an element rather than throwing an exception.)
     *
     * @return {@code true} if the iteration has more elements
     */
    @Override
    public boolean hasNext() {
        return iter.hasNext() || passes < numPasses;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration
     */
    @Override
    public DataSet next() {
        if (!iter.hasNext() && passes < numPasses) {
            passes++;
            batch = 0;
            log.info("Epoch " + passes + " batch " + batch);
            iter.reset();
        }
        batch++;

        DataSet next = iter.next();
        if (preProcessor != null)
            preProcessor.preProcess(next);
        return next;
    }


    /**
     * Removes from the underlying collection the last element returned
     * by this iterator (optional operation).  This method can be called
     * only once per call to {@link #next}.  The behavior of an iterator
     * is unspecified if the underlying collection is modified while the
     * iteration is in progress in any way other than by calling this
     * method.
     *
     * @throws UnsupportedOperationException if the {@code remove}
     *                                       operation is not supported by this iterator
     * @throws IllegalStateException         if the {@code next} method has not
     *                                       yet been called, or the {@code remove} method has already
     *                                       been called after the last call to the {@code next}
     *                                       method
     */
    @Override
    public void remove() {
        iter.remove();
    }
}

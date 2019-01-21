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

package org.deeplearning4j.ui.stats.api;

import java.io.Serializable;

/**
 * Similar to {@link StatsInitializationConfiguration}, StatsUpdateConfiguration is an interface defining the stats
 * that should be collected and reported periodically.
 * In some implementations, this configuration may vary over time (i.e., stats may in principle be reconfigured by the user)
 *
 * @author Alex Black
 */
public interface StatsUpdateConfiguration extends Serializable {

    /**
     * Get the reporting frequency, in terms of listener calls
     */
    int reportingFrequency();

    //TODO
    //boolean useNTPTimeSource();

    //--- Performance and System Stats ---

    /**
     * Should performance stats be collected/reported?
     * Total time, total examples, total batches, Minibatches/second, examples/second
     */
    boolean collectPerformanceStats();

    /**
     * Should JVM, off-heap and memory stats be collected/reported?
     */
    boolean collectMemoryStats();

    /**
     * Should garbage collection stats be collected and reported?
     */
    boolean collectGarbageCollectionStats();

    //TODO
    //    boolean collectDataSetMetaData();

    //--- General ---

    /**
     * Should per-parameter type learning rates be collected and reported?
     */
    boolean collectLearningRates();

    //--- Histograms ---

    /**
     * Should histograms (per parameter type, or per layer for activations) of the given type be collected?
     *
     * @param type Stats type: Parameters, Updates, Activations
     */
    boolean collectHistograms(StatsType type);

    /**
     * Get the number of histogram bins to use for the given type (for use with {@link #collectHistograms(StatsType)}
     *
     * @param type Stats type: Parameters, Updates, Activatinos
     */
    int numHistogramBins(StatsType type);

    //--- Summary Stats: Mean, Variance, Mean Magnitudes ---

    /**
     * Should the mean values (per parameter type, or per layer for activations) be collected?
     *
     * @param type Stats type: Parameters, Updates, Activations
     */
    boolean collectMean(StatsType type);

    /**
     * Should the standard devication values (per parameter type, or per layer for activations) be collected?
     *
     * @param type Stats type: Parameters, Updates, Activations
     */
    boolean collectStdev(StatsType type);

    /**
     * Should the mean magnitude values (per parameter type, or per layer for activations) be collected?
     *
     * @param type Stats type: Parameters, Updates, Activations
     */
    boolean collectMeanMagnitudes(StatsType type);

}

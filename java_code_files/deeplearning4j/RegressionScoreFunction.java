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

package org.deeplearning4j.arbiter.scoring.impl;

import lombok.*;
import org.deeplearning4j.arbiter.optimize.api.data.DataSource;
import org.deeplearning4j.arbiter.scoring.RegressionValue;
import org.deeplearning4j.arbiter.scoring.util.ScoreUtil;
import org.deeplearning4j.datasets.iterator.MultiDataSetWrapperIterator;
import org.deeplearning4j.eval.RegressionEvaluation;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;

import java.util.Properties;

/**
 * Score function for regression (including multi-label regression) for a MultiLayerNetwork or ComputationGraph
 * on a test set. Supports all regression metrics: {@link RegressionEvaluation.Metric}
 *
 * @author Alex Black
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)  //For JSON
public class RegressionScoreFunction extends BaseNetScoreFunction {

    protected RegressionEvaluation.Metric metric;

    public RegressionScoreFunction(@NonNull RegressionEvaluation.Metric metric) {
        this.metric = metric;
    }

    @Override
    public boolean minimize() {
        switch (metric) {
            case MSE:
            case MAE:
            case RMSE:
            case RSE:
                return true;
            case PC:
            case R2:
                return false;
            default:
                throw new IllegalStateException("Unknown metric: " + metric);
        }
    }

    @Override
    public String toString() {
        return "RegressionScoreFunction(metric=" + metric + ")";
    }

    @Override
    public double score(MultiLayerNetwork net, DataSetIterator iterator) {
        RegressionEvaluation e = net.evaluateRegression(iterator);
        return e.scoreForMetric(metric);
    }

    @Override
    public double score(MultiLayerNetwork net, MultiDataSetIterator iterator) {
        return score(net, new MultiDataSetWrapperIterator(iterator));
    }

    @Override
    public double score(ComputationGraph graph, DataSetIterator iterator) {
        RegressionEvaluation e = graph.evaluateRegression(iterator);
        return e.scoreForMetric(metric);
    }

    @Override
    public double score(ComputationGraph graph, MultiDataSetIterator iterator) {
        RegressionEvaluation e = graph.evaluateRegression(iterator);
        return e.scoreForMetric(metric);
    }
}

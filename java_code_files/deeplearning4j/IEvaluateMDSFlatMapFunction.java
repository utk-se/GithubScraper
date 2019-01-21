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

package org.deeplearning4j.spark.impl.graph.evaluation;

import lombok.extern.slf4j.Slf4j;
import org.apache.spark.broadcast.Broadcast;
import org.datavec.spark.functions.FlatMapFunctionAdapter;
import org.datavec.spark.transform.BaseFlatMapFunctionAdaptee;
import org.deeplearning4j.spark.impl.evaluation.EvaluationRunner;
import org.nd4j.evaluation.IEvaluation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Future;

/**
 * Function to evaluate data (using one or more IEvaluation instances), in a distributed manner
 * Flat map function used to batch examples for computational efficiency + reduce number of IEvaluation objects returned
 * for network efficiency.
 *
 * @author Alex Black
 */
public class IEvaluateMDSFlatMapFunction<T extends IEvaluation>
                extends BaseFlatMapFunctionAdaptee<Iterator<MultiDataSet>, T[]> {

    public IEvaluateMDSFlatMapFunction(Broadcast<String> json, Broadcast<byte[]> params, int evalNumWorkers, int evalBatchSize,
                    T... evaluations) {
        super(new IEvaluateMDSFlatMapFunctionAdapter<>(json, params, evalNumWorkers, evalBatchSize, evaluations));
    }
}


/**
 * Function to evaluate data (using an IEvaluation instance), in a distributed manner
 * Flat map function used to batch examples for computational efficiency + reduce number of IEvaluation objects returned
 * for network efficiency.
 *
 * @author Alex Black
 */
@Slf4j
class IEvaluateMDSFlatMapFunctionAdapter<T extends IEvaluation>
                implements FlatMapFunctionAdapter<Iterator<MultiDataSet>, T[]> {

    protected Broadcast<String> json;
    protected Broadcast<byte[]> params;
    protected int evalNumWorkers;
    protected int evalBatchSize;
    protected T[] evaluations;

    /**
     * @param json Network configuration (json format)
     * @param params Network parameters
     * @param evalBatchSize Max examples per evaluation. Do multiple separate forward passes if data exceeds
     *                              this. Used to avoid doing too many at once (and hence memory issues)
     * @param evaluations Initial evaulation instance (i.e., empty Evaluation or RegressionEvaluation instance)
     */
    public IEvaluateMDSFlatMapFunctionAdapter(Broadcast<String> json, Broadcast<byte[]> params, int evalNumWorkers,
                                              int evalBatchSize, T[] evaluations) {
        this.json = json;
        this.params = params;
        this.evalNumWorkers = evalNumWorkers;
        this.evalBatchSize = evalBatchSize;
        this.evaluations = evaluations;
    }

    @Override
    public Iterable<T[]> call(Iterator<MultiDataSet> dataSetIterator) throws Exception {
        if (!dataSetIterator.hasNext()) {
            return Collections.emptyList();
        }

        if (!dataSetIterator.hasNext()) {
            return Collections.emptyList();
        }

        Future<IEvaluation[]> f = EvaluationRunner.getInstance().execute(
                evaluations, evalNumWorkers, evalBatchSize, null, dataSetIterator, true, json, params);

        IEvaluation[] result = f.get();
        if(result == null){
            return Collections.emptyList();
        } else {
            return Collections.singletonList((T[])result);
        }
    }
}

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

package org.deeplearning4j.spark.parameterserver.functions;

import org.datavec.spark.functions.FlatMapFunctionAdapter;
import org.datavec.spark.transform.BaseFlatMapFunctionAdaptee;
import org.deeplearning4j.spark.api.TrainingResult;
import org.deeplearning4j.spark.api.TrainingWorker;
import org.deeplearning4j.spark.parameterserver.pw.SharedTrainingWrapper;
import org.deeplearning4j.spark.parameterserver.training.SharedTrainingResult;
import org.deeplearning4j.spark.parameterserver.training.SharedTrainingWorker;
import org.nd4j.linalg.dataset.api.MultiDataSet;

import java.util.Collections;
import java.util.Iterator;

/**
 * Created by raver119 on 13.06.17.
 */
public class SharedFlatMapMultiDataSet<R extends TrainingResult>
                extends BaseFlatMapFunctionAdaptee<Iterator<MultiDataSet>, R> {

    public SharedFlatMapMultiDataSet(TrainingWorker<R> worker) {
        super(new SharedFlatMapMultiDataSetAdapter<R>(worker));
    }
}


class SharedFlatMapMultiDataSetAdapter<R extends TrainingResult>
                implements FlatMapFunctionAdapter<Iterator<MultiDataSet>, R> {

    private final SharedTrainingWorker worker;

    public SharedFlatMapMultiDataSetAdapter(TrainingWorker<R> worker) {
        // we're not going to have anything but Shared classes here ever
        this.worker = (SharedTrainingWorker) worker;
    }

    @Override
    public Iterable<R> call(Iterator<MultiDataSet> dataSetIterator) throws Exception {
        //Under some limited circumstances, we might have an empty partition. In this case, we should return immediately
        if(!dataSetIterator.hasNext()){
            return Collections.emptyList();
        }
        /*
            That's the place where we do our stuff. Here's the plan:
            1) we pass given iterator to VirtualDataSetIterator, which acts as holder for them
            2) Virtual iterator will provide load balancing between available devices
            3) we'll lock out here
         */

        // iterator should be silently attached to VirtualDataSetIterator, and used appropriately
        SharedTrainingWrapper.getInstance(worker.getInstanceId()).attachMDS(dataSetIterator);

        // first callee will become master, others will obey and die
        // all threads in this executor will be blocked here until training finished
        SharedTrainingResult result = SharedTrainingWrapper.getInstance(worker.getInstanceId()).run(worker);

        return Collections.singletonList((R) result);
    }
}

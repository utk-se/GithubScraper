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

package org.deeplearning4j.spark.datavec.iterator;

import lombok.AllArgsConstructor;
import org.apache.spark.api.java.function.Function;
import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.writable.Writable;
import org.deeplearning4j.datasets.datavec.RecordReaderMultiDataSetIterator;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class RRMDSIFunction implements Function<DataVecRecords, MultiDataSet> {

    private RecordReaderMultiDataSetIterator iterator;

    @Override
    public MultiDataSet call(DataVecRecords records) throws Exception {



        Map<String, List<List<Writable>>> nextRRVals = Collections.emptyMap();
        Map<String, List<List<List<Writable>>>> nextSeqRRVals = Collections.emptyMap();

        if(records.getRecords() != null && !records.getRecords().isEmpty()){
            nextRRVals = new HashMap<>();

            Map<String, RecordReader> m = iterator.getRecordReaders();
            for(Map.Entry<String,RecordReader> e : m.entrySet()){
                SparkSourceDummyReader dr = (SparkSourceDummyReader)e.getValue();
                int idx = dr.getReaderIdx();
                nextRRVals.put(e.getKey(), Collections.singletonList(records.getRecords().get(idx)));
            }

        }
        if(records.getSeqRecords() != null && !records.getSeqRecords().isEmpty()){
            nextSeqRRVals = new HashMap<>();

            Map<String, SequenceRecordReader> m = iterator.getSequenceRecordReaders();
            for(Map.Entry<String,SequenceRecordReader> e : m.entrySet()){
                SparkSourceDummySeqReader dr = (SparkSourceDummySeqReader)e.getValue();
                int idx = dr.getReaderIdx();
                nextSeqRRVals.put(e.getKey(), Collections.singletonList(records.getSeqRecords().get(idx)));
            }
        }


        MultiDataSet mds = iterator.nextMultiDataSet(nextRRVals, null, nextSeqRRVals, null);
        Nd4j.getExecutioner().commit();

        return mds;
    }
}

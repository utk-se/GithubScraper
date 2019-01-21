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

package org.datavec.perf.timing;

import lombok.val;
import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.split.InputStreamInputSplit;
import org.datavec.api.writable.Writable;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.performance.PerformanceTracker;
import org.nd4j.linalg.memory.MemcpyDirection;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;



/**
 * Timing components of a data vec pipeline
 * consisting of:
 * {@link RecordReader}, {@link InputStreamInputSplit}
 * (note that this uses input stream input split,
 * the record reader must support {@link InputStreamInputSplit} for this to work)
 *
 * @author Adam Gibson
 */
public class IOTiming {


    /**
     * Returns statistics for  components of a datavec pipeline
     * averaged over the specified number of times
     * @param nTimes the number of times to run the pipeline for averaging
     * @param recordReader the record reader
     * @param file the file to read
     * @param function the function
     * @return the averaged {@link TimingStatistics} for input/output on a record
     * reader and ndarray creation (based on the given function
     * @throws Exception
     */
    public static TimingStatistics averageFileRead(long nTimes, RecordReader recordReader, File file, INDArrayCreationFunction function) throws Exception {
        TimingStatistics timingStatistics = null;
        for(int i = 0; i < nTimes; i++) {
            TimingStatistics timingStatistics1 = timeNDArrayCreation(recordReader,new BufferedInputStream(new FileInputStream(file)),function);
            if(timingStatistics == null)
                timingStatistics = timingStatistics1;
            else {
                timingStatistics = timingStatistics.add(timingStatistics1);
            }

        }

        return timingStatistics.average(nTimes);
    }

    /**
     *
     * @param reader
     * @param inputStream
     * @param function
     * @return
     * @throws Exception
     */
    public static TimingStatistics timeNDArrayCreation(RecordReader reader,
                                                       InputStream inputStream,
                                                       INDArrayCreationFunction function) throws Exception {


        reader.initialize(new InputStreamInputSplit(inputStream));
        long longNanos = System.nanoTime();
        List<Writable> next = reader.next();
        long endNanos = System.nanoTime();
        long etlDiff = endNanos - longNanos;
        long startArrCreation = System.nanoTime();
        INDArray arr = function.createFromRecord(next);
        long endArrCreation = System.nanoTime();
        long endCreationDiff = endArrCreation - startArrCreation;
        Map<Integer, Map<MemcpyDirection, Long>> currentBandwidth = PerformanceTracker.getInstance().getCurrentBandwidth();
        val bw = currentBandwidth.get(0).get(MemcpyDirection.HOST_TO_DEVICE);
        val deviceToHost = currentBandwidth.get(0).get(MemcpyDirection.HOST_TO_DEVICE);

        return TimingStatistics.builder()
                .diskReadingTimeNanos(etlDiff)
                .bandwidthNanosHostToDevice(bw)
                .bandwidthDeviceToHost(deviceToHost)
                .ndarrayCreationTimeNanos(endCreationDiff)
                .build();
    }

    public  interface INDArrayCreationFunction {
        INDArray createFromRecord(List<Writable> record);
    }

}

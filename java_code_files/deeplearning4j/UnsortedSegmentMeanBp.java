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

package org.nd4j.linalg.api.ops.impl.transforms.segment.bp;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ops.DynamicCustomOp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unsorted segment mean backprop operation
 *
 * @author Alex Black
 */
public class UnsortedSegmentMeanBp extends DynamicCustomOp {

    private int numSegments;

    public UnsortedSegmentMeanBp(SameDiff sameDiff, SDVariable data, SDVariable segmentIds, SDVariable gradient, int numSegments) {
        super(null, sameDiff,  new SDVariable[] {data, segmentIds, gradient}, false);
        this.numSegments = numSegments;
        addIArgument(numSegments);
    }

    public UnsortedSegmentMeanBp(){ }

    @Override
    public String opName(){
        return "unsorted_segment_mean_bp";
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> inputDataTypes){
        Preconditions.checkState(inputDataTypes != null && inputDataTypes.size() == 3, "Expected exactly 3 input data types for %s, got %s", getClass(), inputDataTypes);
        return Arrays.asList(inputDataTypes.get(0), inputDataTypes.get(1));
    }

}

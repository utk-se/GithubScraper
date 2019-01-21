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

package org.deeplearning4j.clustering.vptree;

import lombok.Getter;
import org.deeplearning4j.clustering.sptree.DataPoint;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Brute force search
 * for running search
 * relative to a target
 * but forced to fill the result list
 * until the desired k is matched.
 *
 * The algorithm does this by searching
 * nearby points by k in a greedy fashion
 */
public class VPTreeFillSearch {
    private VPTree vpTree;
    private int k;
    @Getter
    private List<DataPoint> results;
    @Getter
    private List<Double> distances;
    private INDArray target;

    public VPTreeFillSearch(VPTree vpTree, int k, INDArray target) {
        this.vpTree = vpTree;
        this.k = k;
        this.target = target;
    }

    public void search() {
        results = new ArrayList<>();
        distances = new ArrayList<>();
        //initial search
        //vpTree.search(target,k,results,distances);

        //fill till there is k results
        //by going down the list
        //   if(results.size() < k) {
        INDArray distancesArr = Nd4j.create(vpTree.getItems().rows(), 1);
        vpTree.calcDistancesRelativeTo(target, distancesArr);
        INDArray[] sortWithIndices = Nd4j.sortWithIndices(distancesArr, 0, !vpTree.isInvert());
        results.clear();
        distances.clear();
        if (vpTree.getItems().isVector()) {
            for (int i = 0; i < k; i++) {
                int idx = sortWithIndices[0].getInt(i);
                results.add(new DataPoint(idx, Nd4j.scalar(vpTree.getItems().getDouble(idx))));
                distances.add(sortWithIndices[1].getDouble(idx));
            }
        } else {
            for (int i = 0; i < k; i++) {
                int idx = sortWithIndices[0].getInt(i);
                results.add(new DataPoint(idx, vpTree.getItems().getRow(idx)));
                distances.add(sortWithIndices[1].getDouble(idx));
            }
        }


    }


}

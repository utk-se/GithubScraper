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

package org.deeplearning4j.clustering.condition;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.deeplearning4j.clustering.iteration.IterationHistory;
import org.nd4j.linalg.indexing.conditions.Condition;
import org.nd4j.linalg.indexing.conditions.LessThan;

import java.io.Serializable;

/**
 *
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class VarianceVariationCondition implements ClusteringAlgorithmCondition, Serializable {

    private Condition varianceVariationCondition;
    private int period;



    /**
     *
     * @param varianceVariation
     * @param period
     * @return
     */
    public static VarianceVariationCondition varianceVariationLessThan(double varianceVariation, int period) {
        Condition condition = new LessThan(varianceVariation);
        return new VarianceVariationCondition(condition, period);
    }


    /**
     *
     * @param iterationHistory
     * @return
     */
    public boolean isSatisfied(IterationHistory iterationHistory) {
        if (iterationHistory.getIterationCount() <= period)
            return false;

        for (int i = 0, j = iterationHistory.getIterationCount(); i < period; i++) {
            double variation = iterationHistory.getIterationInfo(j - i).getClusterSetInfo()
                            .getPointDistanceFromClusterVariance();
            variation -= iterationHistory.getIterationInfo(j - i - 1).getClusterSetInfo()
                            .getPointDistanceFromClusterVariance();
            variation /= iterationHistory.getIterationInfo(j - i - 1).getClusterSetInfo()
                            .getPointDistanceFromClusterVariance();

            if (!varianceVariationCondition.apply(variation))
                return false;
        }

        return true;
    }



}

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

package org.deeplearning4j.eval;

import lombok.val;
import org.deeplearning4j.util.TimeSeriesUtils;
import org.nd4j.base.Preconditions;
import org.nd4j.evaluation.IEvaluation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;

import java.util.Arrays;

/**
 * @deprecated Use {@link org.nd4j.evaluation.EvaluationUtils}
 */
@Deprecated
public class EvaluationUtils extends org.nd4j.evaluation.EvaluationUtils {


    public static <T> T copyToLegacy(IEvaluation<?> from, Class<T> to){
        if(from == null)
            return null;
        Preconditions.checkState(to.isAssignableFrom(from.getClass()), "Invalid classes: %s vs %s", from.getClass(), to);


        throw new UnsupportedOperationException("Not implemented");
    }
}

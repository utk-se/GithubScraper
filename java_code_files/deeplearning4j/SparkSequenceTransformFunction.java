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

package org.datavec.spark.transform.sequence;

import lombok.AllArgsConstructor;
import org.apache.spark.api.java.function.Function;
import org.datavec.api.transform.Transform;
import org.datavec.api.writable.Writable;

import java.util.List;

/**
 * Spark function for transforming sequences using a Transform
 * @author Alex Black
 */
@AllArgsConstructor
public class SparkSequenceTransformFunction implements Function<List<List<Writable>>, List<List<Writable>>> {

    private final Transform transform;

    @Override
    public List<List<Writable>> call(List<List<Writable>> v1) throws Exception {
        return transform.mapSequence(v1);
    }
}

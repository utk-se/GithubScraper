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

package org.datavec.spark.transform.analysis.unique;

import lombok.AllArgsConstructor;
import org.apache.spark.api.java.function.Function2;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.writable.Writable;

import java.util.*;

/**
 * Simple function used in AnalyzeSpark.getUnique
 *
 * @author Alex Black
 */
@AllArgsConstructor
public class UniqueAddFunction implements Function2<Map<String,Set<Writable>>, List<Writable>, Map<String,Set<Writable>>> {

    private final List<String> columns;
    private final Schema schema;

    @Override
    public Map<String, Set<Writable>> call(Map<String, Set<Writable>> v1, List<Writable> v2) throws Exception {
        if(v2 == null){
            return v1;
        }

        if(v1 == null){
            v1 = new HashMap<>();
            for(String s : columns){
                v1.put(s, new HashSet<Writable>());
            }
        }
        for(String s : columns){
            int idx = schema.getIndexOfColumn(s);
            Writable value = v2.get(idx);
            v1.get(s).add(value);
        }
        return v1;
    }
}

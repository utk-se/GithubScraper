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

package org.datavec.api.transform.transform.string;

import lombok.Data;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.shade.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Converts String column into a sparse bag-of-words (BOW)
 * represented as an NDArray of indices. Appropriate for
 * embeddings or as efficient storage before being expanded
 * into a dense array.
 *
 * @author dave@skymind.io
 */
@Data
public class StringListToIndicesNDArrayTransform extends StringListToCountsNDArrayTransform {
    /**
     * @param columnName     The name of the column to convert
     * @param vocabulary     The possible tokens that may be present.
     * @param delimiter      The delimiter for the Strings to convert
     * @param ignoreUnknown  Whether to ignore unknown tokens
     */
    public StringListToIndicesNDArrayTransform(String columnName, List<String> vocabulary, String delimiter,
                    boolean binary, boolean ignoreUnknown) {
        super(columnName, vocabulary, delimiter, binary, ignoreUnknown);
    }

    public StringListToIndicesNDArrayTransform(@JsonProperty("columnName") String columnName,
                    @JsonProperty("newColumnName") String newColumnName,
                    @JsonProperty("vocabulary") List<String> vocabulary, @JsonProperty("delimiter") String delimiter,
                    @JsonProperty("binary") boolean binary, @JsonProperty("ignoreUnknown") boolean ignoreUnknown) {
        super(columnName, newColumnName, vocabulary, delimiter, binary, ignoreUnknown);
    }

    @Override
    protected INDArray makeBOWNDArray(Collection<Integer> indices) {
        INDArray counts = Nd4j.zeros(indices.size());
        List<Integer> indicesSorted = new ArrayList<>(indices);
        Collections.sort(indicesSorted);
        for (int i = 0; i < indicesSorted.size(); i++)
            counts.putScalar(i, indicesSorted.get(i));
        Nd4j.getExecutioner().commit();
        return counts;
    }
}

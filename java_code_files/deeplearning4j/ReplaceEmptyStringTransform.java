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
import lombok.EqualsAndHashCode;
import org.datavec.api.writable.Text;
import org.datavec.api.writable.Writable;
import org.nd4j.shade.jackson.annotation.JsonProperty;

/**
 * Replace empty String values with the specified String
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ReplaceEmptyStringTransform extends BaseStringTransform {

    private String value;

    public ReplaceEmptyStringTransform(@JsonProperty("columnName") String columnName,
                    @JsonProperty("value") String value) {
        super(columnName);
        this.value = value;
    }

    @Override
    public Text map(Writable writable) {
        String s = writable.toString();
        if (s == null || s.isEmpty())
            return new Text(value);
        else if (writable instanceof Text)
            return (Text) writable;
        else
            return new Text(writable.toString());
    }

    /**
     * Transform an object
     * in to another object
     *
     * @param input the record to transform
     * @return the transformed writable
     */
    @Override
    public Object map(Object input) {
        String s = input.toString();
        if (s == null || s.isEmpty())
            return value;
        else if (s instanceof String)
            return s;
        else
            return s;
    }
}

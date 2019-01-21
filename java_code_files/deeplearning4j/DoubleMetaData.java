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

package org.datavec.api.transform.metadata;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.datavec.api.transform.ColumnType;
import org.datavec.api.writable.Writable;
import org.nd4j.shade.jackson.annotation.JsonProperty;

/**
 * MetaData for a double column.
 *
 * @author Alex Black
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DoubleMetaData extends BaseColumnMetaData {

    //minAllowedValue/maxAllowedValue are nullable: null -> no restriction on minAllowedValue/maxAllowedValue values
    private final Double minAllowedValue;
    private final Double maxAllowedValue;
    private final boolean allowNaN;
    private final boolean allowInfinite;

    public DoubleMetaData(String name) {
        this(name, null, null, false, false);
    }

    /**
     * @param minAllowedValue Min allowed value. If null: no restriction on minAllowedValue value in this column
     * @param maxAllowedValue Max allowed value. If null: no restriction on maxAllowedValue value in this column
     */
    public DoubleMetaData(@JsonProperty("name") String name, @JsonProperty("minAllowedValue") Double minAllowedValue,
                    @JsonProperty("maxAllowedValue") Double maxAllowedValue) {
        this(name, minAllowedValue, maxAllowedValue, false, false);
    }

    /**
     * @param min           Min allowed value. If null: no restriction on minAllowedValue value in this column
     * @param maxAllowedValue           Max allowed value. If null: no restriction on maxAllowedValue value in this column
     * @param allowNaN      Are NaN values ok?
     * @param allowInfinite Are +/- infinite values ok?
     */
    public DoubleMetaData(String name, Double min, Double maxAllowedValue, boolean allowNaN, boolean allowInfinite) {
        super(name);
        this.minAllowedValue = min;
        this.maxAllowedValue = maxAllowedValue;
        this.allowNaN = allowNaN;
        this.allowInfinite = allowInfinite;
    }

    @Override
    public ColumnType getColumnType() {
        return ColumnType.Double;
    }

    @Override
    public boolean isValid(Writable writable) {
        double d;
        try {
            d = writable.toDouble();
        } catch (Exception e) {
            return false;
        }

        if (allowNaN && Double.isNaN(d))
            return true;
        if (allowInfinite && Double.isInfinite(d))
            return true;

        if (minAllowedValue != null && d < minAllowedValue)
            return false;
        if (maxAllowedValue != null && d > maxAllowedValue)
            return false;

        return true;
    }

    /**
     * Is the given object valid for this column,
     * given the column type and any
     * restrictions given by the
     * ColumnMetaData object?
     *
     * @param input object to check
     * @return true if value, false if invalid
     */
    @Override
    public boolean isValid(Object input) {
        double d;
        try {
            d = Double.valueOf(input.toString());
        } catch (Exception e) {
            return false;
        }

        if (allowNaN && Double.isNaN(d))
            return true;
        if (allowInfinite && Double.isInfinite(d))
            return true;

        if (minAllowedValue != null && d < minAllowedValue)
            return false;
        if (maxAllowedValue != null && d > maxAllowedValue)
            return false;

        return true;
    }

    @Override
    public DoubleMetaData clone() {
        return new DoubleMetaData(name, minAllowedValue, maxAllowedValue, allowNaN, allowInfinite);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DoubleMetaData(name=\"").append(name).append("\",");
        boolean needComma = false;
        if (minAllowedValue != null) {
            sb.append("minAllowed=").append(minAllowedValue);
            needComma = true;
        }
        if (maxAllowedValue != null) {
            if (needComma)
                sb.append(",");
            sb.append("maxAllowed=").append(maxAllowedValue);
            needComma = true;
        }
        if (needComma)
            sb.append(",");
        sb.append("allowNaN=").append(allowNaN).append(",allowInfinite=").append(allowInfinite).append(")");
        return sb.toString();
    }
}

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

package org.datavec.api.transform.condition.column;

import org.datavec.api.transform.ColumnOp;
import org.datavec.api.transform.condition.Condition;
import org.datavec.api.transform.condition.SequenceConditionMode;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.writable.Writable;

import java.util.List;

/**
 * Created by agibsonccc on 11/26/16.
 */
public interface ColumnCondition extends Condition, ColumnOp {
    SequenceConditionMode DEFAULT_SEQUENCE_CONDITION_MODE = SequenceConditionMode.Or;

    @Override
    void setInputSchema(Schema schema);

    /**
     * Get the output schema for this transformation, given an input schema
     *
     * @param inputSchema
     */
    @Override
    Schema transform(Schema inputSchema);

    @Override
    Schema getInputSchema();

    @Override
    boolean condition(List<Writable> list);

    @Override
    boolean conditionSequence(List<List<Writable>> list);

    @Override
    boolean conditionSequence(Object list);

    /**
     * The output column name
     * after the operation has been applied
     *
     * @return the output column name
     */
    @Override
    String outputColumnName();

    /**
     * The output column names
     * This will often be the same as the input
     *
     * @return the output column names
     */
    @Override
    String[] outputColumnNames();

    /**
     * Returns column names
     * this op is meant to run on
     *
     * @return
     */
    @Override
    String[] columnNames();

    /**
     * Returns a singular column name
     * this op is meant to run on
     *
     * @return
     */
    @Override
    String columnName();

    /**
     * Returns whether the given element
     * meets the condition set by this operation
     * @param writable the element to test
     * @return true if the condition is met
     * false otherwise
     */
    boolean columnCondition(Writable writable);
}

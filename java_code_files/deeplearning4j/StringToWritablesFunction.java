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

package org.datavec.local.transforms.misc;

import lombok.AllArgsConstructor;
import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.split.StringSplit;
import org.datavec.api.writable.Writable;
import org.nd4j.linalg.function.Function;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Convert a String to a List<Writable> using a DataVec record reader
 *
 */
@AllArgsConstructor
public class StringToWritablesFunction implements Function<String, List<Writable>> {

    private RecordReader recordReader;

    @Override
    public List<Writable> apply(String s) {
        try {
            recordReader.initialize(new StringSplit(s));
        } catch (IOException e) {
           throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        Collection<Writable> next = recordReader.next();
        if (next instanceof List)
            return (List<Writable>) next;
        return new ArrayList<>(next);
    }
}

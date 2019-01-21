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

package org.datavec.api.writable.comparator;

import org.datavec.api.writable.Writable;
import org.datavec.api.writable.WritableType;

import java.util.Comparator;

public class Comparators {

    private Comparators(){ }

    public static Comparator<Writable> forType(WritableType type) {
        return forType(type, true);
    }

    public static Comparator<Writable> forType(WritableType type, boolean ascending){
        Comparator<Writable> c;
        switch (type){
            case Byte:
            case Int:
                c = new IntWritableComparator();
                break;
            case Double:
                c = new DoubleWritableComparator();
                break;
            case Float:
                c = new FloatWritableComparator();
                break;
            case Long:
                c = new LongWritableComparator();
                break;
            case Text:
                c = new TextWritableComparator();
                break;
            case Boolean:
            case NDArray:
            case Image:
            case Null:
            default:
                throw new UnsupportedOperationException("No built-in comparator for writable type: " + type);
        }
        if(ascending){
            return c;
        }
        return new ReverseComparator<>(c);
    }
}

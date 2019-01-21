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

package org.datavec.spark.transform.misc;

import lombok.AllArgsConstructor;
import org.apache.spark.api.java.function.Function;
import org.datavec.api.writable.Writable;

import java.util.List;

/**
 * Simple function to map an example to a String format (such as CSV)
 * with given quote around the string value if it contains the delimiter.
 *
 * @author Alex Black
 */
@AllArgsConstructor
public class WritablesToStringFunction implements Function<List<Writable>, String> {

    private final String delim;
    private final String quote;

    public WritablesToStringFunction(String delim) {
        this(delim, null);
    }

    @Override
    public String call(List<Writable> c) throws Exception {

        StringBuilder sb = new StringBuilder();
        append(c, sb, delim, quote);

        return sb.toString();
    }

    public static void append(List<Writable> c, StringBuilder sb, String delim, String quote) {
        boolean first = true;
        for (Writable w : c) {
            if (!first)
                sb.append(delim);
            String s = w.toString();
            boolean needQuotes = s.contains(delim);
            if (needQuotes && quote != null) {
                sb.append(quote);
                s = s.replace(quote, quote + quote);
            }
            sb.append(s);
            if (needQuotes && quote != null) {
                sb.append(quote);
            }
            first = false;
        }
    }
}

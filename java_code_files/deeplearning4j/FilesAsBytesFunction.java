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

package org.datavec.local.transforms.functions.data;


import org.apache.commons.io.IOUtils;
import org.datavec.api.writable.BytesWritable;
import org.datavec.api.writable.Text;
import org.nd4j.linalg.function.Function;
import org.nd4j.linalg.primitives.Pair;

import java.io.IOException;
import java.io.InputStream;

/**A PairFunction that simply loads bytes[] from a PortableDataStream, and wraps it (and the String key)
 * in Text and BytesWritable respectively.
 * @author Alex Black
 */
public class FilesAsBytesFunction implements Function<Pair<String, InputStream>, Pair<Text, BytesWritable>> {
    @Override
    public Pair<Text, BytesWritable> apply(Pair<String, InputStream> in) {
        try {
            return Pair.of(new Text(in.getFirst()), new BytesWritable(IOUtils.toByteArray(in.getSecond())));
        } catch (IOException e) {
            throw new IllegalStateException(e);

        }

    }
}

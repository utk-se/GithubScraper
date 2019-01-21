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

package org.nd4j.linalg.api.indexing.shape;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.nd4j.linalg.BaseNd4jTest;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.indexing.Indices;
import org.nd4j.linalg.indexing.NDArrayIndex;

/**
 * @author Adam Gibson
 */
@RunWith(Parameterized.class)
public class IndexShapeTests2d extends BaseNd4jTest {

    public IndexShapeTests2d(Nd4jBackend backend) {
        super(backend);
    }

    private long[] shape = {3, 2};


    @Test
    public void test2dCases() {
        assertArrayEquals(new long[] {1, 2}, Indices.shape(shape, NDArrayIndex.point(1)));
        assertArrayEquals(new long[] {3, 1},
                        Indices.shape(shape, NDArrayIndex.all(), NDArrayIndex.point(1)));
    }

    @Test
    public void testNewAxis2d() {
        assertArrayEquals(new long[] {1, 3, 2}, Indices.shape(shape,
                NDArrayIndex.newAxis(), NDArrayIndex.all(), NDArrayIndex.all()));
        assertArrayEquals(new long[] {3, 1, 2}, Indices.shape(shape,
                NDArrayIndex.all(), NDArrayIndex.newAxis(), NDArrayIndex.all()));

    }


    @Override
    public char ordering() {
        return 'f';
    }
}

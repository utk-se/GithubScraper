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

package org.nd4j.linalg.api.blas;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.nd4j.linalg.BaseNd4jTest;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;

import static org.junit.Assert.assertEquals;

/**
 * @author raver119@gmail.com
 */
@RunWith(Parameterized.class)
public class Level2Test extends BaseNd4jTest {
    public Level2Test(Nd4jBackend backend) {
        super(backend);
    }

    @Test
    public void testGemv1() {
        INDArray array1 = Nd4j.linspace(1, 1000, 1000).reshape(10, 100);
        INDArray array2 = Nd4j.linspace(1, 100, 100).reshape(100, 1);

        INDArray array3 = array1.mmul(array2);

        assertEquals(10, array3.length());
        assertEquals(338350f, array3.getFloat(0), 0.001f);
        assertEquals(843350f, array3.getFloat(1), 0.001f);
        assertEquals(1348350f, array3.getFloat(2), 0.001f);
        assertEquals(1853350f, array3.getFloat(3), 0.001f);
    }

    @Test
    public void testGemv2() {
        INDArray array1 = Nd4j.linspace(1, 1000, 1000).reshape(10, 100);
        INDArray array2 = Nd4j.linspace(1, 100, 100).reshape('f', 100, 1);

        INDArray array3 = array1.mmul(array2);

        assertEquals(10, array3.length());
        assertEquals(338350f, array3.getFloat(0), 0.001f);
        assertEquals(843350f, array3.getFloat(1), 0.001f);
        assertEquals(1348350f, array3.getFloat(2), 0.001f);
        assertEquals(1853350f, array3.getFloat(3), 0.001f);
    }

    @Test
    public void testGemv3() {
        INDArray array1 = Nd4j.linspace(1, 1000, 1000).reshape('f', 10, 100);
        INDArray array2 = Nd4j.linspace(1, 100, 100).reshape('f', 100, 1);

        INDArray array3 = array1.mmul(array2);

        assertEquals(10, array3.length());
        assertEquals(3338050f, array3.getFloat(0), 0.001f);
        assertEquals(3343100f, array3.getFloat(1), 0.001f);
        assertEquals(3348150f, array3.getFloat(2), 0.001f);
        assertEquals(3353200f, array3.getFloat(3), 0.001f);
    }

    @Test
    public void testGemv4() {
        INDArray array1 = Nd4j.linspace(1, 1000, 1000).reshape('f', 10, 100);
        INDArray array2 = Nd4j.linspace(1, 100, 100).reshape(100, 1);

        INDArray array3 = array1.mmul(array2);

        assertEquals(10, array3.length());
        assertEquals(3338050f, array3.getFloat(0), 0.001f);
        assertEquals(3343100f, array3.getFloat(1), 0.001f);
        assertEquals(3348150f, array3.getFloat(2), 0.001f);
        assertEquals(3353200f, array3.getFloat(3), 0.001f);
    }

    @Test
    public void testGemv5() {
        INDArray array1 = Nd4j.linspace(1, 1000, 1000).reshape(10, 100);
        INDArray array2 = Nd4j.linspace(1, 100, 100).reshape(100, 1);

        INDArray array3 = Nd4j.create(10,1);

        array1.mmul(array2, array3);

        assertEquals(10, array3.length());
        assertEquals(338350f, array3.getFloat(0), 0.001f);
        assertEquals(843350f, array3.getFloat(1), 0.001f);
        assertEquals(1348350f, array3.getFloat(2), 0.001f);
        assertEquals(1853350f, array3.getFloat(3), 0.001f);
    }

    @Test
    public void testGemv6() {
        INDArray array1 = Nd4j.linspace(1, 1000, 1000).reshape('f', 10, 100);
        INDArray array2 = Nd4j.linspace(1, 100, 100).reshape(100, 1);

        INDArray array3 = Nd4j.create(10,1);

        array1.mmul(array2, array3);

        assertEquals(10, array3.length());
        assertEquals(3338050f, array3.getFloat(0), 0.001f);
        assertEquals(3343100f, array3.getFloat(1), 0.001f);
        assertEquals(3348150f, array3.getFloat(2), 0.001f);
        assertEquals(3353200f, array3.getFloat(3), 0.001f);
    }

    @Test
    public void testGemv7() {
        INDArray array1 = Nd4j.linspace(1, 1000, 1000).reshape('f', 10, 100);
        INDArray array2 = Nd4j.linspace(1, 100, 100).reshape(100, 1);

        INDArray array3 = Nd4j.create(10,1);

        array1.mmul(array2, array3);

        assertEquals(10, array3.length());
        assertEquals(3338050f, array3.getFloat(0), 0.001f);
        assertEquals(3343100f, array3.getFloat(1), 0.001f);
        assertEquals(3348150f, array3.getFloat(2), 0.001f);
        assertEquals(3353200f, array3.getFloat(3), 0.001f);
    }

    @Override
    public char ordering() {
        return 'c';
    }
}

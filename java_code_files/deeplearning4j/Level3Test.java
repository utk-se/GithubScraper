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
public class Level3Test extends BaseNd4jTest {
    public Level3Test(Nd4jBackend backend) {
        super(backend);
    }

    @Test
    public void testGemm1() {
        INDArray array1 = Nd4j.linspace(1, 100, 100).reshape(1, 100);
        INDArray array2 = Nd4j.linspace(1, 100, 100).reshape(100, 1);

        INDArray array3 = array1.mmul(array2);

        assertEquals(338350f, array3.getFloat(0), 0.001f);
    }

    @Test
    public void testGemm2() {
        INDArray array1 = Nd4j.linspace(1, 100, 100).reshape('f', 1, 100);
        INDArray array2 = Nd4j.linspace(1, 100, 100).reshape('f', 100, 1);

        INDArray array3 = array1.mmul(array2);

        assertEquals(338350f, array3.getFloat(0), 0.001f);
    }

    @Test
    public void testGemm3() {
        INDArray array1 = Nd4j.linspace(1, 1000, 1000).reshape(10, 100);
        INDArray array2 = Nd4j.linspace(1, 1000, 1000).reshape(100, 10);

        INDArray array3 = array1.mmul(array2);


        //System.out.println("Array3: " + Arrays.toString(array3.data().asFloat()));

        assertEquals(3338050.0f, array3.data().getFloat(0), 0.001f);
        assertEquals(8298050.0f, array3.data().getFloat(1), 0.001f);
        assertEquals(3343100.0f, array3.data().getFloat(10), 0.001f);
        assertEquals(8313100.0f, array3.data().getFloat(11), 0.001f);
        assertEquals(3348150.0f, array3.data().getFloat(20), 0.001f);
        assertEquals(8328150.0f, array3.data().getFloat(21), 0.001f);
    }

    @Test
    public void testGemm4() {
        INDArray array1 = Nd4j.linspace(1, 1000, 1000).reshape(10, 100);
        INDArray array2 = Nd4j.linspace(1, 1000, 1000).reshape('f', 100, 10);

        INDArray array3 = array1.mmul(array2);

        //System.out.println("Array3: " + Arrays.toString(array3.data().asFloat()));

        assertEquals(338350f, array3.data().getFloat(0), 0.001f);
        assertEquals(843350f, array3.data().getFloat(1), 0.001f);
        assertEquals(843350f, array3.data().getFloat(10), 0.001f);
        assertEquals(2348350f, array3.data().getFloat(11), 0.001f);
        assertEquals(1348350f, array3.data().getFloat(20), 0.001f);
        assertEquals(3853350f, array3.data().getFloat(21), 0.001f);
    }

    @Test
    public void testGemm5() {
        INDArray array1 = Nd4j.linspace(1, 1000, 1000).reshape('f', 10, 100);
        INDArray array2 = Nd4j.linspace(1, 1000, 1000).reshape(100, 10);

        INDArray array3 = array1.mmul(array2);

        //System.out.println("Array3: " + Arrays.toString(array3.data().asFloat()));

        //assertEquals(3.29341E7f, array3.data().getFloat(0),10f);
        assertEquals(3.29837E7f, array3.data().getFloat(1), 10f);
        assertEquals(3.3835E7f, array3.data().getFloat(99), 10f);
    }

    @Test
    public void testGemm6() {
        INDArray array1 = Nd4j.linspace(1, 1000, 1000).reshape('f', 10, 100);
        INDArray array2 = Nd4j.linspace(1, 1000, 1000).reshape('f', 100, 10);

        INDArray array3 = array1.mmul(array2);

        //System.out.println("Array3: " + Arrays.toString(array3.data().asFloat()));

        assertEquals(3338050.0f, array3.data().getFloat(0), 0.001f);
        assertEquals(3343100f, array3.data().getFloat(1), 0.001f);
        assertEquals(8298050f, array3.data().getFloat(10), 0.001f);
        assertEquals(8313100.0f, array3.data().getFloat(11), 0.001f);
        assertEquals(1.325805E7f, array3.data().getFloat(20), 5f);
        assertEquals(1.32831E7f, array3.data().getFloat(21), 5f);
    }

    @Override
    public char ordering() {
        return 'c';
    }
}

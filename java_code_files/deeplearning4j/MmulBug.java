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

package org.nd4j.linalg;

/**
 * Created by susaneraly on 8/26/16.
 */

import org.junit.Test;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import static org.junit.Assert.assertEquals;

public class MmulBug {
    @Test
    public void simpleTest() {
        INDArray m1 = Nd4j.create(new double[][] {{1.0}, {2.0}, {3.0}, {4.0}});

        m1 = m1.reshape(2, 2);

        INDArray m2 = Nd4j.create(new double[][] {{1.0, 2.0, 3.0, 4.0},});
        m2 = m2.reshape(2, 2);
        m2.setOrder('f');

        //mmul gives the correct result
        INDArray correctResult;
        correctResult = m1.mmul(m2);
        System.out.println("================");
        System.out.println(m1);
        System.out.println(m2);
        System.out.println(correctResult);
        System.out.println("================");
        INDArray newResult = Nd4j.create(DataType.DOUBLE, correctResult.shape(), 'c');
        m1.mmul(m2, newResult);
        assertEquals(correctResult, newResult);

        //But not so mmuli (which is somewhat mixed)
        INDArray target = Nd4j.linspace(1, 4, 4).reshape(2, 2);
        target = m1.mmuli(m2, m1);
        assertEquals(true, target.equals(correctResult));
        assertEquals(true, m1.equals(correctResult));
    }
}

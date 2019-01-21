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

package org.nd4j.linalg.mixed;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.Test;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.factory.Nd4j;

import static org.junit.Assert.*;

@Slf4j
public class StringArrayTests {

    @Test
    public void testBasicStrings_1() {
        val array = Nd4j.scalar("alpha");

        assertNotNull(array);
        assertEquals(1, array.length());
        assertEquals(0, array.rank());
        assertEquals(DataType.UTF8, array.dataType());

        assertEquals("alpha", array.getStringUnsafe(0));
        String s = array.toString();
        assertTrue(s, s.contains("alpha"));
        System.out.println(s);
    }

    @Test
    public void testBasicStrings_2() {
        val array = Nd4j.create("alpha","beta", "gamma");

        assertNotNull(array);
        assertEquals(3, array.length());
        assertEquals(1, array.rank());
        assertEquals(DataType.UTF8, array.dataType());

        assertEquals("alpha", array.getStringUnsafe(0));
        assertEquals("beta", array.getStringUnsafe(1));
        assertEquals("gamma", array.getStringUnsafe(2));
        String s = array.toString();
        assertTrue(s, s.contains("alpha"));
        assertTrue(s, s.contains("beta"));
        assertTrue(s, s.contains("gamma"));
        System.out.println(s);
    }

    @Test
    public void testBasicStrings_3() {
        val arrayX = Nd4j.create("alpha", "beta", "gamma");
        val arrayY = Nd4j.create("alpha", "beta", "gamma");
        val arrayZ = Nd4j.create("Alpha", "bEta", "gamma");

        assertEquals(arrayX, arrayX);
        assertEquals(arrayX, arrayY);
        assertNotEquals(arrayX, arrayZ);
    }
}

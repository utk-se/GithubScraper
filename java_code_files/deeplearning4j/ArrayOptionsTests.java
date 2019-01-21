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

package org.nd4j.linalg.options;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.nd4j.linalg.BaseNd4jTest;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.shape.options.ArrayOptionsHelper;
import org.nd4j.linalg.api.shape.options.ArrayType;
import org.nd4j.linalg.factory.Nd4jBackend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@Slf4j
@RunWith(Parameterized.class)
public class ArrayOptionsTests extends BaseNd4jTest {
    private static long[] shapeInfo;

    public ArrayOptionsTests(Nd4jBackend backend) {
        super(backend);
    }


    @Before
    public void setUp() {
        shapeInfo = new long[]{2, 2, 2, 2, 1, 0, 1, 99};
    }

    @Test
    public void testArrayType_0() {
        assertEquals(ArrayType.DENSE, ArrayOptionsHelper.arrayType(shapeInfo));
    }

    @Test
    public void testArrayType_1() {
        ArrayOptionsHelper.setOptionBit(shapeInfo, ArrayType.EMPTY);

        assertEquals(ArrayType.EMPTY, ArrayOptionsHelper.arrayType(shapeInfo));
    }

    @Test
    public void testArrayType_2() {
        ArrayOptionsHelper.setOptionBit(shapeInfo, ArrayType.SPARSE);

        assertEquals(ArrayType.SPARSE, ArrayOptionsHelper.arrayType(shapeInfo));
    }

    @Test
    public void testArrayType_3() {
        ArrayOptionsHelper.setOptionBit(shapeInfo, ArrayType.COMPRESSED);

        assertEquals(ArrayType.COMPRESSED, ArrayOptionsHelper.arrayType(shapeInfo));
    }

    @Test
    public void testDataTypesToFromLong(){

        for(DataType dt : DataType.values()){
            if(dt == DataType.UNKNOWN)
                continue;
            String s = dt.toString();
            long l = 0;
            l = ArrayOptionsHelper.setOptionBit(l, dt);
            assertNotEquals(s, 0, l);
            DataType dt2 = ArrayOptionsHelper.dataType(l);
            assertEquals(s, dt, dt2);
        }

    }

    @Override
    public char ordering() {
        return 'c';
    }
}

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

package org.nd4j.linalg.api.ndarray;


import lombok.val;
import org.nd4j.linalg.api.buffer.BaseDataBuffer;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author Susan Eraly
 */
public class BaseNDArrayProxy implements java.io.Serializable {

    /**
     * This is a proxy class so that ndarrays can be serialized and deserialized independent of the backend
     * Be it cpu or gpu
     */

    protected long[] arrayShape;
    protected long length;
    protected char arrayOrdering;
    protected transient DataBuffer data;

    public BaseNDArrayProxy(INDArray anInstance) {
        if (anInstance.isView()) {
            anInstance = anInstance.dup(anInstance.ordering());
        }
        this.arrayShape = anInstance.shape();
        this.length = anInstance.length();
        this.arrayOrdering = anInstance.ordering();
        this.data = anInstance.data();
    }

    // READ DONE HERE - return an NDArray using the available backend
    private Object readResolve() throws java.io.ObjectStreamException {
        return Nd4j.create(data, arrayShape, Nd4j.getStrides(arrayShape, arrayOrdering), 0, arrayOrdering);
    }

    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        try {
            //Should have array shape and ordering here
            s.defaultReadObject();
            //Need to call deser explicitly on data buffer
            read(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    //Custom deserialization for Java serialization
    protected void read(ObjectInputStream s) throws IOException, ClassNotFoundException {
        val header = BaseDataBuffer.readHeader(s);
        data = Nd4j.createBuffer(header.getRight(), length, false);

        data.read(s, header.getLeft(), header.getMiddle(), header.getRight());
    }

    // WRITE DONE HERE
    private void writeObject(ObjectOutputStream out) throws IOException {
        //takes care of everything but data buffer
        out.defaultWriteObject();
        write(out);
    }

    //Custom serialization for Java serialization
    protected void write(ObjectOutputStream out) throws IOException {
        data.write(out);
    }

}

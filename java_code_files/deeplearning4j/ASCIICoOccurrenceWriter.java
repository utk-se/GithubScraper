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

package org.deeplearning4j.models.glove.count;

import lombok.NonNull;
import org.deeplearning4j.models.sequencevectors.sequence.SequenceElement;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;

/**
 * @author raver119@gmail.com
 */
public class ASCIICoOccurrenceWriter<T extends SequenceElement> implements CoOccurrenceWriter<T> {

    private File file;
    private PrintWriter writer;

    public ASCIICoOccurrenceWriter(@NonNull File file) {
        this.file = file;
        try {
            this.writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(file), 10 * 1024 * 1024));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeObject(CoOccurrenceWeight<T> object) {
        StringBuilder builder = new StringBuilder(String.valueOf(object.getElement1().getIndex())).append(" ")
                        .append(String.valueOf(object.getElement2().getIndex())).append(" ")
                        .append(String.valueOf(object.getWeight()));
        writer.println(builder.toString());
    }

    @Override
    public void queueObject(CoOccurrenceWeight<T> object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void finish() {
        try {
            writer.flush();
        } catch (Exception e) {
        }

        try {
            writer.close();
        } catch (Exception e) {
        }
    }
}

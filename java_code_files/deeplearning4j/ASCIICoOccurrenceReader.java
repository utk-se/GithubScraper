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
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.PrefetchingSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;

import java.io.File;
import java.io.PrintWriter;

/**
 * @author raver119@gmail.com
 */
public class ASCIICoOccurrenceReader<T extends SequenceElement> implements CoOccurenceReader<T> {
    private File file;
    private PrintWriter writer;
    private SentenceIterator iterator;
    private VocabCache<T> vocabCache;

    public ASCIICoOccurrenceReader(@NonNull File file, @NonNull VocabCache<T> vocabCache) {
        this.vocabCache = vocabCache;
        this.file = file;
        try {
            iterator = new PrefetchingSentenceIterator.Builder(new BasicLineIterator(file)).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public boolean hasMoreObjects() {
        return iterator.hasNext();
    }


    /**
     * Returns next CoOccurrenceWeight object
     *
     * PLEASE NOTE: This method can return null value.
     * @return
     */
    @Override
    public CoOccurrenceWeight<T> nextObject() {
        String line = iterator.nextSentence();
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] strings = line.split(" ");

        CoOccurrenceWeight<T> object = new CoOccurrenceWeight<>();
        object.setElement1(vocabCache.elementAtIndex(Integer.valueOf(strings[0])));
        object.setElement2(vocabCache.elementAtIndex(Integer.valueOf(strings[1])));
        object.setWeight(Double.parseDouble(strings[2]));

        return object;
    }



    @Override
    public void finish() {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}

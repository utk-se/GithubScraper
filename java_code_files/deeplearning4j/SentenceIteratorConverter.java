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

package org.deeplearning4j.text.sentenceiterator.interoperability;

import lombok.NonNull;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Simple class providing compatibility between SentenceIterator/LabelAwareSentenceIterator and LabelAwareIterator
 *
 * @author raver119@gmail.com
 */
public class SentenceIteratorConverter implements LabelAwareIterator {
    private SentenceIterator backendIterator;
    private LabelsSource generator;
    protected static final Logger log = LoggerFactory.getLogger(SentenceIteratorConverter.class);

    public SentenceIteratorConverter(@NonNull SentenceIterator iterator) {
        this.backendIterator = iterator;
        this.generator = new LabelsSource();
    }

    public SentenceIteratorConverter(@NonNull SentenceIterator iterator, @NonNull LabelsSource generator) {
        this.backendIterator = iterator;
        this.generator = generator;
    }

    @Override
    public boolean hasNextDocument() {
        return backendIterator.hasNext();
    }

    @Override
    public LabelledDocument nextDocument() {
        LabelledDocument document = new LabelledDocument();

        document.setContent(backendIterator.nextSentence());
        if (backendIterator instanceof LabelAwareSentenceIterator) {
            List<String> labels = ((LabelAwareSentenceIterator) backendIterator).currentLabels();
            if (labels != null) {
                for (String label : labels) {
                    document.addLabel(label);
                    generator.storeLabel(label);
                }
            } else {
                String label = ((LabelAwareSentenceIterator) backendIterator).currentLabel();
                if (label != null) {
                    document.addLabel(label);
                    generator.storeLabel(label);
                }
            }
        } else if (generator != null)
            document.addLabel(generator.nextLabel());

        return document;
    }

    @Override
    public void reset() {
        generator.reset();
        backendIterator.reset();
    }

    @Override
    public boolean hasNext() {
        return hasNextDocument();
    }

    @Override
    public LabelledDocument next() {
        return nextDocument();
    }

    @Override
    public void remove() {
        // no-op
    }

    @Override
    public LabelsSource getLabelsSource() {
        return generator;
    }

    @Override
    public void shutdown() {
        // no-op
    }
}

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

package org.deeplearning4j.text.documentiterator;

import lombok.Data;
import lombok.ToString;
import org.deeplearning4j.models.word2vec.VocabWord;

import java.util.ArrayList;
import java.util.List;

/**
 * This is primitive holder of document, and it's label.
 *
 * @author raver119@gmail.com
 */
@Data
@ToString(exclude = "referencedContent")
public class LabelledDocument {

    // optional field
    private String id;

    // initial text representation of current document
    private String content;


    private List<String> labels = new ArrayList<>();


    /*
        as soon as sentence was parsed for vocabulary words, there's no need to hold string representation, and we can just stick to references to those VocabularyWords
      */
    private List<VocabWord> referencedContent;

    /**
     * This method returns first label for the document.
     *
     * PLEASE NOTE: This method is here only for backward compatibility purposes, getLabels() should be used instead.
     *
     * @return
     */
    @Deprecated
    public String getLabel() {
        return labels.get(0);
    }

    @Deprecated
    public void setLabel(String label) {
        if (!labels.isEmpty())
            labels.set(0, label);
        else
            labels.add(label);
    }

    public void addLabel(String label) {
        labels.add(label);
    }

}

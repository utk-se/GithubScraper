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

package org.datavec.nlp.vectorizer;

import org.datavec.api.conf.Configuration;
import org.datavec.api.records.Record;
import org.datavec.api.records.reader.RecordReader;
import org.datavec.nlp.tokenization.tokenizer.Tokenizer;
import org.datavec.nlp.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.datavec.nlp.tokenization.tokenizerfactory.TokenizerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Tf idf vectorizer
 * @author Adam Gibson
 */
public abstract class AbstractTfidfVectorizer<VECTOR_TYPE> extends TextVectorizer<VECTOR_TYPE> {

    @Override
    public void doWithTokens(Tokenizer tokenizer) {
        Set<String> seen = new HashSet<>();
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (!stopWords.contains(token)) {
                cache.incrementCount(token);
                if (!seen.contains(token)) {
                    cache.incrementDocCount(token);
                }
                seen.add(token);
            }
        }
    }

    @Override
    public TokenizerFactory createTokenizerFactory(Configuration conf) {
        String clazz = conf.get(TOKENIZER, DefaultTokenizerFactory.class.getName());
        try {
            Class<? extends TokenizerFactory> tokenizerFactoryClazz =
                            (Class<? extends TokenizerFactory>) Class.forName(clazz);
            return tokenizerFactoryClazz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public abstract VECTOR_TYPE createVector(Object[] args);

    @Override
    public abstract VECTOR_TYPE fitTransform(RecordReader reader);

    @Override
    public abstract VECTOR_TYPE transform(Record record);
}

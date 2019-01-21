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

package org.deeplearning4j.models.word2vec;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.deeplearning4j.text.documentiterator.FileLabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.io.ClassPathResource;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Collection;

import static org.junit.Assert.assertEquals;


@Slf4j
public class Word2VecTestsSmall {
    WordVectors word2vec;

    @Before
    public void setUp() throws Exception {
        word2vec = WordVectorSerializer.readWord2VecModel(new ClassPathResource("vec.bin").getFile());
    }

    @Test
    public void testWordsNearest2VecTxt() {
        String word = "Adam";
        String expectedNeighbour = "is";
        int neighbours = 1;

        Collection<String> nearestWords = word2vec.wordsNearest(word, neighbours);
        System.out.println(nearestWords);
        assertEquals(expectedNeighbour, nearestWords.iterator().next());
    }

    @Test
    public void testWordsNearest2NNeighbours() {
        String word = "Adam";
        int neighbours = 2;

        Collection<String> nearestWords = word2vec.wordsNearest(word, neighbours);
        System.out.println(nearestWords);
        assertEquals(neighbours, nearestWords.size());
    }

    @Test
    public void testUnkSerialization_1() throws Exception {
        val inputFile = new ClassPathResource("/big/raw_sentences.txt").getFile();

        val iter = new BasicLineIterator(inputFile);
        val t = new DefaultTokenizerFactory();
        t.setTokenPreProcessor(new CommonPreprocessor());

        val vec = new Word2Vec.Builder()
                .minWordFrequency(1)
                .epochs(1)
                .layerSize(300)
                .limitVocabularySize(1) // Limit the vocab size to 2 words
                .windowSize(5)
                .allowParallelTokenization(true)
                .batchSize(512)
                .learningRate(0.025)
                .minLearningRate(0.0001)
                .negativeSample(0.0)
                .sampling(0.0)
                .useAdaGrad(false)
                .useHierarchicSoftmax(true)
                .iterations(1)
                .useUnknown(true) // Using UNK with limited vocab size causes the issue
                .seed(42)
                .iterate(iter)
                .workers(4)
                .tokenizerFactory(t).build();

        vec.fit();

        val tmpFile = File.createTempFile("temp","temp");
        tmpFile.deleteOnExit();

        WordVectorSerializer.writeWord2VecModel(vec, tmpFile); // NullPointerException was thrown here
    }

    @Test
    public void testLabelAwareIterator_1() throws Exception {
        val resource = new ClassPathResource("/labeled");
        val file = resource.getFile();

        val iter = (LabelAwareIterator) new FileLabelAwareIterator.Builder().addSourceFolder(file).build();

        val t = new DefaultTokenizerFactory();

        val w2v = new Word2Vec.Builder()
                .iterate(iter)
                .tokenizerFactory(t)
                .build();

        // we hope nothing is going to happen here
    }

    @Test
    public void testPlot() {
        //word2vec.lookupTable().plotVocab();
    }
}

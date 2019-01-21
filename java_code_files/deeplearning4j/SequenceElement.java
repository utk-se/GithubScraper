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

package org.deeplearning4j.models.sequencevectors.sequence;

import com.google.common.util.concurrent.AtomicDouble;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.util.HashUtil;
import org.nd4j.shade.jackson.annotation.JsonIgnore;
import org.nd4j.shade.jackson.databind.DeserializationFeature;
import org.nd4j.shade.jackson.databind.MapperFeature;
import org.nd4j.shade.jackson.databind.ObjectMapper;
import org.nd4j.shade.jackson.databind.SerializationFeature;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 *  SequenceElement is basic building block for SequenceVectors. Any data sequence can be represented as ordered set of SequenceElements,
 *  and then one can learn distributed representation of each SequenceElement in this sequence using CBOW or SkipGram.
 *
 * @author raver119@gmail.com
 */
public abstract class SequenceElement implements Comparable<SequenceElement>, Serializable {

    private static final long serialVersionUID = 2223750736522624732L;

    protected AtomicDouble elementFrequency = new AtomicDouble(0);

    //used in comparison when building the huffman tree
    protected int index = -1;
    protected List<Byte> codes = new ArrayList<>();

    protected List<Integer> points = new ArrayList<>();
    protected short codeLength = 0;

    // this var defines, if this token can't be truncated with minWordFrequency threshold
    @Getter
    @Setter
    protected boolean special;

    // this var defines that we have label here
    protected boolean isLabel;

    // this var defines how many documents/sequences contain this word
    protected AtomicLong sequencesCount = new AtomicLong(0);


    // this var is used as state for preciseWeightInit routine, to avoid multiple initializations for the same data
    @Getter
    @Setter
    protected boolean init;

    /*
            Reserved for Joint/Distributed vocabs mechanics
    */
    @Setter
    protected Long storageId;

    /**
     * This method should return string representation of this SequenceElement, so it can be used for
     *
     * @return
     */
    abstract public String getLabel();

    /**
     * This method returns number of documents/sequences where this element was evidenced
     *
     * @return
     */
    public long getSequencesCount() {
        return sequencesCount.get();
    }

    /**
     * This method sets documents count to specified value
     *
     * @param count
     */
    public void setSequencesCount(long count) {
        this.sequencesCount.set(count);
    }

    /**
     * Increments document count by one
     */
    public void incrementSequencesCount() {
        this.sequencesCount.incrementAndGet();
    }

    /**
     * Increments document count by specified value
     * @param count
     */
    public void incrementSequencesCount(long count) {
        this.sequencesCount.addAndGet(count);
    }

    /**
     * Returns whether this element was defined as label, or no
     *
     * @return
     */
    public boolean isLabel() {
        return isLabel;
    }

    /**
     * This method specifies, whether this element should be treated as label for some sequence/document or not.
     *
     * @param isLabel
     */
    public void markAsLabel(boolean isLabel) {
        this.isLabel = isLabel;
    }

    /**
     * This method returns SequenceElement's frequency in current training corpus.
     *
     * @return
     */
    public double getElementFrequency() {
        return elementFrequency.get();
    }

    /**
     * This method sets frequency value for this element
     *
     * @param value
     */
    public void setElementFrequency(long value) {
        elementFrequency.set(value);
    }

    /**
     * Increases element frequency counter by 1
     */
    public void incrementElementFrequency() {
        increaseElementFrequency(1);
    }

    /**
     * Increases element frequency counter by argument
     *
     * @param by
     */
    public void increaseElementFrequency(int by) {
        elementFrequency.getAndAdd(by);
    }

    /**
     * Equals method override should be properly implemented for any extended class, otherwise it will be based on label equality
     *
     * @param object
     * @return
     */
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null)
            return false;
        if (!(object instanceof SequenceElement))
            return false;

        return this.getLabel().equals(((SequenceElement) object).getLabel());
    }

    /**
     *  Returns index in Huffman tree
     *
     * @return index >= 0, if tree was built, -1 otherwise
     */
    public int getIndex() {
        return index;
    }

    /**
     * Sets index in Huffman tree
     *
     * @param index
     */
    public void setIndex(int index) {
        this.index = index;
    }

    /**
     * Returns Huffman tree codes
     * @return
     */
    public List<Byte> getCodes() {
        return codes;
    }

    /**
     * Sets Huffman tree codes
     * @param codes
     */
    public void setCodes(List<Byte> codes) {
        this.codes = codes;
    }

    /**
     * Returns Huffman tree points
     *
     * @return
     */
    public List<Integer> getPoints() {
        return points;
    }

    /**
     * Sets Huffman tree points
     *
     * @param points
     */
    public void setPoints(List<Integer> points) {
        this.points = points;
    }

    /**
     * Sets Huffman tree points
     *
     * @param points
     */
    @JsonIgnore
    public void setPoints(int[] points) {
        this.points = new ArrayList<>();
        for (int i = 0; i < points.length; i++) {
            this.points.add(points[i]);
        }
    }

    /**
     * Returns Huffman code length.
     *
     * Please note: maximum vocabulary/tree size depends on code length
     *
     * @return
     */
    public int getCodeLength() {
        return codeLength;
    }

    /**
     * This method fills codes and points up to codeLength
     *
     * @param codeLength
     */
    public void setCodeLength(short codeLength) {
        this.codeLength = codeLength;
        if (codes.size() < codeLength) {
            for (int i = 0; i < codeLength; i++)
                codes.add((byte) 0);
        }

        if (points.size() < codeLength) {
            for (int i = 0; i < codeLength; i++)
                points.add(0);
        }
    }


    public static final long getLongHash(@NonNull String string) {
        return HashUtil.getLongHash(string);
    }

    /**
     * Returns gradient for this specific element, at specific position
     * @param index
     * @param g
     * @param lr
     * @return
     */
    @Deprecated
    public double getGradient(int index, double g, double lr) {
        /*
        if (adaGrad == null)
            adaGrad = new AdaGrad(1,getCodeLength(), lr);
        
        return adaGrad.getGradient(g, index, new int[]{1, getCodeLength()});
        */
        return 0.0;
    }

    @Deprecated
    public void setHistoricalGradient(INDArray gradient) {
        /*
        if (adaGrad == null)
            adaGrad = new AdaGrad(1,getCodeLength(), 0.025);
        
        adaGrad.setHistoricalGradient(gradient);
        */
    }

    @Deprecated
    public INDArray getHistoricalGradient() {
        /*
        if (adaGrad == null)
            adaGrad = new AdaGrad(1,getCodeLength(), 0.025);
        return adaGrad.getHistoricalGradient();
        */
        return null;
    }

    /**
     * hashCode method override should be properly implemented for any extended class, otherwise it will be based on label hashCode
     *
     * @return hashCode for this SequenceElement
     */
    public int hashCode() {
        if (this.getLabel() == null)
            throw new IllegalStateException("Label should not be null");
        return this.getLabel().hashCode();
    }

    @Override
    public int compareTo(SequenceElement o) {
        return Double.compare(elementFrequency.get(), o.elementFrequency.get());
    }

    @Override
    public String toString() {
        return "SequenceElement: {label: '" + this.getLabel() + "'," + " freq: '" + elementFrequency.get() + "',"
                        + " codes: " + codes.toString() + " points: " + points.toString() + " index: '" + this.index
                        + "'}";
    }

    /**
     *
     * @return
     */
    public abstract String toJSON();

    public Long getStorageId() {
        if (storageId == null)
            storageId = SequenceElement.getLongHash(this.getLabel());
        return storageId;
    }

    public static ObjectMapper mapper() {
        /*
              DO NOT ENABLE INDENT_OUTPUT FEATURE
              we need THIS json to be single-line
          */
        ObjectMapper ret = new ObjectMapper();
        ret.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ret.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        ret.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        return ret;
    }
}

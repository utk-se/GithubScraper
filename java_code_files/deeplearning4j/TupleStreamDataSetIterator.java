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

package org.deeplearning4j.nn.dataimport.solr.client.solrj.io.stream;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.solr.client.solrj.io.SolrClientCache;
import org.apache.solr.client.solrj.io.Tuple;
import org.apache.solr.client.solrj.io.stream.CloudSolrStream;
import org.apache.solr.client.solrj.io.stream.TupStream;
import org.apache.solr.client.solrj.io.stream.StreamContext;
import org.apache.solr.client.solrj.io.stream.TupleStream;
import org.apache.solr.client.solrj.io.stream.expr.DefaultStreamFactory;
import org.apache.solr.client.solrj.io.stream.expr.StreamFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
  * A {@link DataSetIterator} which uses a <a href="https://lucene.apache.org/solr/guide/7_5/streaming-expressions.html">streaming expression</a>
 * to fetch data from Apache Solr and/or one of the sources (e.g. <code>jdbc</code>) supported as a <a href="https://lucene.apache.org/solr/guide/7_5/stream-source-reference.html">stream source</a>.
 * <p>
 * Example code snippet:
<pre>{
  try (final TupleStreamDataSetIterator tsdsi =
         new TupleStreamDataSetIterator(
         batch,
         "id",
         new String[] { "fieldA", "fieldB", "fieldC" },
         new String[] { "fieldX", "fieldY" },
         "search(mySolrCollection," +
         "q=\"id:*\"," +
         "fl=\"id,fieldA,fieldB,fieldC,fieldX,fieldY\"," +
         "sort=\"id asc\"," +
         "qt=\"/export\")",
         zkServerAddress)) {

    model.fit(tsdsi);
  }
</pre>
  */
public class TupleStreamDataSetIterator implements Closeable, DataSetIterator {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Getter
    protected DataSetPreProcessor preProcessor;

    final private int batch;
    final private String idKey;
    final private String[] featureKeys;
    final private String[] labelKeys;

    private StreamContext streamContext;
    private TupleStream tupleStream;
    private Tuple tuple;

    private static class CloseableStreamContext extends StreamContext implements Closeable {
        private SolrClientCache solrClientCache;
        public CloseableStreamContext() {
            solrClientCache = new SolrClientCache();
            setSolrClientCache(solrClientCache);
        }
        public void close() throws IOException {
            if (solrClientCache != null) {
                solrClientCache.close();
            }
        }
    }

    public TupleStreamDataSetIterator(
        int batch,
        String idKey,
        String[] featureKeys,
        String[] labelKeys,
        String expression,
        String defaultZkHost)
        throws IOException {

        this(batch, idKey, featureKeys, labelKeys,
             new DefaultStreamFactory().withDefaultZkHost(defaultZkHost),
             expression);
    }

    public TupleStreamDataSetIterator(
        int batch,
        String idKey,
        String[] featureKeys,
        String[] labelKeys,
        StreamFactory streamFactory,
        String expression)
        throws IOException {

        this(batch, idKey, featureKeys, labelKeys,
             streamFactory,
             expression,
             new CloseableStreamContext());
    }

    public TupleStreamDataSetIterator(
        int batch,
        String idKey,
        String[] featureKeys,
        String[] labelKeys,
        StreamFactory streamFactory,
        String expression,
        StreamContext streamContext)
        throws IOException {

        this.batch = batch;
        this.idKey = idKey;
        this.featureKeys = featureKeys;
        this.labelKeys = labelKeys;

        this.streamContext = streamContext;
        this.tupleStream = streamFactory.constructStream(expression);
        this.tupleStream.setStreamContext(streamContext);

        this.tupleStream.open();
        this.tuple = this.tupleStream.read();
    }

    public void close() throws IOException {
        this.tuple = null;
        if (this.tupleStream != null) {
            this.tupleStream.close();
            this.tupleStream = null;
        }
        if (this.streamContext != null &&
            this.streamContext instanceof CloseableStreamContext) {
            ((CloseableStreamContext)this.streamContext).close();
            this.streamContext = null;
        }
    }

    private List<DataSet> getRawDataSets(int numWanted) throws IOException {

        final List<DataSet> rawDataSets = new ArrayList<DataSet>();

        while (hasNext() && 0 < numWanted) {

            final INDArray features = getValues(this.tuple, this.featureKeys, this.idKey);
            final INDArray labels = getValues(this.tuple, this.labelKeys, this.idKey);
            final DataSet rawDataSet = new DataSet(features, labels);
            rawDataSets.add(rawDataSet);

            --numWanted;
            this.tuple = this.tupleStream.read();
        }

        return rawDataSets;
    }

    private DataSet convertDataSetsToDataSet(List<DataSet> rawDataSets) throws IOException {

        final int numFound = rawDataSets.size();

        final INDArray inputs = Nd4j.create(numFound, inputColumns());
        final INDArray labels = Nd4j.create(numFound, totalOutcomes());
        for (int ii = 0; ii < numFound; ++ii) {
            final DataSet dataSet = rawDataSets.get(ii);
            if (preProcessor != null) {
                preProcessor.preProcess(dataSet);
            }
            inputs.putRow(ii, dataSet.getFeatures());
            labels.putRow(ii, dataSet.getLabels());
        }

        return new DataSet(inputs, labels);
    }

    private static INDArray getValues(Tuple tuple, String[] keys, String idKey) {
      final double[] values = new double[keys.length];
      for (int ii=0; ii<keys.length; ++ii)
      {
        values[ii] = getValue(tuple, keys[ii], idKey);
      }
      return Nd4j.create(values);
    }

    private static double getValue(Tuple tuple, String key, String idKey) {
        final Double value = tuple.getDouble(key);
        if (value == null) {
          // log potentially useful debugging info here ...
          if (idKey == null) {
            log.info("tuple[{}]={}", key, value);
          } else {
            log.info("tuple[{}]={} tuple[{}]={}", key, value, idKey, tuple.get(idKey));
          }
          // ... before proceeding to hit the NullPointerException below
        }
        return value.doubleValue();
    }

    @Override
    public boolean hasNext() {
        return this.tuple != null && !this.tuple.EOF;
    }

    @Override
    public DataSet next() {
        return next(batch);
    }

    @Override
    public DataSet next(int num) {
        try {
            final List<DataSet> rawDataSets = getRawDataSets(num);
            return convertDataSetsToDataSet(rawDataSets);
        } catch (IOException ioe) {
            return null;
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int inputColumns() {
        return this.featureKeys.length;
    }

    @Override
    public int totalOutcomes() {
        return this.labelKeys.length;
    }

    @Override
    public boolean resetSupported() {
        return false;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int batch() {
        return this.batch;
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor preProcessor) {
        this.preProcessor = preProcessor;
    }

    @Override
    public List<String> getLabels() {
        throw new UnsupportedOperationException();
    }

}

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

package org.deeplearning4j.graph.data;

import org.apache.commons.lang3.ArrayUtils;
import org.deeplearning4j.graph.api.Edge;
import org.deeplearning4j.graph.api.IGraph;
import org.deeplearning4j.graph.data.impl.WeightedEdgeLineProcessor;
import org.deeplearning4j.graph.graph.Graph;
import org.deeplearning4j.graph.vertexfactory.StringVertexFactory;
import org.deeplearning4j.graph.vertexfactory.VertexFactory;
import org.junit.Test;
import org.nd4j.linalg.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class TestGraphLoadingWeighted {

    @Test(timeout = 10000L)
    public void testWeightedDirected() throws IOException {

        String path = new ClassPathResource("deeplearning4j-graph/WeightedGraph.txt").getTempFileFromArchive().getAbsolutePath();
        int numVertices = 9;
        String delim = ",";
        String[] ignoreLinesStartingWith = new String[] {"//"}; //Comment lines start with "//"

        IGraph<String, Double> graph =
                        GraphLoader.loadWeightedEdgeListFile(path, numVertices, delim, true, ignoreLinesStartingWith);

        assertEquals(numVertices, graph.numVertices());

        int[] vertexOutDegrees = {2, 2, 1, 2, 2, 1, 1, 1, 1};
        for (int i = 0; i < numVertices; i++)
            assertEquals(vertexOutDegrees[i], graph.getVertexDegree(i));
        int[][] edges = new int[][] {{1, 3}, //0->1 and 1->3
                        {2, 4}, //1->2 and 1->4
                        {5}, //etc
                        {4, 6}, {5, 7}, {8}, {7}, {8}, {0}};
        double[][] edgeWeights = new double[][] {{1, 3}, {12, 14}, {25}, {34, 36}, {45, 47}, {58}, {67}, {78}, {80}};

        for (int i = 0; i < numVertices; i++) {
            List<Edge<Double>> edgeList = graph.getEdgesOut(i);
            assertEquals(edges[i].length, edgeList.size());
            for (Edge<Double> e : edgeList) {
                int from = e.getFrom();
                int to = e.getTo();
                double weight = e.getValue();
                assertEquals(i, from);
                assertTrue(ArrayUtils.contains(edges[i], to));
                int idx = ArrayUtils.indexOf(edges[i], to);
                assertEquals(edgeWeights[i][idx], weight, 0.0);
            }
        }

        System.out.println(graph);
    }


    @Test(timeout = 10000L)
    public void testWeightedDirectedV2() throws Exception {

        String path = new ClassPathResource("deeplearning4j-graph/WeightedGraph.txt").getTempFileFromArchive().getAbsolutePath();
        int numVertices = 9;
        String delim = ",";
        boolean directed = true;
        String[] ignoreLinesStartingWith = new String[] {"//"}; //Comment lines start with "//"

        IGraph<String, Double> graph = GraphLoader.loadWeightedEdgeListFile(path, numVertices, delim, directed, false,
                        ignoreLinesStartingWith);

        assertEquals(numVertices, graph.numVertices());

        //EdgeLineProcessor: used to convert lines -> edges
        EdgeLineProcessor<Double> edgeLineProcessor =
                        new WeightedEdgeLineProcessor(delim, directed, ignoreLinesStartingWith);
        //Vertex factory: used to create vertex objects, given an index for the vertex
        VertexFactory<String> vertexFactory = new StringVertexFactory();

        Graph<String, Double> graph2 =
                        GraphLoader.loadGraph(path, edgeLineProcessor, vertexFactory, numVertices, false);

        assertEquals(graph, graph2);
    }

}

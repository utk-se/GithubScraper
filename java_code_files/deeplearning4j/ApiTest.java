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

package org.deeplearning4j.ui;

import org.apache.commons.io.IOUtils;
import org.deeplearning4j.plot.BarnesHutTsne;
import org.junit.Ignore;
import org.junit.Test;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.io.ClassPathResource;

import java.io.File;
import java.util.List;

/**
 * @author Adam Gibson
 */
public class ApiTest {
    @Test
    @Ignore
    public void testUpdateCoords() throws Exception {
        Nd4j.factory().setDType(DataType.DOUBLE);
        Nd4j.getRandom().setSeed(123);
        BarnesHutTsne b = new BarnesHutTsne.Builder().stopLyingIteration(250).theta(0.5).learningRate(500)
                        .useAdaGrad(false).numDimension(2).build();

        ClassPathResource resource = new ClassPathResource("/mnist2500_X.txt");
        File f = resource.getFile();
        INDArray data = Nd4j.readNumpy(f.getAbsolutePath(), "   ").get(NDArrayIndex.interval(0, 100),
                        NDArrayIndex.interval(0, 784));



        ClassPathResource labels = new ClassPathResource("mnist2500_labels.txt");
        List<String> labelsList = IOUtils.readLines(labels.getInputStream()).subList(0, 100);
        b.fit(data);
        b.saveAsFile(labelsList, "coords.csv");
        //        String coords =  client.target("http://localhost:8080").path("api").path("update")
        //                .request().accept(MediaType.APPLICATION_JSON)
        ////                .post(Entity.entity(new UrlResource("http://localhost:8080/api/coords.csv"), MediaType.APPLICATION_JSON))
        //                .readEntity(String.class);
        //        ObjectMapper mapper = new ObjectMapper();
        //        List<String> testLines = mapper.readValue(coords,List.class);
        //        List<String> lines = IOUtils.readLines(new FileInputStream("coords.csv"));
        //        assertEquals(testLines,lines);

        throw new RuntimeException("Not implemented");
    }

}

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

package org.nd4j.parameterserver.distributed.transport;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.nd4j.parameterserver.distributed.conf.VoidConfiguration;
import org.nd4j.parameterserver.distributed.enums.NodeRole;
import org.nd4j.parameterserver.distributed.logic.ClientRouter;
import org.nd4j.parameterserver.distributed.logic.completion.Clipboard;
import org.nd4j.parameterserver.distributed.messages.VoidMessage;
import org.nd4j.parameterserver.distributed.messages.requests.IntroductionRequestMessage;
import org.nd4j.parameterserver.distributed.logic.routing.InterleavedRouter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author raver119@gmail.com
 */
@Ignore
@Deprecated
public class RoutedTransportTest {
    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }


    /**
     * This test
     *
     * @throws Exception
     */
    @Test(timeout = 30000)
    public void testMessaging1() throws Exception {

        List<String> list = new ArrayList<>();
        for (int t = 0; t < 5; t++) {
            list.add("127.0.0.1:3838" + t);
        }

        VoidConfiguration voidConfiguration = VoidConfiguration.builder().shardAddresses(list).build();
        voidConfiguration.setUnicastControllerPort(43120); // this port will be used only by client

        // first of all we start shards
        RoutedTransport[] transports = new RoutedTransport[list.size()];
        for (int t = 0; t < transports.length; t++) {

            Clipboard clipboard = new Clipboard();

            transports[t] = new RoutedTransport();
            transports[t].setIpAndPort("127.0.0.1", Integer.valueOf("3838" + t));
            transports[t].init(voidConfiguration, clipboard, NodeRole.SHARD, "127.0.0.1",
                            voidConfiguration.getUnicastControllerPort(), (short) t);
        }

        for (int t = 0; t < transports.length; t++) {
            transports[t].launch(Transport.ThreadingModel.DEDICATED_THREADS);
        }

        // now we start client, for this test we'll have only one client
        Clipboard clipboard = new Clipboard();
        RoutedTransport clientTransport = new RoutedTransport();
        clientTransport.setIpAndPort("127.0.0.1", voidConfiguration.getUnicastControllerPort());

        // setRouter call should be called before init, and we need
        ClientRouter router = new InterleavedRouter(0);
        clientTransport.setRouter(router);
        router.init(voidConfiguration, clientTransport);

        clientTransport.init(voidConfiguration, clipboard, NodeRole.CLIENT, "127.0.0.1",
                        voidConfiguration.getUnicastControllerPort(), (short) -1);
        clientTransport.launch(Transport.ThreadingModel.DEDICATED_THREADS);

        // we send message somewhere
        VoidMessage message = new IntroductionRequestMessage("127.0.0.1", voidConfiguration.getUnicastControllerPort());
        clientTransport.sendMessage(message);

        Thread.sleep(500);
        message = transports[0].messages.poll(1, TimeUnit.SECONDS);
        assertNotEquals(null, message);

        for (int t = 1; t < transports.length; t++) {
            message = transports[t].messages.poll(1, TimeUnit.SECONDS);
            assertEquals(null, message);
        }


        /**
         * This is very important part, shutting down all transports
         */
        for (RoutedTransport transport : transports) {
            transport.shutdown();
        }

        clientTransport.shutdown();
    }
}

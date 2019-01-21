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

package org.nd4j.parameterserver.distributed.v2.transport;

import io.reactivex.functions.Consumer;
import org.nd4j.parameterserver.distributed.v2.enums.PropagationMode;
import org.nd4j.parameterserver.distributed.v2.messages.ResponseMessage;
import org.nd4j.parameterserver.distributed.v2.messages.RequestMessage;
import org.nd4j.parameterserver.distributed.v2.messages.VoidMessage;
import org.nd4j.parameterserver.distributed.v2.messages.INDArrayMessage;
import org.nd4j.parameterserver.distributed.v2.util.MeshOrganizer;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * This interface describes Transport abstraction, used to communicate between cluster nodes
 *
 * @author raver119@gmail.com
 */
public interface Transport {

    /**
     * This method returns id of the current transport
     * @return
     */
    String id();

    /**
     * This methos returns Id of the upstream node
     * @return
     */
    String getUpstreamId();

    /**
     * This method returns random
     *
     * @param id
     * @param exclude
     * @return
     */
    String getRandomDownstreamFrom(String id, String exclude);

    /**
     * This method returns consumer that accepts messages for delivery
     * @return
     */
    Consumer<VoidMessage> outgoingConsumer();

    /**
     * This method returns flow of messages for parameter server
     * @return
     */
    Publisher<INDArrayMessage> incomingPublisher();

    /**
     * This method starts  this Transport instance
     */
    void launch();

    /**
     * This method will start this Transport instance
     */
    void launchAsMaster();

    /**
     * This method shuts down this Transport instance
     */
    void shutdown();

    /**
     * This method will send message to the network, using tree structure
     * @param message
     */
    void propagateMessage(VoidMessage message, PropagationMode mode) throws IOException;

    /**
     * This method will send message to the node specified by Id
     *
     * @param message
     * @param id
     */
    void sendMessage(VoidMessage message, String id);

    /**
     * This method will send message to specified node, and will return its response
     *
     * @param message
     * @param id
     * @param <T>
     * @return
     */
    <T extends ResponseMessage> T sendMessageBlocking(RequestMessage message, String id) throws InterruptedException;

    /**
     * This method will send message to specified node, and will return its response
     *
     * @param message
     * @param id
     * @param <T>
     * @return
     */
    <T extends ResponseMessage> T sendMessageBlocking(RequestMessage message, String id, long waitTime, TimeUnit timeUnit) throws InterruptedException;

    /**
     * This method will be invoked for all incoming messages
     * PLEASE NOTE: this method is mostly suited for tests
     *
     * @param message
     */
    void processMessage(VoidMessage message);


    /**
     * This method allows to set callback instance, which will be called upon restart event
     * @param callback
     */
    void setRestartCallback(RestartCallback callback);

    /**
     * This methd allows to set callback instance for various
     * @param cls
     * @param callback
     * @param <T1> RequestMessage class
     * @param <T2> ResponseMessage class
     */
    <T extends RequestMessage> void  addRequestConsumer(Class<T> cls, Consumer<T> consumer);

    /**
     * This method will be called if mesh update was received
     *
     * PLEASE NOTE: This method will be called ONLY if new mesh differs from current one
     * @param mesh
     */
    void onMeshUpdate(MeshOrganizer mesh);

    /**
     * This method will be called upon remap request
     * @param id
     */
    void onRemap(String id);

    /**
     * This method returns total number of nodes known to this Transport
     * @return
     */
    int totalNumberOfNodes();


    /**
     * This method returns ID of the root node
     * @return
     */
    String getRootId();

    /**
     *  This method checks if all connections required for work are established
     * @return true
     */
    boolean isConnected();

    /**
     * This method checks if this node was properly introduced to driver
     * @return
     */
    boolean isIntroduced();

    /**
     * This method checks connection to the given node ID, and if it's not connected - establishes connection
     * @param id
     */
    void ensureConnection(String id);
}

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

package org.nd4j.parameterserver.distributed.v2.transport.impl;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.parameterserver.distributed.conf.VoidConfiguration;
import org.nd4j.parameterserver.distributed.v2.messages.RequestMessage;
import org.nd4j.parameterserver.distributed.v2.messages.VoidMessage;
import org.nd4j.parameterserver.distributed.v2.messages.impl.GradientsUpdateMessage;
import org.nd4j.parameterserver.distributed.v2.transport.MessageCallable;
import org.nd4j.parameterserver.distributed.v2.transport.Transport;
import org.nd4j.parameterserver.distributed.v2.util.MeshOrganizer;
import org.nd4j.parameterserver.distributed.v2.util.MessageSplitter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * This class is an in-memory implementation of Transport interface, written for tests
 *
 * @author raver119@gmail.com
 */
@Slf4j
public class DummyTransport extends BaseTransport {
    // this is for tests only
    protected Map<String, MessageCallable> interceptors = new HashMap<>();
    protected Map<String, MessageCallable> precursors = new HashMap<>();

    protected final Connector connector;


    public DummyTransport(String id, Connector connector) {
        super();
        this.id = id;
        this.connector = connector;

        this.splitter = new MessageSplitter();
    }

    public DummyTransport(String id, Connector connector, @NonNull String rootId) {
        super(rootId);
        this.id = id;
        this.connector = connector;

        this.splitter = new MessageSplitter();
    }

    public DummyTransport(String id, Connector connector, @NonNull String rootId, @NonNull VoidConfiguration configuration) {
        super(rootId, configuration);
        this.id = id;
        this.connector = connector;

        this.splitter = new MessageSplitter();
    }

    @Override
    public void launch() {
        super.launch();
    }

    @Override
    public void sendMessage(@NonNull VoidMessage message, @NonNull String id) {
        if (message.getOriginatorId() == null)
            message.setOriginatorId(this.id());

        //if (message.getMessageId() == null)


        // TODO: get rid of UUID!!!11
        if (message instanceof RequestMessage) {
            if (((RequestMessage) message).getRequestId() == null)
                ((RequestMessage) message).setRequestId(java.util.UUID.randomUUID().toString());
        }

        connector.transferMessage(message, this.id(), id);
    }

    @Override
    public String id() {
        return id;
    }

    /**
     * This method add interceptor for incoming messages. If interceptor is defined for given message class - runnable will be executed instead of processMessage()
     * @param cls
     * @param callable
     */
    public <T extends VoidMessage> void addInterceptor(@NonNull Class<T> cls, @NonNull MessageCallable<T> callable) {
        interceptors.put(cls.getCanonicalName(), callable);
    }

    /**
     * This method add precursor for incoming messages. If precursor is defined for given message class - runnable will be executed before processMessage()
     * @param cls
     * @param callable
     */
    public <T extends VoidMessage> void addPrecursor(@NonNull Class<T> cls, @NonNull MessageCallable<T> callable) {
        precursors.put(cls.getCanonicalName(), callable);
    }

    @Override
    public void processMessage(@NonNull VoidMessage message) {
        val name = message.getClass().getCanonicalName();
        val callable = interceptors.get(name);

        if (callable != null)
            callable.apply(message);
        else {
            val precursor = precursors.get(name);
            if (precursor != null)
                precursor.apply(message);

            super.internalProcessMessage(message);
        }
    }

    @Override
    protected void internalProcessMessage(VoidMessage message) {
        processMessage(message);
    }

    /**
     * This class is written to mimic network connectivity locally
     */
    public static class Connector {
        private Map<String, Transport> transports = new ConcurrentHashMap<>();
        private ThreadPoolExecutor executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                val t = Executors.defaultThreadFactory().newThread(r);
                //t.setDaemon(true);
                return t;
            }
        });

        public void register(Transport... transports) {
            for (val transport:transports)
                this.transports.putIfAbsent(transport.id(), transport);
        }

        public void blockUntilFinished() throws InterruptedException {
            val timeStart = System.currentTimeMillis();
            while (executorService.getActiveCount() > 0 && executorService.getQueue().size() > 0) {
                Thread.sleep(500);
            }
            val timeStop = System.currentTimeMillis();

            if (timeStop - timeStart < 700)
                Thread.sleep(700);
        }

        public void transferMessage(@NonNull VoidMessage message, @NonNull String senderId, @NonNull String targetId) {

            //if (message instanceof GradientsUpdateMessage)
            //    log.info("Trying to send message [{}] from [{}] to [{}]", message.getClass().getSimpleName(), senderId, targetId);

            val target = transports.get(targetId);
            if (target == null)
                throw new ND4JIllegalStateException("Unknown target specified");

            target.processMessage(message);
        }

        public ExecutorService executorService() {
            return executorService;
        }

        public void dropConnection(@NonNull String... ids) {
            Arrays.stream(ids).filter(Objects::nonNull).forEach(transports::remove);
        }
    }

    /**
     * This method returns Mesh stored in this Transport instance
     * PLEASE NOTE: This method is suited for tests
     * @return
     */
    public MeshOrganizer getMesh() {
        synchronized (mesh) {
            return mesh.get();
        }
    }

    public void setMesh(MeshOrganizer mesh) {
        synchronized (this.mesh) {
            this.mesh.set(mesh);
        }
    }

    @Override
    public boolean isConnected() {
        return true;
    }
}

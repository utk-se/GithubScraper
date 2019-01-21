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

package org.nd4j.aeron.ipc.response;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import lombok.Builder;
import lombok.Data;
import org.agrona.CloseHelper;
import org.agrona.concurrent.SigInt;
import org.nd4j.aeron.ipc.AeronConnectionInformation;
import org.nd4j.aeron.ipc.AeronUtil;
import org.nd4j.aeron.ipc.NDArrayHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An aeron subscriber which, given
 * aeron connection information and an
 * @link {NDArrayHolder} will retrieve
 * the ndarray from the holder and send
 * an ndarray over aeron to the given
 * aeron channel.
 *
 * @author Adam Gibson
 */
@Builder
@Data
public class AeronNDArrayResponder implements AutoCloseable {
    // The channel (an endpoint identifier) to receive messages from
    private String channel;
    // A unique identifier for a stream within a channel. Stream ID 0 is reserved
    // for internal use and should not be used by applications.
    private int streamId = -1;
    // A unique identifier for a stream within a channel. Stream ID 0 is reserved
    // for internal use and should not be used by applications.
    private int responseStreamId = -1;

    // Maximum number of message fragments to receive during a single 'poll' operation
    private int fragmentLimitCount;
    // Create a context, needed for client connection to media driver
    // A separate media driver process need to run prior to running this application
    private Aeron.Context ctx;
    private AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean init = new AtomicBoolean(false);
    private static Logger log = LoggerFactory.getLogger(AeronNDArrayResponder.class);
    private NDArrayHolder ndArrayHolder;
    private Aeron aeron;
    private AtomicBoolean launched;


    private void init() {
        ctx = ctx == null ? new Aeron.Context() : ctx;
        channel = channel == null ? "aeron:udp?endpoint=localhost:40123" : channel;
        fragmentLimitCount = fragmentLimitCount == 0 ? 5000 : fragmentLimitCount;
        streamId = streamId == 0 ? 10 : streamId;
        responseStreamId = responseStreamId == 0 ? -1 : responseStreamId;
        running = running == null ? new AtomicBoolean(true) : running;
        if (ndArrayHolder == null)
            throw new IllegalStateException("NDArray callback must be specified in the builder.");
        init.set(true);
        launched = new AtomicBoolean(false);
        log.info("Channel subscriber " + channel + " and stream id " + streamId);
    }


    /**
     * Launch a background thread
     * that subscribes to  the aeron context
     * @throws Exception
     */
    public void launch() throws Exception {
        if (init.get())
            return;
        // Register a SIGINT handler for graceful shutdown.
        if (!init.get())
            init();

        log.info("Subscribing to " + channel + " on stream Id " + streamId);

        // Register a SIGINT handler for graceful shutdown.
        SigInt.register(() -> running.set(false));

        // Create an Aeron instance with client-provided context configuration, connect to the
        // media driver, and add a subscription for the given channel and stream using the supplied
        // dataHandler method, which will be called with new messages as they are received.
        // The Aeron and Subscription classes implement AutoCloseable, and will automatically
        // clean up resources when this try block is finished.
        //Note here that we are either creating 1 or 2 subscriptions.
        //The first one is a  normal 1 subscription listener.
        //The second one is when we want to send responses
        boolean started = false;
        int numTries = 0;
        while (!started && numTries < 3) {
            try {
                try (final Subscription subscription = aeron.addSubscription(channel, streamId)) {
                    log.info("Beginning subscribe on channel " + channel + " and stream " + streamId);
                    AeronUtil.subscriberLoop(new FragmentAssembler(NDArrayResponseFragmentHandler.builder().aeron(aeron)
                                    .context(ctx).streamId(responseStreamId).holder(ndArrayHolder).build()),
                                    fragmentLimitCount, running, launched).accept(subscription);
                    started = true;
                }


            } catch (Exception e) {
                numTries++;
                log.warn("Failed to connect..trying again", e);
            }
        }

        if (numTries >= 3)
            throw new IllegalStateException("Was unable to start responder after " + numTries + "tries");


    }


    /**
     * Returns the connection uri in the form of:
     * host:port:streamId
     * @return
     */
    public String connectionUrl() {
        String[] split = channel.replace("aeron:udp?endpoint=", "").split(":");
        String host = split[0];
        int port = Integer.parseInt(split[1]);
        return AeronConnectionInformation.of(host, port, streamId).toString();
    }

    /**
     * Start a subscriber in another thread
     * based on the given parameters
     * @param aeron the aeron instance to use
     * @param host the host opName to bind to
     * @param port the port to bind to
     * @param callback the call back to use for the subscriber
     * @param streamId the stream id to subscribe to
     * @return the subscriber reference
     */
    public static AeronNDArrayResponder startSubscriber(Aeron aeron, String host, int port, NDArrayHolder callback,
                    int streamId) {

        if (callback == null)
            throw new IllegalArgumentException("NDArrayHolder must be specified");

        final AtomicBoolean running = new AtomicBoolean(true);


        AeronNDArrayResponder subscriber = AeronNDArrayResponder.builder().streamId(streamId).aeron(aeron)
                        .channel(AeronUtil.aeronChannel(host, port)).running(running).ndArrayHolder(callback).build();


        Thread t = new Thread(() -> {
            try {
                subscriber.launch();
            } catch (Exception e) {
                e.printStackTrace();
            }

        });

        t.start();

        return subscriber;
    }

    /**
     * Start a subscriber in another thread
     * based on the given parameters
     * @param context the context to use
     * @param host the host opName to bind to
     * @param port the port to bind to
     * @param callback the call back to use for the subscriber
     * @param streamId the stream id to subscribe to
     * @return the subscriber reference
     */
    public static AeronNDArrayResponder startSubscriber(Aeron.Context context, String host, int port,
                    NDArrayHolder callback, int streamId) {
        if (callback == null)
            throw new IllegalArgumentException("NDArrayHolder must be specified");


        final AtomicBoolean running = new AtomicBoolean(true);


        AeronNDArrayResponder subscriber = AeronNDArrayResponder.builder().streamId(streamId).ctx(context)
                        .channel(String.format("aeron:udp?endpoint=%s:%d", host, port)).running(running)
                        .ndArrayHolder(callback).build();


        Thread t = new Thread(() -> {
            try {
                subscriber.launch();
            } catch (Exception e) {
                e.printStackTrace();
            }

        });

        t.start();

        return subscriber;
    }

    /**
     * Closes this resource, relinquishing any underlying resources.
     * This method is invoked automatically on objects managed by the
     * {@code try}-with-resources statement.
     * <p>
     * <p>While this interface method is declared to throw {@code
     * Exception}, implementers are <em>strongly</em> encouraged to
     * declare concrete implementations of the {@code close} method to
     * throw more specific exceptions, or to throw no exception at all
     * if the close operation cannot fail.
     * <p>
     * <p> Cases where the close operation may fail require careful
     * attention by implementers. It is strongly advised to relinquish
     * the underlying resources and to internally <em>mark</em> the
     * resource as closed, prior to throwing the exception. The {@code
     * close} method is unlikely to be invoked more than once and so
     * this ensures that the resources are released in a timely manner.
     * Furthermore it reduces problems that could arise when the resource
     * wraps, or is wrapped, by another resource.
     * <p>
     * <p><em>Implementers of this interface are also strongly advised
     * to not have the {@code close} method throw {@link
     * InterruptedException}.</em>
     * <p>
     * This exception interacts with a thread's interrupted status,
     * and runtime misbehavior is likely to occur if an {@code
     * InterruptedException} is {@linkplain Throwable#addSuppressed
     * suppressed}.
     * <p>
     * More generally, if it would cause problems for an
     * exception to be suppressed, the {@code AutoCloseable.close}
     * method should not throw it.
     * <p>
     * <p>Note that unlike the {@link Closeable#close close}
     * method of {@link Closeable}, this {@code close} method
     * is <em>not</em> required to be idempotent.  In other words,
     * calling this {@code close} method more than once may have some
     * visible side effect, unlike {@code Closeable.close} which is
     * required to have no effect if called more than once.
     * <p>
     * However, implementers of this interface are strongly encouraged
     * to make their {@code close} methods idempotent.
     *
     * @throws Exception if this resource cannot be closed
     */
    @Override
    public void close() throws Exception {
        CloseHelper.close(aeron);
    }
}

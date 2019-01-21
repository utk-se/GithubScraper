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

package org.nd4j.parameterserver.distributed.conf;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.parameterserver.distributed.enums.ExecutionMode;
import org.nd4j.parameterserver.distributed.enums.FaultToleranceStrategy;
import org.nd4j.parameterserver.distributed.enums.NodeRole;
import org.nd4j.parameterserver.distributed.enums.TransportType;
import org.nd4j.parameterserver.distributed.v2.enums.MeshBuildMode;
import org.nd4j.parameterserver.distributed.v2.transport.PortSupplier;
import org.nd4j.parameterserver.distributed.v2.transport.impl.StaticPortSupplier;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Basic configuration pojo for VoidParameterServer.
 * This is used for example in Deeplearning4j's SharedTrainingMaster gradient sharing distributed training implementation
 *
 * @author raver119@gmail.com
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
@Data
public class VoidConfiguration implements Serializable {
    public static final int DEFAULT_AERON_UDP_PORT = 49876;

    /**
     * StreamId is used for Aeron configuration
     */
    @Builder.Default
    private int streamId = -1;

    /**
     * This variable defines UDP port that will be used for communication with cluster driver.<br>
     * NOTE: Use {@link #setPortSupplier(PortSupplier)} to set the port to use - the value of the unicastControllerPort
     * field will automatically be updated to the value set by the PortSupplier provided on the master/controller machine,
     * before communicating to the worker machines. Setting this field directly will NOT control the port that is
     * used.
     *
     */
    @Builder.Default
    private int unicastControllerPort = 49876;

    /**
     * This method specifies UDP port for multicast/broadcast transport
     */
    @Builder.Default
    private int multicastPort = 59876;

    /**
     * This method defines number of shards. Reserved for future use.
     */
    @Builder.Default
    private int numberOfShards = 1;

    /**
     * Reserved for future use.
     */
    @Builder.Default
    private FaultToleranceStrategy faultToleranceStrategy = FaultToleranceStrategy.NONE;

    /**
     * Reserved for future use.
     */
    @Builder.Default
    private ExecutionMode executionMode = ExecutionMode.SHARDED;

    /**
     * Reserved for future use.
     */
    @Builder.Default
    private List<String> shardAddresses = new ArrayList<>();

    /**
     * Reserved for future use.
     */
    @Builder.Default
    private List<String> backupAddresses = new ArrayList<>();

    /**
     * This variable defines network transport to be used for comms
     */
    @Builder.Default
    private TransportType transportType = TransportType.ROUTED_UDP;

    /**
     * This variable defines how cluster nodes are organized
     */
    @Builder.Default
    private MeshBuildMode meshBuildMode = MeshBuildMode.PLAIN;

    /**
     * This variable acts as hint for ParameterServer about IP address to be used for comms.
     * Used only if SPARK_PUBLIC_DNS is undefined (i.e. as in YARN environment)
     */
    private String networkMask;

    /**
     * This value is optional, and has effect only for UDP MulticastTransport
     */
    @Builder.Default
    private String multicastNetwork = "224.0.1.1";

    /**
     * This value is optional, and has effect only for UDP MulticastTransport
     */
    private String multicastInterface;

    /**
     * This value is optional, and has effect only for UDP MulticastTransport
     */
    @Builder.Default
    private int ttl = 4;

    /**
     * This option is for debugging mostly. Do not use it, unless you have to.
     */
    protected NodeRole forcedRole;

    // FIXME: probably worth moving somewhere else
    /**
     * This value is optional, and has effect only for UDP MulticastTransport
     */
    @Deprecated
    private boolean useHS = true;
    /**
     * This value is optional, and has effect only for UDP MulticastTransport
     */
    @Deprecated
    private boolean useNS = false;

    /**
     * This variable defines, how long transport should wait before resending message in case of network issues.
     * Measured in milliseconds.
     */
    @Builder.Default
    private long retransmitTimeout = 1000;

    /**
     * This variable defines, how long transport should wait for response on specific messages.
     */
    @Builder.Default
    private long responseTimeframe = 500;

    /**
     * This variable defines, how long transport should wait for answer on specific messages.
     */
    @Builder.Default
    private long responseTimeout = 30000;

    /**
     * This variable defines amount of memory used of
     * Default value: 1GB
     */
    @Builder.Default
    private long chunksBufferSize = 1073741824;

    /**
     * This variable defines max chunk size for INDArray splits
     */
    @Builder.Default
    private int maxChunkSize = 65536;

    /**
     * This variable defines max number of allowed reconnects per node
     */
    @Builder.Default
    private int maxFailuresPerNode = 3;

    /**
     * This optional variable defines IP address of the box which acts as master for gradients training.
     * Leave it null, and Spark Master node will be used as Master for parameter server as well.
     */
    private String controllerAddress;

    /**
     * The port supplier used to provide the networking port to use for communication to/from the driver (UDP via Aeron).
     * This setting can be used to control how ports get assigned (including port setting different ports on different machines).<br>
     * See {@link PortSupplier} for further details.<br>
     * Default: static port (i.e., {@link StaticPortSupplier}) with value {@link #DEFAULT_AERON_UDP_PORT}
     */
    @Builder.Default private PortSupplier portSupplier = new StaticPortSupplier(49876);

    public void setStreamId(int streamId) {
        if (streamId < 1)
            throw new ND4JIllegalStateException("You can't use streamId 0, please specify other one");

        this.streamId = streamId;
    }

    protected void validateNetmask() {
        if (networkMask == null)
            return;

        // micro-validaiton here
        String[] chunks = networkMask.split("\\.");
        if (chunks.length == 1 || networkMask.isEmpty())
            throw new ND4JIllegalStateException(
                            "Provided netmask doesn't look like a legit one. Proper format is: 192.168.1.0/24 or 10.0.0.0/8");


        // TODO: add support for IPv6 eventually here
        if (chunks.length != 4) {
            throw new ND4JIllegalStateException("4 octets expected here for network mask");
        }

        for (int i = 0; i < 3; i++) {
            String curr = chunks[i];
            try {
                int conv = Integer.valueOf(curr);
                if (conv < 0 || conv > 255)
                    throw new ND4JIllegalStateException();
            } catch (Exception e) {
                throw new ND4JIllegalStateException("All IP address octets should be in range of 0...255");
            }
        }

        if (Integer.valueOf(chunks[0]) == 0)
            throw new ND4JIllegalStateException("First network mask octet should be non-zero. I.e. 10.0.0.0/8");

        // we enforce last octet to be 0/24 always
        if (!networkMask.contains("/") || !chunks[3].startsWith("0")) {
            chunks[3] = "0/24";
        }

        this.networkMask = chunks[0] + "." + chunks[1] + "." + chunks[2] + "." + chunks[3];
    }

    /**
     * This option is very important: in shared network environment and yarn (like on EC2 etc),
     * please set this to the network, which will be available on all boxes. I.e. 10.1.1.0/24 or 192.168.0.0/16
     *
     * @param netmask netmask to be used for IP address selection
     */
    public void setNetworkMask(@NonNull String netmask) {
        this.networkMask = netmask;
        validateNetmask();
    }

    /**
     * This method returns network mask
     *
     * @return
     */
    public String getNetworkMask() {
        validateNetmask();
        return this.networkMask;
    }

    public void setShardAddresses(List<String> addresses) {
        this.shardAddresses = addresses;
    }

    public void setShardAddresses(String... ips) {
        if (shardAddresses == null)
            shardAddresses = new ArrayList<>();

        for (String ip : ips) {
            if (ip != null)
                shardAddresses.add(ip);
        }
    }

    public void setBackupAddresses(List<String> addresses) {
        this.backupAddresses = addresses;
    }

    public void setBackupAddresses(String... ips) {
        if (backupAddresses == null)
            backupAddresses = new ArrayList<>();

        for (String ip : ips) {
            if (ip != null)
                backupAddresses.add(ip);
        }
    }

    public void setExecutionMode(@NonNull ExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    //Partial implementation to hideh/exclude unicastControllerPort from builder - users should use portSupplier method instead
    // in the builder - otherwise, users might think they can set it via this method (instead, it gets overriden by whatever
    // is provided by the port supplier)
    //Also hide from builder some unsupported methods
    public static class VoidConfigurationBuilder {

        /**
         * Equivalent to calling {@link #portSupplier(PortSupplier)} with a {@link StaticPortSupplier}
         * @param unicastPort Port to use for
         * @see #portSupplier(PortSupplier)
         */
        public VoidConfigurationBuilder unicastPort(int unicastPort){
            return portSupplier(new StaticPortSupplier(unicastPort));
        }

        private VoidConfigurationBuilder unicastControllerPort(int unicastControllerPort){
            throw new UnsupportedOperationException("Not supported. Use portSupplier method instead");
        }

        private VoidConfigurationBuilder faultToleranceStrategy(FaultToleranceStrategy faultToleranceStrategy){
            throw new UnsupportedOperationException("Reserved for future use");
        }

        public VoidConfigurationBuilder backupAddresses(List<String> backupAddresses){
            throw new UnsupportedOperationException("Reserved for future use");
        }

    }
}

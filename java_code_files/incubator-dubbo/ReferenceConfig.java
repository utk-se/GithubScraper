/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.config;

import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.ClassHelper;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.metadata.integration.MetadataReportService;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.directory.StaticDirectory;
import org.apache.dubbo.rpc.cluster.support.ClusterUtils;
import org.apache.dubbo.rpc.cluster.support.RegistryAwareCluster;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ConsumerModel;
import org.apache.dubbo.rpc.protocol.injvm.InjvmProtocol;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.ProtocolUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.apache.dubbo.common.utils.NetUtils.isInvalidLocalHost;

/**
 * ReferenceConfig
 *
 * @export
 */
public class ReferenceConfig<T> extends AbstractReferenceConfig {

    private static final long serialVersionUID = -5864351140409987595L;

    /**
     * The {@link Protocol} implementation with adaptive functionality,it will be different in different scenarios.
     * A particular {@link Protocol} implementation is determined by the protocol attribute in the {@link URL}.
     * For example:
     *
     * <li>when the url is registry://224.5.6.7:1234/org.apache.dubbo.registry.RegistryService?application=dubbo-sample,
     * then the protocol is <b>RegistryProtocol</b></li>
     *
     * <li>when the url is dubbo://224.5.6.7:1234/org.apache.dubbo.config.api.DemoService?application=dubbo-sample, then
     * the protocol is <b>DubboProtocol</b></li>
     * <p>
     * Actually，when the {@link ExtensionLoader} init the {@link Protocol} instants,it will automatically wraps two
     * layers, and eventually will get a <b>ProtocolFilterWrapper</b> or <b>ProtocolListenerWrapper</b>
     */
    private static final Protocol refprotocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

    /**
     * The {@link Cluster}'s implementation with adaptive functionality, and actually it will get a {@link Cluster}'s
     * specific implementation who is wrapped with <b>MockClusterInvoker</b>
     */
    private static final Cluster cluster = ExtensionLoader.getExtensionLoader(Cluster.class).getAdaptiveExtension();

    /**
     * A {@link ProxyFactory} implementation that will generate a reference service's proxy,the JavassistProxyFactory is
     * its default implementation
     */
    private static final ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

    /**
     * The url of the reference service
     */
    private final List<URL> urls = new ArrayList<URL>();

    /**
     * The interface name of the reference service
     */
    private String interfaceName;

    /**
     * The interface class of the reference service
     */
    private Class<?> interfaceClass;
    
    /**
     * client type
     */
    private String client;

    /**
     * The url for peer-to-peer invocation
     */
    private String url;

    /**
     * The method configs
     */
    private List<MethodConfig> methods;

    /**
     * The consumer config (default)
     */
    private ConsumerConfig consumer;

    /**
     * Only the service provider of the specified protocol is invoked, and other protocols are ignored.
     */
    private String protocol;

    /**
     * The interface proxy reference
     */
    private transient volatile T ref;

    /**
     * The invoker of the reference service
     */
    private transient volatile Invoker<?> invoker;

    /**
     * The flag whether the ReferenceConfig has been initialized
     */
    private transient volatile boolean initialized;

    /**
     * whether this ReferenceConfig has been destroyed
     */
    private transient volatile boolean destroyed;

    @SuppressWarnings("unused")
    private final Object finalizerGuardian = new Object() {
        @Override
        protected void finalize() throws Throwable {
            super.finalize();

            if (!ReferenceConfig.this.destroyed) {
                logger.warn("ReferenceConfig(" + url + ") is not DESTROYED when FINALIZE");

                /* don't destroy for now
                try {
                    ReferenceConfig.this.destroy();
                } catch (Throwable t) {
                        logger.warn("Unexpected err when destroy invoker of ReferenceConfig(" + url + ") in finalize method!", t);
                }
                */
            }
        }
    };

    public ReferenceConfig() {
    }

    public ReferenceConfig(Reference reference) {
        appendAnnotation(Reference.class, reference);
    }

    public URL toUrl() {
        return urls.isEmpty() ? null : urls.iterator().next();
    }

    public List<URL> toUrls() {
        return urls;
    }

    /**
     * This method should be called right after the creation of this class's instance, before any property in other config modules is used.
     * Check each config modules are created properly and override their properties if necessary.
     */
    public void checkAndUpdateSubConfigs() {
        if (StringUtils.isEmpty(interfaceName)) {
            throw new IllegalStateException("<dubbo:reference interface=\"\" /> interface not allow null!");
        }
        // get consumer's global configuration
        checkDefault();
        this.refresh();
        if (getGeneric() == null && getConsumer() != null) {
            setGeneric(getConsumer().getGeneric());
        }
        if (ProtocolUtils.isGeneric(getGeneric())) {
            interfaceClass = GenericService.class;
        } else {
            try {
                interfaceClass = Class.forName(interfaceName, true, Thread.currentThread()
                        .getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            checkInterfaceAndMethods(interfaceClass, methods);
        }
        resolveFile();
        if (consumer != null) {
            inheritIfAbsentFromConsumer();
        }
        if (module != null) {
            inheritIfAbsentFromModule();
        }
        if (application != null) {
            inheritIfAbsentFromApplication();
        }
        checkApplication();
        checkMetadataReport();
        checkRegistryDataConfig();
    }

    public synchronized T get() {
        checkAndUpdateSubConfigs();

        if (destroyed) {
            throw new IllegalStateException("The invoker of ReferenceConfig(" + url + ") has already destroyed!");
        }
        if (ref == null) {
            init();
        }
        return ref;
    }

    public synchronized void destroy() {
        if (ref == null) {
            return;
        }
        if (destroyed) {
            return;
        }
        destroyed = true;
        try {
            invoker.destroy();
        } catch (Throwable t) {
            logger.warn("Unexpected error occured when destroy invoker of ReferenceConfig(" + url + ").", t);
        }
        invoker = null;
        ref = null;
    }

    private void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        checkStubAndLocal(interfaceClass);
        checkMock(interfaceClass);
        Map<String, String> map = new HashMap<String, String>();

        map.put(Constants.SIDE_KEY, Constants.CONSUMER_SIDE);
        appendRuntimeParameters(map);
        if (!isGeneric()) {
            String revision = Version.getVersion(interfaceClass, version);
            if (revision != null && revision.length() > 0) {
                map.put("revision", revision);
            }

            String[] methods = Wrapper.getWrapper(interfaceClass).getMethodNames();
            if (methods.length == 0) {
                logger.warn("No method found in service interface " + interfaceClass.getName());
                map.put("methods", Constants.ANY_VALUE);
            } else {
                map.put("methods", StringUtils.join(new HashSet<String>(Arrays.asList(methods)), ","));
            }
        }
        map.put(Constants.INTERFACE_KEY, interfaceName);
        appendParameters(map, application);
        appendParameters(map, module);
        appendParameters(map, consumer, Constants.DEFAULT_KEY);
        appendParameters(map, this);
        Map<String, Object> attributes = null;
        if (CollectionUtils.isNotEmpty(methods)) {
            attributes = new HashMap<String, Object>();
            for (MethodConfig methodConfig : methods) {
                appendParameters(map, methodConfig, methodConfig.getName());
                String retryKey = methodConfig.getName() + ".retry";
                if (map.containsKey(retryKey)) {
                    String retryValue = map.remove(retryKey);
                    if ("false".equals(retryValue)) {
                        map.put(methodConfig.getName() + ".retries", "0");
                    }
                }
                attributes.put(methodConfig.getName(), convertMethodConfig2AyncInfo(methodConfig));
            }
        }

        String hostToRegistry = ConfigUtils.getSystemProperty(Constants.DUBBO_IP_TO_REGISTRY);
        if (StringUtils.isEmpty(hostToRegistry)) {
            hostToRegistry = NetUtils.getLocalHost();
        } else if (isInvalidLocalHost(hostToRegistry)) {
            throw new IllegalArgumentException("Specified invalid registry ip from property:" + Constants.DUBBO_IP_TO_REGISTRY + ", value:" + hostToRegistry);
        }
        map.put(Constants.REGISTER_IP_KEY, hostToRegistry);

        ref = createProxy(map);

        ConsumerModel consumerModel = new ConsumerModel(getUniqueServiceName(), interfaceClass, ref, interfaceClass.getMethods(), attributes);
        ApplicationModel.initConsumerModel(getUniqueServiceName(), consumerModel);
    }

    @SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
    private T createProxy(Map<String, String> map) {
        URL tmpUrl = new URL("temp", "localhost", 0, map);
        final boolean isJvmRefer;
        if (isInjvm() == null) {
            if (url != null && url.length() > 0) { // if a url is specified, don't do local reference
                isJvmRefer = false;
            } else {
                // by default, reference local service if there is
                isJvmRefer = InjvmProtocol.getInjvmProtocol().isInjvmRefer(tmpUrl);
            }
        } else {
            isJvmRefer = isInjvm();
        }

        if (isJvmRefer) {
            URL url = new URL(Constants.LOCAL_PROTOCOL, Constants.LOCALHOST_VALUE, 0, interfaceClass.getName()).addParameters(map);
            invoker = refprotocol.refer(interfaceClass, url);
            if (logger.isInfoEnabled()) {
                logger.info("Using injvm service " + interfaceClass.getName());
            }
        } else {
            if (url != null && url.length() > 0) { // user specified URL, could be peer-to-peer address, or register center's address.
                String[] us = Constants.SEMICOLON_SPLIT_PATTERN.split(url);
                if (us != null && us.length > 0) {
                    for (String u : us) {
                        URL url = URL.valueOf(u);
                        if (StringUtils.isEmpty(url.getPath())) {
                            url = url.setPath(interfaceName);
                        }
                        if (Constants.REGISTRY_PROTOCOL.equals(url.getProtocol())) {
                            urls.add(url.addParameterAndEncoded(Constants.REFER_KEY, StringUtils.toQueryString(map)));
                        } else {
                            urls.add(ClusterUtils.mergeUrl(url, map));
                        }
                    }
                }
            } else { // assemble URL from register center's configuration
                List<URL> us = loadRegistries(false);
                if (CollectionUtils.isNotEmpty(us)) {
                    for (URL u : us) {
                        URL monitorUrl = loadMonitor(u);
                        if (monitorUrl != null) {
                            map.put(Constants.MONITOR_KEY, URL.encode(monitorUrl.toFullString()));
                        }
                        urls.add(u.addParameterAndEncoded(Constants.REFER_KEY, StringUtils.toQueryString(map)));
                    }
                }
                if (urls.isEmpty()) {
                    throw new IllegalStateException("No such any registry to reference " + interfaceName + " on the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion() + ", please config <dubbo:registry address=\"...\" /> to your spring config.");
                }
            }

            if (urls.size() == 1) {
                invoker = refprotocol.refer(interfaceClass, urls.get(0));
            } else {
                List<Invoker<?>> invokers = new ArrayList<Invoker<?>>();
                URL registryURL = null;
                for (URL url : urls) {
                    invokers.add(refprotocol.refer(interfaceClass, url));
                    if (Constants.REGISTRY_PROTOCOL.equals(url.getProtocol())) {
                        registryURL = url; // use last registry url
                    }
                }
                if (registryURL != null) { // registry url is available
                    // use RegistryAwareCluster only when register's cluster is available
                    URL u = registryURL.addParameter(Constants.CLUSTER_KEY, RegistryAwareCluster.NAME);
                    // The invoker wrap relation would be: RegistryAwareClusterInvoker(StaticDirectory) -> FailoverClusterInvoker(RegistryDirectory, will execute route) -> Invoker
                    invoker = cluster.join(new StaticDirectory(u, invokers));
                } else { // not a registry url, must be direct invoke.
                    invoker = cluster.join(new StaticDirectory(invokers));
                }
            }
        }

        Boolean c = check;
        if (c == null && consumer != null) {
            c = consumer.isCheck();
        }
        if (c == null) {
            c = true; // default true
        }
        if (c && !invoker.isAvailable()) {
            // make it possible for consumer to retry later if provider is temporarily unavailable
            initialized = false;
            throw new IllegalStateException("Failed to check the status of the service " + interfaceName + ". No provider available for the service " + (group == null ? "" : group + "/") + interfaceName + (version == null ? "" : ":" + version) + " from the url " + invoker.getUrl() + " to the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion());
        }
        if (logger.isInfoEnabled()) {
            logger.info("Refer dubbo service " + interfaceClass.getName() + " from url " + invoker.getUrl());
        }
        /**
         * @since 2.7.0
         * ServiceData Store
         */
        MetadataReportService metadataReportService = null;
        if ((metadataReportService = getMetadataReportService()) != null) {
            URL consumerURL = new URL(Constants.CONSUMER_PROTOCOL, map.remove(Constants.REGISTER_IP_KEY), 0, map.get(Constants.INTERFACE_KEY), map);
            metadataReportService.publishConsumer(consumerURL);
        }
        // create service proxy
        return (T) proxyFactory.getProxy(invoker);
    }

    private void checkDefault() {
        if (consumer == null) {
            consumer = new ConsumerConfig();
        }
        consumer.refresh();
    }

    private void inheritIfAbsentFromConsumer() {
        if (application == null) {
            application = consumer.getApplication();
        }
        if (module == null) {
            module = consumer.getModule();
        }
        if (registries == null) {
            registries = consumer.getRegistries();
        }
        if (monitor == null) {
            monitor = consumer.getMonitor();
        }
    }

    private void inheritIfAbsentFromModule() {
        if (registries == null) {
            registries = module.getRegistries();
        }
        if (monitor == null) {
            monitor = module.getMonitor();
        }
    }

    private void inheritIfAbsentFromApplication() {
        if (registries == null) {
            registries = application.getRegistries();
        }
        if (monitor == null) {
            monitor = application.getMonitor();
        }
    }


    public Class<?> getInterfaceClass() {
        if (interfaceClass != null) {
            return interfaceClass;
        }
        if (isGeneric()
                || (getConsumer() != null && getConsumer().isGeneric())) {
            return GenericService.class;
        }
        try {
            if (interfaceName != null && interfaceName.length() > 0) {
                this.interfaceClass = Class.forName(interfaceName, true, ClassHelper.getClassLoader());
            }
        } catch (ClassNotFoundException t) {
            throw new IllegalStateException(t.getMessage(), t);
        }
        return interfaceClass;
    }

    /**
     * @param interfaceClass
     * @see #setInterface(Class)
     * @deprecated
     */
    @Deprecated
    public void setInterfaceClass(Class<?> interfaceClass) {
        setInterface(interfaceClass);
    }

    public String getInterface() {
        return interfaceName;
    }

    public void setInterface(String interfaceName) {
        this.interfaceName = interfaceName;
        if (StringUtils.isEmpty(id)) {
            id = interfaceName;
        }
    }

    public void setInterface(Class<?> interfaceClass) {
        if (interfaceClass != null && !interfaceClass.isInterface()) {
            throw new IllegalStateException("The interface class " + interfaceClass + " is not a interface!");
        }
        this.interfaceClass = interfaceClass;
        setInterface(interfaceClass == null ? null : interfaceClass.getName());
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        checkName("client", client);
        this.client = client;
    }

    @Parameter(excluded = true)
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<MethodConfig> getMethods() {
        return methods;
    }

    @SuppressWarnings("unchecked")
    public void setMethods(List<? extends MethodConfig> methods) {
        this.methods = (List<MethodConfig>) methods;
    }

    public ConsumerConfig getConsumer() {
        return consumer;
    }

    public void setConsumer(ConsumerConfig consumer) {
        this.consumer = consumer;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    // just for test
    Invoker<?> getInvoker() {
        return invoker;
    }

    @Parameter(excluded = true)
    public String getUniqueServiceName() {
        StringBuilder buf = new StringBuilder();
        if (group != null && group.length() > 0) {
            buf.append(group).append("/");
        }
        buf.append(interfaceName);
        if (StringUtils.isNotEmpty(version)) {
            buf.append(":").append(version);
        }
        return buf.toString();
    }

    @Override
    @Parameter(excluded = true)
    public String getPrefix() {
        return Constants.DUBBO + ".reference." + interfaceName;
    }

    private void resolveFile() {
        String resolve = System.getProperty(interfaceName);
        String resolveFile = null;
        if (StringUtils.isEmpty(resolve)) {
            resolveFile = System.getProperty("dubbo.resolve.file");
            if (StringUtils.isEmpty(resolveFile)) {
                File userResolveFile = new File(new File(System.getProperty("user.home")), "dubbo-resolve.properties");
                if (userResolveFile.exists()) {
                    resolveFile = userResolveFile.getAbsolutePath();
                }
            }
            if (resolveFile != null && resolveFile.length() > 0) {
                Properties properties = new Properties();
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(new File(resolveFile));
                    properties.load(fis);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to load " + resolveFile + ", cause: " + e.getMessage(), e);
                } finally {
                    try {
                        if (null != fis) {
                            fis.close();
                        }
                    } catch (IOException e) {
                        logger.warn(e.getMessage(), e);
                    }
                }
                resolve = properties.getProperty(interfaceName);
            }
        }
        if (resolve != null && resolve.length() > 0) {
            url = resolve;
            if (logger.isWarnEnabled()) {
                if (resolveFile != null) {
                    logger.warn("Using default dubbo resolve file " + resolveFile + " replace " + interfaceName + "" + resolve + " to p2p invoke remote service.");
                } else {
                    logger.warn("Using -D" + interfaceName + "=" + resolve + " to p2p invoke remote service.");
                }
            }
        }
    }
}

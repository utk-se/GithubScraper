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

package org.deeplearning4j.arbiter.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Simple utility class used to get access to files at the classpath, or packed into jar.
 * Based on Spring ClassPathResource implementation + jar internals access implemented.
 *
 *
 * @author raver119@gmail.com
 */
public class ClassPathResource {

    private String resourceName;

    private static Logger log = LoggerFactory.getLogger(ClassPathResource.class);

    /**
     * Builds new ClassPathResource object
     *
     * @param resourceName String name of resource, to be retrieved
     */
    public ClassPathResource(String resourceName) {
        if (resourceName == null)
            throw new IllegalStateException("Resource name can't be null");
        this.resourceName = resourceName;
    }

    /**
     *  Returns URL of the requested resource
     *
     * @return URL of the resource, if it's available in current Jar
     */
    private URL getUrl() {
        ClassLoader loader = null;
        try {
            loader = Thread.currentThread().getContextClassLoader();
        } catch (Exception e) {
            // do nothing
        }

        if (loader == null) {
            loader = ClassPathResource.class.getClassLoader();
        }

        URL url = loader.getResource(this.resourceName);
        if (url == null) {
            // try to check for mis-used starting slash
            // TODO: see TODO below
            if (this.resourceName.startsWith("/")) {
                url = loader.getResource(this.resourceName.replaceFirst("[\\\\/]", ""));
                if (url != null)
                    return url;
            } else {
                // try to add slash, to make clear it's not an issue
                // TODO: change this mechanic to actual path purifier
                url = loader.getResource("/" + this.resourceName);
                if (url != null)
                    return url;
            }
            throw new IllegalStateException("Resource '" + this.resourceName + "' cannot be found.");
        }
        return url;
    }

    /**
     * Returns requested ClassPathResource as File object
     *
     * Please note: if this method called from compiled jar, temporary file will be created to provide File access
     *
     * @return File requested at constructor call
     * @throws FileNotFoundException
     */
    public File getFile() throws FileNotFoundException {
        URL url = this.getUrl();

        if (isJarURL(url)) {
            /*
                This is actually request for file, that's packed into jar. Probably the current one, but that doesn't matters.
             */
            try {
                url = extractActualUrl(url);
                File file = File.createTempFile("canova_temp", "file");
                file.deleteOnExit();

                ZipFile zipFile = new ZipFile(url.getFile());
                ZipEntry entry = zipFile.getEntry(this.resourceName);
                if (entry == null) {
                    if (this.resourceName.startsWith("/")) {
                        entry = zipFile.getEntry(this.resourceName.replaceFirst("/", ""));
                        if (entry == null) {
                            throw new FileNotFoundException("Resource " + this.resourceName + " not found");
                        }
                    } else
                        throw new FileNotFoundException("Resource " + this.resourceName + " not found");
                }

                long size = entry.getSize();

                InputStream stream = zipFile.getInputStream(entry);
                FileOutputStream outputStream = new FileOutputStream(file);
                byte[] array = new byte[1024];
                int rd = 0;
                long bytesRead = 0;
                do {
                    rd = stream.read(array);
                    outputStream.write(array, 0, rd);
                    bytesRead += rd;
                } while (bytesRead < size);

                outputStream.flush();
                outputStream.close();

                stream.close();
                zipFile.close();

                return file;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        } else {
            /*
                It's something in the actual underlying filesystem, so we can just go for it
             */

            try {
                URI uri = new URI(url.toString().replaceAll(" ", "%20"));
                return new File(uri.getSchemeSpecificPart());
            } catch (URISyntaxException e) {
                return new File(url.getFile());
            }
        }
    }

    /**
     * Checks, if proposed URL is packed into archive.
     *
     * @param url URL to be checked
     * @return True, if URL is archive entry, False otherwise
     */
    private boolean isJarURL(URL url) {
        String protocol = url.getProtocol();
        return "jar".equals(protocol) || "zip".equals(protocol) || "wsjar".equals(protocol)
                        || "code-source".equals(protocol) && url.getPath().contains("!/");
    }

    /**
     * Extracts parent Jar URL from original ClassPath entry URL.
     *
     * @param jarUrl Original URL of the resource
     * @return URL of the Jar file, containing requested resource
     * @throws MalformedURLException
     */
    private URL extractActualUrl(URL jarUrl) throws MalformedURLException {
        String urlFile = jarUrl.getFile();
        int separatorIndex = urlFile.indexOf("!/");
        if (separatorIndex != -1) {
            String jarFile = urlFile.substring(0, separatorIndex);

            try {
                return new URL(jarFile);
            } catch (MalformedURLException var5) {
                if (!jarFile.startsWith("/")) {
                    jarFile = "/" + jarFile;
                }

                return new URL("file:" + jarFile);
            }
        } else {
            return jarUrl;
        }
    }

    /**
     * Returns requested ClassPathResource as InputStream object
     *
     * @return File requested at constructor call
     * @throws FileNotFoundException
     */
    public InputStream getInputStream() throws FileNotFoundException {
        URL url = this.getUrl();
        if (isJarURL(url)) {
            try {
                url = extractActualUrl(url);
                ZipFile zipFile = new ZipFile(url.getFile());
                ZipEntry entry = zipFile.getEntry(this.resourceName);

                if (entry == null) {
                    if (this.resourceName.startsWith("/")) {
                        entry = zipFile.getEntry(this.resourceName.replaceFirst("/", ""));
                        if (entry == null) {
                            throw new FileNotFoundException("Resource " + this.resourceName + " not found");
                        }
                    } else
                        throw new FileNotFoundException("Resource " + this.resourceName + " not found");
                }

                InputStream stream = zipFile.getInputStream(entry);
                return stream;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            File srcFile = this.getFile();
            return new FileInputStream(srcFile);
        }
    }
}

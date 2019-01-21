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

package org.nd4j.linalg.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

public class VfsResource extends AbstractResource {
    private final Object resource;

    public VfsResource(Object resources) {
        Assert.notNull(resources, "VirtualFile must not be null");
        this.resource = resources;
    }

    public InputStream getInputStream() throws IOException {
        return VfsUtils.getInputStream(this.resource);
    }

    public boolean exists() {
        return VfsUtils.exists(this.resource);
    }

    public boolean isReadable() {
        return VfsUtils.isReadable(this.resource);
    }

    public URL getURL() throws IOException {
        try {
            return VfsUtils.getURL(this.resource);
        } catch (Exception var2) {
            throw new IOException("Failed to obtain URL for file " + this.resource, var2);
        }
    }

    public URI getURI() throws IOException {
        try {
            return VfsUtils.getURI(this.resource);
        } catch (Exception var2) {
            throw new IOException("Failed to obtain URI for " + this.resource, var2);
        }
    }

    public File getFile() throws IOException {
        return VfsUtils.getFile(this.resource);
    }

    public long contentLength() throws IOException {
        return VfsUtils.getSize(this.resource);
    }

    public long lastModified() throws IOException {
        return VfsUtils.getLastModified(this.resource);
    }

    public Resource createRelative(String relativePath) throws IOException {
        if (!relativePath.startsWith(".") && relativePath.contains("/")) {
            try {
                return new VfsResource(VfsUtils.getChild(this.resource, relativePath));
            } catch (IOException var3) {

            }
        }

        return new VfsResource(VfsUtils.getRelative(new URL(this.getURL(), relativePath)));
    }

    public String getFilename() {
        return VfsUtils.getName(this.resource);
    }

    public String getDescription() {
        return this.resource.toString();
    }

    public boolean equals(Object obj) {
        return obj == this || obj instanceof VfsResource && this.resource.equals(((VfsResource) obj).resource);
    }

    public int hashCode() {
        return this.resource.hashCode();
    }
}

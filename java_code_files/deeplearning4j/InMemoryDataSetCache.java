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

package org.nd4j.linalg.dataset.api.iterator.cache;

import org.nd4j.linalg.dataset.DataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by anton on 7/16/16.
 */
public class InMemoryDataSetCache implements DataSetCache {

    private static final Logger log = LoggerFactory.getLogger(DataSetCache.class);

    private Map<String, byte[]> cache = new HashMap<>();
    private Set<String> completeNamespaces = new HashSet<>();

    @Override
    public boolean isComplete(String namespace) {
        return completeNamespaces.contains(namespace);
    }

    @Override
    public void setComplete(String namespace, boolean value) {
        if (value) {
            completeNamespaces.add(namespace);
        } else {
            completeNamespaces.remove(namespace);
        }
    }

    @Override
    public DataSet get(String key) {

        if (!cache.containsKey(key)) {
            return null;
        }

        byte[] data = cache.get(key);

        ByteArrayInputStream is = new ByteArrayInputStream(data);

        DataSet ds = new DataSet();

        ds.load(is);

        return ds;
    }

    @Override
    public void put(String key, DataSet dataSet) {
        if (cache.containsKey(key)) {
            log.debug("evicting key %s from data set cache", key);
            cache.remove(key);
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        dataSet.save(os);

        cache.put(key, os.toByteArray());
    }

    @Override
    public boolean contains(String key) {
        return cache.containsKey(key);
    }
}

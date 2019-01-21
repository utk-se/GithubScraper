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

package org.deeplearning4j.spark.time;

import org.deeplearning4j.config.DL4JSystemProperties;

import java.lang.reflect.Method;

/**
 * TimeSourceProvider: used to get a TimeSource via a static method.<br>
 * Defaults to the Network Time Protocol implementation {@link NTPTimeSource}, but can be switched to other implementations
 * via the {@link TimeSourceProvider#TIMESOURCE_CLASSNAME_PROPERTY} system property.
 *
 * @author Alex Black
 */
public class TimeSourceProvider {

    /**
     * Default class to use when getting a TimeSource instance
     */
    public static final String DEFAULT_TIMESOURCE_CLASS_NAME = NTPTimeSource.class.getName();

    /**
     * @deprecated Use {@link DL4JSystemProperties#TIMESOURCE_CLASSNAME_PROPERTY}
     */
    @Deprecated
    public static final String TIMESOURCE_CLASSNAME_PROPERTY = DL4JSystemProperties.TIMESOURCE_CLASSNAME_PROPERTY;

    private TimeSourceProvider() {}

    /**
     * Get a TimeSource
     * the default TimeSource instance (default: {@link NTPTimeSource}
     *
     * @return TimeSource
     */
    public static TimeSource getInstance() {
        String className = System.getProperty(DL4JSystemProperties.TIMESOURCE_CLASSNAME_PROPERTY, DEFAULT_TIMESOURCE_CLASS_NAME);

        return getInstance(className);
    }

    /**
     * Get a specific TimeSource by class name
     *
     * @param className Class name of the TimeSource to return the instance for
     * @return TimeSource instance
     */
    public static TimeSource getInstance(String className) {
        try {
            Class<?> c = Class.forName(className);
            Method m = c.getMethod("getInstance");
            return (TimeSource) m.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException("Error getting TimeSource instance for class \"" + className + "\"", e);
        }
    }
}

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

package org.deeplearning4j.nn.modelimport.keras.layers.pooling;

import org.deeplearning4j.nn.conf.layers.PoolingType;
import org.deeplearning4j.nn.modelimport.keras.config.KerasLayerConfiguration;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;

/**
 * Utility functionality for Keras pooling layers.
 *
 * @author Max Pumperla
 */
public class KerasPoolingUtils {

    /**
     * Map Keras pooling layers to DL4J pooling types.
     *
     * @param className name of the Keras pooling class
     * @return DL4J pooling type
     * @throws UnsupportedKerasConfigurationException Unsupported Keras config
     */
    public static PoolingType mapPoolingType(String className, KerasLayerConfiguration conf)
            throws UnsupportedKerasConfigurationException {
        PoolingType poolingType;
        if (className.equals(conf.getLAYER_CLASS_NAME_MAX_POOLING_2D()) ||
                className.equals(conf.getLAYER_CLASS_NAME_MAX_POOLING_1D()) ||
                className.equals(conf.getLAYER_CLASS_NAME_MAX_POOLING_3D()) ||
                className.equals(conf.getLAYER_CLASS_NAME_GLOBAL_MAX_POOLING_1D()) ||
                className.equals(conf.getLAYER_CLASS_NAME_GLOBAL_MAX_POOLING_2D())) {
            poolingType = PoolingType.MAX;
        } else if (className.equals(conf.getLAYER_CLASS_NAME_AVERAGE_POOLING_2D()) ||
                className.equals(conf.getLAYER_CLASS_NAME_AVERAGE_POOLING_1D()) ||
                className.equals(conf.getLAYER_CLASS_NAME_AVERAGE_POOLING_3D()) ||
                className.equals(conf.getLAYER_CLASS_NAME_GLOBAL_AVERAGE_POOLING_1D()) ||
                className.equals(conf.getLAYER_CLASS_NAME_GLOBAL_AVERAGE_POOLING_2D())) {
            poolingType = PoolingType.AVG;
        } else {
            throw new UnsupportedKerasConfigurationException("Unsupported Keras pooling layer " + className);
        }
        return poolingType;
    }

    /**
     * Map Keras pooling layers to DL4J pooling dimensions.
     *
     * @param className name of the Keras pooling class
     * @return pooling dimensions as int array
     * @throws UnsupportedKerasConfigurationException Unsupported Keras config
     */
    public static int[] mapGlobalPoolingDimensions(String className, KerasLayerConfiguration conf)
            throws UnsupportedKerasConfigurationException {
        int[] dimensions;
        if (className.equals(conf.getLAYER_CLASS_NAME_GLOBAL_MAX_POOLING_1D()) ||
                className.equals(conf.getLAYER_CLASS_NAME_GLOBAL_AVERAGE_POOLING_1D())) {
            dimensions = new int[]{2};
        } else if (className.equals(conf.getLAYER_CLASS_NAME_GLOBAL_MAX_POOLING_2D()) ||
                className.equals(conf.getLAYER_CLASS_NAME_GLOBAL_AVERAGE_POOLING_2D())) {
            dimensions = new int[]{2, 3};
        } else if (className.equals(conf.getLAYER_CLASS_NAME_GLOBAL_MAX_POOLING_3D()) ||
            className.equals(conf.getLAYER_CLASS_NAME_GLOBAL_AVERAGE_POOLING_3D())) {
        dimensions = new int[]{2, 3, 4};
    }  else {
            throw new UnsupportedKerasConfigurationException("Unsupported Keras pooling layer " + className);
        }
        return dimensions;
    }
}

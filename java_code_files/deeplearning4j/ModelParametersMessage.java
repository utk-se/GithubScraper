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

package org.nd4j.parameterserver.distributed.v2.messages.pairs.params;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.parameterserver.distributed.v2.messages.ResponseMessage;
import org.nd4j.parameterserver.distributed.v2.messages.impl.base.BaseINDArrayMessage;

/**
 * This message holds INDArray with model parameters
 * @author raver119@gmail.com
 */
@NoArgsConstructor
public final class ModelParametersMessage extends BaseINDArrayMessage implements ResponseMessage {
    private static final long serialVersionUID = 1L;

    @Getter
    @Setter
    private int iterationNumber = 0;

    @Getter
    @Setter
    private int epochNumber = 0;

    public ModelParametersMessage(@NonNull String messageId, INDArray payload) {
        super(messageId, payload);
    }
}

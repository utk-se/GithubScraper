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

package org.nd4j.parameterserver.distributed.messages.complete;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.parameterserver.distributed.messages.BaseVoidMessage;
import org.nd4j.parameterserver.distributed.messages.MeaningfulMessage;

/**
 * This message contains information about finished computations for specific batch, being sent earlier
 *
 * @author raver119@gmail.com
 */
@Data
@Slf4j
@Deprecated
public abstract class BaseCompleteMessage extends BaseVoidMessage implements MeaningfulMessage {

    protected INDArray payload;

    public BaseCompleteMessage() {
        super(10);
    }

    public BaseCompleteMessage(int messageType) {
        super(messageType);
    }


    @Override
    public void processMessage() {
        // no-op
    }
}

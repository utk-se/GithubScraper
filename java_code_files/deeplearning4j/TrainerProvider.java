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

package org.nd4j.parameterserver.distributed.training;

import lombok.NonNull;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.parameterserver.distributed.conf.VoidConfiguration;
import org.nd4j.parameterserver.distributed.logic.Storage;
import org.nd4j.parameterserver.distributed.logic.completion.Clipboard;
import org.nd4j.parameterserver.distributed.messages.TrainingMessage;
import org.nd4j.parameterserver.distributed.transport.Transport;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * @author raver119@gmail.com
 */
@Deprecated
public class TrainerProvider {
    private static final TrainerProvider INSTANCE = new TrainerProvider();

    // we use Class.getSimpleName() as key here
    protected Map<String, TrainingDriver<?>> trainers = new HashMap<>();

    protected VoidConfiguration voidConfiguration;
    protected Transport transport;
    protected Clipboard clipboard;
    protected Storage storage;

    private TrainerProvider() {
        loadProviders();
    }

    public static TrainerProvider getInstance() {
        return INSTANCE;
    }

    protected void loadProviders(){
        ServiceLoader<TrainingDriver> serviceLoader = ServiceLoader.load(TrainingDriver.class);
        for(TrainingDriver d : serviceLoader){
            trainers.put(d.targetMessageClass(), d);
        }

        if (trainers.size() < 1)
            throw new ND4JIllegalStateException("No TrainingDrivers were found via ServiceLoader mechanism");
    }

    public void init(@NonNull VoidConfiguration voidConfiguration, @NonNull Transport transport,
                    @NonNull Storage storage, @NonNull Clipboard clipboard) {
        this.voidConfiguration = voidConfiguration;
        this.transport = transport;
        this.clipboard = clipboard;
        this.storage = storage;

        for (TrainingDriver<?> trainer : trainers.values()) {
            trainer.init(voidConfiguration, transport, storage, clipboard);
        }
    }



    @SuppressWarnings("unchecked")
    protected <T extends TrainingMessage> TrainingDriver<T> getTrainer(T message) {
        TrainingDriver<?> driver = trainers.get(message.getClass().getSimpleName());
        if (driver == null)
            throw new ND4JIllegalStateException("Can't find trainer for [" + message.getClass().getSimpleName() + "]");

        return (TrainingDriver<T>) driver;
    }

    public <T extends TrainingMessage> void doTraining(T message) {
        TrainingDriver<T> trainer = getTrainer(message);
        trainer.startTraining(message);
    }
}

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

package org.deeplearning4j.nn.conf.dropout;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.pairwise.arithmetic.OldMulOp;
import org.nd4j.linalg.api.ops.random.impl.DropOutInverted;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.schedule.ISchedule;
import org.nd4j.shade.jackson.annotation.JsonIgnoreProperties;
import org.nd4j.shade.jackson.annotation.JsonProperty;
import org.nd4j.util.OneTimeLogger;

/**
 * Implements standard (inverted) dropout.<br>
 * <br>
 * Regarding dropout probability. This is the probability of <it>retaining</it> each input activation value for a layer.
 * Thus, each input activation x is independently set to:<br>
 * x <- 0, with probability 1-p<br>
 * x <- x/p with probability p<br>
 * Note that this "inverted" dropout scheme maintains the expected value of activations - i.e., E(x) is the same before
 * and after dropout.<br>
 * Dropout schedules (i.e., varying probability p as a function of iteration/epoch) are also supported.<br>
 * <br>
 * Other libraries (notably, Keras) use p == probability(<i>dropping</i> an activation)<br>
 * In DL4J, {@code new Dropout(x)} will keep an input activation with probability x, and set to 0 with probability 1-x.<br>
 * Thus, a dropout value of 1.0 is functionally equivalent to no dropout: i.e., 100% probability of retaining
 * each input activation.<br>
 * <p>
 * Note 1: As per all IDropout instances, dropout is applied at training time only - and is automatically not applied at
 * test time (for evaluation, etc)<br>
 * Note 2: Care should be taken when setting lower (probability of retaining) values for (too much information may be
 * lost with aggressive (very low) dropout values).<br>
 * Note 3: Frequently, dropout is not applied to (or, has higher retain probability for) input (first layer)
 * layers. Dropout is also often not applied to output layers.<br>
 * Note 4: Implementation detail (most users can ignore): DL4J uses inverted dropout, as described here:
 * <a href="http://cs231n.github.io/neural-networks-2/">http://cs231n.github.io/neural-networks-2/</a>
 * </p>
 * <br>
 * See: Srivastava et al. 2014: Dropout: A Simple Way to Prevent Neural Networks from Overfitting
 * <a href="http://www.cs.toronto.edu/~rsalakhu/papers/srivastava14a.pdf">http://www.cs.toronto.edu/~rsalakhu/papers/srivastava14a.pdf</a>
 *
 * @author Alex Black
 */
@Data
@JsonIgnoreProperties({"mask", "helper"})
@EqualsAndHashCode(exclude = {"mask", "helper"})
@Slf4j
public class Dropout implements IDropout {

    private double p;
    private ISchedule pSchedule;
    private transient INDArray mask;
    private transient DropoutHelper helper;

    /**
     * @param activationRetainProbability Probability of retaining an activation - see {@link Dropout} javadoc
     */
    public Dropout(double activationRetainProbability) {
        this(activationRetainProbability, null);
        if(activationRetainProbability < 0.0){
            throw new IllegalArgumentException("Activation retain probability must be > 0. Got: " + activationRetainProbability);
        }
        if(activationRetainProbability == 0.0){
            throw new IllegalArgumentException("Invalid probability value: Dropout with 0.0 probability of retaining "
                    + "activations is not supported");
        }
    }

    /**
     * @param activationRetainProbabilitySchedule Schedule for probability of retaining an activation - see {@link Dropout} javadoc
     */
    public Dropout(ISchedule activationRetainProbabilitySchedule){
        this(Double.NaN, activationRetainProbabilitySchedule);
    }

    protected Dropout(@JsonProperty("p") double activationRetainProbability, @JsonProperty("pSchedule") ISchedule activationRetainProbabilitySchedule) {
        this.p = activationRetainProbability;
        this.pSchedule = activationRetainProbabilitySchedule;
        initializeHelper();
    }

    /**
     * Initialize the CuDNN dropout helper, if possible
     */
    protected void initializeHelper(){
        String backend = Nd4j.getExecutioner().getEnvironmentInformation().getProperty("backend");
        if("CUDA".equalsIgnoreCase(backend)) {
            try {
                helper = Class.forName("org.deeplearning4j.nn.layers.dropout.CudnnDropoutHelper")
                        .asSubclass(DropoutHelper.class).newInstance();
                log.debug("CudnnDropoutHelper successfully initialized");
                if (!helper.checkSupported()) {
                    helper = null;
                }
            } catch (Throwable t) {
                if (!(t instanceof ClassNotFoundException)) {
                    log.warn("Could not initialize CudnnDropoutHelper", t);
                }
                //Unlike other layers, don't warn here about CuDNN not found - if the user has any other layers that can
                // benefit from them cudnn, they will get a warning from those
            }
        }
    }


    @Override
    public INDArray applyDropout(INDArray inputActivations, INDArray output, int iteration, int epoch, LayerWorkspaceMgr workspaceMgr) {
        double currP;
        if(pSchedule != null){
            currP = pSchedule.valueAt(iteration, epoch);
        } else {
            currP = p;
        }

        if(helper != null){
            helper.applyDropout(inputActivations, output, p);
            return output;
        }

        mask = workspaceMgr.createUninitialized(ArrayType.INPUT, output.shape(), output.ordering()).assign(1.0);
        Nd4j.getExecutioner().exec(new DropOutInverted(mask, mask, currP));
        Nd4j.getExecutioner().exec(new OldMulOp(inputActivations.castTo(Nd4j.defaultFloatingPointType()), mask, output));
        return output;
    }

    @Override
    public INDArray backprop(INDArray gradAtOutput, INDArray gradAtInput, int iteration, int epoch) {
        if(helper != null){
            helper.backprop(gradAtOutput, gradAtInput);
            return gradAtInput;
        }

        Preconditions.checkState(mask != null, "Cannot perform backprop: Dropout mask array is absent (already cleared?)");
        //dL/dx = dL/dz * dz/dx, with z=0 or x/p
        //Mask already contains either 0 or 1/p, so just muli
        Nd4j.getExecutioner().exec(new OldMulOp(gradAtOutput, mask, gradAtInput));
        mask = null;
        return gradAtInput;
    }

    @Override
    public void clear() {
        mask = null;
    }

    @Override
    public Dropout clone() {
        return new Dropout(p, pSchedule == null ? null : pSchedule.clone());
    }
}

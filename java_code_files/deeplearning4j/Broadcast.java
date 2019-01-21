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

package org.nd4j.linalg.factory;

import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.broadcast.*;
import org.nd4j.linalg.api.ops.impl.broadcast.bool.*;
import org.nd4j.linalg.api.ops.impl.transforms.pairwise.arithmetic.*;
import org.nd4j.linalg.api.ops.impl.transforms.comparison.*;
import org.nd4j.linalg.api.ops.impl.transforms.same.AMax;
import org.nd4j.linalg.api.ops.impl.transforms.same.AMin;

import java.util.Arrays;

/**
 * Convenience methods for broadcasts
 *
 * @author Alex Black
 */
public class Broadcast {

    private Broadcast(){ }

    /**
     * Broadcast add op. See: {@link BroadcastAddOp}
     */
    public static INDArray add(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldAddOp(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastAddOp(x,y,z,dimensions));
    }

    /**
     * Broadcast copy op. See: {@link BroadcastCopyOp}
     */
    public static INDArray copy(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new CopyOp(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastCopyOp(x,y,z,dimensions));
    }

    /**
     * Broadcast divide op. See: {@link BroadcastDivOp}
     */
    public static INDArray div(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldDivOp(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastDivOp(x,y,z,dimensions));
    }

    /**
     * Broadcast equal to op. See: {@link BroadcastEqualTo}
     */
    public static INDArray eq(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldEqualTo(x,y,z));
        }
        return Nd4j.getExecutioner().exec(new BroadcastEqualTo(x,y,z,dimensions));
    }

    /**
     * Broadcast greater than op. See: {@link BroadcastGreaterThan}
     */
    public static INDArray gt(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldGreaterThan(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastGreaterThan(x,y,z,dimensions));
    }

    /**
     * Broadcast greater than or equal to op. See: {@link BroadcastGreaterThanOrEqual}
     */
    public static INDArray gte(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldGreaterThanOrEqual(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastGreaterThanOrEqual(x,y,z,dimensions));
    }

    /**
     * Broadcast less than op. See: {@link BroadcastLessThan}
     */
    public static INDArray lt(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldLessThan(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastLessThan(x,y,z,dimensions));
    }

    /**
     * Broadcast less than or equal to op. See: {@link BroadcastLessThanOrEqual}
     */
    public static INDArray lte(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldLessThanOrEqual(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastLessThanOrEqual(x,y,z,dimensions));
    }

    /**
     * Broadcast element-wise multiply op. See: {@link BroadcastMulOp}
     */
    public static INDArray mul(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldMulOp(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastMulOp(x,y,z,dimensions));
    }

    /**
     * Broadcast not equal to op. See: {@link BroadcastNotEqual}
     */
    public static INDArray neq(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldNotEqualTo(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastNotEqual(x,y,z,dimensions));
    }

    /**
     * Broadcast reverse division op. See: {@link BroadcastRDivOp}
     */
    public static INDArray rdiv(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldRDivOp(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastRDivOp(x,y,z,dimensions));
    }

    /**
     * Broadcast reverse subtraction op. See: {@link BroadcastRSubOp}
     */
    public static INDArray rsub(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldSubOp(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastRSubOp(x,y,z,dimensions));
    }

    /**
     * Broadcast subtraction op. See: {@link BroadcastSubOp}
     */
    public static INDArray sub(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldSubOp(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastSubOp(x,y,z,dimensions));
    }

    /**
     * Broadcast max op. See: {@link BroadcastMax}
     */
    public static INDArray max(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldMax(x,y,z));
        }


        return Nd4j.getExecutioner().exec(new BroadcastMax(x,y,z,dimensions));
    }

    /**
     * Broadcast min op. See: {@link BroadcastMin}
     */
    public static INDArray min(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new OldMin(x,y,z));
        }


        return Nd4j.getExecutioner().exec(new BroadcastMin(x,y,z,dimensions));
    }

    /**
     * Broadcast absolute max op. See: {@link BroadcastAMax}
     */
    public static INDArray amax(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new AMax(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastAMax(x,y,z,dimensions));
    }

    /**
     * Broadcast absolute min op. See: {@link BroadcastAMax}
     */
    public static INDArray amin(INDArray x, INDArray y, INDArray z, int... dimensions) {
        if(dimensions == null || dimensions.length == 0) {
            validateShapesNoDimCase(x,y,z);
            return Nd4j.getExecutioner().exec(new AMin(x,y,z));
        }

        return Nd4j.getExecutioner().exec(new BroadcastAMin(x,y,z,dimensions));
    }

    public static void validateShapesNoDimCase(INDArray x, INDArray y, INDArray z){
        Preconditions.checkArgument(x.equalShapes(y), "When no dimensions are provided, X and Y shapes must be" +
                " equal (x shape: %s, y shape: %s)", x.shape(), y.shape());
        Preconditions.checkArgument(x.equalShapes(z), "When no dimensions are provided, X and Z (result) shapes must be" +
                " equal (x shape: %s, z shape: %s)", x.shape(), z.shape() );
    }

    /**
     * Validate the broadcast dimensions for manual broadcast ops such as {@link BroadcastMulOp}.
     * Here, the dimensions are those that the arrays match on WRT X.
     * For example, mul([a,b,c], [a,c], 0,2)
     */
    public static void validateBroadcastDims(INDArray x, INDArray y, INDArray z, int... dimensions){
        Preconditions.checkArgument(x == z || x.equalShapes(z), "X and Z arrays must be equal shape. X shape: %s, Z shape: %s",
                x.shape(), z.shape());
        long[] sx = x.shape();
        long[] sy = y.shape();
        //Possibility 1: equal ranks - dimensions must match
        if(dimensions.length == 1 && sy.length == 2 && (sy[0] == 1 || sy[1] == 1)) {
            //Edge case: x=[a,b,c], y=[1,b], dim=1 etc
            int d2 = dimensions[0] < 0 ? dimensions[0] + sx.length : dimensions[0]; //Handle negative dimensions
            if (sy[0] == 1) {
                Preconditions.checkState(sx[d2] == sy[1], "Shapes do not match: dimensions[0] - x[%s] must match y[%s], x shape %s, y shape %s, dimensions %s",
                        dimensions[0], 1, sx, sy, dimensions);
            } else {
                Preconditions.checkState(sx[d2] == sy[0], "Shapes do not match: dimensions[0] - x[%s] must match y[%s], x shape %s, y shape %s, dimensions %s",
                        dimensions[0], 0, sx, sy, dimensions);
            }
        } else if(sx.length == sy.length){
            for(int d : dimensions){
                int d2 = d < 0 ? d + sx.length : d; //Handle negative dimensions
                Preconditions.checkState(sx[d2] == sy[d2], "Dimensions mismatch on dimension %s: x shape %s, y shape %s", d, sx, sy);
            }
        } else if(dimensions.length == sy.length) {
            //Possibility 2: different ranks - for example, mul([a,b,c],[a,c], [0,2]) - dimensions refer to x
            for (int i = 0; i < dimensions.length; i++) {
                int d2 = dimensions[i] < 0 ? dimensions[i] + sx.length : dimensions[i]; //Handle negative dimensions
                Preconditions.checkState(sx[d2] == sy[i], "Shapes do not match: dimensions[%s] - x[%s] must match y[%s], x shape %s, y shape %s, dimensions %s",
                        i, d2, i, sx, sy, dimensions);
            }
        } else {
            throw new IllegalStateException("Invalid broadcast dimensions: x shape " + Arrays.toString(sx) + ", y shape " + Arrays.toString(sy)
                    + ", dimensions " + Arrays.toString(dimensions));
        }
    }

}

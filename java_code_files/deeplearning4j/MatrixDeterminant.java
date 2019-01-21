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

package org.nd4j.linalg.api.ops.impl.transforms.custom;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Collections;
import java.util.List;

/**
 * Matrix Determinant op
 *
 * Given input with shape [..., N, N] output the determinant for each sub-matrix.
 *
 * @author Alex Black
 */
public class MatrixDeterminant extends DynamicCustomOp {

    public MatrixDeterminant() {
        //
    }

    public MatrixDeterminant(SameDiff sameDiff, SDVariable in, boolean inPlace) {
        super(null, sameDiff, new SDVariable[]{in}, inPlace);
    }


    @Override
    public String opName() {
        return "matrix_determinant";
    }

    @Override
    public String[] tensorflowNames() {
        return new String[]{"MatrixDeterminant","BatchMatrixDeterminant"};
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> i_v) {
        //TODO support rank 3+ case
        //Derivative of matrix determinant
        //From: Matrix Cookbook - Petersen & Pedersen
        // z=det(X) then dz/dx = z * tr(X^-1)
        //Unfortunately: this is NOT passing gradient checks :(
//        SDVariable inverse = f().matrixInverse(arg());
//        SDVariable trace = f().trace(inverse.mul(sameDiff.onesLike(arg())));
//        SDVariable dOutdIn = outputVariable().mul(trace);
//        return Collections.singletonList(i_v.get(0).mul(dOutdIn).mul(sameDiff.onesLike(arg())));
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public List<DataType> calculateOutputDataTypes(List<DataType> dataTypes){
        Preconditions.checkState(dataTypes != null && dataTypes.size() == 1, "Expected exactly 1 input datatype for %s, got %s", getClass(), dataTypes);
        Preconditions.checkState(dataTypes.get(0).isFPType(), "Input datatype must be a floating point type, got %s", dataTypes.get(0));
        return Collections.singletonList(dataTypes.get(0));
    }
}

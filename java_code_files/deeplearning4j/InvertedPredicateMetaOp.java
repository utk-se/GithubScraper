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

package org.nd4j.linalg.api.ops.impl.meta;

import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.Op;
import org.nd4j.linalg.api.ops.ScalarOp;
import org.nd4j.linalg.api.ops.TransformOp;
import org.nd4j.linalg.api.ops.grid.OpDescriptor;

import java.util.List;

/**
 * This MetaOp covers case, when Op A and Op B are both using linear memory access
 *
 * You're NOT supposed to directly call this op. Do it on your own risk, only if you're absolutely have to.
 *
 * @author raver119@gmail.com
 */
public class InvertedPredicateMetaOp extends BaseMetaOp {

    public InvertedPredicateMetaOp() {

    }

    public InvertedPredicateMetaOp(INDArray x, INDArray y) {
        super(x, y);
    }

    public InvertedPredicateMetaOp(Op opA, Op opB) {
        super(opA, opB);
    }

    public InvertedPredicateMetaOp(OpDescriptor opA, OpDescriptor opB) {
        super(opA, opB);
    }

    public InvertedPredicateMetaOp(ScalarOp opA, TransformOp opB) {
        super(opA, opB);
    }

    public InvertedPredicateMetaOp(TransformOp opA, TransformOp opB) {
        super(opA, opB);
    }

    public InvertedPredicateMetaOp(TransformOp opA, ScalarOp opB) {
        super(opA, opB);
    }

    public InvertedPredicateMetaOp(ScalarOp opA, ScalarOp opB) {
        super(opA, opB);
    }

    @Override
    public int opNum() {
        return 3;
    }

    @Override
    public String opName() {
        return "meta_predicate_inverted";
    }

    @Override
    public List<SDVariable> doDiff(List<SDVariable> f1) {
        return null;
    }
}

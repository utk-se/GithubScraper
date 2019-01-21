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

package org.deeplearning4j.arbiter.optimize.parameter.math;

import org.deeplearning4j.arbiter.optimize.api.AbstractParameterSpace;
import org.deeplearning4j.arbiter.optimize.api.ParameterSpace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A simple parameter space that implements pairwise mathematical operations on another parameter space. This allows you
 * to do things like Z = X + Y, where X and Y are parameter spaces.
 *
 * @param <T> Type of the parameter space
 * @author Alex Black
 */
public class PairMathOp<T extends Number> extends AbstractParameterSpace<T> {

    private ParameterSpace<T> first;
    private ParameterSpace<T> second;
    private Op op;

    public PairMathOp(ParameterSpace<T> first, ParameterSpace<T> second, Op op){
        this.first = first;
        this.second = second;
        this.op = op;
    }

    @Override
    public T getValue(double[] parameterValues) {
        T f = first.getValue(parameterValues);
        T s = second.getValue(parameterValues);
        return op.doOp(f, s);
    }

    @Override
    public int numParameters() {
        return first.numParameters() + second.numParameters();
    }

    @Override
    public List<ParameterSpace> collectLeaves() {
        List<ParameterSpace> l = new ArrayList<>();
        l.addAll(first.collectLeaves());
        l.addAll(second.collectLeaves());
        return l;
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public void setIndices(int... indices) {
        int n1 = first.numParameters();
        int n2 = second.numParameters();
        int[] s1 = Arrays.copyOfRange(indices, 0, n1);
        int[] s2 = Arrays.copyOfRange(indices, n1, n1+n2);
        first.setIndices(s1);
        second.setIndices(s2);
    }
}

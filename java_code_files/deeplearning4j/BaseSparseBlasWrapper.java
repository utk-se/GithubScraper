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

import org.nd4j.linalg.api.blas.Lapack;
import org.nd4j.linalg.api.blas.Level1;
import org.nd4j.linalg.api.blas.Level2;
import org.nd4j.linalg.api.blas.Level3;
import org.nd4j.linalg.api.ndarray.INDArray;

/**
 * @author Audrey Loeffel
 */
public abstract class BaseSparseBlasWrapper implements BlasWrapper {


    @Override
    public Lapack lapack() {
        return Nd4j.sparseFactory().lapack();
    }

    @Override
    public Level1 level1() {
        return Nd4j.sparseFactory().level1();
    }

    @Override
    public Level2 level2() {
        return Nd4j.sparseFactory().level2();

    }

    @Override
    public Level3 level3() {
        return Nd4j.sparseFactory().level3();

    }

    // ================== TODO ====================
    @Override
    public INDArray swap(INDArray x, INDArray y) {
        return null;
    }

    @Override
    public INDArray scal(double alpha, INDArray x) {
        return null;
    }

    @Override
    public INDArray scal(float alpha, INDArray x) {
        return null;
    }

    @Override
    public INDArray copy(INDArray x, INDArray y) {
        return null;
    }

    @Override
    public INDArray axpy(double da, INDArray dx, INDArray dy) {
        return null;
    }

    @Override
    public INDArray axpy(float da, INDArray dx, INDArray dy) {
        return null;
    }

    @Override
    public INDArray axpy(Number da, INDArray dx, INDArray dy) {
        return null;
    }

    @Override
    public double dot(INDArray x, INDArray y) {
        return 0;
    }

    @Override
    public double nrm2(INDArray x) {
        return 0;
    }

    @Override
    public double asum(INDArray x) {
        return 0;
    }

    @Override
    public int iamax(INDArray x) {
        return 0;
    }

    @Override
    public INDArray gemv(Number alpha, INDArray a, INDArray x, double beta, INDArray y) {
        return null;
    }

    @Override
    public INDArray gemv(double alpha, INDArray a, INDArray x, double beta, INDArray y) {
        return null;
    }

    @Override
    public INDArray gemv(float alpha, INDArray a, INDArray x, float beta, INDArray y) {
        return null;
    }

    @Override
    public INDArray ger(Number alpha, INDArray x, INDArray y, INDArray a) {
        return null;
    }

    @Override
    public INDArray ger(double alpha, INDArray x, INDArray y, INDArray a) {
        return null;
    }

    @Override
    public INDArray ger(float alpha, INDArray x, INDArray y, INDArray a) {
        return null;
    }

    @Override
    public INDArray gemm(double alpha, INDArray a, INDArray b, double beta, INDArray c) {
        return null;
    }

    @Override
    public INDArray gemm(float alpha, INDArray a, INDArray b, float beta, INDArray c) {
        return null;
    }

    @Override
    public INDArray gesv(INDArray a, int[] ipiv, INDArray b) {
        return null;
    }

    @Override
    public void checkInfo(String name, int info) {

    }

    @Override
    public INDArray sysv(char uplo, INDArray a, int[] ipiv, INDArray b) {
        return null;
    }

    @Override
    public int syev(char jobz, char uplo, INDArray a, INDArray w) {
        return 0;
    }

    @Override
    public int syevx(char jobz, char range, char uplo, INDArray a, double vl, double vu, int il, int iu, double abstol,
                    INDArray w, INDArray z) {
        return 0;
    }

    @Override
    public int syevx(char jobz, char range, char uplo, INDArray a, float vl, float vu, int il, int iu, float abstol,
                    INDArray w, INDArray z) {
        return 0;
    }

    @Override
    public int syevd(char jobz, char uplo, INDArray A, INDArray w) {
        return 0;
    }

    @Override
    public int syevr(char jobz, char range, char uplo, INDArray a, double vl, double vu, int il, int iu, double abstol,
                    INDArray w, INDArray z, int[] isuppz) {
        return 0;
    }

    @Override
    public int syevr(char jobz, char range, char uplo, INDArray a, float vl, float vu, int il, int iu, float abstol,
                    INDArray w, INDArray z, int[] isuppz) {
        return 0;
    }

    @Override
    public int syevr(char jobz, char range, char uplo, INDArray a, float vl, float vu, int il, int iu, Number abstol,
                    INDArray w, INDArray z, int[] isuppz) {
        return 0;
    }

    @Override
    public void posv(char uplo, INDArray A, INDArray B) {

    }

    @Override
    public int geev(char jobvl, char jobvr, INDArray A, INDArray WR, INDArray WI, INDArray VL, INDArray VR) {
        return 0;
    }

    @Override
    public int sygvd(int itype, char jobz, char uplo, INDArray A, INDArray B, INDArray W) {
        return 0;
    }

    @Override
    public void gelsd(INDArray A, INDArray B) {

    }

    @Override
    public void geqrf(INDArray A, INDArray tau) {

    }

    @Override
    public void ormqr(char side, char trans, INDArray A, INDArray tau, INDArray C) {

    }

    @Override
    public void saxpy(double alpha, INDArray x, INDArray y) {

    }

    @Override
    public void saxpy(float alpha, INDArray x, INDArray y) {

    }
}

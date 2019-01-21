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

import org.nd4j.linalg.api.blas.*;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.util.LinAlgExceptions;

/**
 *
 * Base implementation of a blas wrapper that
 * delegates things to the underlying level.
 * This is a migration tool to preserve the old api
 * while allowing for migration to the newer one.
 *
 * @author Adam Gibson
 */

public abstract class BaseBlasWrapper implements BlasWrapper {


    @Override
    public Lapack lapack() {
        return Nd4j.factory().lapack();
    }

    @Override
    public Level1 level1() {
        return Nd4j.factory().level1();
    }

    @Override
    public Level2 level2() {
        return Nd4j.factory().level2();

    }

    @Override
    public Level3 level3() {
        return Nd4j.factory().level3();

    }

    @Override
    public INDArray axpy(Number da, INDArray dx, INDArray dy) {
        //        if(!dx.isVector())
        //            throw new IllegalArgumentException("Unable to use axpy on a non vector");
        LinAlgExceptions.assertSameLength(dx, dy);
        level1().axpy(dx.length(), da.doubleValue(), dx, dy);
        return dy;
    }

    @Override
    public INDArray gemv(Number alpha, INDArray a, INDArray x, double beta, INDArray y) {
        LinAlgExceptions.assertVector(x, y);
        LinAlgExceptions.assertMatrix(a);
        level2().gemv(BlasBufferUtil.getCharForTranspose(a), BlasBufferUtil.getCharForTranspose(x), alpha.doubleValue(),
                        a, x, beta, y);
        return y;
    }

    @Override
    public INDArray ger(Number alpha, INDArray x, INDArray y, INDArray a) {
        level2().ger(BlasBufferUtil.getCharForTranspose(x), alpha.doubleValue(), x, y, a);
        return a;
    }

    @Override
    public int syevr(char jobz, char range, char uplo, INDArray a, float vl, float vu, int il, int iu, Number abstol,
                    INDArray w, INDArray z, int[] isuppz) {
        throw new UnsupportedOperationException();

    }

    @Override
    public INDArray swap(INDArray x, INDArray y) {
        level1().swap(x, y);
        return y;
    }

    @Override
    public INDArray scal(double alpha, INDArray x) {
        LinAlgExceptions.assertVector(x);

        if (x.data().dataType() == DataType.FLOAT)
            return scal((float) alpha, x);
        level1().scal(x.length(), alpha, x);
        return x;
    }

    @Override
    public INDArray scal(float alpha, INDArray x) {
        LinAlgExceptions.assertVector(x);

        if (x.data().dataType() == DataType.DOUBLE)
            return scal((double) alpha, x);
        level1().scal(x.length(), alpha, x);
        return x;
    }

    @Override
    public INDArray copy(INDArray x, INDArray y) {
        LinAlgExceptions.assertVector(x, y);
        level1().copy(x, y);
        return y;
    }

    @Override
    public INDArray axpy(double da, INDArray dx, INDArray dy) {
        LinAlgExceptions.assertVector(dx, dy);

        if (dx.data().dataType() == DataType.FLOAT)
            return axpy((float) da, dx, dy);
        level1().axpy(dx.length(), da, dx, dy);
        return dy;
    }

    @Override
    public INDArray axpy(float da, INDArray dx, INDArray dy) {
        LinAlgExceptions.assertVector(dx, dy);

        if (dx.data().dataType() == DataType.DOUBLE)
            return axpy((double) da, dx, dy);

        level1().axpy(dx.length(), da, dx, dy);
        return dy;
    }

    @Override
    public double dot(INDArray x, INDArray y) {
        return level1().dot(x.length(), 1, x, y);
    }

    @Override
    public double nrm2(INDArray x) {
        LinAlgExceptions.assertVector(x);
        return level1().nrm2(x);
    }

    @Override
    public double asum(INDArray x) {
        LinAlgExceptions.assertVector(x);
        return level1().asum(x);
    }

    @Override
    public int iamax(INDArray x) {
        return level1().iamax(x);
    }

    @Override
    public INDArray gemv(double alpha, INDArray a, INDArray x, double beta, INDArray y) {
        LinAlgExceptions.assertVector(x, y);
        LinAlgExceptions.assertMatrix(a);

        if (a.data().dataType() == DataType.FLOAT) {
            //            DefaultOpExecutioner.validateDataType(DataType.FLOAT, a, x, y);
            return gemv((float) alpha, a, x, (float) beta, y);
        } else {
            level2().gemv('N', 'N', alpha, a, x, beta, y);
        }
        return y;
    }

    @Override
    public INDArray gemv(float alpha, INDArray a, INDArray x, float beta, INDArray y) {
        LinAlgExceptions.assertVector(x, y);
        LinAlgExceptions.assertMatrix(a);

        if (a.data().dataType() == DataType.DOUBLE) {
            return gemv((double) alpha, a, x, (double) beta, y);
        }
        level2().gemv('N', 'N', alpha, a, x, beta, y);
        return y;
    }

    @Override
    public INDArray ger(double alpha, INDArray x, INDArray y, INDArray a) {
        LinAlgExceptions.assertVector(x, y);
        LinAlgExceptions.assertMatrix(a);

        if (x.data().dataType() == DataType.FLOAT) {
            return ger((float) alpha, x, y, a);
        }

        level2().ger('N', alpha, x, y, a);
        return a;
    }

    @Override
    public INDArray ger(float alpha, INDArray x, INDArray y, INDArray a) {
        LinAlgExceptions.assertVector(x, y);
        LinAlgExceptions.assertMatrix(a);


        if (x.data().dataType() == DataType.DOUBLE) {
            return ger((double) alpha, x, y, a);
        }

        level2().ger('N', alpha, x, y, a);
        return a;
    }

    @Override
    public INDArray gemm(double alpha, INDArray a, INDArray b, double beta, INDArray c) {
        LinAlgExceptions.assertMatrix(a, b, c);

        if (a.data().dataType() == DataType.FLOAT) {
            return gemm((float) alpha, a, b, (float) beta, c);
        }


        level3().gemm(BlasBufferUtil.getCharForTranspose(a), BlasBufferUtil.getCharForTranspose(b),
                        BlasBufferUtil.getCharForTranspose(c), alpha, a, b, beta, c);
        return c;
    }

    @Override
    public INDArray gemm(float alpha, INDArray a, INDArray b, float beta, INDArray c) {
        LinAlgExceptions.assertMatrix(a, b, c);


        if (a.data().dataType() == DataType.DOUBLE) {
            return gemm((double) alpha, a, b, (double) beta, c);
        }

        level3().gemm(BlasBufferUtil.getCharForTranspose(a), BlasBufferUtil.getCharForTranspose(b),
                        BlasBufferUtil.getCharForTranspose(c), alpha, a, b, beta, c);
        return c;
    }

    @Override
    public INDArray gesv(INDArray a, int[] ipiv, INDArray b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkInfo(String name, int info) {

    }

    @Override
    public INDArray sysv(char uplo, INDArray a, int[] ipiv, INDArray b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int syev(char jobz, char uplo, INDArray a, INDArray w) {
        return lapack().syev(jobz, uplo, a, w);
    }

    @Override
    public int syevx(char jobz, char range, char uplo, INDArray a, double vl, double vu, int il, int iu, double abstol,
                    INDArray w, INDArray z) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int syevx(char jobz, char range, char uplo, INDArray a, float vl, float vu, int il, int iu, float abstol,
                    INDArray w, INDArray z) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int syevd(char jobz, char uplo, INDArray A, INDArray w) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int syevr(char jobz, char range, char uplo, INDArray a, double vl, double vu, int il, int iu, double abstol,
                    INDArray w, INDArray z, int[] isuppz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int syevr(char jobz, char range, char uplo, INDArray a, float vl, float vu, int il, int iu, float abstol,
                    INDArray w, INDArray z, int[] isuppz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void posv(char uplo, INDArray A, INDArray B) {
        throw new UnsupportedOperationException();

    }

    @Override
    public int geev(char jobvl, char jobvr, INDArray A, INDArray WR, INDArray WI, INDArray VL, INDArray VR) {
        throw new UnsupportedOperationException();

    }

    @Override
    public int sygvd(int itype, char jobz, char uplo, INDArray A, INDArray B, INDArray W) {
        throw new UnsupportedOperationException();

    }

    @Override
    public void gelsd(INDArray A, INDArray B) {
        throw new UnsupportedOperationException();

    }

    @Override
    public void geqrf(INDArray A, INDArray tau) {
        throw new UnsupportedOperationException();

    }

    @Override
    public void ormqr(char side, char trans, INDArray A, INDArray tau, INDArray C) {
        throw new UnsupportedOperationException();

    }



    @Override
    public void saxpy(double alpha, INDArray x, INDArray y) {
        axpy(alpha, x, y);

    }

    @Override
    public void saxpy(float alpha, INDArray x, INDArray y) {
        axpy(alpha, x, y);
    }



}

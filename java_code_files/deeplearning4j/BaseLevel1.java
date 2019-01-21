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

package org.nd4j.linalg.api.blas.impl;

import org.nd4j.linalg.api.blas.BlasBufferUtil;
import org.nd4j.linalg.api.blas.Level1;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.executioner.DefaultOpExecutioner;
import org.nd4j.linalg.api.ops.executioner.OpExecutioner;
import org.nd4j.linalg.api.ops.impl.scalar.ScalarMultiplication;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.profiler.OpProfiler;

/**
 * Base class for level 1 functions, abstract headers pulled from:
 * http://www.netlib.org/blas/blast-forum/cblas.h
 *
 * @author Adam Gibson
 */
public abstract class BaseLevel1 extends BaseLevel implements Level1 {
    /**
     * computes a vector-vector dot product.
     *
     * @param n number of accessed element
     * @param alpha
     * @param X an INDArray
     * @param Y an INDArray
     * @return the vector-vector dot product of X and Y
     */
    @Override
    public double dot(long n, double alpha, INDArray X, INDArray Y) {
        if (Nd4j.getExecutioner().getProfilingMode() == OpExecutioner.ProfilingMode.ALL)
            OpProfiler.getInstance().processBlasCall(false, X, Y);

        if (X.isSparse() && !Y.isSparse()) {
            return Nd4j.getSparseBlasWrapper().level1().dot(n, alpha, X, Y);
        } else if (!X.isSparse() && Y.isSparse()) {
            return Nd4j.getSparseBlasWrapper().level1().dot(n, alpha, Y, X);
        } else if (X.isSparse() && Y.isSparse()) {
            // TODO - MKL doesn't contain such routines
            return 0;
        }

        if (X.data().dataType() == DataType.DOUBLE) {
            DefaultOpExecutioner.validateDataType(DataType.DOUBLE, X, Y);
            return ddot(n, X, BlasBufferUtil.getBlasStride(X), Y, BlasBufferUtil.getBlasStride(Y));
        } else if (X.data().dataType() == DataType.FLOAT) {
            DefaultOpExecutioner.validateDataType(DataType.FLOAT, X, Y);
            return sdot(n, X, BlasBufferUtil.getBlasStride(X), Y, BlasBufferUtil.getBlasStride(Y));
        } else {
            DefaultOpExecutioner.validateDataType(DataType.HALF, X, Y);
            return hdot(n, X, BlasBufferUtil.getBlasStride(X), Y, BlasBufferUtil.getBlasStride(Y));
        }

    }

    @Override
    public double dot(long n, DataBuffer x, int offsetX, int incrX, DataBuffer y, int offsetY, int incrY) {
        if (supportsDataBufferL1Ops()) {
            if (x.dataType() == DataType.FLOAT) {
                return sdot(n, x, offsetX, incrX, y, offsetY, incrY);
            } else if (x.dataType() == DataType.DOUBLE) {
                return ddot(n, x, offsetX, incrX, y, offsetY, incrY);
            } else {
                return hdot(n, x, offsetX, incrX, y, offsetY, incrY);
            }
        } else {
            long[] shapex = {1, n};
            long[] shapey = {1, n};
            long[] stridex = {incrX, incrX};
            long[] stridey = {incrY, incrY};
            INDArray arrX = Nd4j.create(x, shapex, stridex, offsetX, 'c');
            INDArray arrY = Nd4j.create(x, shapey, stridey, offsetY, 'c');
            return dot(n, 0.0, arrX, arrY);
        }
    }

    /**
     * computes the Euclidean norm of a vector.
     *
     * @param arr
     * @return
     */
    @Override
    public double nrm2(INDArray arr) {

        if (arr.isSparse()) {
            return Nd4j.getSparseBlasWrapper().level1().nrm2(arr);
        }
        if (Nd4j.getExecutioner().getProfilingMode() == OpExecutioner.ProfilingMode.ALL)
            OpProfiler.getInstance().processBlasCall(false, arr);
        if (arr.isSparse()) {
            return Nd4j.getSparseBlasWrapper().level1().nrm2(arr);
        }
        if (arr.data().dataType() == DataType.DOUBLE) {
            DefaultOpExecutioner.validateDataType(DataType.DOUBLE, arr);
            return dnrm2(arr.length(), arr, BlasBufferUtil.getBlasStride(arr));
        } else {
            DefaultOpExecutioner.validateDataType(DataType.FLOAT, arr);
            return snrm2(arr.length(), arr, BlasBufferUtil.getBlasStride(arr));
        }
        // TODO: add nrm2 for half, as call to appropriate NativeOp<HALF>
    }

    /**
     * computes the sum of magnitudes of all vector elements or, for a complex vector x, the sum
     *
     * @param arr
     * @return
     */
    @Override
    public double asum(INDArray arr) {

        if (arr.isSparse()) {
            return Nd4j.getSparseBlasWrapper().level1().asum(arr);
        }
        if (Nd4j.getExecutioner().getProfilingMode() == OpExecutioner.ProfilingMode.ALL)
            OpProfiler.getInstance().processBlasCall(false, arr);

        if (arr.data().dataType() == DataType.DOUBLE) {
            DefaultOpExecutioner.validateDataType(DataType.DOUBLE, arr);
            return dasum(arr.length(), arr, BlasBufferUtil.getBlasStride(arr));
        } else if (arr.data().dataType() == DataType.FLOAT) {
            DefaultOpExecutioner.validateDataType(DataType.FLOAT, arr);
            return sasum(arr.length(), arr, BlasBufferUtil.getBlasStride(arr));
        } else {
            DefaultOpExecutioner.validateDataType(DataType.HALF, arr);
            return hasum(arr.length(), arr, BlasBufferUtil.getBlasStride(arr));
        }
    }

    @Override
    public double asum(long n, DataBuffer x, int offsetX, int incrX) {
        if (supportsDataBufferL1Ops()) {
            if (x.dataType() == DataType.FLOAT) {
                return sasum(n, x, offsetX, incrX);
            } else if (x.dataType() == DataType.DOUBLE) {
                return dasum(n, x, offsetX, incrX);
            } else {
                return hasum(n, x, offsetX, incrX);
            }
        } else {
            long[] shapex = {1, n};
            long[] stridex = {incrX, incrX};
            INDArray arrX = Nd4j.create(x, shapex, stridex, offsetX, 'c');
            return asum(arrX);
        }
    }

    @Override
    public int iamax(long n, INDArray arr, int stride) {
        if (Nd4j.getExecutioner().getProfilingMode() == OpExecutioner.ProfilingMode.ALL)
            OpProfiler.getInstance().processBlasCall(false, arr);

        if (arr.data().dataType() == DataType.DOUBLE) {
            DefaultOpExecutioner.validateDataType(DataType.DOUBLE, arr);
            return idamax(n, arr, stride);
        } else {
            DefaultOpExecutioner.validateDataType(DataType.FLOAT, arr);
            return isamax(n, arr, stride);
        }
    }

    @Override
    public int iamax(long n, DataBuffer x, int offsetX, int incrX) {
        if (supportsDataBufferL1Ops()) {
            if (x.dataType() == DataType.FLOAT) {
                return isamax(n, x, offsetX, incrX);
            } else {
                return isamax(n, x, offsetX, incrX);
            }
        } else {
            long[] shapex = {1, n};
            long[] stridex = {incrX, incrX};
            INDArray arrX = Nd4j.create(x, shapex, stridex, offsetX, 'c');
            return iamax(n, arrX, incrX);
        }
    }

    /**
     * finds the element of a
     * vector that has the largest absolute value.
     *
     * @param arr
     * @return
     */
    @Override
    public int iamax(INDArray arr) {
        if (arr.isSparse()) {
            return Nd4j.getSparseBlasWrapper().level1().iamax(arr);
        }
        if (Nd4j.getExecutioner().getProfilingMode() == OpExecutioner.ProfilingMode.ALL)
            OpProfiler.getInstance().processBlasCall(false, arr);

        if (arr.data().dataType() == DataType.DOUBLE) {
            DefaultOpExecutioner.validateDataType(DataType.DOUBLE, arr);
            return idamax(arr.length(), arr, BlasBufferUtil.getBlasStride(arr));
        } else {
            DefaultOpExecutioner.validateDataType(DataType.FLOAT, arr);
            return isamax(arr.length(), arr, BlasBufferUtil.getBlasStride(arr));
        }
    }

    /**
     * finds the element of a vector that has the minimum absolute value.
     *
     * @param arr
     * @return
     */
    @Override
    public int iamin(INDArray arr) {
        if (arr.isSparse()) {
            return Nd4j.getSparseBlasWrapper().level1().iamin(arr);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * swaps a vector with another vector.
     *
     * @param x
     * @param y
     */
    @Override
    public void swap(INDArray x, INDArray y) {
        if (Nd4j.getExecutioner().getProfilingMode() == OpExecutioner.ProfilingMode.ALL)
            OpProfiler.getInstance().processBlasCall(false, x, y);

        if (x.isSparse() || y.isSparse()) {
            Nd4j.getSparseBlasWrapper().level1().swap(x, y);
            return;
        }

        if (x.data().dataType() == DataType.DOUBLE) {
            DefaultOpExecutioner.validateDataType(DataType.DOUBLE, x, y);
            dswap(x.length(), x, BlasBufferUtil.getBlasStride(x), y, BlasBufferUtil.getBlasStride(y));
        } else {
            DefaultOpExecutioner.validateDataType(DataType.FLOAT, x, y);
            sswap(x.length(), x, BlasBufferUtil.getBlasStride(x), y, BlasBufferUtil.getBlasStride(y));
        }
    }


    /**
     * swaps a vector with another vector.
     *
     * @param x
     * @param y
     */
    @Override
    public void copy(INDArray x, INDArray y) {
        if (Nd4j.getExecutioner().getProfilingMode() == OpExecutioner.ProfilingMode.ALL)
            OpProfiler.getInstance().processBlasCall(false, x, y);

        if (x.isSparse() || y.isSparse()) {
            Nd4j.getSparseBlasWrapper().level1().copy(x, y);
            return;
        }
        if (x.data().dataType() == DataType.DOUBLE) {
            DefaultOpExecutioner.validateDataType(DataType.DOUBLE, x, y);
            dcopy(x.length(), x, BlasBufferUtil.getBlasStride(x), y, BlasBufferUtil.getBlasStride(y));
        } else {
            DefaultOpExecutioner.validateDataType(DataType.FLOAT, x, y);
            scopy(x.length(), x, BlasBufferUtil.getBlasStride(x), y, BlasBufferUtil.getBlasStride(y));
        }
    }

    /**copy a vector to another vector.
     */
    @Override
    public void copy(long n, DataBuffer x, int offsetX, int incrX, DataBuffer y, int offsetY, int incrY) {


        if (supportsDataBufferL1Ops()) {
            if (x.dataType() == DataType.DOUBLE) {
                dcopy(n, x, offsetX, incrX, y, offsetY, incrY);
            } else {
                scopy(n, x, offsetX, incrX, y, offsetY, incrY);
            }
        } else {
            long[] shapex = {1, n};
            long[] shapey = {1, n};
            long[] stridex = {incrX, incrX};
            long[] stridey = {incrY, incrY};
            INDArray arrX = Nd4j.create(x, shapex, stridex, offsetX, 'c');
            INDArray arrY = Nd4j.create(x, shapey, stridey, offsetY, 'c');
            copy(arrX, arrY);
        }
    }



    /**
     * computes a vector-scalar product and adds the result to a vector.
     *
     * @param n
     * @param alpha
     * @param x
     * @param y
     */
    @Override
    public void axpy(long n, double alpha, INDArray x, INDArray y) {

        if (Nd4j.getExecutioner().getProfilingMode() == OpExecutioner.ProfilingMode.ALL)
            OpProfiler.getInstance().processBlasCall(false, x, y);

        if (x.isSparse() && !y.isSparse()) {
            Nd4j.getSparseBlasWrapper().level1().axpy(n, alpha, x, y);
        } else if (x.data().dataType() == DataType.DOUBLE) {
            DefaultOpExecutioner.validateDataType(DataType.DOUBLE, x, y);
            daxpy(n, alpha, x, BlasBufferUtil.getBlasStride(x), y, BlasBufferUtil.getBlasStride(y));
        } else if (x.data().dataType() == DataType.FLOAT) {
            DefaultOpExecutioner.validateDataType(DataType.FLOAT, x, y);
            saxpy(n, (float) alpha, x, BlasBufferUtil.getBlasStride(x), y, BlasBufferUtil.getBlasStride(y));
        } else {
            DefaultOpExecutioner.validateDataType(DataType.HALF, x, y);
            haxpy(n, (float) alpha, x, BlasBufferUtil.getBlasStride(x), y, BlasBufferUtil.getBlasStride(y));
        }
    }

    @Override
    public void axpy(long n, double alpha, DataBuffer x, int offsetX, int incrX, DataBuffer y, int offsetY, int incrY) {
        if (supportsDataBufferL1Ops()) {
            if (x.dataType() == DataType.DOUBLE) {
                daxpy(n, alpha, x, offsetX, incrX, y, offsetY, incrY);
            } else if (x.dataType() == DataType.FLOAT) {
                saxpy(n, (float) alpha, x, offsetX, incrX, y, offsetY, incrY);
            } else {
                haxpy(n, (float) alpha, x, offsetX, incrX, y, offsetY, incrY);
            }
        } else {
            long[] shapex = {1, n};
            long[] shapey = {1, n};
            long[] stridex = {incrX, incrX};
            long[] stridey = {incrY, incrY};
            INDArray arrX = Nd4j.create(x, shapex, stridex, offsetX, 'c');
            INDArray arrY = Nd4j.create(x, shapey, stridey, offsetY, 'c');
            axpy(n, alpha, arrX, arrY);
        }
    }

    /**
     * computes parameters for a Givens rotation.
     *
     * @param a
     * @param b
     * @param c
     * @param s
     */
    @Override
    public void rotg(INDArray a, INDArray b, INDArray c, INDArray s) {
        throw new UnsupportedOperationException();
    }

    /**
     * performs rotation of points in the plane.
     *
     * @param N
     * @param X
     * @param Y
     * @param c
     * @param s
     */
    @Override
    public void rot(long N, INDArray X, INDArray Y, double c, double s) {

        if (Nd4j.getExecutioner().getProfilingMode() == OpExecutioner.ProfilingMode.ALL)
            OpProfiler.getInstance().processBlasCall(false, X, Y);

        if (X.isSparse() && !Y.isSparse()) {
            Nd4j.getSparseBlasWrapper().level1().rot(N, X, Y, c, s);
        } else if (X.data().dataType() == DataType.DOUBLE) {
            DefaultOpExecutioner.validateDataType(DataType.DOUBLE, X, Y);
            drot(N, X, BlasBufferUtil.getBlasStride(X), Y, BlasBufferUtil.getBlasStride(X), c, s);
        } else {
            DefaultOpExecutioner.validateDataType(DataType.FLOAT, X, Y);
            srot(N, X, BlasBufferUtil.getBlasStride(X), Y, BlasBufferUtil.getBlasStride(X), (float) c, (float) s);
        }
    }

    /**
     * computes the modified parameters for a Givens rotation.
     *
     * @param d1
     * @param d2
     * @param b1
     * @param b2
     * @param P
     */
    @Override
    public void rotmg(INDArray d1, INDArray d2, INDArray b1, double b2, INDArray P) {
        throw new UnsupportedOperationException();
    }

    /**
     * computes a vector by a scalar product.
     *
     * @param N
     * @param alpha
     * @param X
     */
    @Override
    public void scal(long N, double alpha, INDArray X) {
        if (Nd4j.getExecutioner().getProfilingMode() == OpExecutioner.ProfilingMode.ALL)
            OpProfiler.getInstance().processBlasCall(false, X);

        if (X.isSparse()) {
            Nd4j.getSparseBlasWrapper().level1().scal(N, alpha, X);
        } else if (X.data().dataType() == DataType.DOUBLE)
            dscal(N, alpha, X, BlasBufferUtil.getBlasStride(X));
        else if (X.data().dataType() == DataType.FLOAT)
            sscal(N, (float) alpha, X, BlasBufferUtil.getBlasStride(X));
        else if (X.data().dataType() == DataType.HALF)
            Nd4j.getExecutioner().exec(new ScalarMultiplication(X, alpha));
    }



    /*
    * ===========================================================================
    * Prototypes for level 1 BLAS functions (complex are recast as routines)
    * ===========================================================================
    */
    protected abstract float sdsdot(long N, float alpha, INDArray X, int incX, INDArray Y, int incY);

    protected abstract double dsdot(long N, INDArray X, int incX, INDArray Y, int incY);

    protected abstract float hdot(long N, INDArray X, int incX, INDArray Y, int incY);

    protected abstract float hdot(long N, DataBuffer X, int offsetX, int incX, DataBuffer Y, int offsetY, int incY);

    protected abstract float sdot(long N, INDArray X, int incX, INDArray Y, int incY);

    protected abstract float sdot(long N, DataBuffer X, int offsetX, int incX, DataBuffer Y, int offsetY, int incY);

    protected abstract double ddot(long N, INDArray X, int incX, INDArray Y, int incY);

    protected abstract double ddot(long N, DataBuffer X, int offsetX, int incX, DataBuffer Y, int offsetY, int incY);


    /*
     * Functions having prefixes S D SC DZ
     */
    protected abstract float snrm2(long N, INDArray X, int incX);

    protected abstract float hasum(long N, INDArray X, int incX);

    protected abstract float hasum(long N, DataBuffer X, int offsetX, int incX);

    protected abstract float sasum(long N, INDArray X, int incX);

    protected abstract float sasum(long N, DataBuffer X, int offsetX, int incX);

    protected abstract double dnrm2(long N, INDArray X, int incX);

    protected abstract double dasum(long N, INDArray X, int incX);

    protected abstract double dasum(long N, DataBuffer X, int offsetX, int incX);


    /*
     * Functions having standard 4 prefixes (S D C Z)
     */
    protected abstract int isamax(long N, INDArray X, int incX);

    protected abstract int isamax(long N, DataBuffer X, int offsetX, int incX);

    protected abstract int idamax(long N, INDArray X, int incX);

    protected abstract int idamax(long N, DataBuffer X, int offsetX, int incX);
    /*
     * ===========================================================================
     * Prototypes for level 1 BLAS routines
     * ===========================================================================
     */

    /*
     * Routines with standard 4 prefixes (s, d, c, z)
     */
    protected abstract void sswap(long N, INDArray X, int incX, INDArray Y, int incY);

    protected abstract void scopy(long N, INDArray X, int incX, INDArray Y, int incY);

    protected abstract void scopy(long n, DataBuffer x, int offsetX, int incrX, DataBuffer y, int offsetY, int incrY);

    protected abstract void haxpy(long N, float alpha, INDArray X, int incX, INDArray Y, int incY);

    protected abstract void saxpy(long N, float alpha, INDArray X, int incX, INDArray Y, int incY);

    protected abstract void haxpy(long N, float alpha, DataBuffer x, int offsetX, int incrX, DataBuffer y, int offsetY,
                    int incrY);

    protected abstract void saxpy(long N, float alpha, DataBuffer x, int offsetX, int incrX, DataBuffer y, int offsetY,
                    int incrY);

    protected abstract void dswap(long N, INDArray X, int incX, INDArray Y, int incY);

    protected abstract void dcopy(long N, INDArray X, int incX, INDArray Y, int incY);

    protected abstract void dcopy(long n, DataBuffer x, int offsetX, int incrX, DataBuffer y, int offsetY, int incrY);

    protected abstract void daxpy(long N, double alpha, INDArray X, int incX, INDArray Y, int incY);

    protected abstract void daxpy(long N, double alpha, DataBuffer x, int offsetX, int incrX, DataBuffer y, int offsetY,
                    int incrY);


    /*
     * Routines with S and D prefix only
     */
    protected abstract void srotg(float a, float b, float c, float s);

    protected abstract void srotmg(float d1, float d2, float b1, float b2, INDArray P);

    protected abstract void srot(long N, INDArray X, int incX, INDArray Y, int incY, float c, float s);

    protected abstract void srotm(long N, INDArray X, int incX, INDArray Y, int incY, INDArray P);

    protected abstract void drotg(double a, double b, double c, double s);

    protected abstract void drotmg(double d1, double d2, double b1, double b2, INDArray P);

    protected abstract void drot(long N, INDArray X, int incX, INDArray Y, int incY, double c, double s);


    protected abstract void drotm(long N, INDArray X, int incX, INDArray Y, int incY, INDArray P);

    /*
         * Routines with S D C Z CS and ZD prefixes
         */
    protected abstract void sscal(long N, float alpha, INDArray X, int incX);

    protected abstract void dscal(long N, double alpha, INDArray X, int incX);

    @Override
    public boolean supportsDataBufferL1Ops() {
        return true;
    }

}

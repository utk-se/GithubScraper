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

package org.nd4j.linalg.convolution;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

public class OldConvolution {

    private OldConvolution() {}

    /**
     *
     * @param col
     * @param stride
     * @param padding
     * @param height
     * @param width
     * @return
     */
    public static INDArray col2im(INDArray col, int[] stride, int[] padding, int height, int width) {
        return col2im(col, stride[0], stride[1], padding[0], padding[1], height, width);
    }

    /**
     * Rearrange matrix
     * columns into blocks
    
     * @param col the column
     *            transposed image to convert
     * @param sy stride y
     * @param sx stride x
     * @param ph padding height
     * @param pw padding width
     * @param h height
     * @param w width
     * @return
     */
    public static INDArray col2im(INDArray col, int sy, int sx, int ph, int pw, int h, int w) {
        //number of images
        long n = col.size(0);
        //number of columns
        long c = col.size(1);
        //kernel height
        long kh = col.size(2);
        //kernel width
        long kw = col.size(3);
        //out height
        long outH = col.size(4);
        //out width
        long outW = col.size(5);

        INDArray img = Nd4j.create(n, c, h + 2 * ph + sy - 1, w + 2 * pw + sx - 1);
        for (int i = 0; i < kh; i++) {
            //iterate over the kernel rows
            long iLim = i + sy * outH;
            for (int j = 0; j < kw; j++) {
                //iterate over the kernel columns
                long jLim = j + sx * outW;
                INDArrayIndex[] indices = new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.all(),
                                NDArrayIndex.interval(i, sy, iLim), NDArrayIndex.interval(j, sx, jLim)};

                INDArray get = img.get(indices);

                INDArray colAdd = col.get(NDArrayIndex.all(), NDArrayIndex.all(), NDArrayIndex.point(i),
                                NDArrayIndex.point(j), NDArrayIndex.all(), NDArrayIndex.all());
                get.addi(colAdd);
                img.put(indices, get);

            }
        }

        //return the subset of the padded image relative to the height/width of the image and the padding width/height
        return img.get(NDArrayIndex.all(), NDArrayIndex.all(), NDArrayIndex.interval(ph, ph + h),
                        NDArrayIndex.interval(pw, pw + w));
    }

    /**
     *
     * @param img
     * @param kernel
     * @param stride
     * @param padding
     * @return
     */
    public static INDArray im2col(INDArray img, int[] kernel, int[] stride, int[] padding) {
        return im2col(img, kernel[0], kernel[1], stride[0], stride[1], padding[0], padding[1], 0, false);
    }

    /**
     * Implement column formatted images
     * @param img the image to process
     * @param kh the kernel height
     * @param kw the kernel width
     * @param sy the stride along y
     * @param sx the stride along x
     * @param ph the padding width
     * @param pw the padding height
     * @param pval the padding value
     * @param coverAll whether to cover the whole image or not
     * @return the column formatted image
     *
     */
    public static INDArray im2col(INDArray img, int kh, int kw, int sy, int sx, int ph, int pw, int pval,
                    boolean coverAll) {
        //number of images
        long n = img.size(0);
        //number of channels (depth)
        long c = img.size(1);
        //image height
        long h = img.size(2);
        //image width
        long w = img.size(3);
        long outHeight = outSize(h, kh, sy, ph, coverAll);
        long outWidth = outSize(w, kw, sx, pw, coverAll);
        INDArray padded = Nd4j.pad(img, new int[][] {{0, 0}, {0, 0}, {ph, ph + sy - 1}, {pw, pw + sx - 1}},
                        Nd4j.PadMode.CONSTANT);
        INDArray ret = Nd4j.create(n, c, kh, kw, outHeight, outWidth);
        for (int i = 0; i < kh; i++) {
            //offset for the row based on the stride and output height
            long iLim = i + sy * outHeight;
            for (int j = 0; j < kw; j++) {
                //offset for the column based on stride and output width
                long jLim = j + sx * outWidth;
                INDArray get = padded.get(NDArrayIndex.all(), NDArrayIndex.all(), NDArrayIndex.interval(i, sy, iLim),
                                NDArrayIndex.interval(j, sx, jLim));
                ret.put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.all(), NDArrayIndex.point(i),
                                NDArrayIndex.point(j), NDArrayIndex.all(), NDArrayIndex.all()}, get);
            }
        }
        return ret;
    }

    /**
     *
     * The out size for a convolution
     * @param size
     * @param k
     * @param s
     * @param p
     * @param coverAll
     * @return
     */
    public static int outSize(int size, int k, int s, int p, boolean coverAll) {
        if (coverAll)
            return (size + p * 2 - k + s - 1) / s + 1;
        else
            return (size + p * 2 - k) / s + 1;
    }

    public static long outSize(long size, long k, long s, long p, boolean coverAll) {
        if (coverAll)
            return (size + p * 2 - k + s - 1) / s + 1;
        else
            return (size + p * 2 - k) / s + 1;
    }

}

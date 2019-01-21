package org.deeplearning4j.nn.weights;

import lombok.EqualsAndHashCode;
import org.apache.commons.math3.util.FastMath;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

/**
 * Normal/Gaussian distribution, with mean 0 and standard deviation 1/sqrt(fanIn).
 *
 * @author Adam Gibson
 */
@EqualsAndHashCode
public class WeightInitNormal implements IWeightInit {

    @Override
    public INDArray init(double fanIn, double fanOut, long[] shape, char order, INDArray paramView) {
        Nd4j.randn(paramView).divi(FastMath.sqrt(fanIn));
        return paramView.reshape(order, shape);
    }
}

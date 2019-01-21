package org.deeplearning4j.nn.weights;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.shade.jackson.annotation.JsonAutoDetect;
import org.nd4j.shade.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

/**
 * Interface for weight initialization.
 *
 * @author Christian Skarby
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE)
public interface IWeightInit extends Serializable {

    // Use this in a default method when java 8 support is added
    char DEFAULT_WEIGHT_INIT_ORDER = WeightInitUtil.DEFAULT_WEIGHT_INIT_ORDER;

    /**
     * Initialize parameters in the given view. Double values are used for fanIn and fanOut as some layers
     * (convolution with stride) results in a non-integer number which may be truncated to zero in certain configurations
     * @param fanIn Number of input parameters
     * @param fanOut Number of output parameters
     * @param shape Desired shape of array (users shall assume paramView has this shape after method has finished)
     * @param order Order of array, e.g. Fortran ('f') or C ('c')
     * @param paramView View of parameters to initialize (and reshape)
     */
    INDArray init(double fanIn, double fanOut, long[] shape, char order, INDArray paramView);
}

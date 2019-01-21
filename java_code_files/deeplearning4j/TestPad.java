package org.nd4j.imports.TFGraphs;

import lombok.val;
import org.junit.Test;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.factory.Nd4j;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class TestPad {

    @Test
    public void testPad(){

        INDArray in = Nd4j.create(DataType.FLOAT, 1, 28, 28, 264);
        INDArray pad = Nd4j.createFromArray(new int[][]{{0,0},{0,1},{0,1},{0,0}});
        INDArray out = Nd4j.create(DataType.FLOAT, 1, 29, 29, 264);

        DynamicCustomOp op = DynamicCustomOp.builder("pad")
                .addInputs(in, pad)
                .addOutputs(out)
                .addIntegerArguments(0) //constant mode, with no constant specified
                .build();

        val outShape = Nd4j.getExecutioner().calculateOutputShape(op);
        assertEquals(1, outShape.size());
        assertArrayEquals(new long[]{1, 29, 29, 264}, outShape.get(0).getShape());

        Nd4j.getExecutioner().exec(op); //Crash here
    }
}

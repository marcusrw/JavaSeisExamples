package org.javaseis.examples.scratch;

import org.javaseis.array.ElementType;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.parallel.IParallelContext;

public class ComplexMath {

  /**
   * Return the magnitude of every element in a complex distributed array.
   * @param da - The Distributed Array
   */
  public static DistributedArray cabs(DistributedArray da) {
    //Make a new DA that's the same shape as the input...
    IParallelContext pc = da.getParallelContext();
    ElementType type = da.getElementType();
    int[] lengths = da.getShape();
    DistributedArray absDA = new DistributedArray(pc,type,lengths);
    //...because this didn't work for whatever reason.
    //DistributedArray absDA = new DistributedArray(da);
    //absDA.setElementCount(1);
    int direction = 1; //forward
    int scope = 0; //iterate over samples
    DistributedArrayPositionIterator dapi =
        new DistributedArrayPositionIterator(da,direction,scope);
    float[] inputBuffer = new float[2]; //complex numbers
    float outputBuffer = 0;
    while (dapi.hasNext()) {
      int[] position = dapi.next();
      da.getSample(inputBuffer, position);
      outputBuffer = (float)Math.hypot(inputBuffer[0], inputBuffer[1]);
      absDA.putSample(outputBuffer, position);
    }
    return absDA;
  }

}

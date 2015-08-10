package org.javaseis.examples.scratch;

import java.util.Arrays;
import java.util.logging.Logger;

import org.javaseis.array.ElementType;
import org.junit.Assert;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;

public class Utilities {
  
  private static final Logger LOGGER =
      Logger.getLogger(Utilities.class.getName());

  private Utilities() {
   //Class can't be instantiated.
  }
  
  //TODO replace asserts appropriately

  //Test two float distributed Arrays are equal
  public void testDAEquals(DistributedArray a, DistributedArray b) {
    if (!a.getElementType().equals(ElementType.FLOAT)) {
      throw new IllegalArgumentException("The first distributed array "
          + "does not contain floats.  This method only works for "
          + "distributed arrays containing floats");
    }
    if (!b.getElementType().equals(ElementType.FLOAT)) {
      throw new IllegalArgumentException("The second distributed array "
          + "does not contain floats.  This method only works for "
          + "distributed arrays containing floats");      
    }
    Assert.assertArrayEquals("Distributed Arrays are not the same shape",
        a.getShape(), b.getShape());

    int[] position = new int[a.getShape().length];
    int direction = 1;
    int scope = 0;
    float floateps = 1e-7F;
    DistributedArrayPositionIterator dapi;
    dapi = new DistributedArrayPositionIterator(a, position, direction, scope);

    float[] abuff = new float[a.getElementCount()];
    float[] bbuff = new float[b.getElementCount()];
    while (dapi.hasNext()) {
      position = dapi.next();
      a.getSample(abuff, position);
      b.getSample(bbuff, position);
      Assert.assertArrayEquals("Distributed Arrays differ at position: "
          + Arrays.toString(position), abuff, bbuff, floateps);
    }
    LOGGER.info("Distributed Arrays match to error " + floateps);
  }
}

package org.javaseis.examples.scratch;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.parallel.UniprocessorContext;
import beta.javaseis.parallel.IParallelContext;

public class JTestSepFfts {

  private static final IParallelContext pc = new UniprocessorContext();
  private static final int MAXT = 301,MAXX = 201,MAXY = 101;
  private int[] inputShape;
  private float[] pad = {0,0,0};
  private final int[] TYPE = {-1,1,1};

  private PhaseShiftFFT3D control;
  private PhaseShiftFFT3D intermediate;
  private PhaseShiftFFT3D test;

  @Test
  public void testRoundTripSize() {
    int tSize = MAXX;
    int xSize = MAXY;
    int ySize = 1;

    while (tSize <= MAXT) {
      inputShape = new int[] {tSize,xSize,ySize};
      test = new PhaseShiftFFT3D(pc,inputShape,pad,TYPE);
      control = new PhaseShiftFFT3D(new DistributedArray(test.getArray()));
      test.forwardTemporal();
      intermediate = new PhaseShiftFFT3D(new DistributedArray(test.getArray()));
      int[] intShape = intermediate.getShape();
      test.forwardSpatial2D();


      System.out.println("Input Shape: "
          + Arrays.toString(inputShape)
          + " Intermediate Shape: "
          + Arrays.toString(intShape)
          + " FFT3d Shape: "
          + Arrays.toString(test.getShape()));
      test.inverseSpatial2D();
      Assert.assertArrayEquals(
          "Spatial trip size test fails for array size: "
              + Arrays.toString(new int[] {tSize,xSize,ySize})
              ,test.getShape(),intermediate.getShape());

      test.inverseTemporal();
      Assert.assertArrayEquals(
          "Round trip size test fails for array size: "
              + Arrays.toString(new int[] {tSize,xSize,ySize})
              ,test.getShape(),control.getShape());
      tSize++;
      xSize++;
      ySize++;
    }
  }

  @Test
  public void testRoundError() {
    int tSize = MAXX;
    int xSize = MAXY;
    int ySize = 1;

    while (tSize <= MAXT) {
      inputShape = new int[] {tSize,xSize,ySize};
      test = new PhaseShiftFFT3D(pc,inputShape,pad,TYPE);
      generateRandomData(test.getArray());
      control = new PhaseShiftFFT3D(new DistributedArray(test.getArray()));
      test.forwardTemporal();
      intermediate = new PhaseShiftFFT3D(new DistributedArray(test.getArray()));
      test.forwardSpatial2D();
      test.inverseSpatial2D();
      float maxErrorIntermediate =
          computeMaxError(test.getArray(),intermediate.getArray());
      test.inverseTemporal();
      float maxErrorTotal = 
          computeMaxError(test.getArray(),control.getArray());
      System.out.println("Size: "
          + Arrays.toString(new int[] {tSize,xSize,ySize})
          + " Max Intermediate Error: "
          + maxErrorIntermediate
          + " Max Total Error: "
          + maxErrorTotal);
      tSize++;
      xSize++;
      ySize++;
    }
  }

  private float computeMaxError(DistributedArray da1,DistributedArray da2) {
    if (!Arrays.equals(da1.getShape(),da2.getShape())) {
      System.out.println(Arrays.toString(da1.getShape()));
      System.out.println(Arrays.toString(da2.getShape()));
      throw new IllegalArgumentException("These two distributed arrays are"
          + " not comparable because they are different sizes.");
    }
    if (da1.getElementCount() != da2.getElementCount()) {
      System.out.println(da1.getElementCount());
      System.out.println(da2.getElementCount());
      throw new IllegalArgumentException("These two distributed arrays are"
          + " not comparable because they have different element counts.");
    }

    int[] position;
    float difference;
    float maxError = 0;
    float[] buffer1 = new float[da1.getElementCount()];
    float[] buffer2 = new float[da2.getElementCount()];
    DistributedArrayPositionIterator dapi = initializeIterator(da1); 
    while (dapi.hasNext()) {
      position = dapi.next();
      da1.getSample(buffer1,position);
      da2.getSample(buffer2,position);
      difference = 0;
      for (int k = 0 ; k < buffer1.length ; k++) {
        difference += (buffer1[k] - buffer2[k])*(buffer1[k] - buffer2[k]);
      }
      maxError = Math.max(maxError,(float) Math.sqrt(difference));
    }
    return maxError;
  }

  private void generateRandomData(DistributedArray da) {
    int[] position;
    float buffer;
    DistributedArrayPositionIterator dapi = initializeIterator(da);
    while (dapi.hasNext()) {
      position = dapi.next();
      buffer = (float) (Math.random() - 0.5);
      da.putSample(buffer, position);
    }
  }

  private DistributedArrayPositionIterator initializeIterator(
      DistributedArray da) {
    int[] position = {0,0,0};
    int direction = 1; //forward
    int scope = 0; //samples
    DistributedArrayPositionIterator dapi =
        new DistributedArrayPositionIterator(
            da,position,direction,scope);
    return dapi;
  }


}

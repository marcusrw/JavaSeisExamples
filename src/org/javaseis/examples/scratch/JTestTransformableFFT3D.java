package org.javaseis.examples.scratch;

import java.util.Arrays;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import org.javaseis.imaging.PhaseShiftFFT3D;

import beta.javaseis.distributed.Decomposition;
import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;

public class JTestTransformableFFT3D {

  private static final int VOLUME_NUMBER_OF_DIMENSIONS = 3;

  public JTestTransformableFFT3D() {
  }

  IParallelContext pc;
  int[] shape;
  float[] pad;
  int[] isign;

  DistributedArray da;
  Transformable3D test;

  @Before
  public void makeDefaultArguments() {
    pc = new UniprocessorContext();
    shape = new int[] { 7, 8, 50 };
    pad = new float[] { 30, 10, 0 };
    isign = new int[] { 1, -1, 1 };

    // for the distributed array
    int[] decompTypes = new int[shape.length];
    Arrays.fill(decompTypes, Decomposition.BLOCK);
    da = new DistributedArray(pc, float.class, 3, 1, shape, decompTypes);
  }

  @Test
  public void testISignReadsIntoFFTsigns() {
    test = new PhaseShiftFFT3D(pc, shape, pad, isign);
    String failureMessage = "Since the constructor should truncate the "
        + "fftsigns to 3 elements, this test requires a 3 element input "
        + "array to work.";
    assertEquals(failureMessage,3,isign.length);
    assertArrayEquals("FFT signs aren't reading in properly.", isign,
        test.getFftSigns());
  }

  @Test
  public void testConstructorTruncatesLongerISign() {
    isign = new int[] {1,0,-1,0,1,1,1,1};
    test = new PhaseShiftFFT3D(pc, shape, pad, isign);
    assertArrayEquals("FFT signs aren't truncating.",
        Arrays.copyOf(isign,VOLUME_NUMBER_OF_DIMENSIONS),test.getFftSigns());
  }

  //@Test //Not part of the interface.  Test frequency outputs instead.
  public void testSetSampleRatesTruncatesDimensions() {
    test = new PhaseShiftFFT3D(pc, shape, pad, isign);
    double[] sampleRates = {0.002,20,30,1,1,-500};
    test.setTXYSampleRates(sampleRates);
    /*
    assertArrayEquals("Sample rate array is not truncating.",
        Arrays.copyOf(sampleRates,VOLUME_NUMBER_OF_DIMENSIONS),
        test.getTXYSampleRates());
     */
  }

  @Test
  public void getShapeReturnsInputShape() {
    test = new PhaseShiftFFT3D(pc, shape, pad, isign);
    assertArrayEquals("Shape isn't reading in properly", shape, test.getShape());
  }

  @Test
  public void iSignDefaultIsNegativePositivePositive() {
    test = new PhaseShiftFFT3D(pc, shape, pad);
    assertArrayEquals("The default seismic FFT signs are set wrong.",
        new int[] { -1, 1, 1 }, test.getFftSigns());
  }

  // Note: As of writing, the FFT padding is hidden by the interface,
  // so it can't be tested directly. You can still test for
  // your implementation if you have it expose what the padding is.
}

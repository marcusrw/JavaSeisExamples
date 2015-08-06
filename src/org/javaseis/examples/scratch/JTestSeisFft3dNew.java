package org.javaseis.examples.scratch;


import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Test;

import beta.javaseis.fft.IFFT;
import beta.javaseis.distributed.Decomposition;
import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.ParallelContextThread;
import beta.javaseis.plugin.JssiRegistry;

/**
 * Tests for the SeisFft3dNew class.  Round-trip forward/inverse datasets are
 * compared with unaltered control datasets, flagging differences that exceed
 * a tolerance.
 * <p>
 * Initially the SeisFft3dNew class was being tested with DC input only, which
 * could in theory miss an erroneous array-element rearrangement (since all
 * elements had the same value, it would not be possible to verify that the
 * elements were returned in the desired order).  The RAMP test eliminates this
 * ambiguity.  Further, the original testing scheme only tested the integrity of
 * a single array element that was returned, whereas the current scheme compares
 * every element in the returned array to its corresponding "control array"
 * element.
 * <p>
 * Finally, when it comes to these SeisFft3dNew tests, it's particularly easy to
 * mess up the test setup.  For example, it is important to make the actual data
 * load consistent with the constructor parameters, i.e. don't load data past
 * len[2]-1, len[1]-1, or len[0]-1.  If you just load based on 'range' and do
 * not restrict based on len's within the range you will have subtle problems
 * that will prevent you from obtaining a successful comparison.
 */
public class JTestSeisFft3dNew {
  //1.e-5 is too small and will fail
  private static final double FORWARD_INVERSE_TOLERANCE = 1.e-4;
  private static final double INVERSE_FORWARD_TOLERANCE = 1.e-6;

  private static final Logger LOGGER =
      Logger.getLogger(JTestSeisFft3dNew.class.getName());

  private static final int TEST_DC   = 101;
  private static final int TEST_RAMP = 102;

  private static final boolean CHATTY = false;
  private static final boolean VERBOSE = false;

  private static final int NFMAX = 200;
  private static final int NXMAX =  75;
  private static final int NYMAX =  39;
  private static final int NTESTS = 20;

  public static void main(String[] args) {
    JTestSeisFft3dNew test = new JTestSeisFft3dNew();

    // Tests the conversion from sample indices to actual frequency units.
    test.testCoordinates();

    // Loop doing tests with random dimensions.
    test.testAll();
  }

  @Test
  /**
   * Tests the functionality that converts from array index to real-world units.
   */
  public void testCoordinates() {
    IParallelContext pc = new ParallelContextThread();

    int[] len = new int[] { 70, 100, 200 };
    float[] pad = new float[] { 0f, 0f, 0f };
    double[] sampleRates = new double[] { .004, 10, 10 };  // sec, ft or m, ft or m

    // Hack to make sure SeisFftJtk is used, so that the sizes (and therefore kx,ky,f) are as expected.
    // SeisFftJtk uses factors as large as 13, whereas SeisFftJTransforms uses 235(7?)
    System.setProperty("JavaSeisServices", "IFFT,IMultiArray");
    System.setProperty("IFFT", "beta.javaseis.fft.SeisFftJtk,beta.javaseis.fft.SeisFftJTransform");
    System.setProperty("IMultiArray", "beta.javaseis.array.MultiArray");

    try {
      LOGGER.info(JssiRegistry.getDefaultImplementation(IFFT.class).toString());
    } catch (InstantiationException e) {
      LOGGER.log(Level.INFO,e.getMessage(),e);
    }

    PhaseShiftFFT3D fft3d = new PhaseShiftFFT3D(pc, len, pad);
    fft3d.debugPrint("BEFORE:");
    fft3d.setTXYSampleRates(sampleRates);
    fft3d.forward();
    fft3d.debugPrint("AFTER:");
    int[] shape = fft3d.getShape();

    int[] position = new int[3];
    double[] buf = new double[3];

    // Positions refer to position in frequency domain (forward transform preceeded).
    position[0] = 1;
    position[1] = 1;
    position[2] = shape[2] - 1;
    fft3d.getKyKxFCoordinatesForPosition(position, buf);
    assertEquals(.000481, buf[0], 1.e-6);
    assertEquals(.000962, buf[1], 1.e-6);
    assertEquals(125.,    buf[2], 1.e-5);  // Nyquist
  }

  @Test
  /**
   * Calls the individual round-trip tests.
   */
  public void testAll() {
    if(CHATTY) 
      LOGGER.info("");

    for(int itest=0; itest<NTESTS; itest++) {

      // Get some dimensions at random.
      int nf = (int)((NFMAX-4)*Math.random() + 4);
      int nx = (int)((NXMAX-2)*Math.random() + 2);
      int ny = (int)((NYMAX-2)*Math.random() + 2);
      if(CHATTY)
        LOGGER.info(String.format(
            "SeisFft3dNewTest:  nf=%4d,  nx=%4d,  ny=%4d%n", nf, nx, ny));

      // Run forward round-trip tests.
      doRoundTripForward(TEST_DC, nf, nx, ny);
      doRoundTripForward(TEST_RAMP, nf, nx, ny);

      // Run the "inverse-oriented constructor" round-trip tests also.
      doRoundTripInverse(nf, nx, ny);
    }
  }

  /**
   * Pauses program execution until user hits ENTER (so be sure to comment out
   * occurrences after you're finished debugging).
   */
  /*
  private static void debugPause() {
    try { System.in.read(); } catch(IOException e) { }
  }
   */

  /**
   * Make 2 copies of input data.  Keep one copy (da1) as control copy, and send
   * the other one through round-trip forward/inverse transform sequence.  Then
   * compare every resulting sample to the control sample and report if/when the
   * tolerance is exceeded.
   *
   * @param testType can be TEST_DC or TEST_RAMP
   */
  public void doRoundTripForward(int testType, int nf, int nx, int ny) {

    //IParallelContext pc = new MPIContext();
    IParallelContext pc = new ParallelContextThread();

    float[] pad = { 0.f, 0.f, 0.f };
    int[] len = new int[]{ nf, nx, ny };

    if(VERBOSE) System.out.printf("%n***** begin test w/testType, nf, nx, ny = "
        + testType + " %d %d %d *****%n", nf, nx, ny);

    PhaseShiftFFT3D f3d0 = new PhaseShiftFFT3D( pc, len, pad );
    PhaseShiftFFT3D f3d1 = new PhaseShiftFFT3D( pc, len, pad );
    DistributedArray da0 = f3d0.getArray();
    DistributedArray da1 = f3d1.getArray();

    int[] fftLengths = f3d0.getFftLengths();
    //int[] arrayLengths = f3d0.getShape();

    // Print out dimensions.
    if(VERBOSE) {
      pc.masterPrint(String.format("%nInitial transform fft lengths = %d %d %d",
          fftLengths[0], fftLengths[1], fftLengths[2]) );
      pc.masterPrint(String.format("Initial transform array lengths = %d %d %d",
          da0.getLength(0), da0.getLength(1), da0.getGlobalLength(2)) );
    }

    int[] position = { 0, 0, 0 };
    int j, k, kmax, ia;

    kmax = da0.getLocalLength(2);
    if(CHATTY) pc.serialPrint("local length for task " + pc.rank() + " = " + kmax );

    // Loop: Load the data into a0 (working) and a1 (control copy).
    for (k=0; k<kmax; k++) {
      position[2] = da0.localToGlobal( 2, k );
      if (position[2] >= len[2]) continue;      // use '>=' NOT '>' here!!!
      for (j=0; j<len[1]; j++ ) {
        position[1] = j;
        float ramp = 0;
        for (int i=0; i<len[0]; i++) {
          switch(testType) {
          case TEST_DC:
            da0.putSample(1f, position);
            da1.putSample(1f, position);
            break;
          case TEST_RAMP:
            da0.putSample(ramp, position);
            da1.putSample(ramp, position);
            break;
          }
          ramp++;
        }
      }
    }

    // Do forward transform.
    if(CHATTY) pc.masterPrint( "Apply Forward 3D FFT ... ");
    f3d0.forward();
    int l = len[0]*len[1]*len[2];
    if(CHATTY) pc.masterPrint(String.format("Ky Kx F domain array lengths"
        + "= %d %d %d%n",
        da0.getLength(0), da0.getLength(1), da0.getGlobalLength(2)) );

    // Mark potential failure after forward transform.
    if(CHATTY && testType == TEST_DC) {
      Arrays.fill(position, 0);
      float val = da0.getFloat(position);
      pc.masterPrint( "a0[0] = " + val + " Expected value " + l );
      assertEquals("SeisFft3dNew.forward failure", l, val, 1e-5*l);
    }

    // Do inverse transform.
    if(CHATTY) pc.masterPrint( "Apply Inverse 3D FFT ... ");
    f3d0.inverse();

    if(CHATTY) pc.masterPrint(String.format("T X Y domain array lengths = %d %d %d%n",
        da0.getLength(0), da0.getLength(1), da0.getGlobalLength(2)) );

    if(CHATTY && testType == TEST_DC) {
      Arrays.fill(position, 0);
      float val = da0.getFloat(position);
      pc.masterPrint( "a0[0] = " + val + " Expected value 1.0"  );
      assertEquals("SeisFft3dNew.inverse failure", 1, val, 1e-5);
    }

    // Loop: The acid test -- compare the control copy to the round-trip copy.
    kmax = da0.getLocalLength(2);
    for (k=0; k<kmax; k++) {
      position[2] = da0.localToGlobal( 2, k );
      if (position[2] >= len[2]) continue;      // use '>=' NOT '>' here!!!
      for (j=0; j<len[1]; j++ ) {
        position[1] = j;
        for (int i=0; i<len[0]; i++) {
          float a0 = da0.getFloat(position);
          float a1 = da1.getFloat(position);
          if(VERBOSE) {
            System.out.printf("pos[2], pos[1], ia = %4d %4d %4d%n",
                position[2], position[1], i);
            System.out.printf("  control, result = %10.5f %10.5f%n", a1, a0);
          }
          // assertEquals message below lacks detail -- add some more printout
          if(Math.abs(a1 - a0) > FORWARD_INVERSE_TOLERANCE) {
            System.out.println("%nTOLERANCE exceeded !!!");
            System.out.printf("  testType = %d%n", testType);
            System.out.printf("  nf, nx, ny = %4d %4d %4d%n", nf, nx, ny);
            System.out.printf("  values being compared were %f and %f%n", a1, a0);
            System.out.printf("  position was pos[2], pos[1], ia = %4d %4d %4d%n",
                position[2], position[1], i);
            //debugPause();
          }
          assertEquals(a1, a0, FORWARD_INVERSE_TOLERANCE);
        }
        if(VERBOSE) System.out.println();
      }
    }

    if(CHATTY) pc.masterPrint( f3d0.getClass().toString() + " *** SUCCESS ***");
    pc.finish();
  }

  /**
   * Most of the SeisFft3dNew constructors take untransformed data as input, i.e.
   * it's typically TXY data.  This test is designed to test the SeisFft3dNew
   * constructor that has the data starting out in the KyKxF domain.
   */
  public void doRoundTripInverse(int nf, int nx, int ny) {

    IParallelContext pc = new ParallelContextThread();

    // Get FFT lengths and create an appropriate sized DistributedArray.
    int[] len = PhaseShiftFFT3D.getTransformShape(
        new int[] { nf, nx, ny }, new float[] {0f,0f,0f}, pc );
    int[] type =
        new int[] { Decomposition.BLOCK, Decomposition.BLOCK, Decomposition.BLOCK };
    DistributedArray daCopy = new DistributedArray(pc, float.class, 3, 2, len, type);
    DistributedArray da = new DistributedArray(pc, float.class, 3, 2, len, type);
    PhaseShiftFFT3D fft3d = new PhaseShiftFFT3D(da,new int[] {-1,1,1});

    // Set the shape to the transform domain
    int[] fftlen = fft3d.getFftLengths();
    int nft = 1 + fftlen[0]/2;
    int nkx = fftlen[1];
    int nky = fftlen[2];
    int[] shape = new int[] { nky, nkx, nft };
    da.setShape(shape);
    daCopy.setShape(shape);

    //fft3d.debugPrint("TESTINVERSE");
    int[] position = new int[3];
    float[] buf, bufCopy;

    // Put some data into our KyKxF-ordered DistributedArray.
    //System.out.printf("shape = %d %d %d%n", shape[0], shape[1], shape[2]);
    //System.out.printf("fftLens = %d %d %d%n", fftLen[0], fftLen[1], fftLen[2]);
    buf = new float[2*shape[0]];
    for(int k=0; k<shape[2]; k++) {
      position[2] = k;
      for(int j=0; j<shape[1]; j++) {
        position[1] = j;
        da.getTrace(buf, position);
        for(int i=0; i<shape[0]; i++) {
          buf[2*i] = 1f;  // put value of 1f in every real slot
          //buf[2*i+1] = 1f;  // put value of 1f in every imag slot TODO: why does this fail?
        }
        //if(k == 0 && j == 0) buf[0] = 1f;
        da.putTrace(buf, position);
        daCopy.putTrace(buf, position);
      }
    }

    // Do inverse fft.
    fft3d.inverse();

    // Do forward fft.
    fft3d.forward();

    // Compare to copy of original input.
    double diffRealMax = 0.0;
    double diffImagMax = 0.0;

    shape = da.getShape();
    buf = new float[2*shape[0]];
    bufCopy = new float[2*shape[0]];
    for(int k=0; k<shape[2]; k++) {
      position[2] = k;
      for(int j=0; j<shape[1]; j++) {
        position[1] = j;
        daCopy.getTrace(bufCopy, position);
        da.getTrace(buf, position);
        for(int i=0; i<2*shape[0]; i+=2) {
          double diffReal = Math.abs(bufCopy[i] - buf[i]);
          double diffImag = Math.abs(bufCopy[i+1] - buf[i+1]);
          if(diffReal > diffRealMax) diffRealMax = diffReal;
          if(diffImag > diffImagMax) diffImagMax = diffImag;
          if(diffReal > INVERSE_FORWARD_TOLERANCE ||
              diffImag > INVERSE_FORWARD_TOLERANCE  ) {
            System.out.printf("k=%5d j=%5d%n", k, j);
            for(int n=0; n<2*shape[0]; n++) {
              System.out.printf("%10.5f %10.5f%n", bufCopy[n], buf[n]);
            }
          }
          assertEquals(bufCopy[i], buf[i], INVERSE_FORWARD_TOLERANCE);
          assertEquals(bufCopy[i+1], buf[i+1], INVERSE_FORWARD_TOLERANCE);
        }
      }
    }
    //System.out.println(
    //    "max diff's after round-trip = " + diffRealMax + ", " + diffImagMax);
  }
}

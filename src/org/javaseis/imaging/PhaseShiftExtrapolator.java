package org.javaseis.imaging;

import java.util.logging.Logger;

import org.javaseis.util.IntervalTimer;
import org.javaseis.velocity.IVelocityModel;
import org.junit.Assert;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;

public class PhaseShiftExtrapolator {

  private static final Logger LOGGER = 
      Logger.getLogger(PhaseShiftExtrapolator.class.getName());

  private static final float EVANESCENT_EDGE = 0F;

  //Iterator flags
  private static final int SAMPLE_SCOPE = 0;
  private static final int ITERATE_FORWARD = 1;

  //Extrapolation directions
  private final WavefieldDirection direction;
  private static final int EXTRAP_FORWARD = 1;
  private static final int EXTRAP_REVERSE = -1;

  PhaseShiftFFT3D wavefield;
  IVelocityModel vModel;
  double currentDepth;

  IntervalTimer extrapTime;
  IntervalTimer transformTime;

  private double fMin = -Double.MAX_VALUE;
  private double fMax = Double.MAX_VALUE;

  public static enum WavefieldDirection {
    UPGOING,
    DOWNGOING,
  }

  public PhaseShiftExtrapolator(PhaseShiftFFT3D wavefield,
      double initialDepth) {
    this.wavefield = wavefield;
    this.currentDepth = initialDepth;
    this.extrapTime = new IntervalTimer();
    this.transformTime = new IntervalTimer();
    this.direction = null; //can't use the autoextrap without this.
  }

  public PhaseShiftExtrapolator(PhaseShiftFFT3D wavefield,
      double initialDepth,WavefieldDirection direction) {
    this.wavefield = wavefield;
    this.currentDepth = initialDepth;
    this.direction = direction;
    this.extrapTime = new IntervalTimer();
    this.transformTime = new IntervalTimer();
  }

  public void setBandLimit(double fMin, double fMax) {
    this.fMin = fMin;
    this.fMax = fMax;
  }

  public void PhaseShiftTo(double newDepth,double velocity) {
    int extrapDir = determineExtrapDirection(newDepth);
    //TODO refactor so we don't need the zindx or the fMin
    phaseShift(velocity,Math.abs(newDepth-currentDepth),fMax,extrapDir);
    currentDepth = newDepth;
  }

  public void SplitStepTo(double newDepth,double[][] velocitySlice,
      double averageVelocity) {
    int extrapDir = determineExtrapDirection(newDepth);
    phaseShift(averageVelocity,Math.abs(newDepth-currentDepth),fMax,extrapDir);
    thinLens(velocitySlice, averageVelocity, Math.abs(newDepth - currentDepth), extrapDir);
  }

  private int determineExtrapDirection(double newDepth) {

    if (direction.equals(WavefieldDirection.DOWNGOING) &&
        (newDepth > currentDepth)) {
      return EXTRAP_FORWARD;
    }
    if (direction.equals(WavefieldDirection.UPGOING) &&
        (newDepth > currentDepth)) {
      return EXTRAP_REVERSE;
    }
    if (direction.equals(WavefieldDirection.UPGOING) &&
        (newDepth < currentDepth)) {
      return EXTRAP_FORWARD;
    }
    if (direction.equals(WavefieldDirection.DOWNGOING) &&
        (newDepth < currentDepth)) {
      return EXTRAP_REVERSE;
    }
    throw new UnsupportedOperationException("Zero distance extrapolation "
        + "has yet to be implemented.");
  }

  @Deprecated
  public void forwardExtrapolate(float velocity,double delz,
      int zindx) {
    phaseShift(velocity,delz,fMax,EXTRAP_FORWARD);
  }

  @Deprecated
  public void reverseExtrapolate(float velocity,double delz,
      int zindx) {
    phaseShift(velocity,delz,fMax,EXTRAP_REVERSE);
  }

  public void phaseShift(double velocity,double delz,
      double fMax,int direction) {

    //verify FXY domain
    if (getDomain() != "KyKxF") {
      throw new IllegalStateException("You can only apply the phase shift "
          + "in the KyKxF domain");
    }
    extrapTime.start();

    int arrayNumDimensions = wavefield.getShape().length;

    int[] position = new int[arrayNumDimensions];
    double[] coords = new double[arrayNumDimensions];

    DistributedArray wavefieldDA = wavefield.getArray();

    float[] sampleIn = new float[wavefieldDA.getElementCount()];
    float[] sampleOut = new float[wavefieldDA.getElementCount()];

    DistributedArrayPositionIterator dapi =
        new DistributedArrayPositionIterator(
            wavefieldDA,position,ITERATE_FORWARD,SAMPLE_SCOPE);

    while (dapi.hasNext()) {
      position = dapi.next();
      wavefieldDA.getSample(sampleIn, position);
      wavefield.getKyKxFCoordinatesForPosition(position, coords);
      double kY = coords[0];
      double kX = coords[1];
      double frequency = coords[2];

      //skip if we're over the data threshold.
      //TODO put this back when you have a proper band limited source.
      //if (frequency > fMax) continue;

      double Kz2 = (frequency/velocity)*(frequency/velocity) - kY*kY - kX*kX;
      double exponent = 0;
      if (Kz2 > EVANESCENT_EDGE) {
        exponent = (-2*Math.PI*delz * Math.sqrt(Kz2));
        //TODO use complex math methods instead of doing it manually
        sampleOut[0] = (float) (sampleIn[0]*Math.cos(direction*exponent)
            - sampleIn[1]*Math.sin(direction*exponent));
        sampleOut[1] = (float) (sampleIn[1]*Math.cos(direction*exponent)
            + sampleIn[0]*Math.sin(direction*exponent));

      } else {
        //evanescent region
        if (delz != 0) {
          exponent = 2*Math.PI*Math.abs(delz)*Math.sqrt(Math.abs(Kz2));
        } else {
          exponent = 2*Math.PI*4*Math.sqrt(Math.abs(Kz2));
        }
        sampleOut[0] = (float) (sampleIn[0]*Math.exp(-exponent));
        sampleOut[1] = (float) (sampleIn[1]*Math.exp(-exponent));
      }
      wavefieldDA.putSample(sampleOut, position);
    }
    extrapTime.stop();
  }

  public void transformFromSpaceToWavenumber() {
    transformTime.start();
    wavefield.forwardSpatial2D();
    transformTime.stop();
  }

  public void transformFromWavenumberToSpace() {
    transformTime.start();
    wavefield.inverseSpatial2D();
    transformTime.stop();
  }

  public void transformFromTimeToFrequency() {
    transformTime.start();
    wavefield.forwardTemporal();
    transformTime.stop();
  }

  public void transformFromFrequencyToTime() {
    transformTime.start();
    wavefield.inverseTemporal();
    transformTime.stop();
  }

  public double getTransformTime() {
    return transformTime.total();
  }

  public double getExtrapolationTime() {
    return extrapTime.total();
  }

  //This should be an enum in the FFT object
  public String getDomain() {
    if (wavefield.isTimeTransformed() && wavefield.isSpaceTransformed())
      return "KyKxF";
    if (!wavefield.isTimeTransformed() && wavefield.isSpaceTransformed())
      return "KyKxT";
    if (wavefield.isTimeTransformed() && !wavefield.isSpaceTransformed())
      return "FXY";
    if (!wavefield.isTimeTransformed() && !wavefield.isSpaceTransformed())
      return "TXY";
    throw new NullPointerException("This shouldn't happen");
  }

  public void thinLens(double[][] velocitySlice,
      double avgVelocity, double delz, int direction) {

    extrapTime.start();
    //verify FXY domain
    if (getDomain() != "FXY") {
      throw new IllegalStateException("You can only apply the thinLens "
          + "term in the FXY domain");
    }

    //compute the time shift
    double[][] timeShift = computeTimeShift(velocitySlice, avgVelocity, delz);

    //Iterate over positions
    int arrayNumDimensions = wavefield.getShape().length;

    int[] position = new int[arrayNumDimensions];
    float[] sampleIn = new float[arrayNumDimensions];
    float[] sampleOut = new float[arrayNumDimensions];
    DistributedArray wavefieldDA = wavefield.getArray();
    DistributedArrayPositionIterator dapi =
        new DistributedArrayPositionIterator(
            wavefieldDA,position,ITERATE_FORWARD,SAMPLE_SCOPE);

    while (dapi.hasNext()) {
      position = dapi.next();
      wavefieldDA.getSample(sampleIn, position);
      double frequency = wavefield.getFrequencyForPosition(position);
      //System.out.println("Position: " + Arrays.toString(position));
      //System.out.println("Frequency: " + frequency);
      int tracePosition = position[1];
      int framePosition = position[2];

      //skip if we're over the data threshold.
      //TODO put this back when you have a proper band limited source.
      //if (frequency > fMax) continue;

      double exponent = 2*Math.PI*frequency*
          timeShift[tracePosition][framePosition];

      //TODO use complex math methods instead of doing it manually
      sampleOut[0] = (float) (sampleIn[0]*Math.cos(direction*exponent)
          - sampleIn[1]*Math.sin(direction*exponent));
      sampleOut[1] = (float) (sampleIn[1]*Math.cos(direction*exponent)
          + sampleIn[0]*Math.sin(direction*exponent));

      wavefieldDA.putSample(sampleOut, position);
    }
    extrapTime.stop();
  }

  private double[][] computeTimeShift(double[][] velocitySlice, double avgVelocity,
      double delz) {
    int traceSize = velocitySlice.length;
    int frameSize = velocitySlice[0].length;
    double[][] timeShift = new double[traceSize][frameSize];
    for (int trace = 0 ; trace < traceSize ; trace++) {
      for (int frame = 0 ; frame < frameSize ; frame++) {
        timeShift[trace][frame] = 
            delz/velocitySlice[trace][frame] - delz/avgVelocity;
      }
    }
    return timeShift;
  }

  public void reverseThinLens(double[][] windowedSlice, double velocity,
      double delz) {
    thinLens(windowedSlice,velocity,delz,EXTRAP_REVERSE);
  }

  public void forwardThinLens(double[][] windowedSlice, double velocity,
      double delz) {
    thinLens(windowedSlice,velocity,delz,EXTRAP_FORWARD);
  }

}

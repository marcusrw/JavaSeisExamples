package org.javaseis.imaging;

import java.util.Arrays;
import java.util.logging.Logger;

import org.javaseis.util.IntervalTimer;
import org.javaseis.util.SeisException;

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
  private static final int EXTRAP_FORWARD = 1;
  private static final int EXTRAP_REVERSE = -1;

  PhaseShiftFFT3D wavefield;
  double depth;

  IntervalTimer extrapTime;
  IntervalTimer transformTime;

  public PhaseShiftExtrapolator(PhaseShiftFFT3D wavefield,double initialDepth) {
    this.wavefield = wavefield;
    this.depth = initialDepth;
    this.extrapTime = new IntervalTimer();
    this.transformTime = new IntervalTimer();
  }

  //TODO what if I want both extrapolators to share timers?
  //maybe a constructor with a timer as input.

  public void forwardExtrapolate(float velocity,double delz,
      int zindx,double fMax) {
    phaseShift(velocity,delz,zindx,fMax,EXTRAP_FORWARD);
  }

  public void reverseExtrapolate(float velocity,double delz,
      int zindx,double fMax) {
    phaseShift(velocity,delz,zindx,fMax,EXTRAP_REVERSE);
  }

  public void phaseShift(float velocity,double delz,
      int zindx,double fMax,int direction) {
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
      if (Kz2 > EVANESCENT_EDGE && zindx > 0) {
        exponent = (-2*Math.PI*delz * Math.sqrt(Kz2));
      }
      if (Kz2 > EVANESCENT_EDGE) {
        //TODO fix these if statements so we get proper filtering
        // for depth z = 0.

        //TODO use complex math methods instead of doing it manually
        sampleOut[0] = (float) (sampleIn[0]*Math.cos(direction*exponent)
            - sampleIn[1]*Math.sin(direction*exponent));
        sampleOut[1] = (float) (sampleIn[1]*Math.cos(direction*exponent)
            + sampleIn[0]*Math.sin(direction*exponent));

      } else {
        //evanescent region
        exponent = 2*Math.PI*Math.abs(delz)*Math.sqrt(Math.abs(Kz2));
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
      return "XYF";
    if (!wavefield.isTimeTransformed() && wavefield.isSpaceTransformed())
      return "XYT";
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

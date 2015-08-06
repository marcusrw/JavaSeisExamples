package org.javaseis.examples.scratch;

import java.util.logging.Logger;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;

import org.javaseis.util.IntervalTimer;

public class Extrapolator {

  private static final Logger LOGGER = 
      Logger.getLogger(Extrapolator.class.getName());

  PhaseShiftFFT3D shot;
  PhaseShiftFFT3D rcvr;

  IntervalTimer extrapTime;
  IntervalTimer transformTime;

  public Extrapolator(PhaseShiftFFT3D shot,PhaseShiftFFT3D rcvr) {
    this.rcvr = rcvr;
    this.shot = shot;
    extrapTime = new IntervalTimer();
    transformTime = new IntervalTimer();
  }

  //Constant velocity phase shift
  public void extrapolate(float velocity, double delz,int zindx,double fMax) {

    extrapTime.start();

    float eps = 1E-12F;
    eps = 0F;

    DistributedArray rcvrDA = rcvr.getArray();
    DistributedArray shotDA = shot.getArray();

    int[] position = new int[rcvrDA.getDimensions()];
    int direction = 1; //forward
    int scope = 0; //samples

    //buffers
    float[] recInSample = new float[rcvrDA.getElementCount()];
    float[] souInSample = new float[shotDA.getElementCount()];
    float[] recOutSample = new float[rcvrDA.getElementCount()];
    float[] souOutSample = new float[rcvrDA.getElementCount()];
    double[] coords = new double[position.length];

    DistributedArrayPositionIterator dapi =
        new DistributedArrayPositionIterator(
            rcvrDA,position,direction,scope);
    while (dapi.hasNext()) {
      position = dapi.next();
      rcvrDA.getSample(recInSample, position);
      shotDA.getSample(souInSample, position);
      rcvr.getKyKxFCoordinatesForPosition(position, coords);
      double kY = coords[0];
      double kX = coords[1];
      double frequency = coords[2];

      //skip if we're over the data threshold.
      //TODO put this back when you have a proper band limited source.
      //if (frequency > fMax) continue;

      double Kz2 = (frequency/velocity)*(frequency/velocity) - kY*kY - kX*kX;
      double exponent = 0;
      if (Kz2 > eps && zindx > 0) {
        exponent = (-2*Math.PI*delz * Math.sqrt(Kz2));
      }
      if (Kz2 > eps) {
        //TODO fix these if statements so we get proper filtering
        // for depth z = 0.

        //TODO use complex math methods instead of doing it manually
        recOutSample[0] = (float) (recInSample[0]*Math.cos(-exponent)
            - recInSample[1]*Math.sin(-exponent));
        recOutSample[1] = (float) (recInSample[1]*Math.cos(-exponent)
            + recInSample[0]*Math.sin(-exponent));
        souOutSample[0] = (float) (souInSample[0]*Math.cos(exponent)
            - souInSample[1]*Math.sin(exponent));
        souOutSample[1] = (float) (souInSample[1]*Math.cos(exponent)
            + souInSample[0]*Math.sin(exponent));

      } else {
        exponent = 2*Math.PI*Math.abs(delz)*Math.sqrt(Math.abs(Kz2));
        recOutSample[0] = (float) (recInSample[0]*Math.exp(-exponent));
        recOutSample[1] = (float) (recInSample[1]*Math.exp(-exponent));
        souOutSample[0] = (float) (souInSample[0]*Math.exp(-exponent));
        souOutSample[1] = (float) (souInSample[1]*Math.exp(-exponent));
      }
      rcvrDA.putSample(recOutSample, position);
      shotDA.putSample(souOutSample, position);
    }
    extrapTime.stop();
  }

  public void transformFromSpaceToWavenumber() {
    transformTime.start();
    rcvr.forwardSpatial2D();
    shot.forwardSpatial2D();
    transformTime.stop();

  }

  public void transformFromWavenumberToSpace() {
    transformTime.start();
    rcvr.inverseSpatial2D();
    shot.inverseSpatial2D();
    transformTime.stop();
  }

  public void transformFromTimeToFrequency() {
    transformTime.start();
    rcvr.forwardTemporal();
    shot.forwardTemporal();
    transformTime.stop();
  }

  public void transformFromFrequencyToTime() {
    transformTime.start();
    rcvr.inverseTemporal();
    shot.inverseTemporal();
    transformTime.stop();
  }

  public double getTransformTime() {
    return transformTime.total();
  }

  public double getExtrapolationTime() {
    return extrapTime.total();
  }
}
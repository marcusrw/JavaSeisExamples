package org.javaseis.examples.scratch;

import java.util.Arrays;
import java.util.logging.Logger;

import org.javaseis.util.IntervalTimer;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;

public class ImagingCondition {

  private static final Logger LOGGER = 
      Logger.getLogger(ImagingCondition.class.getName());

  SeisFft3dNew shot;
  SeisFft3dNew rcvr;

  DistributedArray imageDA;

  IntervalTimer imagingTime;

  public ImagingCondition(SeisFft3dNew shot,SeisFft3dNew rcvr,
      DistributedArray imageDA) {
    this.shot = shot;
    this.rcvr = rcvr;
    this.imageDA = imageDA;
    imagingTime = new IntervalTimer();
  }

  public void imagingCondition(DistributedArray imageDA,
      int zindx,double fMax) {
    imagingTime.start();

    DistributedArray rcvrDA = rcvr.getArray();
    DistributedArray shotDA = shot.getArray();
    float[] recInSample2 = new float[rcvrDA.getElementCount()];
    float[] souInSample2 = new float[shotDA.getElementCount()];

    //TODO Trick.  Hide the high frequencies from the iterator
    // so that it doesn't waste time accumulating a bunch of zeros.


    int[] fullShape = rcvrDA.getShape().clone();
    LOGGER.info("Original DA Shape: " + Arrays.toString(fullShape));

    /*
    double fNY = 1/(2*0.002);
    double delf = fNY/DALengths[0];
    int realMaxF = DALengths[0];
    int maxFindx = (int) (fMax/delf)+1;

    //DALengths[0] = maxFindx;
    LOGGER.info("Max F index: " + maxFindx);
    rcvrDA.setShape(DALengths);
    shotDA.setShape(DALengths);
    LOGGER.info(Arrays.toString(DALengths));
     */



    //buffers
    int[] position = new int[rcvrDA.getDimensions()];
    int direction = 1; //forward
    int scope = 0; //samples

    DistributedArrayPositionIterator dapi;
    dapi = new DistributedArrayPositionIterator(rcvrDA,position,
        direction,scope);

    float[] imageSample = new float[imageDA.getElementCount()];
    while (dapi.hasNext()) {
      position = dapi.next();
      int[] outputPosition = position.clone();
      outputPosition[0] = zindx;
      rcvrDA.getSample(recInSample2, position);
      shotDA.getSample(souInSample2, position);

      imageDA.getSample(imageSample, outputPosition);
      imageSample[0] += recInSample2[0]*souInSample2[0]
          + recInSample2[1]*souInSample2[1];
      imageDA.putSample(imageSample, outputPosition);
    }

    //Get the source and receiver samples
    LOGGER.fine("\n\nShot DA shape: "
        + Arrays.toString(shotDA.getShape())
        + "\nShot DA sample count: "
        + shotDA.getTotalSampleCount()
        +"\n\nReceiver DA shape: "
        + Arrays.toString(rcvrDA.getShape()) 
        + "\nReceiver DA sample count: "
        + rcvr.getArray().getTotalSampleCount()
        +"\n\nImage DA shape: " 
        + Arrays.toString(imageDA.getShape()) 
        + "\nImage DA sample count: " 
        + imageDA.getTotalSampleCount()
        + "\n\n");

    imagingTime.stop();
  }

  public double getImagingTime() {
    return imagingTime.total();
  }
}

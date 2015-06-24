package org.javaseis.examples.plot;

import java.util.Arrays;

import org.javaseis.properties.DataDomain;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;

public class SingleVolumeDAViewer {

  //TODO make this into a static method call, like in the backend viewer, so we
  // don't have to instantiate an object to get it.

  private DistributedArray displayDA;
  private boolean shiftsExist = false;
  private boolean[] shiftDimensions;

  public SingleVolumeDAViewer() {
    throw new UnsupportedOperationException("No argument constructor is not valid.");
  }

  public SingleVolumeDAViewer(ISeismicVolume input) {
    DataDomain[] domains = input.getLocalGrid().getAxisDomains();
    shiftDimensions = determineAxesToShift(domains);

    //Get the input distributed array.  If no shifts are necessary, we are done.
    DistributedArray inputDA = input.getDistributedArray();

    //Otherwise, copy the input into a shifted array.
    if (!shiftsExist) {
      displayDA = inputDA;
    } else {
      generateShiftedArray(inputDA);
    }
  }

  private boolean[] determineAxesToShift(DataDomain[] domains) {
    boolean[] shiftDimension = new boolean[domains.length];
  
    for (int k = 0 ; k < domains.length ; k++) {
      if (domains[k].getName() == "wavenumber") {
        shiftDimension[k] = true;
        shiftsExist = true;
      }
    }
    return shiftDimension;
  }

  private void generateShiftedArray(DistributedArray inputDA) {
    displayDA = new DistributedArray(inputDA);
    int[] axisLengths = inputDA.getShape();
    DistributedArrayPositionIterator dapi = initializePositionIterator(inputDA);

    //TODO make this do the absolute value if the samples are complex
    int elementsPerSample = inputDA.getElementCount();
    float[] sample = new float[elementsPerSample];
    while (dapi.hasNext()) {
      int[] position = dapi.next();
      inputDA.getSample(sample, position);
      displayDA.putSample(sample,
          shiftedPosition(position,axisLengths));
    }
  }

  private DistributedArrayPositionIterator initializePositionIterator(
      DistributedArray inputDA) {
    int direction = 1; //traverse the array forwards
    int scope = 0; //iterate over samples
    DistributedArrayPositionIterator dapi =
        new DistributedArrayPositionIterator(inputDA,direction,scope);
    return dapi;
  }

  private int[] shiftedPosition(int[] position,int[] shape) {
    int[] newPosition = Arrays.copyOf(position,position.length);
    for (int k = 0 ; k < shape.length ; k++) {
      if (shiftDimensions[k]) {
        int midwaypoint = shape[k]/2;
        newPosition[k] = (position[k] + midwaypoint) % shape[k];
      }
    }
    return newPosition;
  }

  public void showAsModalDialog() {
    //TODO add support for proper axis annotations. (will probably involve extending
    // the original viewer class.
    DistributedArrayMosaicPlot.showAsModalDialog(displayDA,"Is this working?");    
  }
}

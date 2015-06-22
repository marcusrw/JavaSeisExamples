package org.javaseis.examples.plot;

import java.util.Arrays;

import org.javaseis.properties.DataDomain;
import org.javaseis.tool.ToolContext;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;

public class SingleVolumeDAViewer {

  //TODO make this into a static method call, like in the backend viewer, so we
  // don't have to instantiate an object to get it.

  public SingleVolumeDAViewer() {
    throw new UnsupportedOperationException("No argument constructor is not valid.");
  }

  public SingleVolumeDAViewer(ToolContext toolContext,ISeismicVolume input) {
    DataDomain[] domains = input.getLocalGrid().getAxisDomains();
    boolean[] shiftDimension = determineAxesToShift(domains);

    //copy the inputDA into a second DA, with the positions set to true shifted
    DistributedArray inputDA = input.getDistributedArray();
    DistributedArray displayDA = new DistributedArray(inputDA);
    int[] axisLengths = inputDA.getShape();
    DistributedArrayPositionIterator dapi = new DistributedArrayPositionIterator(inputDA,1,0);

    //TODO make this do the absolute value if the samples are complex
    int elementsPerSample = inputDA.getElementCount();
    float[] sample = new float[elementsPerSample];
    while (dapi.hasNext()) {
      int[] position = dapi.next();
      inputDA.getSample(sample, position);
      displayDA.putSample(sample, ShiftedPosition(position,shiftDimension,axisLengths));
      //System.out.println("Original: " + Arrays.toString(position)
      //    + " Shifted: " + Arrays.toString(ShiftedPosition(position,shiftDimension,axisLengths)));
    }
    
    //TODO add support for proper axis annotations. (will probably involve extending
    // the original viewer class.
    double[] deltas = input.getDeltas();
    double[] origins = new double[deltas.length]; //HACK.  Origins aren't always zero.
    DistributedArrayMosaicPlot.showAsModalDialog(displayDA,"Is this working?");
  }

  private boolean[] determineAxesToShift(DataDomain[] domains) {
    boolean[] shiftDimension = new boolean[domains.length];

    for (int k = 0 ; k < domains.length ; k++) {
      if (domains[k].getName() == "wavenumber") {
        shiftDimension[k] = true;
      }
    }
    return shiftDimension;
  }

  private int[] ShiftedPosition(int[] position,boolean[] shiftDimension,int[] shape) {
    int[] newPosition = Arrays.copyOf(position,position.length);
    for (int k = 0 ; k < shape.length ; k++) {
      if (shiftDimension[k]) {
        int midwaypoint = shape[k]/2;
        newPosition[k] = (newPosition[k] + midwaypoint) % shape[k];
      }
    }
    return newPosition;
  }
}

package org.javaseis.examples.plot;

import java.util.Arrays;

import org.javaseis.array.ElementType;
import org.javaseis.grid.GridDefinition;
import org.javaseis.properties.DataDomain;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.parallel.IParallelContext;

/**
 * Add some functionality to the DistributedArrayMosaicPlot object, including
 * automatically shifting the wavenumber coordinates, and computing the absolute
 * value of a complex distributed array.
 * 
 * @author Marcus Wilson
 *
 */
public class SingleVolumeDAViewer {
  
  //TODO make this into a static method call, like in the backend viewer,
  //so we don't have to instantiate an object to get it.  Maybe.

  private DistributedArray displayDA;
  private boolean shiftsExist = false;
  private boolean dataIsComplex = false;
  private boolean[] shiftDimensions;

  public SingleVolumeDAViewer() {
    throw new UnsupportedOperationException("No argument constructor is not valid.");
  }

  //just plot the Distributed Array.  The grid tells you which is any dimensions
  //need to be shifted.
  public SingleVolumeDAViewer(DistributedArray inputDA,GridDefinition grid) {
    DataDomain[] domains = grid.getAxisDomains();
    shiftDimensions = determineAxesToShift(domains);

    System.out.println("input shape: " + Arrays.toString(inputDA.getShape()));
    System.out.println("input num samples: " + inputDA.getTotalSampleCount());
    System.out.println("input array size: " + inputDA.getArrayLength());
    System.out.println("Element Count: " + inputDA.getElementCount());

    if (inputDA.getElementCount() > 2) {
      throw new IllegalArgumentException("Input distributed array has more than"
          + "2 elements per sample.  This configuration is not supported.");
    }
    if (inputDA.getElementCount() == 2) {
      //Implies complex data (2 floats per sample)
      //compute absolute value
      dataIsComplex = true;
    }
    generateDisplayDA(inputDA);
  }

  //TODO StandaloneVolumeTool is currently broken for complex volumes
  //     because elementCount is hard coded to 1.
  public SingleVolumeDAViewer(ISeismicVolume input) {
    DataDomain[] domains = input.getLocalGrid().getAxisDomains();
    shiftDimensions = determineAxesToShift(domains);

    //Get the input distributed array.  If no shifts are necessary, we are done.
    DistributedArray inputDA = input.getDistributedArray();
    generateDisplayDA(inputDA);
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

  private void generateDisplayDA(DistributedArray inputDA) {
    if (!shiftsExist && !dataIsComplex) {
      displayDA = inputDA;
    } else {
      generateTransformArray(inputDA);
    }
  }


  private void generateTransformArray(DistributedArray inputDA) {
    IParallelContext pc = inputDA.getParallelContext();
    ElementType type = inputDA.getElementType();
    int[] lengths = inputDA.getShape();
    displayDA = new DistributedArray(pc,type,lengths);
    int[] axisLengths = inputDA.getShape();
    DistributedArrayPositionIterator dapi = initializePositionIterator(inputDA);

    //TODO make this do the absolute value if the samples are complex
    int elementsPerSample = inputDA.getElementCount();
    float[] rawSample = new float[elementsPerSample];
    System.out.println("Sample: " + Arrays.toString(rawSample));
    System.out.println("Elements per sample: " + elementsPerSample);
    while (dapi.hasNext()) {
      int[] position = dapi.next();
      inputDA.getSample(rawSample, position);
      if (dataIsComplex) {
        float absValue = complexAbs(rawSample);
        displayDA.putSample(absValue, shiftedPosition(position,axisLengths));
      } else {
        displayDA.putSample(rawSample,shiftedPosition(position,axisLengths));
      }
    }
  }

  private DistributedArrayPositionIterator initializePositionIterator(
      DistributedArray inputDA) {
    int direction = 1; //traverse the array forwards
    int scope = 0; //iterate over samples
    return new DistributedArrayPositionIterator(inputDA,direction,scope);
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

  private float complexAbs(float[] c) {
    return (float)Math.hypot(c[0],c[1]);
  }

  //Method name inherited from DistributedArrayMosaicPlot
  public void showAsModalDialog() {
    //TODO add support for proper axis annotations. (will probably involve extending
    // the original viewer class.
    DistributedArrayMosaicPlot.showAsModalDialog(displayDA,"Is this working?");    
  }
}

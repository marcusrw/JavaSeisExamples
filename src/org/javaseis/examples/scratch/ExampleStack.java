package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.logging.Logger;

import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;

public class ExampleStack extends StandAloneVolumeTool {

  private static final Logger LOGGER =
      Logger.getLogger(ExampleStack.class.getName());

  static ParameterService parms;

  public static void main(String[] args) throws FileNotFoundException, SeisException {
    String inputFileName = "seg45shot.js";
    String vModelFileName = "segsaltmodel.js";
    parms = new FindTestData(inputFileName).getParameterService();
    parms.setParameter("vModelFilePath",vModelFileName);
    ExampleStack.exec(parms,new ExampleStack());
  }

  @Override
  public void serialInit(ToolContext serialToolContext) {
    // TODO Auto-generated method stub

  }

  @Override
  public void parallelInit(ToolContext toolContext) {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean processVolume(ToolContext toolContext, ISeismicVolume input,
      ISeismicVolume output) {

    DistributedArrayMosaicPlot.showAsModalDialog(
        input.getDistributedArray(),"title");

    //figure out how many volumes there are
    //make an array of somethings to store the grid properties from vEdge
    //make a distributed array big enough to fit all of them 
    //OR make a distributed array the size of the velocity model.
    
    //the somethings are something like a physicalOrigin/Delta pair I guess?

    //Check that what you've got so far is empty
    //you may or may not have to set the output GlobalGrid at some point.
    checkOutputDAIsEmpty(input,output);

    //Read the data into that new distributed array in the right place.
    //(trace iterator would work here - but check that the traces aren't
    //too long)

    //let that output to a file by setting return to true

    return true;
  }

  private void checkOutputDAIsEmpty(ISeismicVolume input,
      ISeismicVolume output) {
    if (distributedArrayIsEmpty(output.getDistributedArray())) {
      // Should only be true when we're on the first volume, until the
      // tool
      // is fixed.
      if (!isFirstVolume(input)) {
        LOGGER.info("Is first volume: " 
            + isFirstVolume(input));
        LOGGER.info("Current Volume: " 
            + Arrays.toString(input.getVolumePosition()));
        LOGGER.info("First Volume: " 
            + Arrays.toString(new int[input.getVolumePosition().length]));    

        throw new IllegalArgumentException("The distributed array is"
            + " already empty, so the next step is a waste of time.");
      } else {
        LOGGER.info("First volume output is empty, as expected.");
      }
    }

    output.getDistributedArray().zeroCompletely();

    // Make sure the output DA is empty.
    if (!distributedArrayIsEmpty(output.getDistributedArray())) {
      throw new IllegalArgumentException("Why is the output not empty?");
    }
  }

  private boolean distributedArrayIsEmpty(DistributedArray da) {
    int[] position = new int[da.getShape().length];
    int direction = 1; // forward
    int scope = 0; // sample scope
    float[] buffer = new float[da.getElementCount()];
    DistributedArrayPositionIterator dapi = new DistributedArrayPositionIterator(
        da, position, direction, scope);
    while (dapi.hasNext()) {
      position = dapi.next();
      da.getSample(buffer, position);
      for (float element : buffer) {
        if (element != 0) {
          LOGGER.info("DA is not empty at position: "
              + Arrays.toString(position));
          return false;
        }
      }
    }
    return true;
  }

  private boolean isFirstVolume(ISeismicVolume input) {
    return Arrays.equals(input.getVolumePosition(),
        new int[input.getVolumePosition().length]);
  }

  @Override
  public boolean outputVolume(ToolContext toolContext, ISeismicVolume output) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void parallelFinish(ToolContext toolContext) {
    // TODO Auto-generated method stub

  }

  @Override
  public void serialFinish(ToolContext toolContext) {
    // TODO Auto-generated method stub

  }
}

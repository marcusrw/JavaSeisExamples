package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.logging.Logger;

import org.javaseis.grid.GridDefinition;
import org.javaseis.grid.VolumeEdgeIO;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.junit.Assert;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.parallel.IParallelContext;

public class ExampleStack extends StandAloneVolumeTool {

  private static final Logger LOGGER = Logger.getLogger(ExampleStack.class.getName());

  static ParameterService parms;

  public static void main(String[] args) throws FileNotFoundException, SeisException {
    String inputFileName = "seg45shot.js";
    //String inputFileName = "segshotno1.js";
    String vModelFileName = "segsaltmodel.js";
    String outputFileName = "test.js";
    // parms = new FindTestData(inputFileName).getParameterService();
    parms = new FindTestData(inputFileName, outputFileName).getParameterService();
    parms.setParameter("vModelFilePath", vModelFileName);
    parms.setParameter("outputFileMode", "create");
    ExampleStack.exec(parms, new ExampleStack());
  }

  @Override
  public void serialInit(ToolContext serialToolContext) {
    // TODO Auto-generated method stub

    IParallelContext pc = serialToolContext.pc;

    // Write the volume information to a file
    VolumeEdgeIO veIO = new VolumeEdgeIO(pc, serialToolContext);
    veIO.write();

  }

  @Override
  public void parallelInit(ToolContext toolContext) {
    // TODO Auto-generated method stub

  }

  /**
   * Converts the dataPosition location and maps it to the VelocityModel Postion
   * Location
   * 
   * @param dataPos
   *          [?, ?, ?, ? ...]
   * @param VeloGrid
   *          The Velocity Model GridDefinition
   * @param VolGrid
   *          The Volume Model GridDefinition
   * @return The mapping to the Velocity Model Location
   */
  public int[] convertDataPosToVModelPos(int[] dataPos, GridDefinition VeloGrid, GridDefinition VolGrid) {
    int[] vModelPos = new int[dataPos.length];
    
    int[] V_MODEL_AXIS_ORDER = {2,1,0};

    // calculate the trace-axis maps only
    // don't care about the time axis as we are setting actual traces
    vModelPos[0] = 0;

    // Ex: Axis Index = 1 - CrossLine Axis
    // Axis Index = 2 - Inline Axis
    for (int i = 1; i < dataPos.length - 1; i++) {
      // One Axis from Volume
      AxisDefinition VolumeAxis = VolGrid.getAxis(i);
      // Same Axis from Velocity Model
      AxisDefinition VelocityAxis = VeloGrid.getAxis(i);

      // data physical origin + data delta * index data - velocity model
      // physical origin
      double DpoDDmultIndexDataminusVMo = VolumeAxis.getPhysicalOrigin() + VolumeAxis.getPhysicalDelta() * dataPos[i]
          - VelocityAxis.getPhysicalOrigin();

      vModelPos[i] = (int) (DpoDDmultIndexDataminusVMo / VelocityAxis.getPhysicalDelta());
    }

    // TODO:
    // Set the Volume to nth index
    vModelPos[3] = dataPos[3];

    return vModelPos;
  }

  private int[] convertLongArrayToIntArray(long[] A) {
    int[] B = new int[A.length];
    for (int i = 0; i < A.length; i++) {
      B[i] = (int) A[i];
    }
    return B;
  }

  @Override
  public boolean processVolume(ToolContext toolContext, ISeismicVolume input, ISeismicVolume output) {

    DistributedArrayMosaicPlot.showAsModalDialog(input.getDistributedArray(), "title");

    // figure out how many volumes there are
    // int numVols = veIO.getVolumeNumber();
    VolumeEdgeIO veIO = new VolumeEdgeIO(toolContext.pc, toolContext);

    // Read the velocity model
    GridDefinition velocityGrid = veIO.readVelocityGrid();
    // System.out.println(velocityGrid.toString());

    long[] long_lengths = velocityGrid.getAxisLengths();

    int[] int_lengths = convertLongArrayToIntArray(long_lengths);

    // make a distributed array big enough to fit all of them
    // OR make a distributed array the size of the velocity model.

    // Create a new empty distributed array
    DistributedArray eDA = new DistributedArray(toolContext.pc, int_lengths);

    // The starting position volume
    int[] volumePosIndex = input.getVolumePosition();
    // int[] volPos = new int[] { 0, 0, 0, 0 };

    int totalVolumes = veIO.getTotalVolumes();
    // System.out.println(totalVolumes);

    // iterate over volumes

    for (int j = 0; j < totalVolumes; j++) {

      // set the new Volume Position
      volumePosIndex = input.getVolumePosition();
      volumePosIndex[3] = j;
      
      System.out.println("Volume #" + Arrays.toString(volumePosIndex));

      DistributedArray inputDA = input.getDistributedArray();
      // System.out.println(inputDA.toString());

      GridDefinition volumeGrid = veIO.readVolumeGrid(volumePosIndex);
      // System.out.println(volumeGrid.toString());

      // Iterate over the Trace - Scope = 1
      DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(inputDA, volumePosIndex,
          DistributedArrayPositionIterator.FORWARD, 1);

      while (itrInputArr.hasNext()) {

        // int[] pos = itrInputArr.getPosition();
        int[] pos = itrInputArr.next();

        System.out.println("Global Volume Grid: " + Arrays.toString(pos));

        // TODO: May not be true if not square
        float[] buf = new float[inputDA.getShape()[0]];
        float[] vmodbuf = new float[eDA.getShape()[0]];
        //Assert.assertEquals("Data trace and model trace are different lengths",
         //   buf.length,vmodbuf.length);

        // get the trace from the input array at dataPos
        inputDA.getTrace(buf, pos);

        // Calculate the map for the velocityDistArray Position
        int[] veloPos = convertDataPosToVModelPos(pos, velocityGrid, volumeGrid);

        System.out.println("Velocity Index: " + Arrays.toString(veloPos));

        // put the proper traces into the eda
        eDA.getTrace(vmodbuf,veloPos);
        vmodbuf = addSecondArgToFirst(vmodbuf,buf);
        eDA.putTrace(vmodbuf, veloPos);
      }

    }

    DistributedArrayMosaicPlot.showAsModalDialog(eDA, "Velo");

    // let that output to a file by setting return to true

    return true;
  }
  
  private float[] addSecondArgToFirst(float[] trace1,float[] trace2) {
    Assert.assertTrue(trace1.length <= trace2.length);
    float[] out = new float[trace1.length];
    for (int k = 0 ; k < out.length ; k++) {
      out[k] = trace1[k] + trace2[k];
    }
    return out;
  }

  private void checkOutputDAIsEmpty(ISeismicVolume input, ISeismicVolume output) {
    if (distributedArrayIsEmpty(output.getDistributedArray())) {
      // Should only be true when we're on the first volume, until the
      // tool
      // is fixed.
      if (!isFirstVolume(input)) {
        LOGGER.info("Is first volume: " + isFirstVolume(input));
        LOGGER.info("Current Volume: " + Arrays.toString(input.getVolumePosition()));
        LOGGER.info("First Volume: " + Arrays.toString(new int[input.getVolumePosition().length]));

        throw new IllegalArgumentException(
            "The distributed array is" + " already empty, so the next step is a waste of time.");
      } else {
        LOGGER.info("First volume output is empty, as expected.");
      }
    }

    // output.getDistributedArray().zeroCompletely();

    // Make sure the output DA is empty.
    if (!distributedArrayIsEmpty(output.getDistributedArray())) {
      throw new IllegalArgumentException("Why is the output not empty?");
    }
  }

  private boolean distributedArrayIsEmpty(DistributedArray distributedArray) {
    int[] position = new int[distributedArray.getShape().length];
    int direction = 1; // forward
    int scope = 0; // sample scope
    float[] buffer = new float[distributedArray.getElementCount()];
    DistributedArrayPositionIterator dapi = new DistributedArrayPositionIterator(distributedArray, position, direction,
        scope);
    while (dapi.hasNext()) {
      position = dapi.next();
      distributedArray.getSample(buffer, position);
      for (float element : buffer) {
        if (element != 0) {
          LOGGER.info("DA is not empty at position: " + Arrays.toString(position));
          return false;
        }
      }
    }
    return true;
  }

  private boolean isFirstVolume(ISeismicVolume input) {
    return Arrays.equals(input.getVolumePosition(), new int[input.getVolumePosition().length]);
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
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
    // String inputFileName = "seg45shot.js";
    String inputFileName = "segshotno1.js";
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
    // VolumeEdgeIO veIO = new VolumeEdgeIO(pc, serialToolContext);
    // veIO.write();

    VolumeEdgeIO veIO = new VolumeEdgeIO(pc, serialToolContext);
    GridDefinition volumeGrid = veIO.readVelocityGrid();

    serialToolContext.putFlowGlobal(ToolContext.OUTPUT_GRID, volumeGrid);

  }

  @Override
  public void parallelInit(ToolContext toolContext) {
    // TODO Auto-generated method stub

  }

  /**
   * TODO:Fix logics Converts the dataPosition location and maps it to the
   * VelocityModel Postion Location
   * 
   * @param dataPos
   *          [?, ?, ?, ? ...]
   * @param VeloGrid
   *          The Velocity Model GridDefinition
   * @param VolGrid
   *          The Volume Model GridDefinition
   * @return The mapping to the Velocity Model Location
   */
  public int[] convertDataPosToVModelPos(int[] dataPos, int[] axis_order, GridDefinition VeloGrid,
      GridDefinition VolGrid) {
    int[] vModelPos = new int[dataPos.length];

    int[] v_model_axis = { 2, 1, 0 };

    // axis_order

    // Get Z Axis
    // One Axis from Volume
    AxisDefinition VolumeAxis = VolGrid.getAxis(axis_order[2]);
    // Same Axis from Velocity Model
    AxisDefinition VelocityAxis = VeloGrid.getAxis(v_model_axis[2]);

    // data physical origin + data delta * index data - velocity model
    // physical origin
    double DpoDDmultIndexDataminusVMo = VolumeAxis.getPhysicalOrigin()
        + VolumeAxis.getPhysicalDelta() * dataPos[axis_order[2]] - VelocityAxis.getPhysicalOrigin();

    vModelPos[v_model_axis[2]] = (int) (DpoDDmultIndexDataminusVMo / VelocityAxis.getPhysicalDelta());

    // Get X Axis
    // One Axis from Volume
    VolumeAxis = VolGrid.getAxis(axis_order[0]);
    // Same Axis from Velocity Model
    VelocityAxis = VeloGrid.getAxis(v_model_axis[0]);

    // data physical origin + data delta * index data - velocity model
    // physical origin
    DpoDDmultIndexDataminusVMo = VolumeAxis.getPhysicalOrigin() + VolumeAxis.getPhysicalDelta() * dataPos[axis_order[0]]
        - VelocityAxis.getPhysicalOrigin();

    vModelPos[v_model_axis[0]] = (int) (DpoDDmultIndexDataminusVMo / VelocityAxis.getPhysicalDelta());

    // Get Y Axis
    // One Axis from Volume
    VolumeAxis = VolGrid.getAxis(axis_order[1]);
    // Same Axis from Velocity Model
    VelocityAxis = VeloGrid.getAxis(v_model_axis[1]);

    // data physical origin + data delta * index data - velocity model
    // physical origin
    DpoDDmultIndexDataminusVMo = VolumeAxis.getPhysicalOrigin() + VolumeAxis.getPhysicalDelta() * dataPos[axis_order[1]]
        - VelocityAxis.getPhysicalOrigin();

    vModelPos[v_model_axis[1]] = (int) (DpoDDmultIndexDataminusVMo / VelocityAxis.getPhysicalDelta());

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

  public void checkPublicGrids(ToolContext toolContext) {
    GridDefinition inputGrid = toolContext.inputGrid;
    if (inputGrid == null) {
      LOGGER.severe("The public field toolContext.inputGrid is null, "
          + "doesn't get shared between parallel tasks, and is a huge "
          + "violation of object encapsulation.  You shouldn't use it.");
      inputGrid = (GridDefinition) toolContext.getFlowGlobal(ToolContext.INPUT_GRID);
      toolContext.inputGrid = inputGrid;
    }
    GridDefinition outputGrid = toolContext.outputGrid;
    if (outputGrid == null) {
      LOGGER.severe("The public field toolContext.outputGrid is null, "
          + "doesn't get shared between parallel tasks, and is a huge "
          + "violation of object encapsulation.  You shouldn't use it.");
      outputGrid = (GridDefinition) toolContext.getFlowGlobal(ToolContext.OUTPUT_GRID);
      toolContext.outputGrid = outputGrid;
    }
  }

  @Override
  public boolean processVolume(ToolContext toolContext, ISeismicVolume input, ISeismicVolume output) {

    Assert.assertNotNull(output.getGlobalGrid());

    checkPublicGrids(toolContext);

    // DistributedArrayMosaicPlot.showAsModalDialog(input.getDistributedArray(),
    // "title");

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
    // DistributedArray eDA = new DistributedArray(toolContext.pc, int_lengths);

    DistributedArray eDA = output.getDistributedArray();

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

      // System.out.println("Volume #" + Arrays.toString(volumePosIndex));

      DistributedArray inputDA = input.getDistributedArray();
      // System.out.println(inputDA.toString());

      GridDefinition volumeGrid = veIO.readVolumeGrid(volumePosIndex);
      // System.out.println(volumeGrid.toString());

      int[] axis_order = veIO.getAxisOrder(volumePosIndex);
      // System.out.println("Axis: " + Arrays.toString(axis_order));

      // Iterate over the Trace - Scope = 1
      DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(inputDA, volumePosIndex,
          DistributedArrayPositionIterator.FORWARD, 1);

      while (itrInputArr.hasNext()) {

        // int[] pos = itrInputArr.getPosition();
        int[] pos = itrInputArr.next();

        // Get the axis order of the individual volume
        System.out.println("Global Volume Grid: " + Arrays.toString(pos));

        // TODO: May not be true if not square
        float[] buf = new float[inputDA.getShape()[0]];
        float[] vmodbuf = new float[eDA.getShape()[0]];
        // Assert.assertEquals("Data trace and model trace are different
        // lengths",
        // buf.length,vmodbuf.length);

        // get the trace from the input array at dataPos
        inputDA.getTrace(buf, pos);

        // Calculate the map for the velocityDistArray Position
        int[] veloPos = convertDataPosToVModelPos(pos, axis_order, velocityGrid, volumeGrid);

        // System.out.println("Velocity Index: " + Arrays.toString(veloPos));

        // put the proper traces into the eda
        eDA.getTrace(vmodbuf, veloPos);
        vmodbuf = addSecondArgToFirst(vmodbuf, buf);
        eDA.putTrace(vmodbuf, veloPos);
      }

    }

    DistributedArrayMosaicPlot.showAsModalDialog(eDA, "Velo");

    // let that output to a file by setting return to true

    return true;
  }

  private float[] addSecondArgToFirst(float[] trace1, float[] trace2) {
    Assert.assertTrue(trace1.length <= trace2.length);
    float[] out = new float[trace1.length];
    for (int k = 0; k < out.length; k++) {
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
package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.javaseis.examples.plot.DAFrontendViewer;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.examples.tool.ExampleVolumeOutputTool;
import org.javaseis.examples.tool.VolumeCorrectionTool;
import org.javaseis.grid.GridDefinition;
import org.javaseis.grid.VolumeEdgeIO;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.DataState;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.javaseis.utils.Convert;
import org.javaseis.velocity.VelocityModelFromFile;
import org.javaseis.volume.ISeismicVolume;
import org.junit.Assert;

import beta.javaseis.array.ITraceIterator;
import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;

public class ExampleStack implements IVolumeTool {

  private static final Logger LOGGER = Logger.getLogger(ExampleStack.class.getName());

  static ParameterService parms;

  public static void main(String[] args) throws FileNotFoundException, SeisException {
    String inputFileName = "segshotno1.js";
    String outputFileName = "newTestStack.js";
    String vModelFileName = "segsaltmodel.js";

    try {
      parms = new FindTestData(inputFileName, outputFileName).getParameterService();

      parms.setParameter("vModelFilePath", vModelFileName);
      parms.setParameter("outputFileMode", "create");

      List<String> toolList = new ArrayList<String>();

      toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
      toolList.add(VolumeCorrectionTool.class.getCanonicalName());
      toolList.add(ExampleStack.class.getCanonicalName());
      toolList.add(ExampleVolumeOutputTool.class.getCanonicalName());

      String[] toolArray = Convert.listToArray(toolList);

      try {
        VolumeToolRunner.exec(parms, toolArray);
      } catch (SeisException e) {
        e.printStackTrace();
      }

    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private GridDefinition getVelocityGrid(IParallelContext pc, ToolState toolState) {
    VelocityModelFromFile vff = null;
    try {
      vff = new VelocityModelFromFile(pc, toolState);
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    vff.open("r");

    return vff.getVModelGrid();

    // return null;
  }
  
  private void setOutgoingDataStateGrid(ToolState toolState,
      GridDefinition outputGrid) {
    DataState outputState = toolState.getOutputState();
    outputState.gridDefinition = outputGrid;
    toolState.setOutputState(outputState);
  }

  @Override
  public void serialInit(ToolState serialToolState) {
    IParallelContext upc = new UniprocessorContext();
    GridDefinition veloGrid = getVelocityGrid(upc, serialToolState);
    setOutgoingDataStateGrid(serialToolState, veloGrid);
  }

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState) {

  }

  public int[] convertVolPosToVModelPos(int[] dataPos, int[] axis_order, GridDefinition VeloGrid,
      GridDefinition VolGrid) {
    int[] vModelPos = new int[dataPos.length];

    int[] v_model_axis = { 2, 1, 0 };

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

    // TODO: No longer needed
    // Set the Volume to nth index
    //vModelPos[3] = dataPos[3];

    return vModelPos;
  }

  private int[] convertLongArrayToIntArray(long[] A) {
    int[] B = new int[A.length];
    for (int i = 0; i < A.length; i++) {
      B[i] = (int) A[i];
    }
    return B;
  }

  public void checkPublicGrids(ToolState toolState) {
    GridDefinition inputGrid = toolState.getInputState().gridDefinition;
    if (inputGrid == null) {
      throw new IllegalArgumentException("Input Grid is Null");
    }
    GridDefinition outputGrid = toolState.getOutputState().gridDefinition;
    if (outputGrid == null) {
      throw new IllegalArgumentException("Output Grid is Null");
    }
  }

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolState, ISeismicVolume input, ISeismicVolume output) {

    //Assert.assertNotNull(output.getGlobalGrid());

    checkPublicGrids(toolState);

    // DistributedArrayMosaicPlot.showAsModalDialog(input.getDistributedArray(),
    // "title");

    GridDefinition velocityGrid = getVelocityGrid(pc, toolState);

    System.out.println(velocityGrid.toString());

    long[] long_lengths = velocityGrid.getAxisLengths();

    int[] int_lengths = convertLongArrayToIntArray(long_lengths);

    // DistributedArray eDA = output.getDistributedArray();

    ITraceIterator iti = input.getTraceIterator();
    ITraceIterator oti = output.getTraceIterator();
    
    GridDefinition volGrid = toolState.getInputState().gridDefinition;
    System.out.println(volGrid.toString());

    while (iti.hasNext()) {

      iti.next();

      float[] buf = iti.getTrace();

      float[] vmodbuf = new float[buf.length];
      
      int[] veloPos = convertVolPosToVModelPos(iti.getPosition().clone(), new int[] {2,1,0}, 
          velocityGrid, volGrid);

      if (oti.hasNext()){
      oti.setPosition(veloPos);
      oti.next();

      oti.putTrace(buf);
      }

    }

    DistributedArrayMosaicPlot.showAsModalDialog(output.getDistributedArray(), "Velo");
    /*
     * // figure out how many volumes there are // int numVols =
     * veIO.getVolumeNumber(); VolumeEdgeIO veIO = new VolumeEdgeIO(pc,
     * toolState);
     * 
     * // Read the velocity model GridDefinition velocityGrid =
     * veIO.readVelocityGrid(); // System.out.println(velocityGrid.toString());
     * 
     * long[] long_lengths = velocityGrid.getAxisLengths();
     * 
     * int[] int_lengths = convertLongArrayToIntArray(long_lengths);
     * 
     * // make a distributed array big enough to fit all of them // OR make a
     * distributed array the size of the velocity model.
     * 
     * // Create a new empty distributed array // DistributedArray eDA = new
     * DistributedArray(toolState.pc, int_lengths);
     * 
     * DistributedArray eDA = output.getDistributedArray();
     * 
     * // The starting position volume // int[] volumePosIndex =
     * input.getVolumePosition(); // int[] volPos = new int[] { 0, 0, 0, 0 };
     * 
     * // int totalVolumes = veIO.getTotalVolumes(); //
     * System.out.println(totalVolumes);
     * 
     * // iterate over volumes
     * 
     * // set the new Volume Position // volumePosIndex =
     * input.getVolumePosition();
     * 
     * int[] volumePosIndex = new int[] { 0, 0, 0, 0 };
     * 
     * // System.out.println("Volume #" + Arrays.toString(volumePosIndex));
     * 
     * DistributedArray inputDA = input.getDistributedArray(); //
     * System.out.println(inputDA.toString());
     * 
     * GridDefinition volumeGrid = veIO.readVolumeGrid(volumePosIndex); //
     * System.out.println(volumeGrid.toString());
     * 
     * int[] axis_order = veIO.getAxisOrder(volumePosIndex); //
     * System.out.println("Axis: " + Arrays.toString(axis_order));
     * 
     * // Iterate over the Trace - Scope = 1 DistributedArrayPositionIterator
     * itrInputArr = new DistributedArrayPositionIterator(inputDA,
     * volumePosIndex, DistributedArrayPositionIterator.FORWARD, 1);
     * 
     * while (itrInputArr.hasNext()) {
     * 
     * // int[] pos = itrInputArr.getPosition(); int[] pos = itrInputArr.next();
     * 
     * // Get the axis order of the individual volume // LOGGER.info(
     * "Global Volume Grid: " + Arrays.toString(pos));
     * 
     * // TODO: May not be true if not square float[] buf = new
     * float[inputDA.getShape()[0]]; float[] vmodbuf = new
     * float[eDA.getShape()[0]]; // Assert.assertEquals("Data trace and model
     * trace are different // lengths", // buf.length,vmodbuf.length);
     * 
     * // get the trace from the input array at dataPos inputDA.getTrace(buf,
     * pos);
     * 
     * // Calculate the map for the velocityDistArray Position int[] veloPos =
     * convertDataPosToVModelPos(pos, axis_order, velocityGrid, volumeGrid);
     * 
     * // LOGGER.info("Velocity Index: " + Arrays.toString(veloPos));
     * 
     * // put the proper traces into the eda eDA.getTrace(vmodbuf, veloPos);
     * vmodbuf = addSecondArgToFirst(vmodbuf, buf); eDA.putTrace(vmodbuf,
     * veloPos); }
     * 
     * DistributedArrayMosaicPlot.showAsModalDialog(eDA, "Velo");
     */
    // DAFrontendViewer A = new DAFrontendViewer(eDA, toolState);

    // A.setSliders(338, 128, 1);
    // A.setClipRange(-1000, 1000);
    // A.setAmpFactor(1);

    // A.show("TEST");
    // A = null;
    // eDA = null;

    // let that output to a file by setting return to true

    return true;
  }

  private float[] addSecondArgToFirst(float[] trace1, float[] trace2) {
    // System.out.println(trace1.length + " " +trace2.length);
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
      /*
       * if (!isFirstVolume(input)) { LOGGER.info("Is first volume: " +
       * isFirstVolume(input)); //LOGGER.info("Current Volume: " // +
       * Arrays.toString(input.getVolumePosition())); //LOGGER.info(
       * "First Volume: " // + Arrays.toString(new
       * int[input.getVolumePosition().length]));
       * 
       * throw new IllegalArgumentException("The distributed array is" +
       * " already empty, so the next step is a waste of time."); } else {
       * LOGGER.info("First volume output is empty, as expected."); }
       */
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

  // private boolean isFirstVolume(ISeismicVolume input) {
  // return Arrays.equals(input.getVolumePosition(),
  // new int[input.getVolumePosition().length]);
  // }

  @Override
  public boolean outputVolume(IParallelContext pc, ToolState toolState, ISeismicVolume output) throws SeisException {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void parallelFinish(IParallelContext pc, ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub

  }

  @Override
  public void serialFinish(ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub

  }
}
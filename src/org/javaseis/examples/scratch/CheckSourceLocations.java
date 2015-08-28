package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.parallel.IParallelContext;

import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.examples.tool.ExampleVolumeOutputTool;
import org.javaseis.examples.tool.VolumeCorrectionTool;
import org.javaseis.examples.tool.VolumeToolCheckerTool;
import org.javaseis.grid.GridFromHeaders;
import org.javaseis.grid.GridDefinition;
import org.javaseis.grid.ICheckedGrid;
import org.javaseis.grid.ManualOverrideGrid;
import org.javaseis.grid.VolumeEdgeIO;
import org.javaseis.imaging.ImagingCondition;
import org.javaseis.imaging.PhaseShiftExtrapolator;
import org.javaseis.imaging.PhaseShiftFFT3D;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.properties.AxisLabel;
import org.javaseis.properties.DataDomain;
import org.javaseis.properties.Units;
import org.javaseis.services.ParameterService;
import org.javaseis.source.ISourceVolume;
import org.javaseis.source.DeltaFunctionSourceVolume;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.DataState;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.IntervalTimer;
import org.javaseis.util.SeisException;
import org.javaseis.utils.Convert;
import org.javaseis.velocity.IVelocityModel;
import org.javaseis.velocity.VelocityInDepthModel;
import org.javaseis.velocity.VelocityModelFromFile;
import org.javaseis.volume.ISeismicVolume;
import org.junit.Assert;

/**
 * @author Marcus Wilson 2015
 *
 */
public class CheckSourceLocations implements IVolumeTool {

  private static final float S_TO_MS = 1000;
  private static final int[] FFT_ORIENTATION =
      PhaseShiftFFT3D.SEISMIC_FFT_ORIENTATION;

  private static final Logger LOGGER =
      Logger.getLogger(ExampleMigration.class.getName());

  public CheckSourceLocations() {
  }

  @Override
  public void serialInit(ToolState toolState) {
    //checkPublicGrids(toolState);
    GridDefinition imageGrid = computeImageGrid(toolState);
    setOutgoingDataStateGrid(toolState,imageGrid);

  }

  private void setOutgoingDataStateGrid(ToolState toolState,
      GridDefinition outputGrid) {
    DataState outputState = toolState.getOutputState();
    outputState.gridDefinition = outputGrid;
    toolState.setOutputState(outputState);
  }
  //Figure out if the public inputGrid and outputGrid are populated
  //and populate them if they aren't.
  private void checkPublicGrids(ToolState toolState) {
    GridDefinition inputGrid = toolState.getInputState().gridDefinition;
    if (inputGrid == null) {
      throw new IllegalArgumentException("Input Grid is Null");
      /*LOGGER.severe("The public field toolState.inputGrid is null, "
          + "doesn't get shared between parallel tasks, and is a huge "
          + "violation of object encapsulation.  You shouldn't use it.");
      inputGrid = (GridDefinition) toolState
          .getFlowGlobal(ToolState.INPUT_GRID);
      toolState.inputGrid = inputGrid;*/
    }
    GridDefinition outputGrid = toolState.getOutputState().gridDefinition;
    if (outputGrid == null) {
      throw new IllegalArgumentException("Output Grid is Null");
      /*LOGGER.severe("The public field toolState.outputGrid is null, "
          + "doesn't get shared between parallel tasks, and is a huge "
          + "violation of object encapsulation.  You shouldn't use it.");
      outputGrid = (GridDefinition) toolState
          .getFlowGlobal(ToolState.OUTPUT_GRID);
      toolState.outputGrid = outputGrid;*/
    }
  }

  private GridDefinition computeImageGrid(ToolState toolState) {

    GridDefinition inputGrid = toolState.getInputState().gridDefinition;
    //ParameterService parms = toolState.parms;
    Assert.assertNotNull(inputGrid);
    //Assert.assertNotNull(parms);

    AxisDefinition[] imageAxes =
        new AxisDefinition[inputGrid.getNumDimensions()];

    float zmin = Float.parseFloat(toolState.getParameter("ZMIN"));;
    float zmax = Float.parseFloat(toolState.getParameter("ZMAX"));
    float delz = Float.parseFloat(toolState.getParameter("DELZ"));

    long depthAxisLength = -1;
    depthAxisLength = computeDepthAxisLength(zmin, delz, zmax);

    // Iterate over the axes
    for (int axis = 0; axis < imageAxes.length; axis++) {
      if (axis == 0) {
        imageAxes[axis] = new AxisDefinition(AxisLabel.DEPTH, Units.METERS,
            DataDomain.SPACE, depthAxisLength, 0, 1, zmin, delz);
      } else {
        imageAxes[axis] = inputGrid.getAxis(axis);
      }
    }

    return new GridDefinition(imageAxes.length, imageAxes);
  }

  public long computeDepthAxisLength(float zMin, float deltaZ, float zMax) {
    if (deltaZ <= 0 || (zMax - zMin) < deltaZ)
      return 1;
    else
      return (long) Math.floor((zMax - zMin) / deltaZ) + 1;
  }

  /*private void saveVolumeEdgesIfTraceHeadersExist(ToolState toolState) {
    Assert.assertNotNull(toolState.pc);
    IParallelContext pc = toolState.pc;
    try {
      VolumeEdgeIO vEdgeIO = new VolumeEdgeIO(pc, toolState);
      vEdgeIO.write();
    } catch (NullPointerException e) {
      LOGGER.info("Input javaseis file has no associated trace header file.\n"
          + "No grid orientation information will be saved.");
    }
  }*/

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState) {
    Assert.assertNotNull(pc);
    //TODO These grids aren't working now
    //checkPublicGrids(toolState);
  }

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolState,
      ISeismicVolume input,ISeismicVolume output) {
    
    LOGGER.info("Starting parallelTimer on task #" + pc.rank() + "\n");
    LOGGER.info("Input Grid Definition:\n" + toolState.getInputState().gridDefinition + "\n");
    LOGGER.info("Output Grid Definition:\n" + toolState.getOutputState().gridDefinition + "\n");
    GridDefinition imageGrid = computeImageGrid(toolState);
    setOutgoingDataStateGrid(toolState,imageGrid);
    LOGGER.info("Computed image grid: ");
    LOGGER.info(imageGrid.toString());
    LOGGER.info("Output Grid Definition:\n" + toolState.getOutputState().gridDefinition + "\n");

    int[] imageShape = toolState.getOutputState().gridDefinition.getShape();
    output.getDistributedArray().reshape(imageShape);

    PhaseShiftFFT3D rcvr = createReceiverFFT(pc, toolState,input);
    PhaseShiftFFT3D shot = createSourceFFT(rcvr);

    //get any source/receiver XYZ, because we just need the depth.
    //this position needs to be as big as the global grid, and have
    //the right volume associated with it.  That's probably dumb.
    //the checkgrids object should probably handle that sort of thing.
    int[] gridPos = new int[] { 0, 0, 0 };
    double[] sxyz = new double[3];
    double[] rxyz = new double[3];
    try {
      input.getCoords(gridPos, sxyz, rxyz);
    } catch (IllegalStateException e) {
      LOGGER.log(Level.INFO,e.getMessage(),e);
      //do something else to find the coordinates\
      sxyz[2] = 0;
      rxyz[2] = 0;
    }

    double receiverDepth = sxyz[2];
    double sourceDepth = rxyz[2];

    //TODO hack
    receiverDepth = 0;
    sourceDepth = 0;

    PhaseShiftExtrapolator extrapR =
        new PhaseShiftExtrapolator(rcvr,receiverDepth);
    PhaseShiftExtrapolator extrapS =
        new PhaseShiftExtrapolator(shot,sourceDepth);

    extrapR.transformFromTimeToFrequency();
    extrapS.transformFromTimeToFrequency();

    // This has to be after the time transform.
    double loudness = 1000000;
    ISourceVolume srcVol = new DeltaFunctionSourceVolume(
        toolState.getInputState(),input,shot,loudness);
    shot = srcVol.getShot();
    double[] srcPos = srcVol.getSourceXYZ();
    int[] srcIndex = srcVol.getArrayPositionForPhysicalPosition(toolState.getInputState(),srcPos);
    float[] buf = new float[shot.getArray().getElementCount()];
    shot.getArray().getSample(buf, srcIndex);
    System.out.println(Arrays.toString(buf));
    Assert.assertTrue(buf[0] > 0);
    
    extrapR.transformFromFrequencyToTime();
    extrapS.transformFromFrequencyToTime();

    //shot.plotInTime("SHOT");
    //rcvr.plotInTime("RCVR");

    checkOutputDAIsEmpty(toolState,input, output);

    // depth axis information
    double zmin = imageGrid.getAxisPhysicalOrigin(0);
    double delz = imageGrid.getAxisPhysicalDelta(0);
    long numz = imageGrid.getAxisLength(0);
    LOGGER.info(String.format("zmin: %6.1f, delz: %6.1f, numz: %4d", zmin,
        delz, numz));

    //DistributedArrayMosaicPlot.showAsModalDialog(
    //    output.getDistributedArray(), "Image - In Progress");
    //DistributedArrayMosaicPlot.showAsModalDialog(
    //    input.getDistributedArray(), "Velocity Slice - In Progress");

    toolState.getOutputState().gridDefinition.toString();
    DistributedArray rcvrDA = rcvr.getArray();
    DistributedArray shotDA = shot.getArray();
    DistributedArray outputDA = output.getDistributedArray();
    int[] position = new int[outputDA.getDimensions()];
    int direction = 1; //forward
    int scope = 0; //sample scope
    DistributedArrayPositionIterator dapi =
        new DistributedArrayPositionIterator(outputDA, gridPos, direction, scope);

    float[] shotSample = new float[shotDA.getElementCount()];
    float[] rcvrSample = new float[rcvrDA.getElementCount()];
    float[] outSample = new float[outputDA.getElementCount()];
    while (dapi.hasNext()) {
      position = dapi.next();
      shotDA.getSample(shotSample,position);
      rcvrDA.getSample(rcvrSample,position);
      for (int k = 0 ; k < shotSample.length ; k++) {
        outSample[k] = shotSample[k] + rcvrSample[k];
        if (shotSample[k] > 0) {
          try {
            System.out.println("Shot nonzero at: "
          + Arrays.toString(position));
            double[] sXYZ = new double[3];
            double[] rXYZ = new double[3];
            input.getCoords(position, sXYZ, rXYZ);
            System.out.println("Source Position from Coords: "
            + Arrays.toString(sXYZ));
            System.out.println("Source Position from Shot: "
            + Arrays.toString(srcVol.getSourceXYZ()));
            System.out.println("Receiver Position from Coords: "
            + Arrays.toString(rXYZ));
            System.out.println("Receiver Origin: "
                + Arrays.toString(rxyz));
            System.out.println("source amplitude: " + shotSample[k]);
            System.out.println("receiver amplitude: " + rcvrSample[k]);
            System.out.println("output amplitude: " + outSample[k]);
            System.out.println("press enter to continue");
            System.in.read();
          } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
      }
      outputDA.putSample(outSample, position);
    }

    DistributedArrayMosaicPlot.showAsModalDialog(outputDA,"Source and Receiver Fields");
    try {
      System.in.read();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return true;
  }

  private void checkOutputDAIsEmpty(ToolState toolState,ISeismicVolume input,
      ISeismicVolume output) {
    if (distributedArrayIsEmpty(output.getDistributedArray())) {
      // Should only be true when we're on the first volume, until the
      // tool is fixed.
      if (!isFirstVolume(toolState,input)) {
        LOGGER.info("Is first volume: " + isFirstVolume(toolState,input));
        //LOGGER.info("Current Volume: " 
        //  + Arrays.toString(input.getVolumePosition()));
        //LOGGER.info("First Volume: " 
        //  + Arrays.toString(new int[input.getVolumePosition().length]));    

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

  //TODO debugging code that allows different treatment for volume 1
  private boolean isFirstVolume(ToolState toolState,ISeismicVolume input) {
    int[] volPos = toolState.getInputPosition();
    return Arrays.equals(volPos,
        new int[volPos.length]);
  }

  /* read the data in properly and then handle this
  private ICheckedGrid verifyGridOriginsAndDeltas(ToolState toolState,
      ISeismicVolume input) {
    ICheckedGrid gridFromHeaders;
    try {
      gridFromHeaders = new GridFromHeaders(input, toolState);
    } catch (NullPointerException e) {
      LOGGER.info(e.getMessage());
      LOGGER.info("It's possible that the input dataset has no associated,\n"
          + "trace header file, so that trying to open the coordinate\n"
          + "service failed.");
      gridFromHeaders = new ManualOverrideGrid(input, toolState);
    }

    // Set the Modified Grid = input Grid, since we can't set it in the
    // input
    toolState.getInputState().gridDefinition = gridFromHeaders.getModifiedGrid();
    return gridFromHeaders;
  }
   */

  private PhaseShiftFFT3D createReceiverFFT(IParallelContext pc,
      ToolState toolState, ISeismicVolume input) {

    int[] inputShape = input.getLengths();
    float[] pad = getPad(toolState);
    Assert.assertNotNull(pc);

    Assert.assertNotNull("ParallelContext is null", pc);
    Assert.assertNotNull("Input Shape is null", inputShape);
    Assert.assertNotNull("Pad is null", pad);
    PhaseShiftFFT3D rcvr = new PhaseShiftFFT3D(pc, inputShape, pad,
        FFT_ORIENTATION);

    // copy the receiver data into the rcvr object
    rcvr.getArray().copy(input.getDistributedArray());

    // Specify the sample rates
    double[] sampleRates = computeVolumeSampleRates(input,
        toolState.getInputState().gridDefinition);

    rcvr.setTXYSampleRates(sampleRates);
    LOGGER.info("Created transformable receiver wavefield with sample rates: "
        + Arrays.toString(rcvr.getTXYSampleRates()));
    return rcvr;
  }

  private PhaseShiftFFT3D createSourceFFT(PhaseShiftFFT3D receiverFFT) {

    PhaseShiftFFT3D sourceFFT = new PhaseShiftFFT3D(receiverFFT);
    sourceFFT.getArray().zeroCompletely();
    sourceFFT.setTXYSampleRates(receiverFFT.getTXYSampleRates());
    LOGGER.info("Created transformable source wavefield with sample rates: "
        + Arrays.toString(sourceFFT.getTXYSampleRates()));

    return sourceFFT;
  }

  private float[] getPad(ToolState toolState) {
    //TODO:may need fixing
    //ParameterService parms = toolState.parms;
    //float padT = Float.parseFloat(parms.getParameter("PADT", "10"));
    //float padX = Float.parseFloat(parms.getParameter("PADX", "10"));
    //float padY = Float.parseFloat(parms.getParameter("PADY", "10"));
    float padT = Float.parseFloat(toolState.getParameter("PADT"));
    float padX = Float.parseFloat(toolState.getParameter("PADX"));
    float padY = Float.parseFloat(toolState.getParameter("PADY"));
    float[] pad = new float[] { padT, padX, padY };
    LOGGER.info("Pad: " + Arrays.toString(pad));
    return pad;
  }

  // Currently superfluous check that will start throwing an exception when
  // a better way of removing the output data between volumes is implemented.
  // To remind you to remove the slow emptying of the DA.
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

  private double[] computeVolumeSampleRates(ISeismicVolume input,
      GridDefinition grid) {
    double[] localGridSampleRates = grid.getAxisPhysicalDeltas().clone();
    Units timeAxisUnits = grid.getAxisUnits(0);
    if (timeAxisUnits.equals(Units.SECONDS)) {
      return localGridSampleRates;
    }
    if (timeAxisUnits.equals(Units.MILLISECONDS)) {
      LOGGER.info("Time axis is measured in milliseconds.  We convert to "
          + "seconds here so we get correct frequency axis units.");
      localGridSampleRates[0] = localGridSampleRates[0] / S_TO_MS;
      return localGridSampleRates;
    }
    throw new IllegalArgumentException("Sample axis units are not seconds or "
        + "milliseconds.  I don't know how to deal with that.");
  }

  public boolean outputVolume(IParallelContext pc, ToolState toolState, ISeismicVolume output) throws SeisException {
    // TODO Auto-generated method stub
    return false;
  }

  public void parallelFinish(IParallelContext pc, ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub

  }

  public void serialFinish(ToolState toolState) throws SeisException {
    System.out.println("Press Enter to Exit.");
    try{
      System.in.read();
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE,e.getMessage(),e);
    }
  }

  private static ParameterService basicParameters() {

    String inputFileName = "seg45shot.js";
    String outputFileName = "testCheckSource.js";
    String vModelFileName = "segsaltmodel.js";
    ParameterService parms = null;;
    try {
      parms = new FindTestData(inputFileName,outputFileName).getParameterService();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    parms.setParameter("ZMIN", "0");
    parms.setParameter("ZMAX", "100");
    parms.setParameter("DELZ", "20");
    parms.setParameter("PADT", "20");
    parms.setParameter("PADX", "5");
    parms.setParameter("PADY", "5");
    parms.setParameter("FMAX", "6000");
    parms.setParameter("taskCount", "1");
    parms.setParameter("vModelFilePath", vModelFileName);
    parms.setParameter("outputFileMode", "create");

    return parms;
  }

  public static void main(String[] args) {
    ParameterService parms = basicParameters();

    List<String> toolList = new ArrayList<String>();

    toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
    toolList.add(VolumeCorrectionTool.class.getCanonicalName());
    toolList.add(CheckSourceLocations.class.getCanonicalName());
    toolList.add(ExampleVolumeOutputTool.class.getCanonicalName());

    String[] toolArray = Convert.listToArray(toolList);

    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      e.printStackTrace();
    }
  }

}

package org.javaseis.imaging;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.parallel.IParallelContext;

import org.javaseis.grid.GridFromHeaders;
import org.javaseis.grid.GridDefinition;
import org.javaseis.grid.ICheckedGrid;
import org.javaseis.grid.ManualOverrideGrid;
import org.javaseis.grid.VolumeEdgeIO;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.properties.AxisLabel;
import org.javaseis.properties.DataDomain;
import org.javaseis.properties.Units;
import org.javaseis.services.ParameterService;
import org.javaseis.source.ISourceVolume;
import org.javaseis.source.DeltaFunctionSourceVolume;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.IntervalTimer;
import org.javaseis.util.SeisException;
import org.javaseis.velocity.IVelocityModel;
import org.javaseis.velocity.VelocityInDepthModel;
import org.javaseis.velocity.VelocityModelFromFile;
import org.javaseis.volume.ISeismicVolume;
import org.junit.Assert;

/**
 * @author Marcus Wilson 2015
 *
 */
public class ExampleMigration extends StandAloneVolumeTool {

  private static final float S_TO_MS = 1000;
  private static final int[] DEFAULT_FFT_ORIENTATION = new int[] { -1, 1, 1 };
  private static final Logger LOGGER =
      Logger.getLogger(ExampleMigration.class.getName());

  public ExampleMigration() {
  }

  // allows running this tool from the command line, using key/value pairs to
  // fill in the necessary parameters.
  public static void main(String[] args) {
    ParameterService parms = new ParameterService(args);
    try {
      exec(parms, new ExampleMigration());
    } catch (SeisException e) {
      LOGGER.log(Level.SEVERE,e.getMessage(),e);
    }
  }

  //Figure out if the public inputGrid and outputGrid are populated
  //and populate them if they aren't.
  private void checkPublicGrids(ToolContext toolContext) {
    GridDefinition inputGrid = toolContext.inputGrid;
    if (inputGrid == null) {
      LOGGER.severe("The public field toolContext.inputGrid is null, "
          + "doesn't get shared between parallel tasks, and is a huge "
          + "violation of object encapsulation.  You shouldn't use it.");
      inputGrid = (GridDefinition) toolContext
          .getFlowGlobal(ToolContext.INPUT_GRID);
      toolContext.inputGrid = inputGrid;
    }
    GridDefinition outputGrid = toolContext.outputGrid;
    if (outputGrid == null) {
      LOGGER.severe("The public field toolContext.outputGrid is null, "
          + "doesn't get shared between parallel tasks, and is a huge "
          + "violation of object encapsulation.  You shouldn't use it.");
      outputGrid = (GridDefinition) toolContext
          .getFlowGlobal(ToolContext.OUTPUT_GRID);
      toolContext.outputGrid = outputGrid;
    }
  }

  @Override
  public void serialInit(ToolContext toolContext) {
    checkPublicGrids(toolContext);
    // TODO this method should check that toolContext contains enough
    // information to do a basic extrapolation.
    // Run main for more information. (ex: inputGrid returns null)

    // redundant, until we figure out the design of the toolContext
    GridDefinition imageGrid = computeImageGrid(toolContext);
    toolContext.outputGrid = imageGrid;  //This one doesn't get saved.
    toolContext.putFlowGlobal(ToolContext.OUTPUT_GRID, imageGrid);
    saveVolumeEdgesIfTraceHeadersExist(toolContext);
  }

  private GridDefinition computeImageGrid(ToolContext toolContext) {

    GridDefinition inputGrid = toolContext.inputGrid;
    ParameterService parms = toolContext.parms;

    AxisDefinition[] imageAxes =
        new AxisDefinition[inputGrid.getNumDimensions()];

    float zmin = Float.parseFloat(parms.getParameter("ZMIN", "0"));
    float zmax = Float.parseFloat(parms.getParameter("ZMAX", "2000"));
    float delz = Float.parseFloat(parms.getParameter("DELZ", "50"));

    long depthAxisLength = -1;
    depthAxisLength = computeDepthAxis(zmin, delz, zmax);

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

  // Returns Depth Axis Length
  // DeltaZ - represents the iteration interval
  // ZMin - represents the min depth
  // ZMax - represents the max depth
  public long computeDepthAxis(float zMin, float deltaZ, float zMax) {
    if (deltaZ <= 0 || (zMax - zMin) < deltaZ)
      return 1;
    else
      return (long) Math.floor((zMax - zMin) / deltaZ) + 1;
  }

  private void saveVolumeEdgesIfTraceHeadersExist(ToolContext toolContext) {
    Assert.assertNotNull(toolContext.pc);
    IParallelContext pc = toolContext.pc;
    try {
      VolumeEdgeIO vEdgeIO = new VolumeEdgeIO(pc, toolContext);
      vEdgeIO.write();
    } catch (NullPointerException e) {
      LOGGER.info("Input javaseis file has no associated trace header file.\n"
          + "No grid orientation information will be saved.");
    }
  }

  @Override
  public void parallelInit(ToolContext toolContext) {
    Assert.assertNotNull(toolContext.pc);
    IParallelContext pc = toolContext.pc;
    pc = toolContext.getParallelContext();
    //TODO These grids aren't working now
    checkPublicGrids(toolContext);
    LOGGER.info("Starting parallelTimer on task #" + pc.rank() + "\n");
    LOGGER.info("Input Grid Definition:\n" + toolContext.inputGrid + "\n");
    LOGGER.info("Output Grid Definition:\n" + toolContext.outputGrid + "\n");
  }

  @Override
  public boolean processVolume(ToolContext toolContext,
      ISeismicVolume input,ISeismicVolume output) {

    checkPublicGrids(toolContext);
    if (processFirstVolumeOnly(toolContext) && !isFirstVolume(input))
      return false;

    //initialize timers
    IntervalTimer velocityAccessTime = new IntervalTimer();
    IntervalTimer sourceGenTime = new IntervalTimer();
    IntervalTimer singleVolumeTime = new IntervalTimer();

    singleVolumeTime.start();
    LOGGER.info("Processing Volume #"
        + Arrays.toString(input.getVolumePosition()));

    Assert.assertNotNull(input.getGlobalGrid());
    Assert.assertNotNull(output.getGlobalGrid());

    // Instantiate a checked grid which fixes any misplaced receivers
    ICheckedGrid gridFromHeaders = verifyGridOriginsAndDeltas(toolContext, input);

    GridDefinition imageGrid = computeImageGrid(toolContext);
    toolContext.outputGrid = imageGrid;
    Assert.assertNotNull(toolContext.outputGrid);

    //createSeis3dFfts(toolContext, input);

    PhaseShiftFFT3D rcvr = createReceiverFFT(toolContext,input);
    PhaseShiftFFT3D shot = createSourceFFT(rcvr);

    // Initialize Extrapolator
    //Extrapolator extrapolator = new Extrapolator(shotold, rcvrold);

    // TODO Test Phase Shift Extrapolator
    PhaseShiftExtrapolator extrapR = new PhaseShiftExtrapolator(rcvr);
    PhaseShiftExtrapolator extrapS = new PhaseShiftExtrapolator(shot);

    // Initialize Imaging Condition
    ImagingCondition imagingCondition = new ImagingCondition(shot, rcvr,
        output.getDistributedArray());

    //extrapolator.transformFromTimeToFrequency();
    //ISourceVolume srcVolOld = new SourceVolume(gridFromHeaders, shotold);
    //shotold = srcVolOld.getShot();

    // TODO test code
    extrapR.transformFromTimeToFrequency();
    extrapS.transformFromTimeToFrequency();

    // Plot to check
    //DistributedArrayMosaicPlot.showAsModalDialog(shot.getArray(),
    //"Empty Source Array (FXY)");
    //plotSeisInTime(shot,"Empty Source Array (TXY)");

    // This has to be after the time transform.
    ISourceVolume srcVol = new DeltaFunctionSourceVolume(gridFromHeaders, shot);
    shot = srcVol.getShot();

    // Plot to check
    // DistributedArrayMosaicPlot.showAsModalDialog(shot.getArray(),
    // "Raw source signature (FXY)");
    // plotSeisInTime(shot,"Raw source signature (TXY)");

    checkOutputDAIsEmpty(input, output);
    DistributedArray vModelWindowed = (DistributedArray) output
        .getDistributedArray().clone();

    //test different objects have different DAs
    //Assert.assertNotEquals("Receiver wavefields share an array",
    //    rcvr.getArray(),rcvrold.getArray());
    //Assert.assertNotEquals("Source wavefields share an array",
    //    shot.getArray(),shotold.getArray());

    // depth axis information
    double zmin = imageGrid.getAxisPhysicalOrigin(0);
    double delz = imageGrid.getAxisPhysicalDelta(0);
    long numz = imageGrid.getAxisLength(0);
    double fMax = Double.parseDouble(toolContext.parms.getParameter("FMAX"));
    LOGGER.info(String.format("zmin: %6.1f, delz: %6.1f, numz: %4d", zmin,
        delz, numz));

    // Initialize velocity model input
    velocityAccessTime.start();
    IVelocityModel vmff = getVelocityModelObject(toolContext);
    orientSeismicInVelocityModel(vmff, gridFromHeaders);
    velocityAccessTime.stop();

    double velocity;
    for (int zindx = 0; zindx < numz; zindx++) {
      double depth = zmin + delz * zindx;

      velocityAccessTime.start();
      velocity = vmff.readAverageVelocity(depth);
      double[][] windowedSlice = vmff.readSlice(depth);
      velocityAccessTime.stop();

      LOGGER.info("Volume #" + Arrays.toString(input.getVolumePosition()));
      //extrapolator.transformFromSpaceToWavenumber();

      // TODO Test Code
      extrapR.transformFromSpaceToWavenumber();
      extrapS.transformFromSpaceToWavenumber();

      LOGGER.info(String.format("Begin Extrapolation to depth %5.1f."
          + "  Velocity is %5.1f", depth, velocity));

      // TODO test code
      //testDAEquals(rcvr.getArray(),rcvrold.getArray());
      //testDAEquals(shot.getArray(),shotold.getArray());

      extrapR.reverseExtrapolate((float) velocity, delz, zindx, fMax);
      logTimerOutput("Receiver Extrapolator Time: ",
          extrapR.getExtrapolationTime());
      extrapS.forwardExtrapolate((float) velocity, delz, zindx, fMax);
      logTimerOutput("Source Extrapolator Time: ",
          extrapS.getExtrapolationTime());

      // TODO test code
      //testDAEquals(rcvr.getArray(),rcvrold.getArray());
      //testDAEquals(shot.getArray(),shotold.getArray());

      LOGGER.info("Extrapolation finished for depth " + depth);
      extrapR.transformFromWavenumberToSpace();
      extrapS.transformFromWavenumberToSpace();

      {
        //TODO test code - plot to check
        //plotSeisInTime(rcvrold,"Original Receivers - Depth: " + depth);
        //plotSeisInTime(rcvr,"New Receivers - Depth: " + depth);
        //plotSeisInTime(shotold,"Original Source - Depth: " + depth);
        //plotSeisInTime(shot,"New Source - Depth: " + depth);
      }

      LOGGER.info("Applying imaging condition");
      imagingCondition.imagingCondition(output.getDistributedArray(), zindx,
          fMax);
      LOGGER.info("Imaging condition finished.");
      saveWindowedVelocitySlice(windowedSlice, vModelWindowed, zindx);
    }

    LOGGER.info("Processing of volume "
        + Arrays.toString(input.getVolumePosition()) + " complete.");

    vmff.close();
    singleVolumeTime.stop();
    logTimerOutput("Single Volume Time", singleVolumeTime.total());
    Assert.assertTrue((boolean) toolContext
        .getFlowGlobal(ToolContext.HAS_INPUT));
    Assert.assertTrue((boolean) toolContext
        .getFlowGlobal(ToolContext.HAS_OUTPUT));

    {
      //plot to check
      //DistributedArrayMosaicPlot.showAsModalDialog(output.getDistributedArray(),
      //    "Final Image.");
      //DistributedArrayMosaicPlot.showAsModalDialog(vModelWindowed,
      //    "Velocity Model.");

      // example usage of Front End Viewer
      //DAFrontendViewer A = new DAFrontendViewer(output.getDistributedArray());
      // A.setLogicalTraces(75, 125);
      // A.setLogicalDepth(0, 250);
      // A.setLogicalFrame(75, 125);
      //A.show("Final Image.");
    }

    logTimerOutput("Velocity Access Time", velocityAccessTime.total());
    logTimerOutput("Source Generation Time", sourceGenTime.total());
    logTimerOutput("Transform Time",
        extrapS.getTransformTime() + extrapR.getTransformTime());
    logTimerOutput("Extrapolation Time",
        extrapS.getTransformTime() + extrapR.getExtrapolationTime());
    logTimerOutput("Imaging Time", imagingCondition.getImagingTime());

    return true;
  }

  private boolean processFirstVolumeOnly(ToolContext toolContext) {
    return Boolean.parseBoolean(toolContext.getParameter("FIRSTVOLUME"));
  }

  private void plotSeisInTime(PhaseShiftFFT3D seisFFT,String title) {
    seisFFT.inverseTemporal();
    DistributedArrayMosaicPlot.showAsModalDialog(seisFFT.getArray(), title);
    seisFFT.forwardTemporal();
  }

  private void testDAEquals(DistributedArray a, DistributedArray b) {
    Assert.assertArrayEquals("Distributed Arrays are not the same shape",
        a.getShape(), b.getShape());

    int[] position = new int[a.getShape().length];
    int direction = 1;
    int scope = 0;
    float floateps = 1e-7F;
    DistributedArrayPositionIterator dapi;
    dapi = new DistributedArrayPositionIterator(a, position, direction, scope);

    float[] abuff = new float[a.getElementCount()];
    float[] bbuff = new float[b.getElementCount()];
    while (dapi.hasNext()) {
      position = dapi.next();
      a.getSample(abuff, position);
      b.getSample(bbuff, position);
      Assert.assertArrayEquals("Distributed Arrays differ at position: "
          + Arrays.toString(position), abuff, bbuff, floateps);
    }
    LOGGER.info("Distributed Arrays match to error " + floateps);
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

  private boolean isFirstVolume(ISeismicVolume input) {
    return Arrays.equals(input.getVolumePosition(),
        new int[input.getVolumePosition().length]);
  }

  private ICheckedGrid verifyGridOriginsAndDeltas(ToolContext toolContext,
      ISeismicVolume input) {
    ICheckedGrid gridFromHeaders;
    try {
      gridFromHeaders = new GridFromHeaders(input, toolContext);
    } catch (NullPointerException e) {
      LOGGER.info(e.getMessage());
      LOGGER.info("It's possible that the input dataset has no associated,\n"
          + "trace header file, so that trying to open the coordinate\n"
          + "service failed.");
      gridFromHeaders = new ManualOverrideGrid(input, toolContext);
    }

    // Set the Modified Grid = input Grid, since we can't set it in the
    // input
    toolContext.inputGrid = gridFromHeaders.getModifiedGrid();
    return gridFromHeaders;
  }

  private boolean usingTestData(ToolContext toolContext) {
    return toolContext.getParameter("inputFilePath").equals(
        "100a-rawsynthpwaves.js");
  }

  private void saveWindowedVelocitySlice(double[][] windowedSlice,
      DistributedArray vModelWindowed, int zindx) {
    // TODO Auto-generated method stub
    int[] position = new int[vModelWindowed.getDimensions()];
    int direction = 1; // forward
    int scope = 1; // traces

    DistributedArrayPositionIterator dapi;
    dapi = new DistributedArrayPositionIterator(
        vModelWindowed, position,direction, scope);

    while (dapi.hasNext()) {
      position = dapi.next();
      int[] outputPosition = position.clone();
      outputPosition[0] = zindx;
      int[] pos = dapi.getPosition();

      // Pass x = pos[1], y = pos[2];
      double buffer = windowedSlice[pos[1]][pos[2]];
      vModelWindowed.putSample((float) buffer, outputPosition);
    }
  }

  /*
  private void createSeis3dFfts(ToolContext toolContext, ISeismicVolume input) {
    int[] inputShape = input.getLengths();
    float[] pad = getPad(toolContext);
    Assert.assertNotNull(toolContext.pc);
    IParallelContext pc = toolContext.pc;

    Assert.assertNotNull("ParallelContext is null", pc);
    Assert.assertNotNull("Input Shape is null", inputShape);
    Assert.assertNotNull("Pad is null", pad);
    //rcvrold = new SeisFft3dNew(pc, inputShape, pad, DEFAULT_FFT_ORIENTATION);
    //shotold = new SeisFft3dNew(pc, inputShape, pad, DEFAULT_FFT_ORIENTATION);

    // copy the receiver data into the rcvr object
    //rcvrold.getArray().copy(input.getDistributedArray());

    // Specify the sample rates
    double[] sampleRates = computeVolumeSampleRates(input,
        toolContext.inputGrid);

    //rcvrold.setTXYSampleRates(sampleRates);
    //shotold.setTXYSampleRates(sampleRates);
  }
   */

  private PhaseShiftFFT3D createReceiverFFT(
      ToolContext toolContext, ISeismicVolume input) {

    int[] inputShape = input.getLengths();
    float[] pad = getPad(toolContext);
    Assert.assertNotNull(toolContext.pc);
    IParallelContext pc = toolContext.pc;

    Assert.assertNotNull("ParallelContext is null", pc);
    Assert.assertNotNull("Input Shape is null", inputShape);
    Assert.assertNotNull("Pad is null", pad);
    PhaseShiftFFT3D rcvr = new PhaseShiftFFT3D(pc, inputShape, pad,
        DEFAULT_FFT_ORIENTATION);

    // copy the receiver data into the rcvr object
    rcvr.getArray().copy(input.getDistributedArray());

    // Specify the sample rates
    double[] sampleRates = computeVolumeSampleRates(input,
        toolContext.inputGrid);

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

  private float[] getPad(ToolContext toolContext) {
    ParameterService parms = toolContext.parms;
    float padT = Float.parseFloat(parms.getParameter("PADT", "10"));
    float padX = Float.parseFloat(parms.getParameter("PADX", "10"));
    float padY = Float.parseFloat(parms.getParameter("PADY", "10"));
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

  private IVelocityModel getVelocityModelObject(ToolContext toolContext) {
    IVelocityModel vmff = null;
    try {
      vmff = new VelocityModelFromFile(toolContext);
    } catch (FileNotFoundException e1) {
      LOGGER.log(Level.SEVERE, e1.getMessage(), e1);
    }

    // override the default behaviour if we're working on the test data
    // which has no header information.
    if (usingTestData(toolContext)) {
      vmff = new VelocityInDepthModel(new double[] { 0, 1000, 2000 },
          new double[] { 2000, 3800 });
    }
    vmff.open("r");
    return vmff;
  }

  private void orientSeismicInVelocityModel(IVelocityModel vmff,
      ICheckedGrid inputGridObj) {
    vmff.orientSeismicVolume(inputGridObj.getModifiedGrid(),
        inputGridObj.getAxisOrder());
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

  private void logTimerOutput(String timerName, double totalTime) {
    LOGGER.info(String.format("%s: %.2f.", timerName, totalTime));
  }

  @Override
  public boolean outputVolume(ToolContext toolContext, ISeismicVolume output) {
    return false; // does nothing.
  }

  @Override
  public void parallelFinish(ToolContext toolContext) {
    // does nothing
  }

  @Override
  public void serialFinish(ToolContext toolContext) {
    // does nothing
  }
}

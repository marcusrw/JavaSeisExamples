package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.parallel.IParallelContext;

import org.javaseis.examples.plot.DAFrontendViewer;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.examples.tool.ExampleVolumeOutputTool;
import org.javaseis.examples.tool.VolumeCorrectionTool;
import org.javaseis.grid.GridFromHeaders;
import org.javaseis.grid.GridDefinition;
import org.javaseis.grid.ICheckedGrid;
import org.javaseis.grid.JTestCheckedGridNew;
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
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
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
public class VModelCheckSave implements IVolumeTool {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private static final float S_TO_MS = 1000;
  private static final int[] FFT_ORIENTATION = PhaseShiftFFT3D.SEISMIC_FFT_ORIENTATION;

  private static final Logger LOGGER = Logger.getLogger(VModelCheckSave.class.getName());

  public VModelCheckSave() {
  }

  static ParameterService parms;

  // allows running this tool from the command line, using key/value pairs to
  // fill in the necessary parameters.
  public static void main(String[] args) {
    try {
      programTerminates();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private static String[] listToArray(List<String> list) {
    String[] array = new String[list.size()];
    for (int k = 0; k < list.size(); k++) {
      array[k] = list.get(k);
    }
    return array;
  }

  private static void programTerminates() throws FileNotFoundException {
    String inputFileName = "seg45shot.js";
    String outputFileName = "teststack.js";
    String vModelFileName = "segsaltmodel.js";

    try {
      parms = new FindTestData(inputFileName, outputFileName).getParameterService();
      basicParameters(inputFileName, vModelFileName);

      List<String> toolList = new ArrayList<String>();

      toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
      toolList.add(VolumeCorrectionTool.class.getCanonicalName());
      toolList.add(VModelCheckSave.class.getCanonicalName());
      toolList.add(ExampleVolumeOutputTool.class.getCanonicalName());

      String[] toolArray = listToArray(toolList);

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

  private static void basicParameters(String inputFileName, String vModelFileName) {
    parms.setParameter("ZMIN", "0");
    parms.setParameter("ZMAX", "4000");
    parms.setParameter("DELZ", "20");
    parms.setParameter("PADT", "20");
    parms.setParameter("PADX", "5");
    parms.setParameter("PADY", "5");
    parms.setParameter("FMAX", "6000");
    parms.setParameter("vModelFilePath", vModelFileName);
    parms.setParameter("outputFileMode", "create");
    parms.setParameter("TASK_COUNT", "1");
  }

  // Figure out if the public inputGrid and outputGrid are populated
  // and populate them if they aren't.
  private void checkPublicGrids(ToolState toolContext) {
    GridDefinition inputGrid = toolContext.getInputState().gridDefinition;
    if (inputGrid == null) {
      throw new IllegalArgumentException("Input Grid is Null");
      /*
       * LOGGER.severe("The public field toolContext.inputGrid is null, " +
       * "doesn't get shared between parallel tasks, and is a huge " +
       * "violation of object encapsulation.  You shouldn't use it."); inputGrid
       * = (GridDefinition) toolContext .getFlowGlobal(ToolState.);
       * toolContext.inputGrid = inputGrid;
       */
    }
    // GridDefinition outputGrid = toolContext.outputGrid;
    GridDefinition outputGrid = toolContext.getOutputState().gridDefinition;
    if (outputGrid == null) {
      throw new IllegalArgumentException("Output Grid is Null");
      /*
       * LOGGER.severe("The public field toolContext.outputGrid is null, " +
       * "doesn't get shared between parallel tasks, and is a huge " +
       * "violation of object encapsulation.  You shouldn't use it.");
       * outputGrid = (GridDefinition) toolContext
       * .getFlowGlobal(ToolState.OUTPUT_GRID); toolContext.outputGrid =
       * outputGrid;
       */
    }
  }

  @Override
  public void serialInit(ToolState toolContext) {
    // TODO: Fix if not working
    checkPublicGrids(toolContext);
    // TODO this method should check that toolContext contains enough
    // information to do a basic extrapolation.
    // Run main for more information. (ex: inputGrid returns null)

    // redundant, until we figure out the design of the toolContext
    GridDefinition imageGrid = computeImageGrid(toolContext);
    // toolContext.outputGrid = imageGrid; // This one doesn't get saved.
    toolContext.getOutputState().gridDefinition = imageGrid;
    // toolContext.putFlowGlobal(ToolState.OUTPUT_GRID, imageGrid);
    // saveVolumeEdgesIfTraceHeadersExist(toolContext);
  }

  private GridDefinition computeImageGrid(ToolState toolContext) {

    GridDefinition inputGrid = toolContext.getInputState().gridDefinition;

    // May Require further fix
    // ParameterService parms = toolContext.parms;

    Assert.assertNotNull(inputGrid);
    // Assert.assertNotNull(parms);

    AxisDefinition[] imageAxes = new AxisDefinition[inputGrid.getNumDimensions()];

    // float zmin = Float.parseFloat(parms.getParameter("ZMIN", "0"));
    // float zmax = Float.parseFloat(parms.getParameter("ZMAX", "2000"));
    // float delz = Float.parseFloat(parms.getParameter("DELZ", "50"));

    float zmin = Float.parseFloat(toolContext.getParameter("ZMIN"));
    float zmax = Float.parseFloat(toolContext.getParameter("ZMAX"));
    float delz = Float.parseFloat(toolContext.getParameter("DELZ"));

    long depthAxisLength = -1;
    depthAxisLength = computeDepthAxisLength(zmin, delz, zmax);

    // Iterate over the axes
    for (int axis = 0; axis < imageAxes.length; axis++) {
      if (axis == 0) {
        imageAxes[axis] = new AxisDefinition(AxisLabel.DEPTH, Units.METERS, DataDomain.SPACE, depthAxisLength, 0, 1,
            zmin, delz);
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

  /*
   * private void saveVolumeEdgesIfTraceHeadersExist(ToolState toolContext) {
   * Assert.assertNotNull(toolContext.pc); IParallelContext pc = toolContext.pc;
   * try { VolumeEdgeIO vEdgeIO = new VolumeEdgeIO(pc, toolContext);
   * vEdgeIO.write(); } catch (NullPointerException e) { LOGGER.info(
   * "Input javaseis file has no associated trace header file.\n" +
   * "No grid orientation information will be saved."); } }
   */

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolContext) {
    Assert.assertNotNull(pc);
    // TODO These grids aren't working now
    checkPublicGrids(toolContext);
    LOGGER.info("Starting parallelTimer on task #" + pc.rank() + "\n");
    LOGGER.info("Input Grid Definition:\n" + toolContext.getInputState().gridDefinition + "\n");
    LOGGER.info("Output Grid Definition:\n" + toolContext.getInputState().gridDefinition + "\n");
  }

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolContext, ISeismicVolume input,
      ISeismicVolume output) {

    checkPublicGrids(toolContext);
    // if (processFirstVolumeOnly(toolContext) && !isFirstVolume(input))
    // return false;

    // initialize timers
    IntervalTimer velocityAccessTime = new IntervalTimer();
    IntervalTimer sourceGenTime = new IntervalTimer();
    IntervalTimer singleVolumeTime = new IntervalTimer();

    singleVolumeTime.start();
    // LOGGER.info("Processing Volume #"
    // + Arrays.toString(input.getVolumePosition()));

    Assert.assertNotNull(input.getGlobalGrid());
    Assert.assertNotNull(output.getGlobalGrid());

    // Instantiate a checked grid which fixes any misplaced receivers
    // ICheckedGrid gridFromHeaders = verifyGridOriginsAndDeltas(toolContext,
    // input);

    GridDefinition imageGrid = computeImageGrid(toolContext);
    toolContext.getOutputState().gridDefinition = imageGrid;
    Assert.assertNotNull(toolContext.getOutputState().gridDefinition);

    PhaseShiftFFT3D rcvr = createReceiverFFT(pc, toolContext, input);
    PhaseShiftFFT3D shot = createSourceFFT(rcvr);

    // get any source/receiver XYZ, because we just need the depth.
    // this position needs to be as big as the global grid, and have
    // the right volume associated with it. That's probably dumb.
    // the checkgrids object should probably handle that sort of thing.
    // int[] gridPos = input.getVolumePosition();
    int[] gridPos = new int[] { 0, 0, 0 };
    double[] sxyz = new double[3];
    double[] rxyz = new double[3];
    input.getCoords(gridPos, sxyz, rxyz);
    // double receiverDepth = gridFromHeaders.getReceiverXYZ(gridPos)[2];
    // double sourceDepth = gridFromHeaders.getSourceXYZ()[2];

    double receiverDepth = sxyz[2];
    double sourceDepth = rxyz[2];

    PhaseShiftExtrapolator extrapR = new PhaseShiftExtrapolator(rcvr, receiverDepth);
    PhaseShiftExtrapolator extrapS = new PhaseShiftExtrapolator(shot, sourceDepth);

    // Initialize Imaging Condition
    ImagingCondition imagingCondition = new ImagingCondition(shot, rcvr, output.getDistributedArray());

    extrapR.transformFromTimeToFrequency();
    extrapS.transformFromTimeToFrequency();

    // This has to be after the time transform.

    // TODO: FIX
    ISourceVolume srcVol = new DeltaFunctionSourceVolume(toolContext.getInputState(),
        input, shot);
    shot = srcVol.getShot();

    // Plot to check
    // shot.plotInTime("Raw source signature (TXY)");

    // Probably don't need that
    // checkOutputDAIsEmpty(input, output);

    DistributedArray vModelWindowed = (DistributedArray) output.getDistributedArray().clone();

    // depth axis information
    double zmin = imageGrid.getAxisPhysicalOrigin(0);
    double delz = imageGrid.getAxisPhysicalDelta(0);
    long numz = imageGrid.getAxisLength(0);
    double fMax = Double.parseDouble(toolContext.getParameter("FMAX"));
    LOGGER.info(String.format("zmin: %6.1f, delz: %6.1f, numz: %4d", zmin, delz, numz));

    // Initialize velocity model input
    velocityAccessTime.start();
    IVelocityModel vmff = getVelocityModelObject(pc, toolContext);
    // TODO: Probably don't need to orient
    // orientSeismicInVelocityModel(vmff, gridFromHeaders);
    velocityAccessTime.stop();

    double velocity;
    for (int zindx = 0; zindx < numz; zindx++) {
      double depth = zmin + delz * zindx;

      velocityAccessTime.start();
      velocity = vmff.readAverageVelocity(depth);
      double[][] windowedSlice = vmff.readSlice(depth);
      velocityAccessTime.stop();

      // LOGGER.info("Volume #" + Arrays.toString(input.getVolumePosition()));

      // extrapR.transformFromSpaceToWavenumber();
      // extrapS.transformFromSpaceToWavenumber();

      LOGGER.info(String.format("Begin Extrapolation to depth %5.1f." + "  Velocity is %5.1f", depth, velocity));

      // extrapR.reverseExtrapolate((float) velocity, delz, zindx, fMax);
      // logTimerOutput("Receiver Extrapolator Time: ",
      // extrapR.getExtrapolationTime());
      // extrapS.forwardExtrapolate((float) velocity, delz, zindx, fMax);
      // logTimerOutput("Source Extrapolator Time: ",
      // extrapS.getExtrapolationTime());

      LOGGER.info("Extrapolation finished for depth " + depth);

      // extrapR.transformFromWavenumberToSpace();
      // extrapS.transformFromWavenumberToSpace();

      {
        // TODO test code - plot to check
        // rcvr.plotInTime("New receivers - Depth: " + depth);
        // shot.plotInTime("New Source - Depth: " + depth);
      }

      LOGGER.info("Applying imaging condition");
      // imagingCondition.imagingCondition(output.getDistributedArray(), zindx,
      // fMax);
      LOGGER.info("Imaging condition finished.");

      // save relevant portion of velocity model for comparison
      saveWindowedVelocitySlice(windowedSlice, vModelWindowed, zindx);
    }

    // LOGGER.info("Processing of volume "
    // + Arrays.toString(input.getVolumePosition()) + " complete.");

    vmff.close();
    singleVolumeTime.stop();
    logTimerOutput("Single Volume Time", singleVolumeTime.total());
    assert(boolean) toolContext.getFlowGlobal(ToolState.HAS_INPUT);
    assert(boolean) toolContext.getFlowGlobal(ToolState.HAS_OUTPUT);

    {
      // plot to check
      // DistributedArrayMosaicPlot.showAsModalDialog(output.getDistributedArray(),
      // "Final Image.");
      // DistributedArrayMosaicPlot.showAsModalDialog(vModelWindowed,
      // "Velocity Model.");

      // example usage of Front End Viewer
      // String title = "Volume #" + input.getVolumePosition()[3];
      //String title = "VoL";
      //DAFrontendViewer A = new DAFrontendViewer(vModelWindowed, toolContext);
      // A.setLogicalFrame(10, 190);
      //A.show(title);
      //try {
      //  Thread.sleep(50);
      //} catch (InterruptedException e) {
        // TODO Auto-generated catch block
      //  e.printStackTrace();
      //}
    }

    logTimerOutput("Velocity Access Time", velocityAccessTime.total());
    logTimerOutput("Source Generation Time", sourceGenTime.total());
    logTimerOutput("Transform Time", extrapR.getTransformTime() + extrapS.getTransformTime());
    logTimerOutput("Extrapolation Time", extrapR.getExtrapolationTime() + extrapS.getExtrapolationTime());
    logTimerOutput("Imaging Time", imagingCondition.getImagingTime());
    output.getDistributedArray().putSample(new float[] { 1 }, new int[] { 0, 0, 0, 0 });

    Assert.assertArrayEquals(output.getDistributedArray().getShape(), vModelWindowed.getShape());
    Assert.assertFalse(distributedArrayIsEmpty(vModelWindowed));
    output.getDistributedArray().copy(vModelWindowed);
    vModelWindowed = null;
    return true;
  }

  // TODO debug code
  private boolean processFirstVolumeOnly(ToolState toolContext) {
    return Boolean.parseBoolean(toolContext.getParameter("FIRSTVOLUME"));
  }

  /*
   * private void checkOutputDAIsEmpty(ISeismicVolume input, ISeismicVolume
   * output) { if (distributedArrayIsEmpty(output.getDistributedArray())) { //
   * Should only be true when we're on the first volume, until the // tool is
   * fixed. if (!isFirstVolume(input)) { LOGGER.info("Is first volume: " +
   * isFirstVolume(input)); //LOGGER.info("Current Volume: " // +
   * Arrays.toString(input.getVolumePosition())); //LOGGER.info("First Volume: "
   * // + Arrays.toString(new int[input.getVolumePosition().length]));
   * 
   * throw new IllegalArgumentException("The distributed array is" +
   * " already empty, so the next step is a waste of time."); } else {
   * LOGGER.info("First volume output is empty, as expected."); } }
   * 
   * output.getDistributedArray().zeroCompletely();
   * 
   * // Make sure the output DA is empty. if
   * (!distributedArrayIsEmpty(output.getDistributedArray())) { throw new
   * IllegalArgumentException("Why is the output not empty?"); } }
   */

  /*
   * // TODO debugging code that allows different treatment for volume 1 private
   * boolean isFirstVolume(ISeismicVolume input) { return
   * Arrays.equals(input.getVolumePosition(), new
   * int[input.getVolumePosition().length]); }
   * 
   * // TODO contains testing code for using test dataset private ICheckedGrid
   * verifyGridOriginsAndDeltas(ToolState toolContext, ISeismicVolume input) {
   * ICheckedGrid gridFromHeaders; try { gridFromHeaders = new
   * GridFromHeaders(input, toolContext); } catch (NullPointerException e) {
   * LOGGER.info(e.getMessage()); LOGGER.info(
   * "It's possible that the input dataset has no associated,\n" +
   * "trace header file, so that trying to open the coordinate\n" +
   * "service failed."); gridFromHeaders = new ManualOverrideGrid(input,
   * toolContext); }
   * 
   * // Set the Modified Grid = input Grid, since we can't set it in the //
   * input toolContext.inputGrid = gridFromHeaders.getModifiedGrid(); return
   * gridFromHeaders; }
   */

  // TODO temporary. For testing.
  private boolean usingTestData(ToolState toolContext) {
    return toolContext.getParameter("inputFilePath").equals("100a-rawsynthpwaves.js");
  }

  private void saveWindowedVelocitySlice(double[][] windowedSlice, DistributedArray vModelWindowed, int zindx) {
    int[] position = new int[vModelWindowed.getDimensions()];
    int direction = 1; // forward
    int scope = 1; // traces

    DistributedArrayPositionIterator dapi;
    dapi = new DistributedArrayPositionIterator(vModelWindowed, position, direction, scope);

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

  private PhaseShiftFFT3D createReceiverFFT(IParallelContext pc, ToolState toolContext, ISeismicVolume input) {

    int[] inputShape = input.getLengths();
    float[] pad = getPad(toolContext);
    Assert.assertNotNull(pc);

    Assert.assertNotNull("ParallelContext is null", pc);
    Assert.assertNotNull("Input Shape is null", inputShape);
    Assert.assertNotNull("Pad is null", pad);
    PhaseShiftFFT3D rcvr = new PhaseShiftFFT3D(pc, inputShape, pad, FFT_ORIENTATION);

    // copy the receiver data into the rcvr object
    rcvr.getArray().copy(input.getDistributedArray());

    // Specify the sample rates
    // double[] sampleRates = computeVolumeSampleRates(input,
    // toolContext.inputGrid);

    double[] sampleRates = computeVolumeSampleRates(input, toolContext.getInputState().gridDefinition);

    rcvr.setTXYSampleRates(sampleRates);
    LOGGER.info(
        "Created transformable receiver wavefield with sample rates: " + Arrays.toString(rcvr.getTXYSampleRates()));
    return rcvr;
  }

  private PhaseShiftFFT3D createSourceFFT(PhaseShiftFFT3D receiverFFT) {

    PhaseShiftFFT3D sourceFFT = new PhaseShiftFFT3D(receiverFFT);
    sourceFFT.getArray().zeroCompletely();
    sourceFFT.setTXYSampleRates(receiverFFT.getTXYSampleRates());
    LOGGER.info(
        "Created transformable source wavefield with sample rates: " + Arrays.toString(sourceFFT.getTXYSampleRates()));

    return sourceFFT;
  }

  private float[] getPad(ToolState toolContext) {
    // May need better fixes
    // ParameterService parms = toolContext.parms;
    // float padT = Float.parseFloat(parms.getParameter("PADT", "10"));
    // float padX = Float.parseFloat(parms.getParameter("PADX", "10"));
    // float padY = Float.parseFloat(parms.getParameter("PADY", "10"));

    float padT = Float.parseFloat(toolContext.getParameter("PADT"));
    float padX = Float.parseFloat(toolContext.getParameter("PADX"));
    float padY = Float.parseFloat(toolContext.getParameter("PADY"));
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
    DistributedArrayPositionIterator dapi = new DistributedArrayPositionIterator(da, position, direction, scope);
    while (dapi.hasNext()) {
      position = dapi.next();
      da.getSample(buffer, position);
      for (float element : buffer) {
        if (element != 0) {
          LOGGER.info("DA is not empty at position: " + Arrays.toString(position));
          return false;
        }
      }
    }
    return true;
  }

  private IVelocityModel getVelocityModelObject(IParallelContext pc, ToolState toolContext) {
    IVelocityModel vmff = null;
    try {
      vmff = new VelocityModelFromFile(pc, toolContext);
    } catch (FileNotFoundException e1) {
      LOGGER.log(Level.SEVERE, e1.getMessage(), e1);
    }

    // override the default behaviour if we're working on the test data
    // which has no header information.
    if (usingTestData(toolContext)) {
      vmff = new VelocityInDepthModel(new double[] { 0, 1000, 2000 }, new double[] { 2000, 3800 });
    }
    vmff.open("r");
    return vmff;
  }

  private void orientSeismicInVelocityModel(IVelocityModel vmff, ICheckedGrid inputGridObj) {
    vmff.orientSeismicVolume(inputGridObj.getModifiedGrid(), inputGridObj.getAxisOrder());
  }

  private double[] computeVolumeSampleRates(ISeismicVolume input, GridDefinition grid) {
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
    throw new IllegalArgumentException(
        "Sample axis units are not seconds or " + "milliseconds.  I don't know how to deal with that.");
  }

  private void logTimerOutput(String timerName, double totalTime) {
    LOGGER.info(String.format("%s: %.2f.", timerName, totalTime));
  }

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

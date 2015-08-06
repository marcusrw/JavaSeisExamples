package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.parallel.IParallelContext;

import org.javaseis.examples.scratch.SeisFft3dNew;
import org.javaseis.examples.scratch.MigrationFFT3D;
import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.examples.plot.SingleVolumeDAViewer;
import org.javaseis.grid.GridDefinition;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.properties.AxisLabel;
import org.javaseis.properties.DataDomain;
import org.javaseis.properties.Units;
import org.javaseis.services.ParameterService;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.IntervalTimer;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.junit.Assert;

/**
 * @author Marcus Wilson 2015
 *
 */
public class ExampleMigration extends StandAloneVolumeTool {

  private static final float S_TO_MS = 1000;
  private static final int[] DEFAULT_FFT_ORIENTATION =
      new int[] {-1,1,1};

  private static final Logger LOGGER = 
      Logger.getLogger(ExampleMigration.class.getName());

  int volumeCount;
  IParallelContext pc;

  //Is having rcvr and shot as member variables going to cause
  //some sort of race condition?
  private SeisFft3dNew rcvr,shot;
  private long[] transformAxisLengths;
  private DataDomain[] transformDomains;
  private AxisDefinition[] transformAxes;
  private GridDefinition imageGrid;

  //TODO only for visual checks.  Delete later.
  private GridDefinition transformGrid;
  private SingleVolumeDAViewer display;
  private boolean debug;

  private GridDefinition inputGrid;
  private SeisFft3dNew rcvr2;
  private SeisFft3dNew shot2;

  public ExampleMigration() {
  }

  //allows running this tool from the command line, using key/value pairs to
  //fill in the necessary parameters.
  public static void main(String[] args) {
    ParameterService parms = new ParameterService(args);
    try {
      exec(parms, new ExampleMigration());
    } catch (SeisException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Override
  public void serialInit(ToolContext toolContext) {
    ParameterService parms = toolContext.parms;
    debug = debugIsOn(parms);
    //TODO this method should check that toolContext contains enough
    // information to do a basic extrapolation.
    // Run main for more information. (ex: inputGrid returns null)
    GridDefinition inputGrid = toolContext.inputGrid;
    if (inputGrid == null) {
      inputGrid = (GridDefinition) toolContext.getFlowGlobal(
          ToolContext.INPUT_GRID);
      toolContext.inputGrid = inputGrid;
    }

    imageGrid = computeImageGrid(toolContext.inputGrid,toolContext.parms);
    transformGrid = computeTransformAxes(toolContext);
    //redundant, until we figure out the design of the toolContext
    toolContext.outputGrid = imageGrid;
    toolContext.putFlowGlobal(ToolContext.OUTPUT_GRID,imageGrid);

    saveVolumeEdgesIfTraceHeadersExist(toolContext);
  }

  private void saveVolumeEdgesIfTraceHeadersExist(ToolContext toolContext) {
    try {
      VolumeEdgeIO vEdgeIO = new VolumeEdgeIO(pc, toolContext);
      vEdgeIO.write();
    } catch (NullPointerException e) {
      LOGGER.info("Input javaseis file has no associated trace header file.\n"
          + "No grid orientation information will be saved.");
    }
  }

  private boolean debugIsOn(ParameterService parms) {
    debug = Boolean.parseBoolean(parms.getParameter("DEBUG","FALSE"));
    if (debug) {
      LOGGER.info("RUNNING IN DEBUG MODE");
    }
    return debug;
  }

  //Returns Depth Axis Length
  //DeltaZ - represents the iteration interval
  //ZMin - represents the min depth
  //ZMax - represents the max depth
  public long computeDepthAxis(float zMin, float deltaZ, float zMax){
    if ( deltaZ <= 0 || (zMax - zMin) < deltaZ)
      return 1;
    else
      return (long) Math.floor((zMax - zMin)/deltaZ) + 1;
  }

  private GridDefinition computeImageGrid(GridDefinition inputGrid,
      ParameterService parms) {

    AxisDefinition[] imageAxes = new AxisDefinition[inputGrid.getNumDimensions()];

    float zmin = Float.parseFloat(parms.getParameter("ZMIN","0"));
    float zmax = Float.parseFloat(parms.getParameter("ZMAX","2000"));
    float delz = Float.parseFloat(parms.getParameter("DELZ","50"));

    long depthAxisLength = -1;
    depthAxisLength = computeDepthAxis(zmin, delz, zmax);

    //Iterate over the axes
    for (int axis = 0 ; axis < imageAxes.length ; axis++) {
      if (axis == 0) {
        imageAxes[axis] = new AxisDefinition(
            AxisLabel.DEPTH,
            Units.METERS,
            DataDomain.SPACE,
            depthAxisLength,
            0,1,
            zmin,delz
            );
      } else {
        imageAxes[axis] = inputGrid.getAxis(axis);
      }
    }

    return new GridDefinition(imageAxes.length,imageAxes);
  }

  private float[] getPad(ParameterService parms) {
    float padT = Float.parseFloat(parms.getParameter("PADT","10"));
    float padX = Float.parseFloat(parms.getParameter("PADX","10"));
    float padY = Float.parseFloat(parms.getParameter("PADY","10"));
    float[] pad = new float[] {padT,padX,padY};
    return pad;
  }

  private GridDefinition computeTransformAxes(ToolContext toolContext) {
    inputGrid = toolContext.inputGrid;
    long[] inputAxisLengths = inputGrid.getAxisLengths();
    if (inputAxisLengths.length < 3) {
      throw new IllegalArgumentException("Input dataset is not big "
          + "enough for a Volumetool");
    }
    transformAxisLengths = Arrays.copyOf(inputAxisLengths,
        inputAxisLengths.length);
    int[] inputVolumeLengths = new int[3];
    for (int k = 0 ; k < 3 ; k++) {
      inputVolumeLengths[k] = (int)inputAxisLengths[k];
    }
    pc = toolContext.pc;
    float[] pad = getPad(toolContext.parms);
    Assert.assertNotNull(pc);
    Assert.assertNotNull(inputVolumeLengths);
    Assert.assertNotNull(pad);
    Assert.assertNotNull(DEFAULT_FFT_ORIENTATION);
    rcvr = new SeisFft3dNew(pc,inputVolumeLengths,
        pad,DEFAULT_FFT_ORIENTATION);

    //determine shape in KyKxF domain
    for (int k = 0 ; k < 3 ; k++) {
      transformAxisLengths[k] = rcvr.getFftShape()[k];
    }

    transformAxes = new AxisDefinition[inputAxisLengths.length];
    transformDomains = findTransformDomains(inputGrid.getAxisDomains());
    for (int k = 0 ; k < inputAxisLengths.length ; k++) {
      AxisDefinition inputAxis = inputGrid.getAxis(k);
      transformAxes[k] = new AxisDefinition(inputAxis.getLabel(),
          inputAxis.getUnits(),
          transformDomains[k],
          transformAxisLengths[k],
          inputAxis.getLogicalOrigin(),
          inputAxis.getLogicalDelta(),
          inputAxis.getPhysicalOrigin(),
          inputAxis.getPhysicalDelta());
    }

    return new GridDefinition(inputGrid.getNumDimensions(),transformAxes);
  }

  private DataDomain[] findTransformDomains(DataDomain[] inputAxisDomains) {
    for (int k = 0 ; k < inputAxisDomains.length ; k++) {
      switch (inputAxisDomains[k].toString()) {
      case "time":
        inputAxisDomains[k] = new DataDomain("frequency");
        break;
      case "frequency":
        inputAxisDomains[k] = new DataDomain("time");
        break;
      case "space":
        inputAxisDomains[k] = new DataDomain("wavenumber");
        break;
      case "wavenumber":
        inputAxisDomains[k] = new DataDomain("space");
        break;
      default:
        break; //don't change anything if the domain is anything else
      }
    }
    return inputAxisDomains;
  }

  @Override
  public void parallelInit(ToolContext toolContext) {
    pc = toolContext.getParallelContext();
    LOGGER.info("Starting parallelTimer on task #" + pc.rank() + "\n");
    LOGGER.info("Input Grid Definition:\n" + toolContext.inputGrid + "\n");
    LOGGER.info("Output Grid Definition:\n" + toolContext.outputGrid + "\n");
  }

  @Override
  public boolean processVolume(ToolContext toolContext, ISeismicVolume input,
      ISeismicVolume output) {


    IntervalTimer velocityAccessTime = new IntervalTimer();
    IntervalTimer sourceGenTime = new IntervalTimer();
    IntervalTimer singleVolumeTime = new IntervalTimer();
    singleVolumeTime.start();
    LOGGER.info("[start processVolume on Volume #"
        +Arrays.toString(input.getVolumePosition()));

    //Instantiate a checked grid which fixes any misplaced receivers
    ICheckGrids gridFromHeaders =
        verifyGridOriginsAndDeltas(toolContext, input);

    pc = toolContext.getParallelContext();
    //Assert.assertNotNull(toolContext.outputGrid);
    imageGrid = computeImageGrid(toolContext.inputGrid,toolContext.parms);
    transformGrid = computeTransformAxes(toolContext);
    toolContext.outputGrid = imageGrid;

    createSeis3dFfts(toolContext,input);

    //Initialize Extrapolator
    Extrapolator extrapolator = new Extrapolator(shot,rcvr);

    //TODO Test Phase Shift Extrapolator
    PhaseShiftExtrapolator extrapR = new PhaseShiftExtrapolator(rcvr2);
    PhaseShiftExtrapolator extrapS = new PhaseShiftExtrapolator(shot2);

    //Initialize Imaging Condition
    ImagingCondition imagingCondition = new ImagingCondition(shot,rcvr,
        output.getDistributedArray());

    extrapolator.transformFromTimeToFrequency();

    //TODO test code
    extrapR.transformFromTimeToFrequency();
    extrapS.transformFromTimeToFrequency();

    //This has to be after the time transform.
    ISourceVolume srcVol = new SourceVolume(gridFromHeaders, shot);
    shot = srcVol.getShot();

    //TODO test code
    ISourceVolume srcVol2 = new SourceVolume(gridFromHeaders, shot2);
    shot2 = srcVol2.getShot();

    //Plot to check
    //if (debugIsOn(toolContext.parms)) {
    //  DistributedArrayMosaicPlot.showAsModalDialog(shot.getArray(),
    //      "Raw source signature");
    //}

    DistributedArray vModelWindowed = checkOutputDAIsEmpty(input, output);

    //depth axis information
    double zmin = imageGrid.getAxisPhysicalOrigin(0);
    double delz = imageGrid.getAxisPhysicalDelta(0);
    long numz = imageGrid.getAxisLength(0);
    double fMax = Double.parseDouble(
        toolContext.parms.getParameter("FMAX"));
    LOGGER.info(String.format("zmin: %6.1f, delz: %6.1f, numz: %4d",
        zmin,delz,numz));

    //Initialize velocity model input
    velocityAccessTime.start();
    IVelocityModel vmff = getVelocityModelObject(toolContext);
    orientSeismicInVelocityModel(vmff,gridFromHeaders);
    velocityAccessTime.stop();

    double velocity;
    for (int zindx = 0 ; zindx < numz ; zindx++) {
      double depth = zmin+delz*zindx;

      velocityAccessTime.start();
      velocity = vmff.readAverageVelocity(depth);
      double[][] windowedSlice = vmff.readSlice(depth);
      velocityAccessTime.stop();

      LOGGER.info("Volume #" + Arrays.toString(input.getVolumePosition()));
      extrapolator.transformFromSpaceToWavenumber();

      //TODO Test Code
      extrapR.transformFromSpaceToWavenumber();
      extrapS.transformFromSpaceToWavenumber();

      LOGGER.info(
          String.format("Begin Extrapolation to depth %5.1f."
              + "  Velocity is %5.1f",depth,velocity));

      //TODO test code
      //testDAEquals(rcvr.getArray(),rcvr2.getArray());
      //testDAEquals(shot.getArray(),shot2.getArray());      

      extrapolator.extrapolate((float)velocity, delz, zindx,fMax);
      logTimerOutput("Double Extrapolator Time: ",
          extrapolator.getExtrapolationTime());

      //TODO test code
      extrapR.reverseExtrapolate((float)velocity, delz, zindx,fMax);
      logTimerOutput("Receiver Extrapolator Time: ",
          extrapR.getExtrapolationTime());
      extrapS.forwardExtrapolate((float)velocity, delz, zindx,fMax); 
      logTimerOutput("Source Extrapolator Time: ",
          extrapS.getExtrapolationTime());


      //TODO test code
      //testDAEquals(rcvr.getArray(),rcvr2.getArray());
      //testDAEquals(shot.getArray(),shot2.getArray());

      LOGGER.info("Extrapolation finished");
      extrapolator.transformFromWavenumberToSpace();

      //TODO Test Code
      extrapR.transformFromWavenumberToSpace();
      extrapS.transformFromWavenumberToSpace();

      /*
      if (debugIsOn(toolContext.parms)) {
        rcvr.inverseTemporal();
        display = new SingleVolumeDAViewer(rcvr.getArray(),inputGrid);
        display.showAsModalDialog();
        rcvr.forwardTemporal();

        shot.inverseTemporal();
        display = new SingleVolumeDAViewer(shot.getArray(),inputGrid);
        display.showAsModalDialog();
        shot.forwardTemporal();
      }
       */

      LOGGER.info("Applying imaging condition");
      imagingCondition.imagingCondition(output.getDistributedArray(),
          zindx,fMax);
      LOGGER.info("Imaging condition finished.");
      saveWindowedVelocitySlice(windowedSlice,vModelWindowed,zindx);
    }



    LOGGER.info("Processing of volume "
        + Arrays.toString(input.getVolumePosition())
        + " complete.");

    vmff.close();
    singleVolumeTime.stop();
    logTimerOutput("Single Volume Time",singleVolumeTime.total());
    Assert.assertTrue((boolean)toolContext.getFlowGlobal(ToolContext.HAS_INPUT));
    Assert.assertTrue((boolean)toolContext.getFlowGlobal(ToolContext.HAS_OUTPUT));

    if (debugIsOn(toolContext.parms)) {

      DistributedArrayMosaicPlot.showAsModalDialog(output.getDistributedArray(),
          "Final Image.");
      DistributedArrayMosaicPlot.showAsModalDialog(vModelWindowed,
          "Velocity Model.");

    }

    logTimerOutput("Velocity Access Time",velocityAccessTime.total());
    logTimerOutput("Source Generation Time",sourceGenTime.total());
    logTimerOutput("Transform Time",extrapolator.getTransformTime());
    logTimerOutput("Extrapolation Time",extrapolator.getExtrapolationTime());
    logTimerOutput("Imaging Time",imagingCondition.getImagingTime());

    return true;
  }

  private void testDAEquals(DistributedArray a,DistributedArray b) {
    Assert.assertArrayEquals("Distributed Arrays are not the same shape",
        a.getShape(),b.getShape());

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
      a.getSample(abuff,position);
      b.getSample(bbuff,position);
      Assert.assertArrayEquals("Distributed Arrays differ at position: "
          + Arrays.toString(position),abuff,bbuff,floateps);
    }
    LOGGER.info("Distributed Arrays match to error " + floateps);
  }

  private DistributedArray checkOutputDAIsEmpty(ISeismicVolume input,
      ISeismicVolume output) {
    if (distributedArrayIsEmpty(output.getDistributedArray())) {
      //Should only be true when we're on the first volume, until the tool
      //is fixed.
      if (!Arrays.equals(input.getVolumePosition(),
          new int[input.getGlobalGrid().getNumDimensions()])) {

        throw new IllegalArgumentException("The distributed array is"
            + " already empty, so the next step is a waste of time.");
      } else {
        LOGGER.config("First volume output is empty, as expected.");
      }
    }    

    output.getDistributedArray().zeroCompletely();
    DistributedArray vModelWindowed =
        (DistributedArray)output.getDistributedArray().clone();

    //Make sure the output DA is empty.
    if (!distributedArrayIsEmpty(output.getDistributedArray())) {
      throw new IllegalArgumentException("Why is the output not empty?");
    }
    return vModelWindowed;
  }

  private ICheckGrids verifyGridOriginsAndDeltas(ToolContext toolContext,
      ISeismicVolume input) {
    ICheckGrids gridFromHeaders;
    try{
      gridFromHeaders = new CheckGrids(input, toolContext);
    } catch (NullPointerException e){
      LOGGER.info(e.getMessage());
      LOGGER.info("It's possible that the input dataset has no associated,\n"
          + "trace header file, so that trying to open the coordinate\n"
          + "service failed.");
      gridFromHeaders = new ManualGrid(input, toolContext); 
    }

    //Set the Modified Grid = input Grid, since we can't set it in the input
    toolContext.inputGrid = gridFromHeaders.getModifiedGrid();
    return gridFromHeaders;
  }

  private boolean usingTestData(ToolContext toolContext) {
    return toolContext.getParameter("inputFilePath")
        .equals("100a-rawsynthpwaves.js");
  }

  private void saveWindowedVelocitySlice(double[][] windowedSlice,
      DistributedArray vModelWindowed, int zindx) {
    // TODO Auto-generated method stub
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

      //Pass x = pos[1], y = pos[2];
      double buffer = windowedSlice[pos[1]][pos[2]];
      vModelWindowed.putSample((float)buffer, outputPosition);
    }
  }

  private void createSeis3dFfts(ToolContext toolContext,
      ISeismicVolume input) {
    int[] inputShape = input.getLengths();
    float[] pad = getPad(toolContext.parms);
    Assert.assertNotNull("ParallelContext is null",pc);
    Assert.assertNotNull("Input Shape is null",inputShape);
    Assert.assertNotNull("Pad is null",pad);
    rcvr = new SeisFft3dNew(pc,inputShape,pad,DEFAULT_FFT_ORIENTATION);
    shot = new SeisFft3dNew(pc,inputShape,pad,DEFAULT_FFT_ORIENTATION);

    //TODO test code
    rcvr2 = new SeisFft3dNew(pc,inputShape,pad,DEFAULT_FFT_ORIENTATION);
    shot2 = new SeisFft3dNew(pc,inputShape,pad,DEFAULT_FFT_ORIENTATION);
    rcvr2.getArray().copy(input.getDistributedArray());

    //copy the receiver data into the rcvr object
    rcvr.getArray().copy(input.getDistributedArray());

    //Specify the sample rates
    double[] sampleRates = computeVolumeSampleRates(input,
        toolContext.inputGrid);

    rcvr.setTXYSampleRates(sampleRates);
    shot.setTXYSampleRates(sampleRates);

    //TODO test code
    rcvr2.setTXYSampleRates(sampleRates);
    shot2.setTXYSampleRates(sampleRates);    
  }

  //Currently superfluous check that will start throwing an exception when
  //a better way of removing the output data between volumes is implemented.
  //To remind you to remove the slow emptying of the DA.
  private boolean distributedArrayIsEmpty(DistributedArray da) {
    int[] position = new int[da.getShape().length];
    int direction = 1; //forward
    int scope = 0; //sample scope
    float[] buffer = new float[da.getElementCount()];
    DistributedArrayPositionIterator dapi = 
        new DistributedArrayPositionIterator(da,position,direction,scope);
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

  private IVelocityModel getVelocityModelObject(
      ToolContext toolContext) {
    IVelocityModel vmff = null;
    try {
      vmff = new VelocityModelFromFile(toolContext);
    } catch (FileNotFoundException e1) {
      LOGGER.log(Level.SEVERE,e1.getMessage(),e1);
    }

    //override the default behaviour if we're working on the test data which
    //has no header information.
    if (usingTestData(toolContext)) {
      vmff = new VelocityInDepthModel(
          new double[] {0,1000,2000},new double[] {2000,3800});
    }
    vmff.open("r");
    return vmff;
  }

  private void orientSeismicInVelocityModel(IVelocityModel vmff,
      ICheckGrids inputGridObj) {
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
      localGridSampleRates[0] = localGridSampleRates[0]/S_TO_MS;
      return localGridSampleRates;
    }
    throw new IllegalArgumentException("Sample axis units are not seconds or "
        +"milliseconds.  I don't know how to deal with that.");
  }

  private void logTimerOutput(String timerName,double totalTime) {
    LOGGER.info(String.format("%s: %.2f.",timerName,totalTime));
  }

  @Override
  public boolean outputVolume(ToolContext toolContext, ISeismicVolume output) {
    return false; //does nothing.
  }

  @Override
  public void parallelFinish(ToolContext toolContext) {
    //does nothing
  }

  @Override
  public void serialFinish(ToolContext toolContext) {
    //does nothing
  }
}

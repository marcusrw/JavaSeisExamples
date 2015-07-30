package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.logging.Logger;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;

import org.javaseis.examples.scratch.SeisFft3dNew;

import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.util.Convert;

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
  private static final Logger LOGGER = 
      Logger.getLogger(ExampleMigration.class.getName());
  private static final int[] DEFAULT_FFT_ORIENTATION =
      new int[] {-1,1,1};

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

  private IntervalTimer serialTime = new IntervalTimer();
  private IntervalTimer parallelTime = new IntervalTimer();
  private IntervalTimer sourceGenTime = new IntervalTimer();
  private IntervalTimer velocityAccessTime = new IntervalTimer();
  private IntervalTimer transformTime = new IntervalTimer();
  private IntervalTimer extrapTime = new IntervalTimer();
  private IntervalTimer imageTime = new IntervalTimer();

  private float[] pad;
  private float eps;
  private double fMax = -1;
  private static final float FLOAT_EPSILON = 1.19e-7F;

  private double[] sourceXYZ;
  private GridDefinition inputGrid;
  private ICheckGrids inputGridObj;
  private double[] recXYZ;

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
    serialTime.start();
    ParameterService parms = toolContext.parms;
    debug = checkForDebugMode(parms);
    //TODO this method should check that toolContext contains enough
    // information to do a basic extrapolation.
    // Run main for more information. (ex: inputGrid returns null)
    GridDefinition inputGrid = toolContext.inputGrid;
    if (inputGrid == null) {
      inputGrid = (GridDefinition) toolContext.getFlowGlobal(
          ToolContext.INPUT_GRID);
      toolContext.inputGrid = inputGrid;
    }
    Assert.assertNotNull(inputGrid);

    imageGrid = computeImageGrid(toolContext.inputGrid,toolContext.parms);
    transformGrid = computeTransformAxes(toolContext);
    //redundant, until we figure out the design of the toolContext
    toolContext.outputGrid = imageGrid;
    toolContext.putFlowGlobal(ToolContext.OUTPUT_GRID,imageGrid);
  }

  private boolean checkForDebugMode(ParameterService parms) {
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

    pad = getPad(parms);
    LOGGER.info("FFT Axis padding: " + Arrays.toString(pad) + "\n");

    long depthAxisLength = -1;
    depthAxisLength = computeDepthAxis(zmin, delz, zmax);

    //Interate over the axes
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
    Assert.assertNotNull("padT is null",padT);
    Assert.assertNotNull("padX is null",padX);
    Assert.assertNotNull("padY is null",padY);
    pad = new float[] {padT,padX,padY};
    Assert.assertNotNull("Pad is null",pad);
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
    parallelTime.start();
    pc = toolContext.getParallelContext();
    LOGGER.info("Starting parallelTimer on task #" + pc.rank() + "\n");
    LOGGER.info("Input Grid Definition:\n" + toolContext.inputGrid + "\n");
    LOGGER.info("Output Grid Definition:\n" + toolContext.outputGrid + "\n");
    fMax = Double.parseDouble(
        toolContext.parms.getParameter("FMAX"));
  }

  @Override
  public boolean processVolume(ToolContext toolContext, ISeismicVolume input,
      ISeismicVolume output) {

    IntervalTimer singleVolumeTime = new IntervalTimer();
    singleVolumeTime.start();
    LOGGER.info("[start processVolume on Volume #"
        +Arrays.toString(input.getVolumePosition()));

    //Only extrapolate the first volume if we're in debug mode.
    debug = checkForDebugMode(toolContext.parms);
    if (debug && input.getVolumePosition()[3] > 0)
      return false;

    //Instantiate a checked grid which fixes any misplaced receivers
    //Change code: 65432
    //ICheckGrids CheckedGrid = new ManualGrid(input, toolContext);   
    ICheckGrids CheckedGrid = new CheckGrids(input, toolContext);

    inputGridObj = CheckedGrid;

    //Set the Modified Grid = input Grid
    toolContext.inputGrid = CheckedGrid.getModifiedGrid();

    pad = getPad(toolContext.parms);
    pc = toolContext.getParallelContext();
    //Assert.assertNotNull(toolContext.outputGrid);
    imageGrid = computeImageGrid(toolContext.inputGrid,toolContext.parms);
    transformGrid = computeTransformAxes(toolContext);
    toolContext.outputGrid = imageGrid;


    int[] gridPos = input.getVolumePosition();
    recXYZ = CheckedGrid.getReceiverXYZ(gridPos);
    sourceXYZ = CheckedGrid.getSourceXYZ(gridPos);
    //TODO hack.  depth not zero not implemented.
    sourceXYZ[2] = 0;
    System.out.println("[processVolume]: sourceXYZ is " + 
        Arrays.toString(sourceXYZ));
    System.out.println("[processVolume]: recXYZ is " + 
        Arrays.toString(recXYZ));

    System.out.println(
        Arrays.toString(input.getGlobalGrid().getAxisPhysicalDeltas()));
    createSeis3dFfts(input);
    transformFromTimeToFrequency();

    ISourceVolume srcVol = new SourceVolume(CheckedGrid, shot);
    shot = srcVol.getShot();

    DistributedArrayMosaicPlot.showAsModalDialog(shot.getArray(),
        "Raw source signature");

    eps = 1E-12F;
    eps = 0F;
    double velocity;

    output.getDistributedArray().zeroCompletely();
    //emptyOutputDA(output.getDistributedArray());

    //TODO Make sure the output DA is empty.
    if (!distributedArrayIsEmpty(output.getDistributedArray())) {
      throw new IllegalArgumentException("Why is the output not empty?");
    }

    //depth axis information
    double zmin = imageGrid.getAxisPhysicalOrigin(0);
    double delz = imageGrid.getAxisPhysicalDelta(0);
    long numz = imageGrid.getAxisLength(0);
    LOGGER.info(String.format("zmin: %6.1f, delz: %6.1f, numz: %4d",
        zmin,delz,numz));

    //TODO initialize velocity model input
    velocityAccessTime.start();
    IVelocityModel vmff = getVelocityModelObject(toolContext);
    velocityAccessTime.stop();

    for (int zindx = 0 ; zindx < numz ; zindx++) {
      double depth = zmin+delz*zindx;
      LOGGER.info("Depth: " + depth);
      velocityAccessTime.start();
      velocity = vmff.readAverageVelocity(depth);
      velocityAccessTime.stop();

      //testCoords(input, CheckedGrid, toolContext);

      double[][] depthSlice = vmff.readSlice(depth);
      float[][] floatSlice = new float[depthSlice.length][depthSlice[0].length];
      for (int k = 0 ; k < depthSlice.length ; k++) {
        floatSlice[k] = Convert.DoubleToFloat(depthSlice[k]);
      }

      LOGGER.info("Volume #" + Arrays.toString(input.getVolumePosition()));
      transformFromSpaceToWavenumber();
      //this extrapolator is v(z) only.
      LOGGER.info(
          String.format("Begin Extrapolation to depth %5.1f.  Velocity is %5.1f"
              ,depth,velocity));
      extrapolate((float)velocity, delz, zindx,fMax);
      LOGGER.info("Extrapolation finished");
      transformFromWavenumberToSpace();

      /*
      rcvr.inverseTemporal();
      display = new SingleVolumeDAViewer(rcvr.getArray(),inputGrid);
      display.showAsModalDialog();
      rcvr.forwardTemporal();

      shot.inverseTemporal();
      display = new SingleVolumeDAViewer(shot.getArray(),inputGrid);
      display.showAsModalDialog();
      shot.forwardTemporal();
       */


      LOGGER.info("Applying imaging condition");
      imagingCondition(output,zindx,fMax);
      LOGGER.info("Imaging condition finished."); 
    }

    LOGGER.info("Processing of volume "
        + Arrays.toString(input.getVolumePosition())
        + " complete.");

    vmff.close();
    singleVolumeTime.stop();
    logTimerOutput("Single Volume Time",singleVolumeTime);
    Assert.assertTrue((boolean)toolContext.getFlowGlobal(ToolContext.HAS_INPUT));
    Assert.assertTrue((boolean)toolContext.getFlowGlobal(ToolContext.HAS_OUTPUT));

    DistributedArrayMosaicPlot.showAsModalDialog(output.getDistributedArray(),
        "Final Image.");

    return true;
  }

  private void emptyOutputDA(DistributedArray da) {
    int[] position = new int[da.getShape().length];
    int direction = 1; //forward
    int scope = 0; //sample scope
    float[] buffer = new float[da.getElementCount()];
    DistributedArrayPositionIterator dapi = 
        new DistributedArrayPositionIterator(da,position,direction,scope);
    while (dapi.hasNext()) {
      position = dapi.next();
      for (float element : buffer) {
        element = 0;
      }
      da.putSample(buffer,position);
    }  
  }

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
          System.out.println("DA is not empty as position: "
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
    //Change code: 65432
    //vmff = new VelocityInDepthModel(
    //    new double[] {0,1000,2000},new double[] {2000,3800});
    try {
      vmff = new VelocityModelFromFile(toolContext);
    } catch (FileNotFoundException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    vmff.open("r");
    //System.out.println("[VelocityModelFromFile: Input grid");
    //System.out.println(inputGrid.toString());
    //System.out.println("Axis order: " + Arrays.toString(AXIS_ORDER));

    //TODO:check
    //vmff.orientSeismicVolume(inputGrid,AXIS_ORDER);
    vmff.orientSeismicVolume(inputGridObj.getModifiedGrid(), inputGridObj.getAxisOrder());
    return vmff;
  }

  private void createSeis3dFfts(ISeismicVolume input) {
    int[] inputShape = input.getLengths();
    Assert.assertNotNull("ParallelContext is null",pc);
    Assert.assertNotNull("Input Shape is null",inputShape);
    System.out.println("Pad: " + Arrays.toString(pad));
    Assert.assertNotNull("Pad is null",pad);
    rcvr = new SeisFft3dNew(pc,inputShape,pad,DEFAULT_FFT_ORIENTATION);
    shot = new SeisFft3dNew(pc,inputShape,pad,DEFAULT_FFT_ORIENTATION);

    //copy the receiver data into the rcvr object
    rcvr.getArray().copy(input.getDistributedArray());

    //Specify the sample rates
    double[] sampleRates = computeVolumeSampleRates(input);
    //Change code: 65432
    //Assert.assertEquals(0.008,Math.abs(sampleRates[0]),1e-7);
    Assert.assertEquals(20,Math.abs(sampleRates[1]),1e-7);
    Assert.assertEquals(20,Math.abs(sampleRates[2]),1e-7);
    System.out.println(Arrays.toString(sampleRates));
    rcvr.setTXYSampleRates(sampleRates);
    shot.setTXYSampleRates(sampleRates);
  }

  /**
   * @param input
   * @return
   */
  private double[] computeVolumeSampleRates(ISeismicVolume input) {
    GridDefinition checkedGrid = inputGridObj.getModifiedGrid();
    System.out.println(checkedGrid.toString());
    double[] localGridSampleRates = checkedGrid.getAxisPhysicalDeltas().clone();
    Units timeAxisUnits = checkedGrid.getAxisUnits(0);
    if (timeAxisUnits.equals(Units.SECONDS)) {
      return localGridSampleRates;
    }
    if (timeAxisUnits.equals(Units.MILLISECONDS)) {
      LOGGER.finer("Time axis is measured in milliseconds.  We convert to "
          + "seconds here so we get correct frequency axis units.");
      localGridSampleRates[0] = localGridSampleRates[0]/S_TO_MS;
      return localGridSampleRates;
    }
    throw new IllegalArgumentException("Sample axis units are not seconds or "
        +"milliseconds.  I don't know how to deal with that.");
  }

  private void transformFromTimeToFrequency() {
    transformTime.start();
    rcvr.forwardTemporal();
    shot.forwardTemporal();
    transformTime.stop();
  }

  //Constant velocity phase shift
  private void extrapolate(float velocity, double delz,int zindx,double fMax) {
    extrapTime.start();

    //TODO CHECK!
    LOGGER.fine("Grid Order (STF -> XYZ (or XYT)): " 
        + Arrays.toString(inputGridObj.getAxisOrder()));
    LOGGER.fine("Physical Origins: "
        + Arrays.toString(inputGrid.getAxisPhysicalOrigins()));
    LOGGER.fine("Physical Deltas: "
        + Arrays.toString(inputGrid.getAxisPhysicalDeltas()));
    LOGGER.fine("Source: "
        + Arrays.toString(sourceXYZ));

    double[] sampleRates = rcvr.getTXYSampleRates();
    System.out.println("Receiver sample rate");
    System.out.println(Arrays.toString(sampleRates));

    DistributedArray rcvrDA = rcvr.getArray();
    DistributedArray shotDA = shot.getArray();

    int[] position = new int[rcvrDA.getDimensions()];
    int direction = 1; //forward
    int scope = 0; //samples

    //buffers
    float[] recInSample = new float[rcvrDA.getElementCount()];
    float[] souInSample = new float[shotDA.getElementCount()];
    float[] recOutSample = new float[rcvrDA.getElementCount()];
    float[] souOutSample = new float[rcvrDA.getElementCount()];
    double[] coords = new double[position.length];

    float[] recOutSample2;
    float[] souOutSample2;

    DistributedArrayPositionIterator dapi =
        new DistributedArrayPositionIterator(
            rcvrDA,position,direction,scope);
    while (dapi.hasNext()) {
      position = dapi.next();
      rcvrDA.getSample(recInSample, position);
      shotDA.getSample(souInSample, position);
      rcvr.getKyKxFCoordinatesForPosition(position, coords);
      double kX = coords[0];
      double kY = coords[1];
      double frequency = coords[2];

      //skip if we're over the data threshold.
      //TODO put this back when you have a proper band limited source.
      //if (frequency > fMax) continue;

      double Kz2 = (frequency/velocity)*(frequency/velocity) - kY*kY - kX*kX;
      double exponent = 0;
      if (Kz2 > eps && zindx > 0) {
        exponent = (-2*Math.PI*delz * Math.sqrt(Kz2));
      }
      if (Kz2 > eps) {
        //TODO fix these if statements so we get proper filtering
        // for depth z = 0.

        //TODO use complex math methods instead
        /*
        recOutSample2 = complexMultiply(
            recInSample,complexExponential(-exponent));
        souOutSample2 = complexMultiply(
            souInSample,complexExponential(exponent));
         */

        recOutSample[0] = (float) (recInSample[0]*Math.cos(-exponent)
            - recInSample[1]*Math.sin(-exponent));
        recOutSample[1] = (float) (recInSample[1]*Math.cos(-exponent)
            + recInSample[0]*Math.sin(-exponent));
        souOutSample[0] = (float) (souInSample[0]*Math.cos(exponent)
            - souInSample[1]*Math.sin(exponent));
        souOutSample[1] = (float) (souInSample[1]*Math.cos(exponent)
            + souInSample[0]*Math.sin(exponent));

        /* TODO when these work our complex math methods will be correct
        Assert.assertArrayEquals("recOutSample and recOutSample2 differ",
            recOutSample,recOutSample2,FLOAT_EPSILON);
        Assert.assertArrayEquals("souOutSample and souOutSample2 differ",
            souOutSample,souOutSample2,FLOAT_EPSILON);
         */

      } else {
        exponent = 2*Math.PI*Math.abs(delz)*Math.sqrt(Math.abs(Kz2));
        recOutSample[0] = (float) (recInSample[0]*Math.exp(-exponent));
        recOutSample[1] = (float) (recInSample[1]*Math.exp(-exponent));
        souOutSample[0] = (float) (souInSample[0]*Math.exp(-exponent));
        souOutSample[1] = (float) (souInSample[1]*Math.exp(-exponent));
      }
      rcvrDA.putSample(recOutSample, position);
      shotDA.putSample(souOutSample, position);
    }
    extrapTime.stop();
  }

  private void transformFromSpaceToWavenumber() {
    transformTime.start();
    rcvr.forwardSpatial2D();
    shot.forwardSpatial2D();
    transformTime.stop();

  }

  private void transformFromWavenumberToSpace() {
    transformTime.start();
    rcvr.inverseSpatial2D();
    shot.inverseSpatial2D();
    transformTime.stop();
  }

  private void imagingCondition(ISeismicVolume outputVolume,
      int zindx,double fMax) {
    imageTime.start();

    DistributedArray rcvrDA = rcvr.getArray();
    DistributedArray shotDA = shot.getArray();
    DistributedArray imageDA = outputVolume.getDistributedArray();
    float[] recInSample2 = new float[rcvrDA.getElementCount()];
    float[] souInSample2 = new float[shotDA.getElementCount()];

    //TODO Trick.  Hide the high frequencies from the iterator
    // so that it doesn't waste time accumulating a bunch of zeros.


    int[] fullShape = rcvr.getArray().getShape().clone();
    LOGGER.info(Arrays.toString(fullShape));

    /*
    double fNY = 1/(2*0.002);
    double delf = fNY/DALengths[0];
    int realMaxF = DALengths[0];
    int maxFindx = (int) (fMax/delf)+1;

    //DALengths[0] = maxFindx;
    LOGGER.info("Max F index: " + maxFindx);
    rcvrDA.setShape(DALengths);
    shotDA.setShape(DALengths);
    LOGGER.info(Arrays.toString(DALengths));
     */



    //buffers
    int[] position = new int[rcvrDA.getDimensions()];
    int direction = 1; //forward
    int scope = 0; //samples

    DistributedArrayPositionIterator dapi;
    dapi = new DistributedArrayPositionIterator(rcvrDA,position,
        direction,scope);

    float[] imageSample = new float[imageDA.getElementCount()];
    while (dapi.hasNext()) {
      position = dapi.next();
      int[] outputPosition = position.clone();
      outputPosition[0] = zindx;
      rcvr.getArray().getSample(recInSample2, position);
      shotDA.getSample(souInSample2, position);

      imageDA.getSample(imageSample, outputPosition);
      imageSample[0] += recInSample2[0]*souInSample2[0]
          + recInSample2[1]*souInSample2[1];
      imageDA.putSample(imageSample, outputPosition);
    }

    //Get the source and receiver samples
    LOGGER.info("\n\nShot DA shape: "
        + Arrays.toString(shotDA.getShape())
        + "\nShot DA sample count: "
        + shotDA.getTotalSampleCount()
        +"\n\nReceiver DA shape: "
        + Arrays.toString(rcvr.getArray().getShape()) 
        + "\nReceiver DA sample count: "
        + rcvr.getArray().getTotalSampleCount()
        +"\n\nImage DA shape: " 
        + Arrays.toString(imageDA.getShape()) 
        + "\nImage DA sample count: " 
        + imageDA.getTotalSampleCount()
        + "\n\n");

    imageTime.stop();
  }

  private float[] complexMultiply(float[] c1, float[] c2) {
    float realPart = c1[0]*c2[0]-c1[1]*c2[1];
    float imagPart = c1[0]*c2[1]+c1[1]*c2[0];
    return new float[] {realPart,imagPart};
  }

  //Computes the complex exponential e^(ir) of a real number r
  //by Euler's formula.
  private float[] complexExponential(double r) {
    return new float[] {(float)Math.cos(r),(float)Math.sin(r)};
  }

  private void logTimerOutput(String timerName,IntervalTimer timer) {
    LOGGER.info(String.format("%s: %.2f.",timerName,timer.total()));
  }

  @Override
  public boolean outputVolume(ToolContext toolContext, ISeismicVolume output) {
    //does nothing.
    return false;
  }

  @Override
  public void parallelFinish(ToolContext toolContext) {
    parallelTime.stop();
    LOGGER.info("Stopping parallelTimer on task #" + pc.rank() + "\n");
  }

  @Override
  public void serialFinish(ToolContext toolContext) {
    serialTime.stop();
    //TODO timers stopped working.  All the fields seem to be broken.
    logTimerOutput("Serial Time",serialTime);
    logTimerOutput("Parallel Time",parallelTime);
    LOGGER.info("");
    logTimerOutput("Velocity Access Time",velocityAccessTime);
    logTimerOutput("Source Generation Time",sourceGenTime);
    logTimerOutput("Transform Time",transformTime);
    logTimerOutput("Extrapolation Time",extrapTime);
    logTimerOutput("Imaging Time",imageTime);
  }
}

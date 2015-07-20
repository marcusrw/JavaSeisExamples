package org.javaseis.examples.scratch;

import java.util.Arrays;
import java.util.logging.Logger;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;

import org.javaseis.examples.scratch.SeisFft3dNew;

import beta.javaseis.parallel.IParallelContext;

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
import org.javaseis.volume.ISeismicVolume;

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
  SeisFft3dNew rcvr,shot;
  private long[] transformAxisLengths;
  private DataDomain[] transformDomains;
  private AxisDefinition[] transformAxes;
  private GridDefinition imageGrid;
  //TODO only for visual checks.  Delete later.
  private GridDefinition transformGrid;
  private SingleVolumeDAViewer display;
  private boolean DEBUG;

  private IntervalTimer serialTime = new IntervalTimer();
  private IntervalTimer parallelTime = new IntervalTimer();
  private IntervalTimer sourceGenTime = new IntervalTimer();
  private IntervalTimer transformTime = new IntervalTimer();
  private IntervalTimer extrapTime = new IntervalTimer();
  private IntervalTimer imageTime = new IntervalTimer();

  private float[] PAD;
  private float eps;
  private final double fMax = 60;


  public ExampleMigration() {
  }

  //allows running this tool from the command line, using key/value pairs to
  //fill in the necessary parameters.
  public static void main(String[] args) {
    ParameterService parms = new ParameterService(args);
    exec(parms, new ExampleMigration());
  }

  @Override
  public void serialInit(ToolContext toolContext) {
    serialTime.start();
    ParameterService parms = toolContext.getParameterService();
    GridDefinition inputGrid = toolContext.getInputGrid();
    imageGrid = computeImageGrid(inputGrid,parms);
    pc = toolContext.getParallelContext();
    transformGrid = computeTransformAxes(inputGrid);
    toolContext.setOutputGrid(imageGrid);
    checkForDebugMode(parms);
  }

  private void checkForDebugMode(ParameterService parms) {
    DEBUG = Boolean.parseBoolean(parms.getParameter("DEBUG","FALSE"));
    if (DEBUG) {
      LOGGER.info("RUNNING IN DEBUG MODE");
    }
  }

  private GridDefinition computeImageGrid(GridDefinition inputGrid,
      ParameterService parms) {

    AxisDefinition[] imageAxes = new AxisDefinition[inputGrid.getNumDimensions()];

    float zmin = Float.parseFloat(parms.getParameter("ZMIN","0"));
    float zmax = Float.parseFloat(parms.getParameter("ZMAX","2000"));
    float delz = Float.parseFloat(parms.getParameter("DELZ","50"));
    float PADT = Float.parseFloat(parms.getParameter("PADT","10"));
    float PADX = Float.parseFloat(parms.getParameter("PADX","10"));
    float PADY = Float.parseFloat(parms.getParameter("PADY","10"));
    PAD = new float[] {PADT,PADX,PADY};

    LOGGER.info("FFT Axis padding: " + Arrays.toString(PAD) + "\n");

    long depthAxisLength;
    if (!(delz > 0) || (zmax - zmin) < delz)
      depthAxisLength = 1;
    else
      depthAxisLength = (long) Math.floor((zmax-zmin)/delz);

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

  private GridDefinition computeTransformAxes(GridDefinition inputGrid) {
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
    rcvr = new SeisFft3dNew(pc,inputVolumeLengths,
        PAD,DEFAULT_FFT_ORIENTATION);

    //determine shape of output
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
      }
    }
    return inputAxisDomains;
  }

  @Override
  public void parallelInit(ToolContext toolContext) {
    parallelTime.start();
    pc = toolContext.getParallelContext();
    LOGGER.info("Starting parallelTimer on task #" + pc.rank() + "\n");
    LOGGER.info("Input Grid Definition:\n" + toolContext.getInputGrid() + "\n");
    LOGGER.info("Output Grid Definition:\n" + toolContext.getOutputGrid() + "\n");
  }

  @Override
  public boolean processVolume(ToolContext toolContext, ISeismicVolume input,
      ISeismicVolume outputVolume) {

    IntervalTimer singleVolumeTime = new IntervalTimer();
    singleVolumeTime.start();

    //Only extrapolate the first volume if we're in debug mode.
    if (DEBUG && input.getVolumePosition()[3] > 0)
      return false;

    createSeis3dFfts(input);
    transformFromTimeToFrequency();
    locateSourceAndGenerateShotDA(input);

    eps = 1E-12F;
    eps = 0F;
    float V;

    //depth axis information
    double zmin = outputVolume.getLocalGrid().getAxisPhysicalOrigin(0);
    double delz = outputVolume.getLocalGrid().getAxisPhysicalDelta(0);
    long numz = outputVolume.getLocalGrid().getAxisLength(0);
    LOGGER.info(String.format("zmin: %6.1f, delz: %6.1f, numz: %4d",
        zmin,delz,numz));

    for (int zindx = 0 ; zindx < numz ; zindx++) {
      double depth = zmin+delz*zindx;
      LOGGER.info("Depth: " + depth);
      V = getVelocityModel(depth);     
      extrapolate(V, delz, zindx);
      imagingCondition(outputVolume,zindx);
    }

    singleVolumeTime.stop();
    logTimerOutput("Single Volume Time",singleVolumeTime);
    return true;
  }

  private float getVelocityModel(double depth) {
    float V;
    if (depth <= 1000)
      V = 2000;
    else 
      V = 3800;
    return V;
  }

  private void locateSourceAndGenerateShotDA(ISeismicVolume input) {
    float[] sourceXYZ = locateSourceXYZ(input);
    assert sourceXYZ.length == 3;
    generateSourceSignature(sourceXYZ);
  }

  private void createSeis3dFfts(ISeismicVolume input) {
    int[] inputShape = input.getLengths();
    rcvr = new SeisFft3dNew(pc,inputShape,PAD,DEFAULT_FFT_ORIENTATION);
    shot = new SeisFft3dNew(pc,inputShape,PAD,DEFAULT_FFT_ORIENTATION);

    //copy the receiver data into the rcvr object
    rcvr.getArray().copy(input.getDistributedArray());

    //Specify the sample rates
    double[] sampleRates = computeVolumeSampleRates(input);
    rcvr.setTXYSampleRates(sampleRates);
    shot.setTXYSampleRates(sampleRates);
  }

  private double[] computeVolumeSampleRates(ISeismicVolume input) {
    GridDefinition localGrid = input.getLocalGrid();
    double[] localGridSampleRates = localGrid.getAxisPhysicalDeltas().clone();
    Units timeAxisUnits = localGrid.getAxisUnits(0);
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
    assert !rcvr.isTimeTransformed();
    assert !shot.isTimeTransformed();
    rcvr.forwardTemporal();
    shot.forwardTemporal();
    assert rcvr.isTimeTransformed();
    assert shot.isTimeTransformed();

    transformTime.stop();
  }

  private void generateSourceSignature(float[] sourceXYZ) {
    if (sourceXYZ.length != 3)
      throw new IllegalArgumentException("Wrong number of elements for sourceXYZ");
    if (sourceXYZ[2] > 0)
      throw new UnsupportedOperationException("Sources at Depths besides zero not yet implemented");

    sourceGenTime.start();
    int sourceX = (int) Math.floor(sourceXYZ[0]);
    while (sourceX < sourceXYZ[0] + 1) {
      int sourceY = (int) Math.floor(sourceXYZ[1]);
      while (sourceY < sourceXYZ[1] + 1) {
        float weight = Math.max(0, 1-euclideanDistance(sourceX,sourceY,sourceXYZ));
        putWhiteSpectrum(shot,sourceX,sourceY,weight);
        sourceY++;
      }
      sourceX++;
    }
    sourceGenTime.stop();
  }

  private float[] locateSourceXYZ(ISeismicVolume input) {
    LOGGER.info("Volume Index: "
        + Arrays.toString(input.getVolumePosition()) + "\n");
    //Find the Source Location, assume we have SOU_XYZ
    //For now we're just going to use the globalGrid and our prior knowledge
    //then refactor it into an auto/manual source field generator.

    float[][] sourceLocations = new float[][]
        {
        {14.5F,14.5F,0},
        {34.5F,14.5F,0},
        {14.5F,34.5F,0},
        {34.5F,34.5F,0}
        };

    int volumeArrayIndex;
    GridDefinition globalGrid = input.getGlobalGrid();
    String[] axisLabels = globalGrid.getAxisLabelsStrings();
    for (int k = 0 ; k < axisLabels.length ; k++) {
      if (axisLabels[k] == "SOURCE") {
        volumeArrayIndex = input.getVolumePosition()[k];
        LOGGER.info("Source location: "
            + Arrays.toString(sourceLocations[volumeArrayIndex]) + "\n");
        return sourceLocations[volumeArrayIndex];
      }
    }
    throw new IllegalArgumentException("Unable to find source location.");
  }

  private void extrapolate(float V, double delz, int zindx) {
    transformFromSpaceToWavenumber();
    extrapTime.start();

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
      //LOGGER.info("Position in " + Arrays.toString(position));
      rcvrDA.getSample(recInSample, position);
      shotDA.getSample(souInSample, position);
      rcvr.getKyKxFCoordinatesForPosition(position, coords);
      double Ky = coords[0];
      double Kx = coords[1];
      double F = coords[2];

      //skip if we're over the data threshold.
      //TODO put this back when you have a proper band limited source.
      //if (F > fMax) continue;

      double Kz2 = (F/V)*(F/V) - Kx*Kx - Ky*Ky;
      double exponent = 0;
      if (Kz2 > eps && zindx > 0) {
        exponent = (-2*Math.PI*delz * Math.sqrt(Kz2));
      }
      if (Kz2 > eps) {
        //TODO fix these if statements so we get proper filtering
        // for depth z = 0.
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
        /*
        if (!Arrays.equals(recOutSample,recOutSample2)) {
          System.out.println(Arrays.toString(recOutSample));
          System.out.println(Arrays.toString(recOutSample2));
          System.out.println((recOutSample[0]-recOutSample2[0])/recOutSample[0]);
          System.out.println((recOutSample[1]-recOutSample2[1])/recOutSample[1]);

          throw new ArithmeticException("Results of complex multiplication were different"
              + " for recOutSample");
        }
        if (!Arrays.equals(souOutSample,souOutSample2)) {
          System.out.println(Arrays.toString(souOutSample));
          System.out.println(Arrays.toString(souOutSample2));
          System.out.println((souOutSample[0]-souOutSample2[0])/souOutSample[0]);
          System.out.println((souOutSample[1]-souOutSample2[1])/souOutSample[1]);

          throw new ArithmeticException("Results of complex multiplication were different"
              + " for souOutSample");
        }
         */


      } else {
        exponent = 2*Math.PI*Math.abs(delz)*Math.sqrt(Math.abs(Kz2));
        recOutSample[0] = (float) (recInSample[0]*Math.exp(-exponent));
        recOutSample[1] = (float) (recInSample[1]*Math.exp(-exponent));
        souOutSample[0] = (float) (souInSample[0]*Math.exp(-exponent));
        souOutSample[1] = (float) (souInSample[1]*Math.exp(-exponent));
      }
      //LOGGER.info("Position out: " + Arrays.toString(position));
      rcvrDA.putSample(recOutSample, position);
      shotDA.putSample(souOutSample, position);
    }
    extrapTime.stop();
    transformFromWavenumberToSpace();
  }

  private void transformFromSpaceToWavenumber() {
    transformTime.start();
    assert !rcvr.isSpaceTransformed();
    assert !shot.isSpaceTransformed();
    rcvr.forwardSpatial2D();
    shot.forwardSpatial2D();
    assert rcvr.isSpaceTransformed();
    assert shot.isSpaceTransformed();
    transformTime.stop();

  }

  private void transformFromWavenumberToSpace() {
    transformTime.start();
    assert rcvr.isSpaceTransformed();
    assert shot.isSpaceTransformed();
    rcvr.inverseSpatial2D();
    shot.inverseSpatial2D();
    assert !rcvr.isSpaceTransformed();
    assert !shot.isSpaceTransformed();
    transformTime.stop();
  }

  private void imagingCondition(ISeismicVolume outputVolume,int zindx) {
    //Now image here
    imageTime.start();

    DistributedArray rcvrDA = rcvr.getArray();
    DistributedArray shotDA = shot.getArray();
    DistributedArray imageDA = outputVolume.getDistributedArray();
    float[] recInSample2 = new float[rcvrDA.getElementCount()];
    float[] souInSample2 = new float[shotDA.getElementCount()];

    //TODO Trick.  Hide the high frequencies from the iterator
    // so that it doesn't waste time accumulating a bunch of zeros.


    int[] DALengths = rcvr.getArray().getShape().clone();
    LOGGER.info(Arrays.toString(DALengths));

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
    double[] coords = new double[3];
    int direction = 1; //forward
    int scope = 0; //samples

    DistributedArrayPositionIterator dapi;
    dapi = new DistributedArrayPositionIterator(rcvrDA,position,
        direction,scope);

    System.out.println("Check: "
        + Arrays.toString(DALengths)
        + Arrays.toString(rcvrDA.getShape())
        + Arrays.toString(shotDA.getShape()));

    float[] imageSample = new float[imageDA.getElementCount()];
    while (dapi.hasNext()) {
      position = dapi.next();
      //System.out.println("Position: " + Arrays.toString(position)
      //    + " out of " + Arrays.toString(DALengths));
      //rcvr.getKyKxFCoordinatesForPosition(
      //    new int[] {position[2],position[1],position[0]}, coords);
      //System.out.println("Coords: " + Arrays.toString(coords));
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

  private float[] doubleToFloat(double[] doubleArray) {
    float[] floatArray = new float[doubleArray.length];
    for (int k = 0 ; k < doubleArray.length ; k++ ) {
      floatArray[k] = (float) doubleArray[k];
    }
    return floatArray;
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

  /**
   * @param sourceX - Target X index in the grid
   * @param sourceY - Target Y index in the grid
   * @param sourceXYZ - Actual Source position in the grid
   * @return The  Euclidean distance between the current array index
   *           and the input source.
   */
  private float euclideanDistance(float sourceX, float sourceY, float[] sourceXYZ) {
    float dx2 = (sourceX - sourceXYZ[0])*(sourceX - sourceXYZ[0]);
    float dy2 = (sourceY - sourceXYZ[1])*(sourceY - sourceXYZ[1]);
    return (float)Math.sqrt(dx2+dy2);
  }

  private void putWhiteSpectrum(SeisFft3dNew source,int sourceX,int sourceY,float amplitude) {
    int[] position = new int[] {0,sourceX,sourceY};
    int[] volumeShape = source.getArray().getShape();
    float[] sample = new float[] {amplitude,0}; //amplitude+0i complex
    DistributedArray sourceDA = source.getArray();
    while (position[0] < volumeShape[0]) {
      sourceDA.putSample(sample, position);
      position[0]++;
    }
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
    logTimerOutput("Serial Time",serialTime);
    logTimerOutput("Parallel Time",parallelTime);
    LOGGER.info("");
    logTimerOutput("Source Generation Time",sourceGenTime);
    logTimerOutput("Transform Time",transformTime);
    logTimerOutput("Extrapolation Time",extrapTime);
    logTimerOutput("Imaging Time",imageTime);

    LOGGER.info(String.format("Serial time: %.2fs.",serialTime.total()));
    LOGGER.info(String.format("Parallel time: %.2fs%n",parallelTime.total()));

    LOGGER.info(String.format("Source Generation Time: %.2fs",sourceGenTime.total()));
    LOGGER.info(String.format("Transform Time: %.2fs",transformTime.total()));
    LOGGER.info(String.format("Extrapolation Time: %.2fs",extrapTime.total()));
    LOGGER.info(String.format("Imaging Time: %.2fs%n",imageTime.total()));
  }
}

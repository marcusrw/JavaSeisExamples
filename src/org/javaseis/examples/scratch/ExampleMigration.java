package org.javaseis.examples.scratch;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;

import org.javaseis.examples.scratch.SeisFft3dNew;

import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.services.CoordinateType;
import beta.javaseis.services.JSCoordinateService;
import beta.javaseis.util.Convert;

import org.javaseis.examples.plot.SingleVolumeDAViewer;
import org.javaseis.grid.BinGrid;
import org.javaseis.grid.GridDefinition;
import org.javaseis.io.Seisio;
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
  SeisFft3dNew rcvr,shot;
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
  //private double fMax = Double.POSITIVE_INFINITY;
  private static final float FLOAT_EPSILON = 1.19e-7F;
  private double[] sourceXYZ;
  private GridDefinition inputGrid;

  //TODO clean this up so there are no redundant elements here.
  private int Xindex = 2;
  private int Yindex = 1;
  private int[] AXIS_ORDER = new int[3];

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
    //TODO this method should check that toolContext contains enough
    // information to do a basic extrapolation.
    // Run main for more information. (ex: inputGrid returns null)
    GridDefinition inputGrid = toolContext.inputGrid;
    imageGrid = computeImageGrid(inputGrid,parms);
    pc = toolContext.getParallelContext();
    transformGrid = computeTransformAxes(inputGrid);
    toolContext.outputGrid = imageGrid;
    checkForDebugMode(parms);
  }

  private void checkForDebugMode(ParameterService parms) {
    debug = Boolean.parseBoolean(parms.getParameter("DEBUG","FALSE"));
    if (debug) {
      LOGGER.info("RUNNING IN DEBUG MODE");
    }
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
    float padT = Float.parseFloat(parms.getParameter("PADT","10"));
    float padX = Float.parseFloat(parms.getParameter("PADX","10"));
    float padY = Float.parseFloat(parms.getParameter("PADY","10"));
    pad = new float[] {padT,padX,padY};

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
      ISeismicVolume outputVolume) {

    IntervalTimer singleVolumeTime = new IntervalTimer();
    singleVolumeTime.start();
    LOGGER.info("[start processVolume on Volume #"
        +Arrays.toString(input.getVolumePosition()));

    //Only extrapolate the first volume if we're in debug mode.
    if (debug && input.getVolumePosition()[3] > 0)
      return false;

    checkVolumeGridDefinition(toolContext,input);

    createSeis3dFfts(input);
    transformFromTimeToFrequency();
    generateShotDistributedArray(toolContext,input);


    //SourceVolume src = new SourceVolume(toolContext,input);
    //TODO: fix this commented out for testing
    //if (!src.isFinished()) {
    //return false;
    //}

    eps = 1E-12F;
    eps = 0F;
    double velocity;

    //depth axis information
    double zmin = outputVolume.getLocalGrid().getAxisPhysicalOrigin(0);
    double delz = outputVolume.getLocalGrid().getAxisPhysicalDelta(0);
    long numz = outputVolume.getLocalGrid().getAxisLength(0);
    LOGGER.info(String.format("zmin: %6.1f, delz: %6.1f, numz: %4d",
        zmin,delz,numz));

    //TODO initialize velocity model input
    velocityAccessTime.start();
    VelocityModelFromFile vmff = getVelocityModelObject(toolContext);
    velocityAccessTime.stop();

    for (int zindx = 0 ; zindx < numz ; zindx++) {
      double depth = zmin+delz*zindx;
      LOGGER.info("Depth: " + depth);
      velocityAccessTime.start();
      velocity = vmff.readAverageVelocity(depth);
      velocityAccessTime.stop();

      JSCoordinateService jscs = null;
      try {
        jscs = openTraceHeadersFile(toolContext);
      } catch (SeisException e){
        LOGGER.log(Level.INFO,e.getMessage(),e); 
      }

      //TODO externalize this test if possible
      {
        LOGGER.info("[processVolume]: ----- Called DEBUG TEST: -----");

        //Get the values from the distributed array and compare the jscs values

        //Grab Input ISeismicVolume -> DistributedArray
        DistributedArray inputDistArr = input.getDistributedArray();

        //index of trace [sample, trace, frame, volume]
        int[] globalPosIndex = input.getVolumePosition();
        int[] volumePosIndex = new int[3];

        //Iterate over the traces of the ISeismicVolume in the forward direction (1)
        DistributedArrayPositionIterator itrInputArr = 
            new DistributedArrayPositionIterator(inputDistArr, volumePosIndex, 
                DistributedArrayPositionIterator.FORWARD);

        while (itrInputArr.hasNext()) {
          volumePosIndex = itrInputArr.next();

          globalPosIndex[0] = zindx;
          for (int k = 1 ; k < 3 ; k++) {
            globalPosIndex[k] = volumePosIndex[k];
          }

          //rXYZ2 hold the receiver location for a given trace location [0,?,?,0]
          double [] rXYZ = new double[3];
          jscs.getReceiverXYZ(globalPosIndex, rXYZ);
          //TODO hack - replace the Z index with the depth (since the receiver
          //            position Z is the depth of the actual receiver, and
          //            doesn't match the current depth level.
          rXYZ[2] = depth;

          LOGGER.fine("[processVolume]: Pos: " + Arrays.toString(globalPosIndex) + 
              " recXYZ jscs: " + Arrays.toString(rXYZ));

          ///TEST CODE///
          //Compare the jscs coords to the coord based on pOrigin + indx * pDelta

          //rXYZ value from the Grid instead of jscs
          double [] rXYZ2 = new double[3];

          //Check if both indexes are in range
          double yIndex = globalPosIndex[Yindex];
          double xIndex = globalPosIndex[Xindex];

          //Calculate position in Xline

          int currentAxis = Yindex; //Trace axis (Y we believe)
          double minPhys0 = inputGrid.getAxisPhysicalOrigin(currentAxis);
          double axisPhysDelta = inputGrid.getAxisPhysicalDelta(currentAxis);
          double yval = minPhys0 + yIndex*axisPhysDelta;

          //Calculate position in Iline
          currentAxis = Xindex;
          minPhys0 = inputGrid.getAxisPhysicalOrigin(currentAxis);
          axisPhysDelta = inputGrid.getAxisPhysicalDelta(currentAxis);
          double xval = minPhys0 + xIndex*axisPhysDelta;

          //Set rXYZ2 Grids Calculations 
          rXYZ2[0] = xval;
          rXYZ2[1] = yval;
          rXYZ2[2] = depth;

          LOGGER.fine("[processVolume]: Values From Grid (rXYZ2):" + Arrays.toString(rXYZ2));

          double[] vmodXYZ = vmff.getVelocityModelXYZ(globalPosIndex);
          LOGGER.fine("Physical Location in VModel for Position: "
              + Arrays.toString(globalPosIndex) + " is " + 
              Arrays.toString(vmff.getVelocityModelXYZ(globalPosIndex)));

          for (int k = 0 ; k < 2 ; k++) {
            if (rXYZ2[k] - rXYZ[k] > 0.5) {
              System.out.println("AXIS_ORDER: "
                  + Arrays.toString(AXIS_ORDER));
              System.out.println("Global Position Index: "
                  + Arrays.toString(globalPosIndex));
              System.out.println("Receiver XYZ from RegularGrids: "
                  + Arrays.toString(rXYZ2));
              System.out.println("Receiver XYZ: "
                  + Arrays.toString(rXYZ));
              throw new ArithmeticException("The origin/delta position doesn't match the getRXYZ position");
            }
          }

          for (int k = 0 ; k < vmodXYZ.length ; k++) {
            if (vmodXYZ[k] - rXYZ[k] > 0.5) {
              System.out.println("AXIS_ORDER: "
                  + Arrays.toString(AXIS_ORDER));
              System.out.println("Global Position Index: "
                  + Arrays.toString(globalPosIndex));
              System.out.println("Velocity Model XYZ: "
                  + Arrays.toString(vmodXYZ));
              System.out.println("Receiver XYZ: "
                  + Arrays.toString(rXYZ));
              throw new ArithmeticException(
                  "Seismic and VModel locations don't agree here.");
            }
          }
          //double [] retRecCoord = getVelocityModelXYZ(rDXYZ);

          //Check if the returned coordinates from getVelocityModelXYZ is
          //equal to rXYZ2

          //Assert.assertTrue(Arrays.equals(rXYZ2, retRecCoord));


          //TODO:Test - remove this code!!
          /*try {
    		    Thread.sleep(250);                
    	  } catch(InterruptedException ex) {
    		    Thread.currentThread().interrupt();
    	  }*/
          //end
        }
        LOGGER.info("[processVolume]: ----- DEBUG TEST ENDED -----");
      }


      double[][] depthSlice = vmff.readSlice(depth);
      float[][] floatSlice = new float[depthSlice.length][depthSlice[0].length];
      for (int k = 0 ; k < depthSlice.length ; k++) {
        floatSlice[k] = Convert.DoubleToFloat(depthSlice[k]);
      }

      //visualize
      //PlotArray2D sliceDisplay = new PlotArray2D(floatSlice);
      //sliceDisplay.display();

      LOGGER.info("Volume #" + Arrays.toString(input.getVolumePosition()));
      transformFromSpaceToWavenumber();
      //this extrapolator is v(z) only.
      LOGGER.info(String.format("Begin Extrapolation to depth %5.1f",depth));
      extrapolate((float)velocity, delz, zindx,fMax);
      LOGGER.info("Extrapolation finished");
      transformFromWavenumberToSpace();
      LOGGER.info("Applying imaging condition");
      imagingCondition(outputVolume,zindx,fMax);
      LOGGER.info("Imaging condition finished.");
      LOGGER.info("Processing of volume "
          + Arrays.toString(input.getVolumePosition())
          + " complete.");

    }

    vmff.close();
    singleVolumeTime.stop();
    logTimerOutput("Single Volume Time",singleVolumeTime);
    return true;
  }

  private VelocityModelFromFile getVelocityModelObject(
      ToolContext toolContext) {
    VelocityModelFromFile vmff = null;
    try {
      vmff = new VelocityModelFromFile(toolContext);
      //vmff = new VelocityModelFromFile(pc,folder,file);
    } catch (FileNotFoundException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    vmff.open("r");
    //System.out.println("[VelocityModelFromFile: Input grid");
    //System.out.println(inputGrid.toString());
    //System.out.println("Axis order: " + Arrays.toString(AXIS_ORDER));
    vmff.orientSeismicVolume(inputGrid,AXIS_ORDER);
    return vmff;
  }

  private void checkVolumeGridDefinition(ToolContext toolContext,
      ISeismicVolume input) {
    //The only difference should be the physical origins and deltas
    //are different.




    inputGrid = updateVolumeGridDefinition(toolContext,input);

    //fixedGrid.getAxisPhysicalDelta(0);
    //fixedGrid.getAxisPhysicalDelta(index);
    // TODO check here that receiverX = pOx+xindx+pDx,
    // and receiverY = p0y + yindx+pDy

    /*for (int i = 1; i < fixedGrid.getNumDimensions(); i++){
    	for (int j = 0; j < fixedGrid.getAxis(i).getLength(); j++){
    		double[] rxyz = new double[3];
    		//jscs.getReceiverXYZ
    	}
    }*/

    toolContext.inputGrid = inputGrid;
  }

  private GridDefinition updateVolumeGridDefinition(ToolContext toolContext,
      ISeismicVolume input) {
    //open the JScoordinate service, figure out the physical origins/deltas
    //for the receiver positions, check them against the current grid, 
    //put out a log message if they're wrong, and change them.

    JSCoordinateService jscs = null;  
    try {
      jscs = openTraceHeadersFile(toolContext);
    } catch (SeisException e) {
      LOGGER.log(Level.INFO,e.getMessage(),e); 
    }

    GridDefinition inputGrid = input.getGlobalGrid();

    //TODO update the grid, show a log message if anything changes.
    long[] inputAxisLengths = inputGrid.getAxisLengths();

    int [] VolPos = input.getVolumePosition();
    System.out.println("[updateVolumeGridDefinition] VolumePos: " + Arrays.toString(VolPos));

    int[] pos = Arrays.copyOf(VolPos, VolPos.length);

    //double[] srxyz = new double[6];
    sourceXYZ = new double[3];
    double[] rxyz = new double[3];
    double[] rxyz2 = new double[3];
    double[] recXYZsrc = new double[3];
    //double[] sxyz = new double[3];

    AxisDefinition[] physicalOAxisArray =
        new AxisDefinition[inputAxisLengths.length];

    jscs.getReceiverXYZ(pos, rxyz);

    for (int k = 1 ; k < 3 ; k++) {
      pos[k]++;
      jscs.getReceiverXYZ(pos, rxyz2);
      System.out.println("pos: " + Arrays.toString(pos));
      System.out.println("rxyz: " + Arrays.toString(rxyz));
      System.out.println("rxyz2: " + Arrays.toString(rxyz2));
      if (Math.abs(rxyz[0] - rxyz2[0]) > 0.5) {
        Xindex = k;
        System.out.println("Xindex is " + Xindex);
      }
      pos[k]--;
    }
    for (int k = 1 ; k < 3 ; k++) {
      pos[k]++;
      jscs.getReceiverXYZ(pos, rxyz2);
      System.out.println("pos: " + Arrays.toString(pos));
      System.out.println("rxyz: " + Arrays.toString(rxyz));
      System.out.println("rxyz2: " + Arrays.toString(rxyz2));
      if (Math.abs(rxyz[1] - rxyz2[1]) > 0.5) {
        Yindex = k;
        System.out.println("Yindex is " + Yindex);
      }
      pos[k]--;
    }
    AXIS_ORDER = new int[] {Xindex,Yindex,0};

    jscs.getReceiverXYZ(pos, rxyz);
    System.out.println("[updateVolumeGridDefinition] rec1 Pos: " + Arrays.toString(rxyz));
    pos[1]++;
    pos[2]++;
    jscs.getReceiverXYZ(pos,rxyz2);
    System.out.println("[updateVolumeGridDefinition] rec2 Pos: " + Arrays.toString(rxyz2));
    //TODO hack
    pos[1] = 100;
    pos[2] = 100;
    jscs.getSourceXYZ(pos, sourceXYZ);
    jscs.getReceiverXYZ(pos,recXYZsrc);
    System.out.println("[updateVolumeGridDefinition] sourceXYZ Pos: " + Arrays.toString(sourceXYZ));
    System.out.println("[updateVolumeGridDefinition] sourceXYZ Pos Check: " + Arrays.toString(recXYZsrc));
    for (int k = 0 ; k < rxyz2.length ; k++) {
      rxyz2[k] -= rxyz[k];
    }

    System.out.println("[updateVolumeGridDefinition] New PhysO: " + Arrays.toString(rxyz));
    System.out.println("[updateVolumeGridDefinition] New Deltas: " + Arrays.toString(rxyz2));
    System.out.println("[updateVolumeGridDefinition] Axis Lengths: " + Arrays.toString(inputAxisLengths));

    for (int k = 0; k < inputAxisLengths.length ; k++) {
      AxisDefinition inputAxis = inputGrid.getAxis(k);
      physicalOAxisArray[k] = new AxisDefinition(inputAxis.getLabel(),
          inputAxis.getUnits(),
          inputAxis.getDomain(),
          inputAxis.getLength(),
          inputAxis.getLogicalOrigin(),
          inputAxis.getLogicalDelta(),
          //inputAxis.getPhysicalOrigin(),
          CalculateNewPhysicalOrigin(inputAxis, k, rxyz),
          //inputAxis.getPhysicalDelta());
          CalculateNewDeltaOrigin(inputAxis, k, rxyz2));
    }

    //For debugging
    GridDefinition modifiedGrid = new GridDefinition(inputGrid.getNumDimensions(),physicalOAxisArray);
    //System.out.println(modifiedGrid.toString());

    double[] physicalOrigins = modifiedGrid.getAxisPhysicalOrigins();
    double[] deltaA = modifiedGrid.getAxisPhysicalDeltas();

    System.out.println("[updateVolumeGridDefinition] Physical Origins from data: " + Arrays.toString(rxyz));
    System.out.println("[updateVolumeGridDefinition] Physical Origins from grid: " + Arrays.toString(physicalOrigins));

    System.out.println("[updateVolumeGridDefinition] Physical Origins from data: " + Arrays.toString(rxyz2));
    System.out.println("[updateVolumeGridDefinition] Physical Deltas from grid: " + Arrays.toString(deltaA));
    //DBG end


    return modifiedGrid;
  }

  private double CalculateNewDeltaOrigin(AxisDefinition axis, int k, double[] data) {
    if (k == Xindex){
      return data[0];
    }
    else if (k == Yindex){
      return data[1];
    }
    else{
      return axis.getPhysicalDelta();
      //return 0;
    }
  }

  //Calculate the new Physical Origin based on the 
  private double CalculateNewPhysicalOrigin(AxisDefinition axis, int k, double [] data) {
    if (k == Xindex){
      return data[0];
    }
    else if (k == Yindex){
      return data[1];
    }
    else{
      return axis.getPhysicalOrigin();
      //0
    }
  }

  private JSCoordinateService openTraceHeadersFile(ToolContext toolContext)
      throws SeisException {
    String inputFilePath
    = toolContext.parms.getParameter("inputFileSystem","null")
    + File.separator
    +toolContext.parms.getParameter("inputFilePath","null");

    Seisio sio;

    try {
      sio = new Seisio(inputFilePath);
      sio.open("r");
      sio.usesProperties(true);
      GridDefinition grid = sio.getGridDefinition();
      int xdim = Yindex;  //2nd array index
      int ydim = Xindex;  //3rd array index
      BinGrid bingrid = new BinGrid(grid,xdim,ydim);
      Assert.assertNotNull(bingrid);     
      String[] coordprops = new String[]
          {"SOU_XD","SOU_YD","SOU_ELEV","REC_XD","REC_YD","REC_ELEV"};
      //The JSCS source/javadoc should explain that ORDER MATTERS HERE.
      return new JSCoordinateService(sio,bingrid,
          CoordinateType.SHOTRCVR,coordprops);

    } catch (SeisException e) {
      LOGGER.log(Level.SEVERE,e.getMessage(),e);
      LOGGER.severe("Something is very wrong if you're seeing this.");
      throw e;
    }
  }

  private void createSeis3dFfts(ISeismicVolume input) {
    int[] inputShape = input.getLengths();
    rcvr = new SeisFft3dNew(pc,inputShape,pad,DEFAULT_FFT_ORIENTATION);
    shot = new SeisFft3dNew(pc,inputShape,pad,DEFAULT_FFT_ORIENTATION);

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

  private void generateShotDistributedArray(ToolContext toolContext,
      ISeismicVolume input) {
    float[] sourceXYZ = locateSourceXYZ(toolContext,input);
    assert sourceXYZ.length == 3;
    generateSourceSignature(sourceXYZ);
  }

  private float[] locateSourceXYZ(ToolContext toolContext,
      ISeismicVolume input) {
    LOGGER.info("Volume Index: "
        + Arrays.toString(input.getVolumePosition()) + "\n");
    //TODO this is hard-coded right now.
    //Find the Source Location, assume we have SOU_XYZ
    //For now we're just going to use the globalGrid and our prior knowledge
    //then refactor it into an auto/manual source field generator.

    //float[][] sourceLocations = new float[][] {{100F,100F,0F}};
    /*{
	        {14.5F,14.5F,0},
	        {34.5F,14.5F,0},
	        {14.5F,34.5F,0},
	        {34.5F,34.5F,0}
	        };*/
    JSCoordinateService jscs = null;
    try {
      jscs = openTraceHeadersFile(toolContext);
    } catch (SeisException e){
      LOGGER.log(Level.INFO,e.getMessage(),e); 
    }

    //Get the Distributed Array
    DistributedArray inputDistArr = input.getDistributedArray();

    //Get the current volume
    int[] globalPosIndex = input.getVolumePosition();
    int[] volumePosIndex = new int[3];

    //Iterate over the Volume - Scope = 3
    DistributedArrayPositionIterator itrInputArr = 
        new DistributedArrayPositionIterator(inputDistArr, volumePosIndex, 
            DistributedArrayPositionIterator.FORWARD, 3);

    //Store our source Locations
    ArrayList<Double []> sourceLocationTemp = new ArrayList<Double []>();

    while (itrInputArr.hasNext()) {
      volumePosIndex = itrInputArr.next();

      globalPosIndex[0] = 0;
      for (int k = 1 ; k < 3 ; k++) {
        globalPosIndex[k] = volumePosIndex[k];
      }

      //Get the source positions
      double [] sXYZ = new double[3];
      jscs.getSourceXYZ(globalPosIndex, sXYZ);

      //Do math to get the Source Index
      //First the X then the Y
      int currentAxis = Xindex;
      double minPhys0 = inputGrid.getAxisPhysicalOrigin(currentAxis);
      double axisPhysDelta = inputGrid.getAxisPhysicalDelta(currentAxis);

      double sX = (sXYZ[0] - minPhys0)/axisPhysDelta;

      currentAxis = Yindex;
      minPhys0 = inputGrid.getAxisPhysicalOrigin(currentAxis);
      axisPhysDelta = inputGrid.getAxisPhysicalDelta(currentAxis);

      double sY = (sXYZ[1] - minPhys0)/axisPhysDelta;

      double sZ = 0;

      //System.out.println("source location calc: " + sX + " " + sY + " " + sZ);

      //construct a Double Array for the arrayList
      Double [] InnerArray = new Double[] {sX,sY,sZ};
      sourceLocationTemp.add(InnerArray);  
    }

    //Get the stuff in the Arraylist sourceLocationTemp

    //Declare a new array of the right size to house our elements
    float [][] sourceLocations = new float[sourceLocationTemp.size()][3];

    //Fill sourceLocation array 
    for (int i = 0; i < sourceLocations.length; i++){
      //number of sourceLocations
      Double[] DXYZ_OBJ = sourceLocationTemp.get(i);
      for (int j = 0; j < sourceLocations[i].length; j++){
        //copy the sourceXYZ into the sourceLocations array at a given i index
        sourceLocations[i][j] = DXYZ_OBJ[j].floatValue();
      }

    }

    //sourceLocations set

    int volumeArrayIndex;
    GridDefinition globalGrid = input.getGlobalGrid();
    String[] axisLabels = globalGrid.getAxisLabelsStrings();
    for (int k = 0 ; k < axisLabels.length; k++) {
      if (axisLabels[k] == "SOURCE") {
        volumeArrayIndex = 0; //TODO refactor
        LOGGER.info("Source location: "
            + Arrays.toString(sourceLocations[volumeArrayIndex]) + "\n");
        return sourceLocations[volumeArrayIndex];
      }
    }
    throw new IllegalArgumentException("Unable to find source location.");
  }

  private void generateSourceSignature(float[] sourceXYZ) {
    if (sourceXYZ.length != 3)
      throw new IllegalArgumentException(
          "Wrong number of elements for sourceXYZ");

    if (sourceXYZ[2] > 0)
      throw new UnsupportedOperationException(
          "Sources at Depths besides zero not yet implemented");

    sourceGenTime.start();
    int sourceX = (int) Math.floor(sourceXYZ[0]);
    while (sourceX < sourceXYZ[0] + 1) {
      int sourceY = (int) Math.floor(sourceXYZ[1]);
      while (sourceY < sourceXYZ[1] + 1) {
        float weight = 
            Math.max(0, 1-euclideanDistance(sourceX,sourceY,sourceXYZ));
        putWhiteSpectrum(shot,sourceX,sourceY,weight);
        sourceY++;
      }
      sourceX++;
    }
    sourceGenTime.stop();
  }

  /**
   * @param sourceX - Target X index in the grid
   * @param sourceY - Target Y index in the grid
   * @param sourceXYZ - Actual Source position in the grid
   * @return The  Euclidean distance between the current array index
   *           and the input source.
   */
  private float euclideanDistance(float sourceX,
      float sourceY,float[] sourceXYZ) {

    float dx2 = (sourceX - sourceXYZ[0])*(sourceX - sourceXYZ[0]);
    float dy2 = (sourceY - sourceXYZ[1])*(sourceY - sourceXYZ[1]);
    return (float)Math.sqrt(dx2+dy2);
  }

  private void putWhiteSpectrum(SeisFft3dNew source,int sourceX,
      int sourceY,float amplitude) {

    int[] position = new int[] {0,sourceX,sourceY};
    int[] volumeShape = source.getArray().getShape();
    float[] sample = new float[] {amplitude,0}; //amplitude+0i complex
    DistributedArray sourceDA = source.getArray();
    while (position[0] < volumeShape[0]) {
      sourceDA.putSample(sample, position);
      position[0]++;
    }
  }

  private float getVelocityModel(double depth) {
    float V;
    if (depth <= 1000)
      V = 2000;
    else 
      V = 3800;
    return V;
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
        + Arrays.toString(AXIS_ORDER));
    LOGGER.fine("Physical Origins: "
        + Arrays.toString(inputGrid.getAxisPhysicalOrigins()));
    LOGGER.fine("Physical Deltas: "
        + Arrays.toString(inputGrid.getAxisPhysicalDeltas()));
    LOGGER.fine("Source: "
        + Arrays.toString(sourceXYZ));


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

        recOutSample2 = complexMultiply(
            recInSample,complexExponential(-exponent));
        souOutSample2 = complexMultiply(
            souInSample,complexExponential(exponent));

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

package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.logging.Logger;

import org.javaseis.array.ElementType;
import org.javaseis.grid.GridDefinition;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;

import beta.javaseis.distributed.Decomposition;
import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.FileSystemIOService;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;
import beta.javaseis.plot.PlotArray2D;
import beta.javaseis.util.Convert;

public class VelocityModelFromFile {

  private static final Logger LOGGER = 
      Logger.getLogger(VelocityModelFromFile.class.getName());

  //TODO  add helpful exceptions explaining what to do when these haven't
  //      been initialized yet (ie, tell the user to use .open or .orient
  //      to make it work.
  FileSystemIOService vModelPIO = null;
  DistributedArray vModelData = null;
  GridDefinition vmodelGrid = null;
  GridDefinition volumeGrid = null;

  IParallelContext pc;
  String folder;
  String file;

  private static final int VOLUME_NUM_AXES = 3;
  private int[] AXIS_ORDER = new int[] {2,1,0};
  private int[] V_AXIS_ORDER = new int[] {2,1,0};

  //TODO TEST IDEA: Pass in a model and seismic volume with different grid
  //                spacings.  You expect an ArithmeticException.

  public VelocityModelFromFile(IParallelContext pc,String folder,String file) throws FileNotFoundException {
    startFileSystemIOService(pc, folder, file);
  }

  private void startFileSystemIOService(IParallelContext pc, String folder,
      String file) throws FileNotFoundException {
    try {
      this.pc = pc;
      this.folder = folder;
      this.file = file;
      vModelPIO = new FileSystemIOService(pc,folder);
    } catch (SeisException e) {
      //TODO handle this better
      e.printStackTrace();
      throw new FileNotFoundException(e.getMessage());
    }
  }

  public VelocityModelFromFile(ToolContext toolContext) throws FileNotFoundException {
    pc = toolContext.getParallelContext();
    ParameterService parms = toolContext.parms;
    folder = parms.getParameter("inputFileSystem");
    file =  parms.getParameter("vModelFilePath");
    startFileSystemIOService(pc, folder, file);
  }  

  public void open(String openMode) {
    try {
      vModelPIO.open(file);
    } catch (SeisException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    vmodelGrid = vModelPIO.getGridDefinition();
  }

  /**
   * Orient the input seismic volume within the larger velocity model,
   * so we know where to pull velocities from.
   * @param seismicVolumeGrid - The input seismic grid definition
   *                            (eg. a shot record)
   */
  public void orientSeismicVolume(GridDefinition seismicVolumeGrid,
      int[] axisOrder) {
    volumeGrid = seismicVolumeGrid;
    AXIS_ORDER = axisOrder;
    constructDistributedArrayForVelocityModel();
    loadVelocityModelIntoArray();
  }

  private void constructDistributedArrayForVelocityModel() {
    int[] daShape = Convert.longToInt(
        Arrays.copyOf(vmodelGrid.getAxisLengths(),VOLUME_NUM_AXES));

    int[] decompTypes = new int[]
        {Decomposition.BLOCK,
        Decomposition.BLOCK,
        Decomposition.BLOCK
        };

    vModelData = new DistributedArray(pc,ElementType.FLOAT,
        daShape,decompTypes);
  }

  private void loadVelocityModelIntoArray() {
    vModelPIO.setDistributedArray(vModelData);
    try {
      //read the volume
      vModelPIO.read();
    } catch (SeisException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public void close() {
    try {
      vModelPIO.close();
    } catch (SeisException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  //TODO  get rid of all these get functions and just make these attributes
  //      into fields.
  private long[] getVolumeGridLengths() {
    return volumeGrid.getAxisLengths();
  }

  private double[] getVolumeGridOrigins() {
    return volumeGrid.getAxisPhysicalOrigins();
  }

  private double[] getVolumeGridDeltas() {
    return volumeGrid.getAxisPhysicalDeltas();
  }

  public long[] getVModelGridLengths() {
    return vmodelGrid.getAxisLengths();
  }

  private double[] getVModelGridOrigins() {
    return vmodelGrid.getAxisPhysicalOrigins();
  }

  private double[] getVModelGridDeltas() {
    return vmodelGrid.getAxisPhysicalDeltas();
  }

  //Temporary testing method, maybe
  public double[] getVelocityModelXYZ(int[] seisVolumePositionIndexInDepth) {
    int[] vvindx = mapSeimicVolumeIndexToVelocityVolumeIndex(
        seisVolumePositionIndexInDepth);
    return modelXYZForIndex(vvindx);
  }

  private int[] mapSeimicVolumeIndexToVelocityVolumeIndex(
      int[] volIndexInDepth) {

    assert volIndexInDepth.length >= VOLUME_NUM_AXES;

    double[] volGridOrigins = getVolumeGridOrigins();
    double[] volGridDeltas = getVolumeGridDeltas();

    double[] vmodelGridOrigins = getVModelGridOrigins();
    double[] vmodelGridDeltas = getVModelGridDeltas();

    //TODO Hack - replace the 0th index with the depth index because 
    //            the data doesn't know about the depth
    volGridDeltas[0] = vmodelGridDeltas[0];

    int[] vModelIndex = new int[VOLUME_NUM_AXES];
    for (int k = 0 ; k < vModelIndex.length; k++) {
      int vIndx = V_AXIS_ORDER[k];
      int sIndx = AXIS_ORDER[k];
      double physicalPosition = volGridOrigins[sIndx]
          + volIndexInDepth[sIndx]*volGridDeltas[sIndx];
      double vModelIndexD =
          (physicalPosition - vmodelGridOrigins[vIndx])/vmodelGridDeltas[vIndx];
      if (!doubleIsAnInteger(vModelIndexD)) {
        System.out.println("Input volume Position (volIndexInDepth): "
            + Arrays.toString(volIndexInDepth));
        System.out.println("Physical Position: " + physicalPosition);
        System.out.println("Estimated index: " + vModelIndexD);
        System.out.println("Closest Integer: " + Math.rint(vModelIndexD));        
        throw new ArithmeticException("Array index value doesn't evaluate to a "
            + "mathematical integer.  Some interpolation is called for here, "
            + "(Not implemented)");
      }
      vModelIndex[k] = (int)vModelIndexD;
    }
    return vModelIndex;
  }

  //Temporary, for testing, maybe
  private double[] modelXYZForIndex(int[] vModelPositionIndex) {

    double[] vmodelGridOrigins = getVModelGridOrigins();
    double[] vmodelGridDeltas = getVModelGridDeltas();

    double[] modelXYZ = new double[VOLUME_NUM_AXES];
    for (int k = 0 ; k < modelXYZ.length ; k++) {
      int gridIndex = AXIS_ORDER[k];
      modelXYZ[k] = vmodelGridOrigins[gridIndex]
          + vmodelGridDeltas[gridIndex]*vModelPositionIndex[k];
    }

    return modelXYZ;
  }

  private boolean doubleIsAnInteger(double number) {
    return number == Math.rint(number);
  }

  public double readAverageVelocity(double depth) {
    double[][] velocitySlice = readSlice(depth);
    return averageVelocity(velocitySlice);
  }

  private double averageVelocity(double[][] velocitySlice) {
    double sum = 0;
    int numElements = 0;
    for (int row = 0 ; row < velocitySlice.length ; row++) {
      for (int col = 0 ; col < velocitySlice[row].length ; col++) {
        sum += velocitySlice[row][col];
        numElements++;
      }
    }
    return sum/numElements;
  }

  /**
   * @param depth - The physical depth in the model.  This method will return
   *                the slice that is closest to that depth.
   *                If you've already oriented your seismic volume within the 
   *                model, this will grab only the part of the model your data
   *                passes through.
   */
  public double[][] readSlice(double depth) {
    if (volumeGrid == null) {
      orientSeismicVolume(vmodelGrid,AXIS_ORDER);
    }
    return getWindowedDepthSlice(getVolumeGridOrigins(),
        Convert.longToInt(getVolumeGridLengths()),depth);
  }

  public double[][] getEntireDepthSlice(double depth) {
    return getWindowedDepthSlice(getVModelGridOrigins(),
        Convert.longToInt(getVModelGridLengths()),depth);
  }

  /**
   * Get a rectangle out of the velocity model.  Pass in the origins and
   * deltas of your shot record to get just the part of the velocity model
   * your data passes through during migration.
   * @param windowOrigin - in STFVH order
   * @param windowLength - in STFVH order 
   * @return a slice of the velocity model
   */
  public double[][] getWindowedDepthSlice(double[] windowOrigin,
      int[] windowLength,double depth) {
    LOGGER.fine("Velocity Grids: ");
    LOGGER.fine(vmodelGrid.toString());
    LOGGER.fine(volumeGrid.toString());

    //convert origin/depth to array indices
    windowOrigin[0] = depth;

    //TODO we need to know the axis orders in order to get this right.
    long[] depthIndex = convertLocationToArrayIndex(windowOrigin);
    //loop over window lengths
    int xIndex = AXIS_ORDER[0];
    int yIndex = AXIS_ORDER[1];
    int zIndex = AXIS_ORDER[2];

    long x0 = depthIndex[xIndex];
    long y0 = depthIndex[yIndex];
    long z0 = depthIndex[zIndex];

    //TODO maybe this should be a float array
    double[][] depthSlice =
        new double[windowLength[xIndex]]
            [windowLength[yIndex]];

    int[] pos = new int[VOLUME_NUM_AXES];
    pos[zIndex] = (int)z0;
    float[] buffer = new float[1];

    //This loop checks the sign of the seismic deltas but not the magnitude
    //TODO check and account for that at some point.
    for (int xindx = 0 ; xindx < windowLength[xIndex] ; xindx++) {
      if (getVolumeGridDeltas()[xIndex] < 0) {
        pos[V_AXIS_ORDER[0]] = (int)(x0 - xindx);
      } else {
        pos[V_AXIS_ORDER[0]] = (int)(x0 + xindx);
      }
      for (int yindx = 0 ; yindx < windowLength[yIndex] ; yindx++) {
        if (getVolumeGridDeltas()[yIndex] < 0) {
          pos[V_AXIS_ORDER[1]] = (int)(y0 - yindx);
        } else {
          pos[V_AXIS_ORDER[1]] = (int)(y0 + yindx);
        }
        vModelData.getSample(buffer,pos);
        depthSlice[xindx][yindx] = buffer[0];
      }
    }
    return depthSlice;
  }

  private long[] convertLocationToArrayIndex(double[] location) {
    double[] vModelOrigin = getVModelGridOrigins();
    double[] vModelDeltas = getVModelGridDeltas();

    long[] posIndx = new long[VOLUME_NUM_AXES];
    for (int k = 0 ; k < VOLUME_NUM_AXES ; k++) {
      double doubleIndex = (location[k] - vModelOrigin[k])/vModelDeltas[k];
      if (doubleIndex != Math.rint(doubleIndex)) {
        throw new ArithmeticException("Got an noninteger array index");
      }
      posIndx[k] = (int)Math.rint(doubleIndex);
    }
    LOGGER.fine("Array index for position: "
        + Arrays.toString(location) + " is " 
        + Arrays.toString(posIndx));
    return posIndx;
  }

  public static void main(String[] args) {
    String file = "segsaltmodel.js";
    ParameterService parms = null;
    try {
      parms = new FindTestData(file).getParameterService();
    } catch (FileNotFoundException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    IParallelContext pc = new UniprocessorContext();
    ToolContext toolContext = new ToolContext(parms);
    toolContext.setParallelContext(pc);

    VelocityModelFromFile vmff = null;
    try {
      String folder = parms.getParameter("inputFileSystem","null");
      vmff = new VelocityModelFromFile(pc,
          folder,file);
    } catch (FileNotFoundException e) {
      System.out.println("Reading dataset failed.");
      e.printStackTrace();
    }

    vmff.open("r");
    vmff.orientSeismicVolume(vmff.vmodelGrid,vmff.AXIS_ORDER);
    double[] windowOrigin = new double[] {2000,1000,1000};
    int[] windowLength = new int[] {1,338,338};
    double[][] depthSlice = vmff.getWindowedDepthSlice(windowOrigin,
        windowLength,2000.0);
    float[][] floatSlice = new float[depthSlice.length][depthSlice[0].length];
    for (int k = 0 ; k < depthSlice.length ; k++) {
      floatSlice[k] = Convert.DoubleToFloat(depthSlice[k]);
    }

    //visualize
    //PlotArray2D sliceDisplay = new PlotArray2D(floatSlice);
    //sliceDisplay.display();
    //DistributedArrayMosaicPlot.showAsModalDialog(vmff.vModelData,"title2344");
    vmff.close();
  }
}

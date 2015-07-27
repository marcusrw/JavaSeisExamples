package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.Arrays;

import org.javaseis.array.ElementType;
import org.javaseis.grid.GridDefinition;
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
  private static final int[] AXIS_ORDER = new int[] {2,1,0};

  //TODO TEST IDEA: Pass in a model and seismic volume with different grid
  //                spacings.  You expect an ArithmeticException.

  public VelocityModelFromFile(IParallelContext pc,String folder,String file) throws FileNotFoundException {
    try {
      this.pc = pc;
      this.folder = folder;
      this.file = file;
      System.out.println(folder);
      vModelPIO = new FileSystemIOService(pc,folder);
    } catch (SeisException e) {
      //TODO handle this better
      e.printStackTrace();
      throw new FileNotFoundException(e.getMessage());
    }
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
  public void orientSeismicVolume(GridDefinition seismicVolumeGrid) {
    volumeGrid = seismicVolumeGrid;
    System.out.println(Arrays.toString(vmodelGrid.getAxisLengths()));
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

  //TODO  get rid of all these get functions and just make these attributs
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

  private int[] mapSeimicVolumeIndexToVelocityVolumeIndex(
      int[] volIndexInDepth) {

    assert volIndexInDepth.length >= VOLUME_NUM_AXES;

    double[] volGridOrigins = getVolumeGridOrigins();
    double[] volGridDeltas = getVolumeGridDeltas();

    double[] vmodelGridOrigins = getVModelGridOrigins();
    double[] vmodelGridDeltas = getVModelGridDeltas();

    //TODO Hack
    volGridDeltas[0] = vmodelGridDeltas[0];

    int[] vModelIndex = new int[VOLUME_NUM_AXES];
    for (int k = 0 ; k < vModelIndex.length; k++) {
      double physicalPosition = volGridOrigins[k]
          + volIndexInDepth[k]*volGridDeltas[k];
      double vModelIndexD =
          (physicalPosition - vmodelGridOrigins[k])/vmodelGridDeltas[k];
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

  private boolean doubleIsAnInteger(double number) {
    return number == Math.rint(number);
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
      orientSeismicVolume(vmodelGrid);
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
   * @param windowOrigin
   * @param windowLength
   * @return a slice of the velocity model
   */
  public double[][] getWindowedDepthSlice(double[] windowOrigin,
      int[] windowLength,double depth) {
    //TODO Implement last.
    //convert origin/depth to array indices
    windowOrigin[0] = depth;
    long[] depthIndex = convertLocationToArrayIndex(windowOrigin);
    //loop over window lengths
    long z0 = depthIndex[0];
    long x0 = depthIndex[1];
    long y0 = depthIndex[2];

    //TODO maybe this should be a float array
    double[][] depthSlice = new double[windowLength[1]][windowLength[2]];
    int[] pos = new int[VOLUME_NUM_AXES];
    pos[0] = (int)z0;
    float[] buffer = new float[1];

    //This loop assumes the deltas are the same for the seismic and the model
    //TODO check and account for that at some point.
    for (int xindx = 0 ; xindx < windowLength[1] ; xindx++) {
      pos[1] = (int)(x0 + xindx);
      for (int yindx = 0 ; yindx < windowLength[2] ; yindx++) {
        pos[2] = (int)(y0 + yindx);
        vModelData.getSample(buffer,pos);
        depthSlice[xindx][yindx] = buffer[0];
      }
    }
    return depthSlice;
    // throw new UnsupportedOperationException("Last thing that needs "
    //    + "to be implemented");
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
    System.out.println("Array index for position: "
        + Arrays.toString(location) + " is " 
        + Arrays.toString(posIndx));
    return posIndx;
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

  public static void main(String[] args) {
    IParallelContext pc = new UniprocessorContext();
    VelocityModelFromFile vmff = null;
    String folder = "/home/wilsonmr/javaseis";
    String file = "segsaltmodel.js";
    try {
      vmff = new VelocityModelFromFile(pc,
          folder,file);
    } catch (FileNotFoundException e) {
      System.out.println("Reading dataset failed.");
      e.printStackTrace();
    }

    vmff.open("r");
    vmff.orientSeismicVolume(vmff.vmodelGrid);
    double[] windowOrigin = new double[] {2000,1000,1000};
    int[] windowLength = new int[] {1,338,338};
    double[][] depthSlice = vmff.getWindowedDepthSlice(windowOrigin,
        windowLength,2000.0);
    float[][] floatSlice = new float[depthSlice.length][depthSlice[0].length];
    for (int k = 0 ; k < depthSlice.length ; k++) {
      floatSlice[k] = Convert.DoubleToFloat(depthSlice[k]);
    }

    //visualize
    PlotArray2D sliceDisplay = new PlotArray2D(floatSlice);
    sliceDisplay.display();
    DistributedArrayMosaicPlot.showAsModalDialog(vmff.vModelData,"title2344");
    vmff.close();
  }
}

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
   * @param seismicVolume - The input seismic volume (eg. a shot record)
   */
  public void orientSeismicVolume(GridDefinition seismicVolumeGrid) {
    volumeGrid = seismicVolumeGrid;
    constructDistributedArrayForVelocityModel();
    loadVelocityModelIntoArray();
  }

  private void constructDistributedArrayForVelocityModel() {
    int[] daShape = Convert.longToInt(
        Arrays.copyOf(volumeGrid.getAxisLengths(),VOLUME_NUM_AXES));

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
      int[] volPosInDepth) {

    assert volPosInDepth.length >= VOLUME_NUM_AXES;

    double[] volGridOrigins = getVolumeGridOrigins();
    double[] volGridDeltas = getVolumeGridDeltas();

    double[] vmodelGridOrigins = getVModelGridOrigins();
    double[] vmodelGridDeltas = getVModelGridDeltas();

    int[] vModelIndex = new int[VOLUME_NUM_AXES];
    for (int k = 0 ; k < vModelIndex.length; k++) {
      double physicalPosition = volGridOrigins[k]
          + volPosInDepth[k]*volGridDeltas[k];
      double vModelIndexD =
          (physicalPosition - vmodelGridOrigins[k])/vmodelGridDeltas[k];
      if (!doubleIsAnInteger(vModelIndexD)) {
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
    return getWindowedDepthSlice(getVolumeGridOrigins(),getVolumeGridLengths());
  }

  public double[][] getEntireDepthSlice(double depth) {
    return getWindowedDepthSlice(getVModelGridOrigins(),
        getVModelGridLengths());
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
      long[] windowLength) {
    //TODO Implement last.
    throw new UnsupportedOperationException("Last thing that needs "
        + "to be implemented");
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
    String folder = "/home/seisspace/data/testarea";
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
    double[] origins = vmff.getVModelGridOrigins();
    double[] deltas = vmff.getVModelGridDeltas();
    System.out.println("Origins: " + Arrays.toString(origins));
    System.out.println("Deltas: " + Arrays.toString(deltas));
    
    DistributedArrayMosaicPlot.showAsModalDialog(vmff.vModelData,"title2344");
    
    //TODO:  Test getting a window out of the middle
    //double[] windowOrigin = new double[] {
    //vmff.getWindowedDepthSlice(windowOrigin, windowLength)
    
    vmff.close();
  }
}

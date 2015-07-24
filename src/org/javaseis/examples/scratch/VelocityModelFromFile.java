package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.Arrays;

import org.javaseis.grid.GridDefinition;
import org.javaseis.io.Seisio;
import org.javaseis.volume.ISeismicVolume;
import org.javaseis.util.SeisException;

import beta.javaseis.distributed.FileSystemIOService;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;

public class VelocityModelFromFile {

  FileSystemIOService vModelPIO = null;
  GridDefinition vmodelGrid = null;
  GridDefinition volumeGrid = null;

  String folder;
  String file;


  //Start a seisio handle to read velocity slices in from a file.



  public VelocityModelFromFile(IParallelContext pc,String folder,String file) throws FileNotFoundException {
    try {
      this.folder = folder;
      this.file = file;
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
  }

  public void close() {
    try {
      vModelPIO.close();
    } catch (SeisException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  /**
   * @param depth - The physical depth in the model.  This method will return
   *                the slice that is closest to that depth.
   *                If you've already oriented your seismic volume within the 
   *                model, this will grab only the part of the model your data
   *                passes through.
   */
  public double[][] readSlice(double depth) {
    if (volumeGrid == null) return getEntireDepthSlice(depth);
    return getWindowedDepthSlice(getVolumeGridOrigins(),getVolumeGridLengths());
  }

  private long[] getVolumeGridLengths() {
    // TODO Auto-generated method stub
    return null;
  }

  private double[] getVolumeGridOrigins() {
    // TODO Auto-generated method stub
    return null;
  }
  
  //Temporary testing method.
  //TODO implement.  Basic call to map between shot index and model index.
  private double[] getVelocityModelXYZ(int[] seisVolumePositionIndex) {
    return new double[1];
  }
  
  

  public double[][] getEntireDepthSlice(double depth) {
    return getWindowedDepthSlice(getVelocityModelPhysicalOrigins(),
        getVelocityModelAxisLengths());
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
    //TODO Implement

    return new double[1][1];
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

  public double[] getVelocityModelPhysicalOrigins() {
    return vmodelGrid.getAxisPhysicalOrigins();
  }

  public double[] getVelocityModelPhysicalDeltas() {
    return vmodelGrid.getAxisPhysicalDeltas();
  }

  public long[] getVelocityModelAxisLengths() {
    return vmodelGrid.getAxisLengths();
  }

  public static void main(String[] args) {
    IParallelContext pc = new UniprocessorContext();
    VelocityModelFromFile vmff = null;
    try {
      vmff = new VelocityModelFromFile(pc,
          "/home/wilsonmr/javaseis","inputpwaves.VID");
    } catch (FileNotFoundException e) {
      System.out.println("Reading dataset failed.");
      e.printStackTrace();
    }

    vmff.open("r");
    double[] origins = vmff.getVelocityModelPhysicalOrigins();
    double[] deltas = vmff.getVelocityModelPhysicalDeltas();
    System.out.println("Origins: " + Arrays.toString(origins));
    System.out.println("Deltas: " + Arrays.toString(deltas));
    vmff.close();
  }



}

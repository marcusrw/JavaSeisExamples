package org.javaseis.velocity;

import org.javaseis.grid.GridDefinition;

public interface IVelocityModel {

  public abstract void open(String openMode);

  /**
   * Orient the input seismic volume within the larger velocity model,
   * so we know where to pull velocities from.
   * @param seismicVolumeGrid - The input seismic grid definition
   *                            (eg. a shot record)
   */
  public abstract void orientSeismicVolume(GridDefinition seismicVolumeGrid,
      int[] axisOrder);

  public abstract void close();

  public abstract long[] getVModelGridLengths();

  //Temporary testing method, maybe
  public abstract double[] getVelocityModelXYZ(
      int[] seisVolumePositionIndexInDepth);

  public abstract double readAverageVelocity(double depth);

  /**
   * @param depth - The physical depth in the model.  This method will return
   *                the slice that is closest to that depth.
   *                If you've already oriented your seismic volume within the 
   *                model, this will grab only the part of the model your data
   *                passes through.
   */
  public abstract double[][] readSlice(double depth);

  public abstract double[][] getEntireDepthSlice(double depth);

  /**
   * Get a rectangle out of the velocity model.  Pass in the origins and
   * deltas of your shot record to get just the part of the velocity model
   * your data passes through during migration.
   * @param windowOrigin - in STFVH order
   * @param windowLength - in STFVH order 
   * @return a slice of the velocity model
   */
  public abstract double[][] getWindowedDepthSlice(double[] windowOrigin,
      int[] windowLength, double depth);

}
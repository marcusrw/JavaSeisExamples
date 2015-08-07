package org.javaseis.velocity;

import java.util.Arrays;

import org.javaseis.grid.GridDefinition;

public class VelocityInDepthModel implements IVelocityModel {

  private double[] layerDepths;
  private double[] layerVelocities;

  GridDefinition vmodelGrid = null;

  /**
   * @param layerDepths - the depths of the interfaces between layers, including
   *                      the top and bottom of the model.  Should contain one
   *                      more element than the layerVelocities array
   * @param layerVelocities - The seismic velocities of the layers, in
   *                          consistent units.
   */
  public VelocityInDepthModel(double[] layerDepths,double[] layerVelocities) {
    //ASSERT layerDepths is sorted and contains unique values
    if (layerDepths.length != layerVelocities.length + 1) {
      throw new IllegalArgumentException("layerDepths should contain exactly"
          + " one more element than layerVelocities.");
    }
    this.layerDepths = layerDepths;
    Arrays.sort(layerDepths);
    if (!Arrays.equals(layerDepths,this.layerDepths)) {
      throw new IllegalArgumentException("layerDepths isn't properly sorted.");
    }
    this.layerVelocities = layerVelocities;
  }

  @Override
  public void open(String openMode) {
    // Does nothing, as there is no file to open.
  }

  @Override
  public void orientSeismicVolume(GridDefinition seismicVolumeGrid,
      int[] axisOrder) {
    vmodelGrid = seismicVolumeGrid;
    // Axis order is irrelevant for v(z)
  }

  @Override
  public void close() {
    // Does nothing as there is no file to close.
  }

  @Override
  public long[] getVModelGridLengths() {
    return vmodelGrid.getAxisLengths();
  }

  @Override
  public double[] getVelocityModelXYZ(int[] seisVolumePositionIndexInDepth) {
    throw new UnsupportedOperationException("For a v(z) medium, the physical"
        + " locations are not important.");
  }

  @Override
  public double readAverageVelocity(double depth) {
    int indx = 0;
    while (indx < layerVelocities.length && depth >= layerDepths[indx]) {
      indx++;
    }
    return layerVelocities[indx-1];
  }

  @Override
  public double[][] readSlice(double depth) {

    double velocity = readAverageVelocity(depth);
    int numTraces = (int)vmodelGrid.getAxisLength(1);
    int numFrames = (int)vmodelGrid.getAxisLength(2);

    double[][] slice = new double[numTraces][numFrames];
    for (int trace = 0 ; trace < numTraces ; trace++) {
      for (int frame = 0 ; frame < numFrames ; frame++) {
        slice[trace][frame] = velocity;
      }
    }
    return slice;
  }

  @Override
  public double[][] getEntireDepthSlice(double depth) {
    return readSlice(depth);
  }

  @Override
  public double[][] getWindowedDepthSlice(double[] windowOrigin,
      int[] windowLength, double depth) {
    double velocity = readAverageVelocity(depth);
    double[][] slice = new double[windowLength[0]][windowLength[1]];
    for (int m = 0 ; m < slice.length ; m++) {
      for (int n = 0 ; n < slice[0].length ; n++) {
        slice[m][n] = velocity;
      }
    }
    return slice;
  }

}

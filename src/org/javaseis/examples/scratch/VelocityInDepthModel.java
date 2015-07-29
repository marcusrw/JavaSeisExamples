package org.javaseis.examples.scratch;

import org.javaseis.grid.GridDefinition;

public class VelocityInDepthModel implements IVelocityModel {

  private double[] layerDepths;
  private double[] layerVelocities;

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
    this.layerVelocities = layerVelocities;
  }

  @Override
  public void open(String openMode) {
    // Does nothing, as there is no file to open.
  }

  @Override
  public void orientSeismicVolume(GridDefinition seismicVolumeGrid,
      int[] axisOrder) {
    // Axis order is irrelevant for v(z)
  }

  @Override
  public void close() {
    // Does nothing as there is no file to close.
  }

  @Override
  public long[] getVModelGridLengths() {
    throw new UnsupportedOperationException("For a v(z) medium, the grid lengths"
        + " are whatever you want them to be.");
  }

  @Override
  public double[] getVelocityModelXYZ(int[] seisVolumePositionIndexInDepth) {
    throw new UnsupportedOperationException("For a v(z) medium, the physical"
        + " locations are not important.");
  }

  @Override
  public double readAverageVelocity(double depth) {
    int indx = 0;
    while (depth > layerDepths[indx]) {
      indx++;
    }
    return layerVelocities[indx];
  }

  @Override
  public double[][] readSlice(double depth) {
    double velocity = readAverageVelocity(depth);
    return new double[][] {{velocity}};
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
package org.javaseis.source;

import org.javaseis.grid.ICheckedGrid;
import org.javaseis.imaging.PhaseShiftFFT3D;
import org.javaseis.tool.DataState;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.distributed.DistributedArray;

public interface ISourceVolume {

  /*
   * Converts the physical coordinates to Array Coordinates
   */
  public float[] convertPhysToArray(DataState dataState,
      double[] sourceXYZ, ISeismicVolume input);

  /*
   * Converts the physical coordinates to Array Coordinates
   * @Deprecated
   */
  @Deprecated
  public float[] covertPhysToArray(double[] sourceXYZ, ICheckedGrid CheckedGrid);

  /*
   * Returns the SeisFft3dNew Object (shot)
   */
  public PhaseShiftFFT3D getShot();

  /*
   * Returns the DistributedArray of the shot
   */
  public DistributedArray getDistributedArray();

}

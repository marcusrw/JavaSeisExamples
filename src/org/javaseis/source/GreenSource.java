package org.javaseis.source;

import org.javaseis.grid.ICheckedGrid;
import org.javaseis.imaging.PhaseShiftFFT3D;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.distributed.DistributedArray;

public class GreenSource implements ISourceVolume {

  @Override
  public PhaseShiftFFT3D getShot() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public DistributedArray getDistributedArray() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public float[] convertPhysToArray(double[] sourceXYZ, ISeismicVolume input) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public float[] covertPhysToArray(double[] sourceXYZ, ICheckedGrid CheckedGrid) {
    // TODO Auto-generated method stub
    return null;
  }

}

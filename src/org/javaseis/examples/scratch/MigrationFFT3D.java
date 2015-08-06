package org.javaseis.examples.scratch;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.fft.SeisFft3d;
import beta.javaseis.parallel.IParallelContext;

//I need to understand inheritance better to finish this.
//Probably implement Transformable instead
public class MigrationFFT3D extends SeisFft3d {

  private boolean isTimeTransformed = false;
  private boolean isSpaceTransformed = false;

  public MigrationFFT3D(int[] len, DistributedArray a) {
    super(len, a);
  }

  public MigrationFFT3D(DistributedArray a) {
    super(a);
  }

  public MigrationFFT3D(int[] len, float[] pad, DistributedArray a) {
    super(len, pad, a);
  }

  public MigrationFFT3D(int[] len, float[] pad, int[] isign, DistributedArray a) {
    super(len, pad, isign, a);
  }

  public MigrationFFT3D(IParallelContext pc, int[] len, float[] pad) {
    super(pc, len, pad);
  }

  public MigrationFFT3D(IParallelContext pc, int[] len, float[] pad, int[] isign) {
    super(pc, len, pad, isign);
  }

  //Main constructor.  The others all call this with the rest of the parameters
  //defaulted.
  public MigrationFFT3D(IParallelContext pc, int[] len, float[] pad,
      int[] isign, DistributedArray a) {
    super(pc, len, pad, isign, a);
  }

  public MigrationFFT3D(DistributedArray KyKxF, int[] isign) {
    super(KyKxF, isign);
    setTransformed(true);
  }

  private void setTransformed(boolean isTransformed) {
    this.isTimeTransformed = isTransformed;
    this.isSpaceTransformed = isTransformed;
  }
}

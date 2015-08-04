package org.javaseis.examples.scratch;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.fft.SeisFft3d;
import beta.javaseis.parallel.IParallelContext;

public class MigrationFFT3D extends SeisFft3d {

  public MigrationFFT3D(int[] len, DistributedArray a) {
    super(len, a);
    // TODO Auto-generated constructor stub
  }

  public MigrationFFT3D(DistributedArray a) {
    super(a);
    // TODO Auto-generated constructor stub
  }

  public MigrationFFT3D(int[] len, float[] pad, DistributedArray a) {
    super(len, pad, a);
    // TODO Auto-generated constructor stub
  }

  public MigrationFFT3D(int[] len, float[] pad, int[] isign, DistributedArray a) {
    super(len, pad, isign, a);
    // TODO Auto-generated constructor stub
  }

  public MigrationFFT3D(IParallelContext pc, int[] len, float[] pad) {
    super(pc, len, pad);
    // TODO Auto-generated constructor stub
  }

  public MigrationFFT3D(IParallelContext pc, int[] len, float[] pad, int[] isign) {
    super(pc, len, pad, isign);
    // TODO Auto-generated constructor stub
  }

  public MigrationFFT3D(IParallelContext pc, int[] len, float[] pad,
      int[] isign, DistributedArray a) {
    super(pc, len, pad, isign, a);
    // TODO Auto-generated constructor stub
  }

  public MigrationFFT3D(DistributedArray KyKxF, int[] isign) {
    super(KyKxF, isign);
    // TODO Auto-generated constructor stub
  }

}

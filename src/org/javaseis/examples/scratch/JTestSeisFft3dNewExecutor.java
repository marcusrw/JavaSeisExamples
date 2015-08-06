package org.javaseis.examples.scratch;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.junit.Test;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.ParallelTask;
import beta.javaseis.parallel.ParallelTaskExecutor;


public class JTestSeisFft3dNewExecutor {
  static int nmax = 64;
  static Random r = new Random(12345);
  static int n1, n2, n3;

  @Test
  public void testSeisFft3dNew() throws ExecutionException {
    int ntest = 2;
    for (int ntask = 1; ntask <= 8; ntask++) {
      int nmin = ntask;
      for (int j = 0; j < ntest; j++) {
        n1 = Math.max(nmin, (int) (r.nextFloat() * nmax));
        n2 = Math.max(nmin, (int) (r.nextFloat() * nmax));
        n3 = Math.max(nmin, (int) (r.nextFloat() * nmax));
//        System.out.println("Forward/Inverse FFT for ntask " + ntask + " shape "
//            + Arrays.toString(new int[] { n1, n2, n3 }));
        ParallelTaskExecutor.runTasks(TestForwardInverse.class, ntask);
      }
    }
  }

  public static class TestForwardInverse extends ParallelTask {
    @Override
    public void run() {
      IParallelContext pc = super.getParallelContext();
      int[] lengths = new int[] { n1, n2, n3 };
      float[] pad = new float[] { 0, 0, 0 };
      int[] position = new int[3];
      DistributedArray a = new DistributedArray(pc, lengths);
      DistributedArrayPositionIterator dapi = new DistributedArrayPositionIterator(
          a, position, 1, 0);
      float val;
      while (dapi.hasNext()) {
        dapi.next();
        val = r.nextFloat();
        a.putSample(val, position);
      }
      PhaseShiftFFT3D f3d = new PhaseShiftFFT3D(pc, lengths, pad);
      DistributedArray b = f3d.getArray();
      b.setShape(lengths);
      b.copy(a);
      f3d.forward();
      f3d.inverse();
      dapi.reset();
      float af, bf;
      while (dapi.hasNext()) {
        dapi.next();
        af = a.getFloat(position);
        bf = b.getFloat(position);
        assertEquals(
            "Value out of range at position " + Arrays.toString(position), af,
            bf, 1e-6f);
      }
      pc.barrier();
    }
  }

}

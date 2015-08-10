package org.javaseis.examples.scratch;

import beta.javaseis.distributed.DistributedArray;

public interface Transformable3D {

  /**
   * Helpful for junit test class class SeisFft3dTest.
   * 
   * @param banner caller uses to identify debug stage
   */
  public abstract void debugPrint(String banner);

  /**
   * Return the distributed array for the 3D FFT
   * 
   * @return array that will be used for forward/inverse transforms
   */
  public abstract DistributedArray getArray();

  /**
   * Convenience method.
   * 
   * @return the shape of the distributed array
   */
  public abstract int[] getShape();

  /**
   * Convenience method.
   * 
   * @return the "fft shape" of the distributed array, equals fftLengths for
   *         complex.
   */
  public abstract int[] getFftShape();

  /**
   * Convenience method.
   * 
   * @return the fft lengths
   */
  public abstract int[] getFftLengths();

  public abstract int[] getFftSigns();

  /**
   * Apply a forward Real to Complex 3D fft. The input distributed array
   * consists of real float values in "T,X,Y" order. On output, the distributed
   * array is reshaped to complex float values (MultiArray element count of 2)
   * in "Ky,Kx,F" order - the order of the transformed axes is the reverse of
   * the input.
   * 
   */
  public abstract void forward();

  /**
   * Apply inverse Complex to Real 3D fft. The input distributed array consists
   * of complex float values in "Ky,Kx,F" order. On output, the distributed
   * array is reshaped to real float values in "T,X,Y" order, which is the
   * reverse of the input.
   * 
   */
  public abstract void inverse();

  /**
   * Sets sample rates in time domain (could actually be depth domain).
   * 
   * @param sampleRates sample rates in T,X,Y order (T is in sec, not msec)
   */
  public abstract void setTXYSampleRates(double[] sampleRates);

  /**
   * Converts array indices to physical (Ky, Kx, F) coordinates (cycles/ft and
   * cycles/sec, for example).
   * 
   * @param position in terms of SAMPLE_INDEX, TRACE_INDEX, and FRAME_INDEX
   * @param buf array containing Ky, Kx and F result (Ky and Kx might be
   *          negative)
   */
  public abstract void getKyKxFCoordinatesForPosition(int[] position,
      double[] buf);

}
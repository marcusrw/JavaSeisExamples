package org.javaseis.examples.scratch;

import java.util.Arrays;

import beta.javaseis.array.TransposeType;
import beta.javaseis.distributed.Decomposition;
import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.fft.IFFT;
import beta.javaseis.fft.SeisFft;


/**
 * Sample 3D Fourier Transform for real data that illustrates usage of
 * DistributedArray classes.
 * 
 * @author Chuck Mosher for JavaSeis.org
 * 
 */
public class SeisFft3dNew {

  /**
   *
   */
  private final IFFT       _f1, _f2, _f3;

  //Distributed Array containing data
  private DistributedArray da;

  //Temporary view of the original data
  private DistributedArray realDataView;           

  private final int[]      _inputShape;           // Input lengths
  private final int[]      _fftLengths;           // FFT lengths
  private final int[]      _fftShape;             // FFT shape, equals
  // fftLengths for Complex
  private final int[]      _padShape;             // padded lengths for input
  // axes
  private final int[]      _fpadShape;            // padded lengths transform
  // axes
  private int[]            _fftSigns;
  private double[]         _timeDomainSampleRate; // could actually be depth
  // domain
  private boolean          _isTimeTransformed = false;
  private boolean          _isSpaceTransformed = false;

  /**
   * Use existing storage for a 3D distributed FFT
   * 
   * @param len unpadded actual data length for each axis
   * @param a pre-existing DistributedArray
   */
  public SeisFft3dNew(int[] len, DistributedArray a) {
    this(a.getParallelContext(), len, new float[] { 0.0f, 0.0f, 0.0f },
        new int[] { -1, 1, 1 }, a);
  }

  /**
   * Use existing storage for a 3D distributed FFT using DistributedArray shape as lengths
   * 
   * @param a pre-existing DistributedArray
   */
  public SeisFft3dNew(DistributedArray a) {
    this(a.getParallelContext(), a.getShape(),
        new float[] { 0.0f, 0.0f, 0.0f }, new int[] { -1, 1, 1 }, a);
  }

  /**
   * Use existing storage for a 3D distributed FFT
   * 
   * @param len unpadded actual data length for each axis
   * @param pad factor in percent for each axis
   * @param a pre-existing DistributedArray
   */
  public SeisFft3dNew(int[] len, float[] pad, DistributedArray a) {
    this(a.getParallelContext(), len, pad, new int[] { -1, 1, 1 }, a);
  }

  /**
   * Use existing storage for a 3D distributed FFT
   * 
   * @param len unpadded actual data length for each axis
   * @param pad factor in percent for each axis
   * @param isign exponent signs to use for forward transforms for each axis
   * @param a pre-existing DistributedArray
   */
  public SeisFft3dNew(int[] len, float[] pad, int[] isign, DistributedArray a) {
    this(a.getParallelContext(), len, pad, isign, a);
  }

  /**
   * Allocate storage for a 3D distributed FFT
   * 
   * @param pc parallel context of tasks
   * @param len unpadded actual data length for each axis
   * @param pad transform padding factor in percent for each axis
   */
  public SeisFft3dNew(IParallelContext pc, int[] len, float[] pad) {
    this(pc, len, pad, new int[] { -1, 1, 1 }, null);
  }

  /**
   * Allocate storage for a 3D distributed FFT
   * 
   * @param pc parallel context of tasks
   * @param len unpadded actual data length for each axis
   * @param pad transform padding factor in percent for each axis
   * @param isign exponent signs to use for forward transforms for each axis
   */
  public SeisFft3dNew(IParallelContext pc, int[] len, float[] pad, int[] isign) {
    this(pc, len, pad, isign, null);
  }

  /**
   * Allocate storage for a 3D distributed FFT if 'a' is null, otherwise use
   * 'a'.
   * 
   * @param pc parallel context of tasks
   * @param len length of each axis for the transform
   * @param pad transform padding factor in percent for each axis
   * @param isign exponent signs to use for forward transforms for each axis
   * @param a pre-existing or constructor will instantiate if a == null
   */
  public SeisFft3dNew(IParallelContext pc, int[] len, float[] pad, int[] isign,
      DistributedArray a) {

    // Check that input arrays have a length of at least 3
    assert len.length > 2;
    assert pad.length > 2;
    assert isign.length > 2;

    _fftSigns = new int[] { isign[0], isign[1], isign[2] };

    // Get fft lengths isign == 0 says don't actually FFT that axis,
    // but assume it is being handled separately.
    if (isign[0] != 0)
      _f1 = new SeisFft(len[0], pad[0], IFFT.Type.REAL, isign[0]);
    else
      _f1 = new SeisFft(len[0], pad[0], IFFT.Type.REAL);
    if (isign[1] != 0)
      _f2 = new SeisFft(len[1], pad[1], IFFT.Type.COMPLEX, isign[1]);
    else
      _f2 = new SeisFft(len[1], pad[1], IFFT.Type.COMPLEX);
    if (isign[2] != 0)
      _f3 = new SeisFft(len[2], pad[2], IFFT.Type.COMPLEX, isign[2]);
    else
      _f3 = new SeisFft(len[2], pad[2], IFFT.Type.COMPLEX);

    _fftLengths = new int[] { _f1.getLength(), _f2.getLength(), _f3.getLength() };

    // If 'a' is null, we assume we've been handed an appropriate distributed
    // array
    if (a != null) {
      da = a;

      // If 'a' is null, we create a new distributed array ourselves
    } else {

      // Although the input is real, the transform will be complex during the
      // transposes, so we allocate the distributed array as complex numbers
      int[] lengths = _fftLengths.clone();
      lengths[0] = lengths[0] / 2 + 1;

      // We need BLOCK in all dimensions to prepare for transpose
      int elementCount = 2;
      int[] d = { Decomposition.BLOCK, Decomposition.BLOCK, Decomposition.BLOCK };
      da = new DistributedArray(pc, float.class, 3, elementCount, lengths, d);

      // Reset the element count to 1 and length of the first dimension back to
      // real numbers rather than complex so that the array can be loaded with
      // reals
      // lengths = _a.getShape();
      da.setElementCount(1);
      // lengths[0] = 2*lengths[0];
    }

    // Set the shape to the input data sizes
    _inputShape = len.clone();
    da.setShape(len);
    _fftShape = _fftLengths.clone();
    _fftShape[0] = 1 + _fftLengths[0] / 2;
    _padShape = _inputShape.clone();
    _fpadShape = _fftShape.clone();
    for (int i = 0; i < 3; i++) {
      _padShape[i] = (int) Decomposition
          .paddedLength(_inputShape[i], pc.size());
      _fpadShape[i] = (int) Decomposition.paddedLength(_fftShape[i], pc.size());
    }
    _isTimeTransformed = false;
    _isSpaceTransformed = false;
  }

  /**
   * Constructor for when data is initially in the transformed domain, such that
   * the next logical next step for the caller after constructing will be to
   * call inverse() rather than forward() as with the other SeisFft3d
   * constructors (to date).
   * 
   * @param isign exponent signs previously used for forward transforms for each
   *          axis
   * @param KyKxF distributed array
   */
  public SeisFft3dNew(DistributedArray KyKxF, int[] isign) {

    // Check that input array has a length of at least 3
    assert isign.length > 2;

    da = KyKxF;
    _isTimeTransformed = true;
    _isSpaceTransformed = true;

    int ret;
    int[] len = KyKxF.getShape();

    _fftSigns = new int[] { isign[0], isign[1], isign[2] };

    ret = guaranteeMatchedShape(2 * len[2], 0f, IFFT.Type.REAL);
    if (isign[0] != 0)
      _f1 = new SeisFft(ret, 0f, IFFT.Type.REAL, isign[0]);
    else
      _f1 = new SeisFft(ret, 0f, IFFT.Type.REAL);

    ret = guaranteeMatchedShape(2 * len[1], 0f, IFFT.Type.COMPLEX);
    if (isign[1] != 0)
      _f2 = new SeisFft(ret, 0f, IFFT.Type.COMPLEX, isign[1]);
    else
      _f2 = new SeisFft(ret, 0f, IFFT.Type.COMPLEX);

    ret = guaranteeMatchedShape(2 * len[0], 0f, IFFT.Type.COMPLEX);
    if (isign[2] != 0)
      _f3 = new SeisFft(ret, 0f, IFFT.Type.COMPLEX, isign[2]);
    else
      _f3 = new SeisFft(ret, 0f, IFFT.Type.COMPLEX);

    _fftLengths = new int[] { _f1.getLength(), _f2.getLength(), _f3.getLength() };
    _inputShape = new int[] { _fftLengths[0], _fftLengths[1], _fftLengths[2] };
    _fftShape = _fftLengths.clone();
    _fftShape[0] = 1 + _fftLengths[0] / 2;
    _padShape = _inputShape.clone();
    _fpadShape = _fftShape.clone();
  }

  /**
   * Here is a safe way to guarantee a matched shape -- send it through the same
   * code it would go through if we were starting with a forward transform.
   * 
   * @param len value of shape that we're needing to match
   * @param pad fft padding
   * @param type REAL or COMPLEX
   * @return data len that
   */
  private final int guaranteeMatchedShape(int matchLen, float pad,
      IFFT.Type type) {
    if (matchLen < 2)
      throw new IllegalArgumentException("len cannot be less than 2");

    // Make a good starting guess.
    int len = Math.min(2, matchLen / 2);

    // Coarse loop, increasing.
    while (true) {
      SeisFft fft = new SeisFft(len, pad, type);
      if (fft.getArrayLength() > matchLen)
        break;
      len += 10;
    }

    // Fine loop, decreasing.
    while (len > 2) {
      SeisFft fft = new SeisFft(len, pad, type);
      if (fft.getArrayLength() <= matchLen)
        break;
      len--;
    }

    // len will be the largest data length compatible with matchLen (shape[n])
    return len;
  }

  /**
   * Return a DistributedArray shape that is large enough for FFT and transpose
   * padding. Returned shape is for elementCount = 2, complex numbers.
   * 
   * @param lengths input data lengths
   * @param pad FFT padding
   * @param pc IParallelContext for the distributed array
   * @return DistributedArray shape
   */
  public static int[] getTransformShape(int[] lengths, float[] pad,
      IParallelContext pc) {

    return getTransformShape(lengths, pad, pc.size());
  }

  /**
   * Return a DistributedArray shape that is large enough for FFT and transpose
   * padding for a given task count. Returned shape is for elementCount = 2,
   * complex numbers.
   * 
   * @param lengths input data lengths
   * @param pad FFT padding
   * @param size number of mpi tasks the job is (or will be) running on
   * @return DistributedArray shape
   */
  public static int[] getTransformShape(int[] lengths, float[] pad, int size) {

    int[] len = new int[3];

    int flen = SeisFft.getFftLength(lengths[0], pad[0], IFFT.Type.REAL);
    len[0] = (int) Decomposition.paddedLength(1 + flen / 2, size);

    flen = SeisFft.getFftLength(lengths[1], pad[1], IFFT.Type.COMPLEX);
    len[1] = (int) Decomposition.paddedLength(flen, size);

    flen = SeisFft.getFftLength(lengths[2], pad[2], IFFT.Type.COMPLEX);
    len[2] = (int) Decomposition.paddedLength(flen, size);

    return len;
  }

  /**
   * Helpful for junit test class class SeisFft3dTest.
   * 
   * @param banner caller uses to identify debug stage
   */
  public final void debugPrint(String banner) {
    System.out.println("***********************************************");
    System.out.println(banner);
    System.out.printf("_inputShape = %d %d %d\n", _inputShape[0],
        _inputShape[1], _inputShape[2]);
    System.out.printf("_fftLengths = %d %d %d\n", _fftLengths[0],
        _fftLengths[1], _fftLengths[2]);
    System.out.printf("shape = %d %d %d\n", da.getShape()[0], da.getShape()[1],
        da.getShape()[2]);
    System.out.println("***********************************************");
  }

  /**
   * Return the distributed array for the 3D FFT
   * 
   * @return array that will be used for forward/inverse transforms
   */
  public DistributedArray getArray() {
    return da;
  }

  /**
   * Convenience method.
   * 
   * @return the shape of the distributed array
   */
  public int[] getShape() {
    return da.getShape();
  }

  /**
   * Convenience method.
   * 
   * @return the "fft shape" of the distributed array, equals fftLengths for
   *         complex.
   */
  public int[] getFftShape() {
    return _fftShape.clone();
  }

  /**
   * Convenience method.
   * 
   * @return the fft lengths
   */
  public int[] getFftLengths() {
    return _fftLengths.clone();
  }

  public int[] getFftSigns() {
    return _fftSigns.clone();
  }

  /**
   * Apply a forward Real to Complex 3D fft. The input distributed array
   * consists of real float values in "T,X,Y" order. On output, the distributed
   * array is reshaped to complex float values (MultiArray element count of 2)
   * in "Ky,Kx,F" order - the order of the transformed axes is the reverse of
   * the input.
   * 
   */
  public void forward() {

    /*
    TODO refactor so these choices are clear without having to do
    a big data dump.
    //Get shape information
    //Original input shape
    int nt = _inputShape[0];
    int nx = _inputShape[1];
    int ny = _inputShape[2];
    // Transform padded shape
    int nft = _fftShape[0];
    int nkx = _fftShape[1];
    int nky = _fftShape[2];
    // Transpose padded shape for input data
    int ntp = _padShape[0];
    int nxp = _padShape[1];
    int nyp = _padShape[2];
    // Transpose padded shape for transform data
    int nftp = _fpadShape[0];
    int nkxp = _fpadShape[1];
    int nkyp = _fpadShape[2];
     */

    forwardTemporal();
    forwardSpatial2D();
  }

  public void forwardTemporal() {
    checkDAShapeForForwardTransform();

    if (_isTimeTransformed) {
      throw new IllegalArgumentException(
          "Attempted temporal FFT on data that is already in the frequency domain.");
    }
    // Make the padded y-axis 'visible'
    da.setShape(new int[] { _inputShape[0], _inputShape[1], _padShape[2] });

    // Reshape the first two dimensions for the complex transforms
    da.reshape(new int[] { 2 * _fpadShape[0], _fftShape[1], _padShape[2] });

    // Retain a temporary view of the real data
    realDataView = da.distributedView();

    // Set the element count and length for the complex transform
    //In inverse, setting the element count back to 1 and setShape are
    //done together.  I'm changing this one for consistency
    //da.setElementCount(2);
    //da.setShape(2,new int[] { _fpadShape[0], _fftShape[1], _padShape[2] });
    da.setShape(2,new int[] { _fpadShape[0], _inputShape[1], _padShape[2] });

    // FFT along "T" axis
    // T,X,Y -> F,X,Y
    System.out.println("FFT over T");
    int[] inputShape = _inputShape;
    temporalFFT(_f1,inputShape);
    //release realDataView for the garbage collector
    realDataView = null;
    _isTimeTransformed = true;
  }

  private void checkDAShapeForForwardTransform() {
    if (da.getLength(0) != _inputShape[0] || da.getLength(1) != _inputShape[1]
        || da.getLength(2) != _inputShape[2] || da.getElementCount() != 1)
      throw new RuntimeException("\nDistributedArray shape "
          + Arrays.toString(da.getShape())
          + "\nDoes not match SeisFft3d input shape "
          + Arrays.toString(_inputShape));
  }

  private void temporalFFT(IFFT fft,int[] inputShape) {
    int[] position = {0,0,0};
    float[] fftBuffer = createFFTBuffer(_fftShape);
    for (int k = 0; k < da.getLocalLength(2); k++) {
      position[2] = da.localToGlobal(2, k);
      if (position[2] >= inputShape[2])
        continue;
      for (int j = 0; j < inputShape[1]; j++) {
        position[1] = j;
        realDataView.getTrace(fftBuffer, position);
        fft.realToComplex(fftBuffer);
        da.putTrace(fftBuffer, position);
      }
    }
  }

  public void forwardSpatial2D() {
    if (_isSpaceTransformed) {
      throw new IllegalArgumentException(
          "Attempted spatial FFT on data that is already in the wavenumber domain.");
    }
    // Transpose and bring "X" axis to front
    // nftp,nkx,nyp (213) nkx,nftp,nyp
    da.setShape(2,new int[] { _fpadShape[0], _fftShape[1], _padShape[2] });
    da.transpose(TransposeType.T213);
    // FFT over "X" axis
    System.out.println("FFT over X");
    int[] inputShape = new int[] {_inputShape[1],_fftShape[0],_inputShape[2]};
    spatialFFT(_f2,inputShape);

    // Transpose and bring "Y" axis to front
    // nkx,nftp,nyp (312) nyp,nkx,nftp
    da.transpose(TransposeType.T312);

    // Expand Y axis to transform length
    // nyp,nkx,nftp --> nky,nkx,nftp
    da.reshape(new int[] { _fftShape[2], _fftShape[1], _fpadShape[0] });   
    System.out.println("FFT over Y");
    inputShape = new int[] {_inputShape[2],_fftShape[1],_fftShape[0]};
    spatialFFT(_f3,inputShape);

    // Hide the frequency axis padding
    // (nky,nkx,nftp) --> (nky,nkx,nft)
    da.setShape(2, new int[] { _fftShape[2], _fftShape[1], _fftShape[0] });

    // Return with data axes Ky,Kx,F
    _isSpaceTransformed = true;
  }

  private void spatialFFT(IFFT fft,int[] inputShape) {
    int[] position = {0,0,0};
    float[] fftBuffer = createFFTBuffer(_fftShape);
    for (int k = 0; k < da.getLocalLength(2); k++) {
      position[2] = da.localToGlobal(2, k);
      if (position[2] >= inputShape[2])
        continue;
      for (int j = 0; j < inputShape[1]; j++) {
        position[1] = j;
        da.getTrace(fftBuffer, position);
        fft.complexForward(fftBuffer);
        da.putTrace(fftBuffer, position);
      }
    }
  }

  /**
   * Apply inverse Complex to Real 3D fft. The input distributed array consists
   * of complex float values in "Ky,Kx,F" order. On output, the distributed
   * array is reshaped to real float values in "T,X,Y" order, which is the
   * reverse of the input.
   * 
   */
  public void inverse() {

    /*
    // Get shape information
    // Original input shape
    int nt = _inputShape[0];
    int nx = _inputShape[1];
    int ny = _inputShape[2];
    // Transform padded shape
    int nft = _fftShape[0];
    int nkx = _fftShape[1];
    int nky = _fftShape[2];
    // Transpose padded shape for input data
    int ntp = _padShape[0];
    int nxp = _padShape[1];
    int nyp = _padShape[2];
    // Transpose padded shape for transform data
    int nftp = _fpadShape[0];
    int nkxp = _fpadShape[1];
    int nkyp = _fpadShape[2];
     */

    inverseSpatial2D();
    inverseTemporal();
  }

  public void inverseSpatial2D() {
    checkDAShapeForInverseTransform();

    if (!_isSpaceTransformed) {
      throw new IllegalArgumentException(
          "Attempted spatial IFFT on data that is not in the wavenumber domain.");      
    }
    // Make 3rd dimension padding 'visible'
    da.setShape(new int[] { _fftShape[2], _fftShape[1], _fpadShape[0] });

    // Inverse FFT over "Y" axis
    int[] inputShape = new int[] {_fftShape[2],_fftShape[1],_fftShape[0]};
    System.out.println("IFFT over Y");
    spatialIFFT(_f3,inputShape);

    // Reshape to truncate Y axis back to original padded length
    da.reshape(new int[] { _padShape[2], _fftShape[1], _fpadShape[0] });

    // Transpose and bring "Kx" axis to front
    // nyp,nkx,nftp (231) nkx,nftp,nyp
    da.transpose(TransposeType.T231);

    // Inverse FFT over "Kx" axis
    inputShape = new int[] {_fftShape[1],_fftShape[0],_inputShape[2]};
    System.out.println("IFFT over X");
    System.out.println("DA task #" + da.getParallelContext().rank());
    spatialIFFT(_f2,inputShape);
    // Transpose and bring "F" axis to front
    // nx,nftp,nyp (213) nftp,nx,nyp
    da.transpose(TransposeType.T213);
    _isSpaceTransformed = false;
    da.setShape(2,new int[] { _fpadShape[0], _inputShape[1], _padShape[2] });
  }

  private void checkDAShapeForInverseTransform() {
    if (da.getLength(0) != _fftShape[2] || da.getLength(1) != _fftShape[1]
        || da.getLength(2) != _fftShape[0] || da.getElementCount() != 2)
      throw new RuntimeException("\nDistributedArray shape "
          + Arrays.toString(da.getShape())
          + "\nDoes not match transform shape " + Arrays.toString(_fftShape));
  }

  private void spatialIFFT(IFFT fft3, int[] inputShape) {
    int[] position = new int[] { 0, 0, 0 };
    float[] fftBuffer = createFFTBuffer(_fftShape);
    for (int k = 0; k < da.getLocalLength(2); k++) {
      position[2] = da.localToGlobal(2, k);
      if (position[2] >= inputShape[2])
        continue;
      for (int j = 0; j < inputShape[1]; j++) {
        position[1] = j;
        da.getTrace(fftBuffer, position);
        fft3.complexInverse(fftBuffer);
        da.putTrace(fftBuffer, position);
      }
    }
  }

  public void inverseTemporal() {
    // Inverse FFT along F axis
    if (!_isTimeTransformed) {
      throw new IllegalArgumentException(
          "Attempted temporal IFFT on data that is not in the frequency domain.");      
    }
    System.out.println("IFFT over T");
    int[] inputShape = new int[] {_fftShape[0],_inputShape[1],_inputShape[2]};
    temporalIFFT(_f1,inputShape);

    // Reshape to real numbers and remove inner padding
    da.reshape(1, new int[] { _inputShape[0], _inputShape[1], _padShape[2] });

    // Re-establish the outer padding
    da.setShape(new int[] { _inputShape[0], _inputShape[1], _inputShape[2] });

    // Return with data in T,X,Y order
    _isTimeTransformed = false;
  }

  private void temporalIFFT(IFFT fft,int[] inputShape) {
    float[] fftBuffer = createFFTBuffer(_fftShape);
    int[] position = new int[] { 0, 0, 0 };
    for (int k = 0; k < da.getLocalLength(2); k++) {
      position[2] = da.localToGlobal(2, k);
      if (position[2] >= inputShape[2])
        continue;
      for (int j = 0; j < inputShape[1]; j++) {
        position[1] = j;
        da.getTrace(fftBuffer, position);
        fft.complexToReal(fftBuffer);
        da.putTrace(fftBuffer, position);
      }
    }
  }

  private float[] createFFTBuffer(int[] fftShape) {
    int fftBufferLength = findLongestAxisLength(fftShape);
    return new float[2*fftBufferLength];
  }

  private int findLongestAxisLength(int[] fftShape) {
    int fftBufferLength = 0;
    for (int k = 0 ; k < fftShape.length ; k++) {
      fftBufferLength = Math.max(fftBufferLength,fftShape[k]);
    }
    return fftBufferLength;
  }

  /**
   * Sets sample rates in time domain (could actually be depth domain).
   * 
   * @param sampleRates sample rates in T,X,Y order (T is in sec, not msec)
   */
  public void setTXYSampleRates(double[] sampleRates) {
    if (_timeDomainSampleRate == null)
      _timeDomainSampleRate = new double[3];
    _timeDomainSampleRate[0] = sampleRates[0];
    _timeDomainSampleRate[1] = sampleRates[1];
    _timeDomainSampleRate[2] = sampleRates[2];
  }

  /**
   * Converts array indices to physical (Ky, Kx, F) coordinates (cycles/ft and
   * cycles/sec, for example).
   * 
   * @param position in terms of SAMPLE_INDEX, TRACE_INDEX, and FRAME_INDEX
   * @param buf array containing Ky, Kx and F result (Ky and Kx might be
   *          negative)
   */
  public void getKyKxFCoordinatesForPosition(int[] position, double[] buf) {
    if (_timeDomainSampleRate == null)
      throw new IllegalStateException(
          "You need to call setTXYSampleRates() before calling this method.");

    int pos0 = position[0]; // Ky position
    int pos1 = position[1]; // Kx position
    int pos2 = position[2]; // F position
    if (pos0 > _fftLengths[2] / 2)
      pos0 = pos0 - _fftLengths[2];
    if (pos1 > _fftLengths[1] / 2)
      pos1 = pos1 - _fftLengths[1];
    // NOTE: Do not unwrap pos2 (frequency) axis.

    buf[0] = pos0 / (_timeDomainSampleRate[2] * _fftLengths[2]);
    buf[1] = pos1 / (_timeDomainSampleRate[1] * _fftLengths[1]);
    buf[2] = pos2 / (_timeDomainSampleRate[0] * _fftLengths[0]);
  }
}


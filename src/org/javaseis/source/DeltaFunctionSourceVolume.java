package org.javaseis.source;

import org.javaseis.grid.ICheckedGrid;
import org.javaseis.imaging.PhaseShiftFFT3D;
import org.javaseis.volume.ISeismicVolume;
import org.junit.Assert;

import beta.javaseis.distributed.DistributedArray;

public class DeltaFunctionSourceVolume implements ISourceVolume {

  PhaseShiftFFT3D shot;
  double[] physicalSourceXYZ;
  float[] arraySourceXYZ;
  int[] AXIS_ORDER;

  public DeltaFunctionSourceVolume(ISeismicVolume input, PhaseShiftFFT3D shot) {
    int[] aPosInVol = new int[] { 0, 0, 0 };
    double[] voidRecXYZ = new double[3];
    input.getCoords(aPosInVol, this.physicalSourceXYZ, voidRecXYZ);
    // TODO:
    Assert.assertNotEquals("Source Depth should be 20", 0, this.physicalSourceXYZ[0]);

    this.arraySourceXYZ = convertPhysToArray(this.physicalSourceXYZ, input);
    this.shot = checkShotIsInFXY(shot);

    generateSourceSignature(arraySourceXYZ);
  }

  public DeltaFunctionSourceVolume(ISeismicVolume input, PhaseShiftFFT3D shot, double[] physicalSourceXYZ) {
    this.physicalSourceXYZ = physicalSourceXYZ;
    // TODO:
    Assert.assertNotEquals("Source Depth should be 20", 0, this.physicalSourceXYZ[0]);

    this.arraySourceXYZ = convertPhysToArray(this.physicalSourceXYZ, input);
    this.shot = checkShotIsInFXY(shot);

    generateSourceSignature(arraySourceXYZ);
  }

  public DeltaFunctionSourceVolume(ICheckedGrid CheckedGrid, PhaseShiftFFT3D shot) {

    // Get the physical source
    this.physicalSourceXYZ = CheckedGrid.getSourceXYZ();
    Assert.assertNotEquals("Source Depth should be 20", 0, this.physicalSourceXYZ[0]);

    // Get the Axis Order
    this.AXIS_ORDER = CheckedGrid.getAxisOrder();

    // Compute the Array coordinates based on the grid
    this.arraySourceXYZ = covertPhysToArray(this.physicalSourceXYZ, CheckedGrid);

    // Check the shot DA is in the right domain (FXY in this case)
    this.shot = checkShotIsInFXY(shot);

    // Generate
    generateSourceSignature(arraySourceXYZ);
  }

  public DeltaFunctionSourceVolume(ICheckedGrid CheckedGrid, PhaseShiftFFT3D shot, double[] physicalSourceXYZ,
      int[] AXIS_ORDER) {
    // Get the physical source
    this.physicalSourceXYZ = physicalSourceXYZ;

    // Get the Axis Order
    this.AXIS_ORDER = AXIS_ORDER;

    // Need to convert this grid into array coordinates
    this.arraySourceXYZ = covertPhysToArray(this.physicalSourceXYZ, CheckedGrid);

    this.shot = checkShotIsInFXY(shot);

    // Generate
    generateSourceSignature(arraySourceXYZ);
  }

  private PhaseShiftFFT3D checkShotIsInFXY(PhaseShiftFFT3D shot) {
    if (!shot.isTimeTransformed()) {
      shot.forwardTemporal();
      return shot;
    }
    return shot;
  }

  /*
   * Converts the physical coordinates to Array Coordinates
   */
  public float[] convertPhysToArray(double[] sourceXYZ, ISeismicVolume input) {

    int numDims = input.getNumDimensions();
    float[] vS = new float[numDims];

    for (int i = 0; i < numDims; i++) {
      // TODO: stuff
      vS[i] = 0;
      double minPhys0 = input.getGlobalGrid().getAxisPhysicalOrigin(i);
      double phyDelta = input.getGlobalGrid().getAxisPhysicalDelta(i);

      vS[i] = (float) ((sourceXYZ[i] - minPhys0) / phyDelta);
    }

    return vS;
  }

  /*
   * Converts the physical coordinates to Array Coordinates
   */
  public float[] covertPhysToArray(double[] sourceXYZ, ICheckedGrid CheckedGrid) {

    // output buffer
    float[] vS = new float[AXIS_ORDER.length];

    for (int i = 0; i < AXIS_ORDER.length; i++) {
      int currentAxis = CheckedGrid.getAxisOrder()[i];
      double minPhys0 = CheckedGrid.getModifiedGrid().getAxisPhysicalOrigin(currentAxis);
      double axisPhysDelta = CheckedGrid.getModifiedGrid().getAxisPhysicalDelta(currentAxis);
      vS[i] = (float) ((sourceXYZ[i] - minPhys0) / axisPhysDelta);
    }
    return vS;
  }

  private void generateSourceSignature(float[] sourceXYZ) {
    if (sourceXYZ.length != 3)
      throw new IllegalArgumentException("Wrong number of elements for sourceXYZ");

    int sourceX = (int) Math.floor(sourceXYZ[0]);
    while (sourceX < sourceXYZ[0] + 1) {
      int sourceY = (int) Math.floor(sourceXYZ[1]);
      while (sourceY < sourceXYZ[1] + 1) {
        float weight = Math.max(0, 1 - euclideanDistance(sourceX, sourceY, sourceXYZ));
        putWhiteSpectrum(shot, sourceX, sourceY, weight);
        sourceY++;
      }
      sourceX++;
    }
  }

  /**
   * @param sourceX
   *          - Target X index in the grid
   * @param sourceY
   *          - Target Y index in the grid
   * @param sourceXYZ
   *          - Actual Source position in the grid
   * @return The Euclidean distance between the current array index and the
   *         input source.
   */
  private float euclideanDistance(float sourceX, float sourceY, float[] sourceXYZ) {

    float dx2 = (sourceX - sourceXYZ[0]) * (sourceX - sourceXYZ[0]);
    float dy2 = (sourceY - sourceXYZ[1]) * (sourceY - sourceXYZ[1]);
    return (float) Math.sqrt(dx2 + dy2);
  }

  private void putWhiteSpectrum(PhaseShiftFFT3D source, int sourceX, int sourceY, float amplitude) {

    int[] position = new int[] { 0, sourceX, sourceY };
    int[] volumeShape = source.getArray().getShape();
    float[] sample = new float[] { amplitude, 0 }; // amplitude+0i complex
    DistributedArray sourceDA = source.getArray();
    while (position[0] < volumeShape[0]) {
      sourceDA.putSample(sample, position);
      position[0]++;
    }
  }

  /*
   * Returns the SeisFft3dNew Object (shot)
   */
  public PhaseShiftFFT3D getShot() {
    return shot;
  }

  public DistributedArray getShotDA() {
    return shot.getArray();
  }

  /*
   * Returns the DistributedArray of the shot
   */
  public DistributedArray getDistributedArray() {
    return shot.getArray();
  }
}

package org.javaseis.examples.scratch;

import beta.javaseis.distributed.DistributedArray;

public class SourceVolume implements ISourceVolume {

  SeisFft3dNew shot;
  double[] physicalSourceXYZ;
  float[] arraySourceXYZ;
  int[] AXIS_ORDER;

  public SourceVolume(ICheckGrids CheckedGrid, SeisFft3dNew shot) {
    // Get the physical source
    this.physicalSourceXYZ = CheckedGrid.getSourceXYZ();

    // Get the Axis Order
    this.AXIS_ORDER = CheckedGrid.getAxisOrder();

    // Compute the Array coordinates based on the grid
    this.arraySourceXYZ = covertPhysToArray(this.physicalSourceXYZ, CheckedGrid);

    // Check the shot DA is in the right domain (FXY in this case)
    this.shot = checkShotIsInFXY(shot);

    // Generate
    generateSourceSignature(arraySourceXYZ);

  }

  public SourceVolume(ICheckGrids CheckedGrid, SeisFft3dNew shot, double[] physicalSourceXYZ, int[] AXIS_ORDER) {
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

  private SeisFft3dNew checkShotIsInFXY(SeisFft3dNew shot) {
    if (!shot.isTimeTransformed()) {
      shot.forwardTemporal();
      return shot;
    }
    return shot;
  }

  /*
   * Converts the physical coordinates to Array Coordinates
   */
  public float[] covertPhysToArray(double[] sourceXYZ, ICheckGrids CheckedGrid) {
    //Correct
    //System.out.println("[covertPhysToArray]: " + Arrays.toString(sourceXYZ));

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
    //System.out.println("[generateSourceSignature]: " + Arrays.toString(sourceXYZ));
    if (sourceXYZ.length != 3)
      throw new IllegalArgumentException("Wrong number of elements for sourceXYZ");

    //TODO: !!!!!THIS IS NOT CORRECT FIX IT AT SOME POINT!!!!!
    if (sourceXYZ[2] > 0){
      sourceXYZ[2] = 0;
      System.out.println("Source Depth changed to: " + sourceXYZ[2]);
      //throw new UnsupportedOperationException("Sources at Depths besides zero not yet implemented");
    }

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
   *            - Target X index in the grid
   * @param sourceY
   *            - Target Y index in the grid
   * @param sourceXYZ
   *            - Actual Source position in the grid
   * @return The Euclidean distance between the current array index and the
   *         input source.
   */
  private float euclideanDistance(float sourceX, float sourceY, float[] sourceXYZ) {

    float dx2 = (sourceX - sourceXYZ[0]) * (sourceX - sourceXYZ[0]);
    float dy2 = (sourceY - sourceXYZ[1]) * (sourceY - sourceXYZ[1]);
    return (float) Math.sqrt(dx2 + dy2);
  }

  private void putWhiteSpectrum(SeisFft3dNew source, int sourceX, int sourceY, float amplitude) {

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
  public SeisFft3dNew getShot() {
    return shot;
  }

  /*
   * Returns the DistributedArray of the shot
   */
  public DistributedArray getDistributedArray() {
    return shot.getArray();
  }
}

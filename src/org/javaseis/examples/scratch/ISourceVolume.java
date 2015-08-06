package org.javaseis.examples.scratch;

import beta.javaseis.distributed.DistributedArray;

public interface ISourceVolume {

	/*
	 * Converts the physical coordinates to Array Coordinates
	 */
	public float[] covertPhysToArray(double[] sourceXYZ, ICheckGrids CheckedGrid);

	/*
	 * Returns the SeisFft3dNew Object (shot)
	 */
	public PhaseShiftFFT3D getShot();

	/*
	 * Returns the DistributedArray of the shot
	 */
	public DistributedArray getDistributedArray();

}

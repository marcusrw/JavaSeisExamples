package org.javaseis.source;

import org.javaseis.grid.ICheckedGrid;
import org.javaseis.imaging.PhaseShiftFFT3D;

import beta.javaseis.distributed.DistributedArray;

public interface ISourceVolume {

	/*
	 * Converts the physical coordinates to Array Coordinates
	 */
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
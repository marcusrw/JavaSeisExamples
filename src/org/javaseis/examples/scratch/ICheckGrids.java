package org.javaseis.examples.scratch;

import org.javaseis.grid.GridDefinition;

public interface ICheckGrids {

	/*
	 * Gets the Modified Grid.
	 */
	public GridDefinition getModifiedGrid();
	
	/*
	 * Gets the Source from a Global Position. [0,?,?,v]  
	 */
	public double[] getSourceXYZ(int[] gridPos);
	
	/*
	 * Gets the Source from a Global Position. [0,?,?,v]
	 */
	public double[] getReceiverXYZ(int[] gridPos);
	
	/*
	 * Gets the Axis Order
	 */
	public int[] getAxisOrder();
	
}


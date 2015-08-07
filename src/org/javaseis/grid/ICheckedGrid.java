package org.javaseis.grid;

import org.javaseis.grid.GridDefinition;

public interface ICheckedGrid {

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
	
	/*
	 * Get the source from the corrected grid
	 */
	public double[] getSourceXYZ();
	
}


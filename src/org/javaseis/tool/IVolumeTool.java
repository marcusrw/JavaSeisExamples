package org.javaseis.tool;

import org.javaseis.volume.ISeismicVolume;

public interface IVolumeTool {
  
	/**
	 * Performs any initialization steps that only need to be done once, such as 
	 * checking/setting the input/output grids, verifying the data exists and is
	 * in the correct form for this task, etc.
	 * 
	 * @param toolContext
	 */
	public void serialInit( ToolContext toolContext );
	
	/**
	 * Performs any initialization steps that need to be done for each volume,
	 * such as verifying the volume is not empty, broadcasting messages about what
	 * work is happening where, etc.
	 * 
	 * @param toolContext
	 */
	public void parallelInit( ToolContext toolContext );
	
	/**
	 * Perform the necessary operations on the input volume.
	 * 
	 * @param toolContext
	 * @param input
	 * @param output
	 * @return true if the process returns an output that needs to be written to disk.
	 */
	public boolean processVolume( ToolContext toolContext, ISeismicVolume input, ISeismicVolume output);
	
	//TODO what is this supposed to do?  The StandAloneVolumeTool can output data
	//even though this method just returns false.
	/**
	 * @param toolContext
	 * @param output
	 * @return
	 */
	public boolean outputVolume( ToolContext toolContext, ISeismicVolume output );
	
	/**
	 * Perform any cleanup tasks that need to be done for every volume.
	 * //TODO Such as?
	 * 
	 * @param toolContext
	 */
	public void parallelFinish( ToolContext toolContext);
	
	
	/**
	 * Perform any cleanup tasks that only need to be done once, such as closing the 
	 * input and output file streams.
	 * 
	 * @param toolContext
	 */
	public void serialFinish( ToolContext toolContext );
}

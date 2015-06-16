package org.javaseis.tool;

import org.javaseis.volume.ISeismicVolume;

public interface IVolumeTool {
	public void serialInit( ToolContext toolContext );
	public void parallelInit( ToolContext toolContext );
	public boolean processVolume( ToolContext toolContext, ISeismicVolume input, ISeismicVolume output);
	
	//TODO what is this supposed to do?  The StandAloneVolumeTool can output data
	//even though this method just returns false.
	public boolean outputVolume( ToolContext toolContext, ISeismicVolume output );
	public void parallelFinish( ToolContext toolContext);
	public void serialFinish( ToolContext toolContext );
}

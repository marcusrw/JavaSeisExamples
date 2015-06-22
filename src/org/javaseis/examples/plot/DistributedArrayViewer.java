package org.javaseis.examples.plot;

import org.javaseis.services.ParameterService;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolContext;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;

/**
 * Display each volume in the input data set in a Mosaic plot.  If
 * any of the spatial dimensions are Fourier Transformed, shift those dimensions
 * so they are centered.
 * 
 * @author wilsonmr
 *
 */
public class DistributedArrayViewer extends StandAloneVolumeTool {
  
  private DistributedArrayViewer() {}
  
  public DistributedArrayViewer(ParameterService parms) {
    exec(parms,new DistributedArrayViewer());
  }
  
  public static void main(String[] args) {
    throw new UnsupportedOperationException(
        "This method has not been implemented yet.");
  }
  
  @Override
  public void serialInit(ToolContext toolContext) {
    //Get the grid
    //Check the volume axes for Time/Space vs Frequency
    //Decide which dimensions to shift
    
    //get parallelcontext
  }
  
  @Override  //display the volume, then wait for the user to close it
  public boolean processVolume(ToolContext toolContext, ISeismicVolume input,
      ISeismicVolume output) {
    new SingleVolumeDAViewer(toolContext,input);
    return false;
  }
  
  @Override //Definitely no output, so does nothing.
  public boolean outputVolume(ToolContext toolContext, ISeismicVolume output) {
    return false;
  }
  
  @Override
  public void parallelFinish(ToolContext toolContext) {
    // TODO Auto-generated method stub
  }

  @Override
  public void serialFinish(ToolContext toolContext) {
    // TODO Auto-generated method stub
  }
  
}

package org.javaseis.examples.plot;

import org.javaseis.services.ParameterService;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolContext;
import org.javaseis.volume.ISeismicVolume;

/**
 * Display each volume in the input data set in a Mosaic plot.  If
 * any of the spatial dimensions are Fourier Transformed, shift those dimensions
 * so they are centered.
 * 
 * @author Marcus Wilson
 *
 */
public class DistributedArrayViewer extends StandAloneVolumeTool {

  public DistributedArrayViewer() {}

  //Why does my compiler say I need this?
  public DistributedArrayViewer(ParameterService parms) {
    //exec(parms,new DistributedArrayViewer());
  }

  public static void main(String[] args) {
    throw new UnsupportedOperationException(
        "This method has not been implemented yet.");
  }

  @Override
  public void serialInit(ToolContext toolContext) {
    //Does nothing
  }

  @Override
  public void parallelInit(ToolContext toolContext) {
    //Does nothing
  }

  @Override  //display the volume, then wait for the user to close it
  public boolean processVolume(ToolContext toolContext, ISeismicVolume input,
      ISeismicVolume output) {
    SingleVolumeDAViewer daView = new SingleVolumeDAViewer(input);
    daView.showAsModalDialog();
    return false;
  }

  @Override //Definitely no output, so does nothing.
  public boolean outputVolume(ToolContext toolContext, ISeismicVolume output) {
    return false;
  }

  @Override
  public void parallelFinish(ToolContext toolContext) {
    //Does nothing
  }

  @Override
  public void serialFinish(ToolContext toolContext) {
    //Does nothing
  }

}

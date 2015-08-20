package org.javaseis.examples.plot;

import org.javaseis.grid.GridDefinition;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.parallel.IParallelContext;

import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;

/**
 * Display each volume in the input data set in a Mosaic plot.  If
 * any of the spatial dimensions are Fourier Transformed, shift those dimensions
 * so they are centered.
 * 
 * @author Marcus Wilson
 *
 */
public class DistributedArrayViewer implements IVolumeTool {

  public DistributedArrayViewer() {
  }

  public static void main(String[] args) {
    throw new UnsupportedOperationException(
        "This method has not been implemented yet.");
  }

  @Override
  public void serialInit(ToolState toolContext) {
    //Does nothing
  }

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState)
      throws SeisException {
    // TODO Auto-generated method stub
    
  }

  @Override //display the volume, then wait for the user to close it
  public boolean processVolume(IParallelContext pc, ToolState toolState,
      ISeismicVolume input, ISeismicVolume output) throws SeisException {
    DistributedArray inputDA = input.getDistributedArray();
    DistributedArray outputDA = output.getDistributedArray();
    outputDA.copy(inputDA);
    
    DistributedArrayMosaicPlot.showAsModalDialog(inputDA,"INPUT");
    
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    return true;
  }

  @Override
  public boolean outputVolume(IParallelContext pc, ToolState toolState,
      ISeismicVolume output) throws SeisException {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void parallelFinish(IParallelContext pc, ToolState toolState)
      throws SeisException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void serialFinish(ToolState toolContext) {
    //Does nothing
  }
}

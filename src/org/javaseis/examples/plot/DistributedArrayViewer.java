package org.javaseis.examples.plot;

import java.io.FileNotFoundException;

import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.grid.GridDefinition;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.parallel.IParallelContext;

import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
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

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  public DistributedArrayViewer() {
  }

  public static void main(String[] args) {
    String inputFileName = "testCompare.js";
    ParameterService parms = null;
    try {
      parms = new FindTestData(inputFileName).getParameterService();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    String[] toolArray = new String[] {
        ExampleVolumeInputTool.class.getCanonicalName(),
        DistributedArrayViewer.class.getCanonicalName()
        };
    
    try {
      VolumeToolRunner.exec(parms,toolArray);
    } catch (SeisException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
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

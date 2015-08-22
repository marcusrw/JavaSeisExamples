package org.javaseis.examples.tool;

import java.util.Arrays;

import org.javaseis.grid.GridDefinition;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.parallel.IParallelContext;

/**
 * 
 * A JUnit testing tool that allows you to verify properties of a 
 * JavaSeis grid definition for a given input ISeismicVolume.
 * Simply add the properties you expect to the input ParameterService in the
 * form:
 * 
 * <Form to be determined>
 * 
 * This tool produces no output, so it should only occur at the end of a test
 * tool chain.
 *  
 * @author Marcus Wilson
 *
 */
public class GridDefinitionInpectorTool implements IVolumeTool {

  private static final long serialVersionUID = 1L;

  public GridDefinitionInpectorTool() {
    // TODO Auto-generated constructor stub
  }

  @Override
  public void serialInit(ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState)
      throws SeisException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolState,
      ISeismicVolume input, ISeismicVolume output) throws SeisException {
    // TODO Auto-generated method stub
    
    System.out.println(Arrays.toString(input.getDeltas()));
    GridDefinition inputGrid = toolState.getInputState().gridDefinition;
    System.out.println("Check input grid: ");
    System.out.println(inputGrid.toString());
    
    GridDefinition inputVolGrid = input.getGlobalGrid();
    
    GridDefinition outputGrid = toolState.getOutputState().gridDefinition;
    System.out.println("Check output grid: ");
    System.out.println(outputGrid.toString());   
    
    return false;
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
  public void serialFinish(ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub
    
  }

}

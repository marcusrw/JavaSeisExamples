package org.javaseis.grid;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.javaseis.examples.scratch.ExampleImageOutput;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.parallel.IParallelContext;

public class JTestCheckedGridNew implements IVolumeTool{

  static ParameterService parms;
  
  private static String[] listToArray(List<String> list) {
    String[] array = new String[list.size()];
    for (int k = 0 ; k < list.size() ; k++) {
      array[k] = list.get(k);
    }
    return array;
  }
  
  private static ParameterService basicParameters() {
    String inputFileName = "segshotno1.js";
    //String outputFileName = "fishfish.js";
    ParameterService parms = null;
    try {
      parms = new FindTestData(inputFileName).getParameterService();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    parms.setParameter(ToolState.TASK_COUNT, "1");
    return parms;
  }

  public static void main(String[] args) throws FileNotFoundException {
    
    ParameterService parms = basicParameters();

    List<String> toolList = new ArrayList<String>();

    toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
    toolList.add(JTestCheckedGridNew.class.getCanonicalName());

    String[] toolArray = listToArray(toolList);

    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      e.printStackTrace();
    }

  }
  
  @Override
  public void serialInit(ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolState, ISeismicVolume input, ISeismicVolume output)
      throws SeisException {
    
    new GridFromHeaders(toolState);
    
    return false;
  }

  @Override
  public boolean outputVolume(IParallelContext pc, ToolState toolState, ISeismicVolume output) throws SeisException {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void parallelFinish(IParallelContext pc, ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void serialFinish(ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub
    
  }

}

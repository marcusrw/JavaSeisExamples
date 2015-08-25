package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.examples.tool.ExampleVolumeOutputTool;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;

public class ToolChainNotes {

  public ToolChainNotes() {
    // TODO Auto-generated constructor stub
  }

  public static void main(String[] args) {

    ParameterService parms = basicParameters();

    List<String> toolList = new ArrayList<String>();

    toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
    toolList.add(DistributedArrayViewer.class.getCanonicalName());
    toolList.add(ExampleVolumeOutputTool.class.getCanonicalName());

    String[] toolArray = listToArray(toolList);

    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      e.printStackTrace();
    }
  }

  private static String[] listToArray(List<String> list) {
    String[] array = new String[list.size()];
    for (int k = 0 ; k < list.size() ; k++) {
      array[k] = list.get(k);
    }
    return array;
  }

  private static ParameterService basicParameters() {
    String inputFileName = "100a-rawsynthpwaves.js";
    String outputFileName = "fishfish.js";
    ParameterService parms = null;
    try {
      parms = new FindTestData(inputFileName,outputFileName).getParameterService();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    parms.setParameter(ToolState.TASK_COUNT, "1");
    return parms;
  }
}

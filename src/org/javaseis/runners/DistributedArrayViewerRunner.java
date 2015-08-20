package org.javaseis.runners;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.examples.scratch.VModelCheckSave;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.junit.Test;

//A wrapper script to run the example migration and save/visualize the results
public class DistributedArrayViewerRunner {

  private static final Logger LOGGER = Logger
      .getLogger(DistributedArrayViewerRunner.class.getName());

  private static ParameterService parms;
  
  private static String[] listToArray(List<String> list) {
    String[] array = new String[list.size()];
    for (int k = 0; k < list.size(); k++) {
      array[k] = list.get(k);
    }
    return array;
  }

  @Test
  public void programTerminates() throws FileNotFoundException {
    String inputFileName = "segshotno1.js";
    
    parms = new FindTestData(inputFileName).getParameterService();
    List<String> toolList = new ArrayList<String>();

    toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
    toolList.add(DistributedArrayViewer.class.getCanonicalName());

    String[] toolArray = listToArray(toolList);

    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      e.printStackTrace();
    }
  }
}

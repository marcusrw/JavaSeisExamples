package org.javaseis.examples.plot.test;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.grid.JTestCheckedGridNew;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.junit.Assert;
import org.junit.Test;

public class JTestDistributedArrayViewer {
  
  private static final Logger LOGGER =
      Logger.getLogger(JTestDistributedArrayViewer.class.getName());
  
  ParameterService parms;
  
  private static String[] listToArray(List<String> list) {
    String[] array = new String[list.size()];
    for (int k = 0; k < list.size(); k++) {
      array[k] = list.get(k);
    }
    return array;
  }
  
  private static ParameterService basicParameters(String datasetname) {
    ParameterService parms = null;
    try {
      parms = new FindTestData(datasetname).getParameterService();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    parms.setParameter(ToolState.TASK_COUNT, "1");
    return parms;
  }

  private void loadDataset(String datasetname) {
    ParameterService parms = basicParameters(datasetname);

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
  
  //@Test
  public void toolExecutes() {
    //TODO randomly generate a single random volume for this test.
    //loadDataset("100a-rawsynthpwaves.js");
    loadDataset("100a-rawsynthpwaves.js");
  }
  
  //@Test
  public void noChangesToTimeDomainData() {
    //The data that is shown in the viewer is exactly the data
    //that is input
  }
  
  //@Test
  public void complexDataDisplaysAbsValue() {
    //Given complex input, the amplitude spectra is shown
  }
  
  //@Test
  public void waveNumberCoordsAreShifted() {
    //Given input in wavenumber coordinates, those axes are shifted so that
    //zero is in the centre.
  }
}

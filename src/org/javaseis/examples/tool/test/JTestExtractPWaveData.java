package org.javaseis.examples.tool.test;

import java.io.FileNotFoundException;

import org.javaseis.examples.scratch.VModelCheckSave;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.examples.tool.ExampleVolumeOutputTool;
import org.javaseis.examples.tool.ExtractPWaveData;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

//TODO Simple tests to make sure the sub-setting method does what you expect.
public class JTestExtractPWaveData {

  private static final Logger LOGGER = 
      Logger.getLogger(JTestExtractPWaveData.class.getName());

  ParameterService parms;
  
  private static String[] listToArray(List<String> list) {
    String[] array = new String[list.size()];
    for (int k = 0; k < list.size(); k++) {
      array[k] = list.get(k);
    }
    return array;
  }

  @Test
  public void toolExecutes() {
    String inputFileName = "100-rawsyntheticdata.js";
    String outputFileName = "100a-rawsynthpwaves.js";
    
    try {
      parms = new FindTestData(inputFileName, outputFileName).getParameterService();

      List<String> toolList = new ArrayList<String>();

      toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
      toolList.add(ExtractPWaveData.class.getCanonicalName());
      toolList.add(ExampleVolumeOutputTool.class.getCanonicalName());

      String[] toolArray = listToArray(toolList);

      try {
        VolumeToolRunner.exec(parms, toolArray);
      } catch (SeisException e) {
        e.printStackTrace();
      }

    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
  }

  @Before
  public void generateTestData() {

  }

  @After
  public void removeTestDataAndOutput() {

  }

  //@Test
  public void testSingleThread() {

  }

  //@Test
  public void testDoubleThread() {

  }

  //@Test
  public void testTripleThread() {

  }

}

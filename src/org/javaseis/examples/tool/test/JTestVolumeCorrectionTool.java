package org.javaseis.examples.tool.test;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.examples.scratch.ExampleMigration;
import org.javaseis.examples.scratch.ExampleMigrationRunner;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.examples.tool.ExampleVolumeOutputTool;
import org.javaseis.examples.tool.GridDefinitionInpectorTool;
import org.javaseis.examples.tool.VolumeCorrectionTool;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import  org.junit.Before;
import  org.junit.Test;

public class JTestVolumeCorrectionTool {

  private static final Logger LOGGER = 
      Logger.getLogger(JTestVolumeCorrectionTool.class.getName());

  private ParameterService parms;
  private String[] toolArray;



  public JTestVolumeCorrectionTool() {
    // Empty
  }

  private static String[] listToArray(List<String> list) {
    String[] array = new String[list.size()];
    for (int k = 0; k < list.size(); k++) {
      array[k] = list.get(k);
    }
    return array;
  }

  @Before
  public void loadExampleDataAndToolChain() {

    locateTestDataset();
    constructToolChain();
  }

  private void constructToolChain() {
    List<String> toolList = new ArrayList<String>();

    toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
    toolList.add(VolumeCorrectionTool.class.getCanonicalName());
    toolList.add(GridDefinitionInpectorTool.class.getCanonicalName());

    toolArray = listToArray(toolList);
  }

  private void locateTestDataset() {
    String inputFileName = "segshotno1.js";   
    try {
      parms = new FindTestData(inputFileName).getParameterService();
    } catch (FileNotFoundException e) {
      LOGGER.log(Level.SEVERE,e.getMessage(),e);
      fail("Unable to find test dataset.  Add the location of your "
          + "test data folder to the 'candidates' array in the "
          + "FindTestData class if you want to run this test.");
    }
  }

  @Test
  public void toolChainExecutesSuccessfully() {
    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      LOGGER.log(Level.SEVERE,e.getMessage(),e);
      e.printStackTrace();
      fail("Tool Chain threw a SeisException during execution.");
    }
  }

  @Test
  public void updatedGridDefinitionIsCorrect() {
    assertFalse("Test is not implemented yet",true);
  }

}

package org.javaseis.runners;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.utils.Convert;
import org.javaseis.examples.scratch.ExampleMigration;
import org.javaseis.examples.scratch.VModelCheckSave;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.examples.tool.ExampleVolumeOutputTool;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.junit.Test;

//A wrapper script to run the example migration and save/visualize the results
public class SegShotNo1MigrationRunner {

  private static final Logger LOGGER = Logger
      .getLogger(SegShotNo1MigrationRunner.class.getName());

  private static ParameterService parms;
  
  @Test
  public void programTerminates() throws FileNotFoundException {
    String inputFileName = "segshotno1.js";
    String outputFileName = "segimageno1.js";
    String vModelFileName = "segsaltmodel.js";

    try {
      parms = new FindTestData(inputFileName, outputFileName)
          .getParameterService();
      basicParameters(inputFileName, outputFileName, vModelFileName);
      
      List<String> toolList = new ArrayList<String>();

      toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
      toolList.add(ExampleMigration.class.getCanonicalName());
      toolList.add(ExampleVolumeOutputTool.class.getCanonicalName());

      String[] toolArray = Convert.listToArray(toolList);

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

  private void basicParameters(String inputFileName, String outputFileName, String vModelFileName) {   
    parms.setParameter("ZMIN", "0");
    parms.setParameter("ZMAX", "4000");
    parms.setParameter("DELZ", "20");
    parms.setParameter("PADT", "20");
    parms.setParameter("PADX", "5");
    parms.setParameter("PADY", "5");
    parms.setParameter("FMAX", "6000");
    parms.setParameter("taskCount", "1");
    parms.setParameter("vModelFilePath", vModelFileName);
    parms.setParameter("outputFileMode", "create");
  }
}

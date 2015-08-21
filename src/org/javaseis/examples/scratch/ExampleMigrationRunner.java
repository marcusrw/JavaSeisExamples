package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.examples.tool.ExampleVolumeOutputTool;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.junit.Test;


//A wrapper script to run the example migration and save/visualize the results
public class ExampleMigrationRunner {

  private static final Logger LOGGER = 
      Logger.getLogger(ExampleMigrationRunner.class.getName());

  private static ParameterService parms;

  private static String[] listToArray(List<String> list) {
    String[] array = new String[list.size()];
    for (int k = 0; k < list.size(); k++) {
      array[k] = list.get(k);
    }
    return array;
  }
  
  @Test
  public void manualTest() throws FileNotFoundException {
    
    LOGGER.setLevel(Level.FINER);
    //String inputFileName = "100a-rawsynthpwaves.js";
    String inputFileName = "segshotno1.js";
    //String inputFileName = "seg45shot.js";
    String outputFileName = "test.js";
    String vModelFileName = "segsaltmodel.js";

    try {
      parms = new FindTestData(inputFileName,outputFileName).getParameterService();
      //set basic user inputs
      basicParameters(inputFileName,vModelFileName);
      List<String> toolList = new ArrayList<String>();

      toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
      toolList.add(ExampleMigration.class.getCanonicalName());
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

  private void basicParameters(String inputFileName,String vModelFileName) {
    parms.setParameter("ZMIN","0");
    parms.setParameter("ZMAX","4000");
    parms.setParameter("DELZ","20");
    parms.setParameter("PADT","20");
    parms.setParameter("PADX","5");
    parms.setParameter("PADY","5");
    parms.setParameter("FMAX","6000");
    parms.setParameter("taskCount", "1");
    parms.setParameter("vModelFilePath",vModelFileName);
    parms.setParameter("outputFileMode","create");

    if (inputFileName.equals("100a-rawsynthpwaves.js")) {
      parms.setParameter("ZMAX","2000");
      parms.setParameter("PADT","20");
      parms.setParameter("PADX","5");
      parms.setParameter("PADY","5");
      parms.setParameter("FIRSTVOLUME","FALSE");
    }
  }
}

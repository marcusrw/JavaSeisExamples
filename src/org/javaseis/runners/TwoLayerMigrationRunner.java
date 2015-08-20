package org.javaseis.runners;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.examples.scratch.ExampleMigration;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.examples.tool.ExampleVolumeOutputTool;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.junit.Test;


//A wrapper script to run the example migration and save/visualize the results
public class TwoLayerMigrationRunner {

  private static final Logger LOGGER = 
      Logger.getLogger(TwoLayerMigrationRunner.class.getName());

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
    String inputFileName = "100a-rawsynthpwaves.js";
    String outputFileName = "twolayer.js";

    try {
      parms = new FindTestData(inputFileName,outputFileName).getParameterService();
      //set basic user inputs
      List<String> toolList = new ArrayList<String>();
      
      toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
      toolList.add(ExampleMigration.class.getCanonicalName());
      toolList.add(ExampleVolumeOutputTool.class.getCanonicalName());
      
      basicParameters(inputFileName);

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

  private void basicParameters(String inputFileName) {
    parms.setParameter("ZMIN","0");
    parms.setParameter("ZMAX","2000");
    parms.setParameter("DELZ","20");
    parms.setParameter("PADT","20");
    parms.setParameter("PADX","5");
    parms.setParameter("PADY","5");
    parms.setParameter("FMAX","6000");
    parms.setParameter("taskCount", "1");
    parms.setParameter("FIRSTVOLUME","FALSE");
    parms.setParameter("outputFileMode","create");
  }
}

package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.junit.Test;


//A wrapper script to run the example migration and save/visualize the results
public class ExampleMigrationRunner {

  private static final Logger LOGGER = 
      Logger.getLogger(ExampleMigrationRunner.class.getName());

  private static ParameterService parms;

  @Test
  public void test() throws FileNotFoundException {
    //String inputFileName = "100a-rawsynthpwaves.js";
    String inputFileName = "segshotno1.js";
    String outputFileName = "test10m.js";

    parms = new FindTestData(inputFileName,outputFileName).getParameterService();

    //set basic user inputs
    parms.setParameter("ZMIN","0");
    parms.setParameter("ZMAX","2000");
    parms.setParameter("DELZ","2000");
    parms.setParameter("PADT","10");
    parms.setParameter("PADX","10");
    parms.setParameter("PADY","10");
    parms.setParameter("DEBUG","FALSE");    

    //parms.setParameter("threadCount", "1");
    ExampleMigration.exec(parms,new ExampleMigration());
    boolean debugIsOn = Boolean.parseBoolean(parms.getParameter("DEBUG"));
    if (debugIsOn) {
      setOutputAsInput(parms);
      DistributedArrayViewer.exec(parms,new DistributedArrayViewer());
    }
  }

  private static void setOutputAsInput(ParameterService parms) {
    String outputFilePath = parms.getParameter("outputFilePath","null");
    String outputFileSystem = parms.getParameter("outputFileSystem","null");
    parms.setParameter("inputFilePath",outputFilePath);
    parms.setParameter("inputFileSystem",outputFileSystem);
  }
}

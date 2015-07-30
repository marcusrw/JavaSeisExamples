package org.javaseis.examples.scratch;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.logging.Logger;

import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.util.SeisException;
import org.junit.Test;


//A wrapper script to run the example migration and save/visualize the results
public class ExampleMigrationRunner {

  private static final Logger LOGGER = 
      Logger.getLogger(ExampleMigrationRunner.class.getName());

  private static ParameterService parms;

  @Test
  public void test() throws FileNotFoundException {
    String inputFileName = "100a-rawsynthpwaves.js";
    //String inputFileName = "segshotno1.js";
    //String inputFileName = "seg45shot.js";
    String outputFileName = "test.js";
    String vModelFileName = "segsaltmodel.js";

    parms = new FindTestData(inputFileName,outputFileName).getParameterService();
    //set basic user inputs
    parms.setParameter("ZMIN","0");
    parms.setParameter("ZMAX","2000");
    parms.setParameter("DELZ","40");
    parms.setParameter("PADT","20");
    parms.setParameter("PADX","5");
    parms.setParameter("PADY","5");
    parms.setParameter("FMAX","6000");
    parms.setParameter("taskCount", "1");
    parms.setParameter("vModelFilePath",vModelFileName);
    parms.setParameter("DEBUG","TRUE");
    parms.setParameter("outputFileMode","create");

    try {
      ExampleMigration.exec(parms,new ExampleMigration());
    } catch (SeisException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    boolean debugIsOn = Boolean.parseBoolean(parms.getParameter("DEBUG"));
    if (debugIsOn) {
      setOutputAsInput(parms);
      parms.setParameter("outputFilePath",outputFileName);
      try {
        System.out.println("Displaying data in "
            + parms.getParameter("inputFileSystem")
            + File.separator + parms.getParameter("inputFilePath"));
        DistributedArrayViewer.exec(parms,new DistributedArrayViewer());
      } catch (SeisException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  private static void setOutputAsInput(ParameterService parms) {
    String outputFilePath = parms.getParameter("outputFilePath","null");
    String outputFileSystem = parms.getParameter("outputFileSystem","null");
    parms.setParameter("inputFilePath",outputFilePath);
    parms.setParameter("inputFileSystem",outputFileSystem);
  }
}

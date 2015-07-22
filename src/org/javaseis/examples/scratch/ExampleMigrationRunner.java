package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.junit.Assert;
import org.junit.Test;


//A wrapper script to run the example migration and save/visualize the results
public class ExampleMigrationRunner {

  private static final Logger LOGGER = 
      Logger.getLogger(ExampleMigrationRunner.class.getName());

  private static ParameterService parms;

  public static void main(String[] args) throws FileNotFoundException {
    String inputFileName = "100a-rawsynthpwaves.js";
    String outputFileName = "test10m.js";

    parms = new FindTestData(inputFileName,outputFileName).getParameterService();

    //set basic user inputs
    parms.setParameter("ZMIN","0");
    parms.setParameter("ZMAX","2000");
    parms.setParameter("DELZ","10");
    parms.setParameter("PADT","0");
    parms.setParameter("PADX","0");
    parms.setParameter("PADY","0");
    parms.setParameter("DEBUG","TRUE");    

    //parms.setParameter("threadCount", "1");
    ExampleMigration.exec(parms,new ExampleMigration());
    if (Boolean.parseBoolean(parms.getParameter("DEBUG"))) {
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

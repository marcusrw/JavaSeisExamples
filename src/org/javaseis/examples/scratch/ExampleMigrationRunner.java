package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.junit.Assert;
import org.junit.Test;

public class ExampleMigrationRunner {
  
  private static final Logger LOGGER = 
      Logger.getLogger(ExampleMigrationRunner.class.getName());

  private static ParameterService parms;

  //test harness to see if the process runs
  public static void main(String[] args) throws FileNotFoundException {
    String inputFileName = "100a-rawsynthpwaves.js";
    //String inputFileName = "segshotno1.js";
    String outputFileName = "test100m.js";

    parms = new FindTestData(inputFileName,outputFileName).getParameterService();
    
    //set basic user inputs
    parms.setParameter("ZMIN","0");
    parms.setParameter("ZMAX","2000");
    parms.setParameter("DELZ","1000");
    parms.setParameter("PADT","50");
    parms.setParameter("PADX","50");
    parms.setParameter("PADY","50");
    parms.setParameter("DEBUG","TRUE");    

    //parms.setParameter("threadCount", "1");
    ExampleMigration.exec(parms,new ExampleMigration());
    setOutputAsInput(parms);
    DistributedArrayViewer.exec(parms,new DistributedArrayViewer());
    
    
  }
  
  private static void setOutputAsInput(ParameterService parms) {
    String outputFilePath = parms.getParameter("outputFilePath","null");
    String outputFileSystem = parms.getParameter("outputFileSystem","null");
    parms.setParameter("inputFilePath",outputFilePath);
    parms.setParameter("inputFileSystem",outputFileSystem);
  }
  
  private void loadDataset(String datasetname) {
    try {
      parms = new FindTestData(datasetname).getParameterService();
      DistributedArrayViewer.exec(parms,new DistributedArrayViewer());
    } catch (FileNotFoundException e) {
      LOGGER.log(Level.INFO,"Unable to open test dataset",e);
      Assert.fail(e.getMessage());
    }
  }
  
  @Test
  public void toolExecutes() {
    //TODO randomly generate a single random volume for this test.
    //loadDataset("100a-rawsynthpwaves.js");
    loadDataset("test100m.js");
  }

}

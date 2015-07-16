package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;

import org.javaseis.examples.scratch.ExampleMigration;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.ExampleRandomDataset;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.util.SeisException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

public class JTestExampleMigration {

  private static final Logger LOGGER = 
      Logger.getLogger(JTestExampleMigration.class.getName());

  public JTestExampleMigration() {};

  //test harness to see if the process runs
  public static void main(String[] args) throws FileNotFoundException {
    String inputFileName = "100a-rawsynthpwaves.js";
    //String inputFileName = "segshotno1.js";
    String outputFileName = "benchmark500m.js";
    
    ParameterService parms =
        new FindTestData(inputFileName,outputFileName).getParameterService();
    parms.setParameter("ZMIN","0");
    parms.setParameter("ZMAX","2000");
    parms.setParameter("DELZ","500");
    parms.setParameter("PADT","50");
    parms.setParameter("PADX","50");
    parms.setParameter("PADY","50");
    
    
    //parms.setParameter("threadCount", "1");
    ExampleMigration.exec(parms,new ExampleMigration());
  }

  //@Test
  //TODO this test is not finished yet.
  public void generateTestData() {
    String path1 = "/tmp/tempin.js";
    String path2 = "/tmp/tempout.js";
    ExampleRandomDataset testdata1 = new ExampleRandomDataset(path1);
    ExampleRandomDataset testdata2 = new ExampleRandomDataset(path2);

    ParameterService parms = null;
    try {
      parms = new FindTestData(testdata1.dataFullPath,
          testdata2.dataFullPath).getParameterService();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      LOGGER.log(Level.INFO,e.getMessage(),e);
      Assert.fail();
    }    

    ExampleMigration.exec(parms,new ExampleMigration());

    testdata1.frameIterator();

    try {
      testdata1.deleteJavaSeisData();
      testdata2.deleteJavaSeisData();
    } catch (SeisException e) {
      LOGGER.log(Level.INFO,e.getMessage(),e);
    }
  }
}

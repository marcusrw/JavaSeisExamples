package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;

import org.javaseis.examples.plot.JavaSeisMovieRunner;
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
    //String inputFileName = "100a-rawsynthpwaves.js";
    String inputFileName = "segshotno1.js";
    String outputFileName = "testImage.js";
    ParameterService parms =
        new FindTestData(inputFileName,outputFileName).getParameterService();
    
    parms.setParameter("threadCount", "1");
    ExampleMigration.exec(parms,new ExampleMigration());
    LOGGER.fine("Displaying input file: " + inputFileName);
    JavaSeisMovieRunner.showMovie(inputFileName);
    LOGGER.fine("Displaying output file: " + outputFileName);
    JavaSeisMovieRunner.showMovie(outputFileName);
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

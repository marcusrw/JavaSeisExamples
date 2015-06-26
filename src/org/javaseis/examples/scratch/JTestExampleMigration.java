package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;




import org.javaseis.examples.plot.JavaSeisMovieRunner;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;

import java.util.logging.Logger;

public class JTestExampleMigration {

  private static final Logger LOGGER = 
      Logger.getLogger(JTestExampleMigration.class.getName());

  private JTestExampleMigration() {};

  //test harness to see if the process runs
  public static void main(String[] args) throws FileNotFoundException {
    String inputFileName = "seg45shot.js";
    String outputFileName = "testFFT.js";
    ParameterService parms =
        new FindTestData(inputFileName,outputFileName).getParameterService();
    ExampleMigration.exec(parms,new ExampleMigration());
    LOGGER.info("Displaying input file: " + inputFileName);
    JavaSeisMovieRunner.showMovie(inputFileName);
    LOGGER.info("Displaying output file: " + outputFileName);
    JavaSeisMovieRunner.showMovie(outputFileName);
  }
}

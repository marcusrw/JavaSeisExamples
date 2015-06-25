package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;

import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;

public class JTestExampleMigration {

  private JTestExampleMigration() {};

  //test harness to see if the process runs
  public static void main(String[] args) throws FileNotFoundException {
    String inputFileName = "100a-rawsynthpwaves.js";
    String outputFileName = "testFFT.js";
    ParameterService parms =
        new FindTestData(inputFileName,outputFileName).getParameterService();
    ExampleMigration.exec(parms,new ExampleMigration());
  }
}

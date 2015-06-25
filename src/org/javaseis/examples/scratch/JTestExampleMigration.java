package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.logging.Logger;

import org.javaseis.test.testdata.FindTestData;

public class JTestExampleMigration {

  private JTestExampleMigration() {};

  //test harness to see if the process runs
  public static void main(String[] args) throws FileNotFoundException {
    String inputFileName = "100a-rawsynthpwaves.js";
    String outputFileName = "testFFT.js";
      FindTestData ftd =
          new FindTestData(inputFileName,outputFileName);
      ExampleMigration exmig =
          new ExampleMigration(ftd.getParameterService());
  }
}

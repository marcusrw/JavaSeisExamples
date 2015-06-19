package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;

import org.javaseis.test.testdata.FindTestData;

public class JTestExampleMigration {

  public static void main(String[] args) {
    String inputFileName = "100a-rawsynthpwaves.js";
    String outputFileName = "testFFT.js";
    try {
      FindTestData ftd = new FindTestData(inputFileName,outputFileName);
      new ExampleMigration(ftd.getParameterService());
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}

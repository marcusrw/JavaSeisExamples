package org.javaseis.volume.test;

import org.javaseis.grid.GridDefinition;
import org.javaseis.parallel.IParallelContext;
import org.javaseis.parallel.UniprocessorContext;
import org.javaseis.test.testdata.ExampleRandomDataset;
import org.javaseis.util.SeisException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class JTestSeismicVolume {

  ExampleRandomDataset exampleInput;
  ExampleRandomDataset exampleOutput;
  IParallelContext pc;

  @Before
  //Create input and output datasets that have different gridDefinitions
  public void setupTestDataset() {
    pc = new UniprocessorContext();
    exampleInput = new ExampleRandomDataset();
  }

  @After
  public void deleteTestDataset() {
    try {
      exampleInput.deleteJavaSeisData();
    } catch (SeisException e) {
      System.out.println("Unable to delete temporary data at " + exampleInput.dataFullPath);
      e.printStackTrace();
    }
  }

  //TODO design some tests for this guy.
  @Test
  public void placeHolder() {
    Assert.assertTrue(true);
  }


}

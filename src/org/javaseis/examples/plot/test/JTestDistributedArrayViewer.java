package org.javaseis.examples.plot.test;

import java.io.FileNotFoundException;

import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.junit.Assert;
import org.junit.Test;



public class JTestDistributedArrayViewer {
  
  ParameterService parms;

  private void loadDataset(String datasetname) {
    try {
      parms = new FindTestData(datasetname).getParameterService();
      new DistributedArrayViewer(parms);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }
  
  @Test
  public void toolExecutes() {
    loadDataset("testFFT.js");
  }
}

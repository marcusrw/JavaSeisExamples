package org.javaseis.examples.plot.test;

import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.junit.Assert;
import org.junit.Test;




public class JTestDistributedArrayViewer {
  
  ParameterService parms;
  
  private static final Logger LOGGER =
      Logger.getLogger(JTestDistributedArrayViewer.class.getName());

  private void loadDataset(String datasetname) {
    try {
      parms = new FindTestData(datasetname).getParameterService();
      new DistributedArrayViewer(parms);
    } catch (FileNotFoundException e) {
      LOGGER.log(Level.INFO,"Unable to open test dataset",e);
      Assert.fail(e.getMessage());
    }
  }
  
  @Test
  public void toolExecutes() {
    loadDataset("testFFT.js");
  }
}

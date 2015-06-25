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
  
  private static final Logger LOGGER =
      Logger.getLogger(JTestDistributedArrayViewer.class.getName());
  
  ParameterService parms;

  //Try to find a .js folder with the given name in a few folders.
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
    loadDataset("100-rawsyntheticdata.js");
  }
}

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
    //TODO randomly generate a single random volume for this test.
    //loadDataset("100a-rawsynthpwaves.js");
    loadDataset("benchmark500m.js");
  }
  
  @Test
  public void noChangesToTimeDomainData() {
    //The data that is shown in the viewer is exactly the data
    //that is input
  }
  
  @Test
  public void complexDataDisplaysAbsValue() {
    //Given complex input, the amplitude spectra is shown
  }
  
  @Test
  public void waveNumberCoordsAreShifted() {
    //Given input in wavenumber coordinates, those axes are shifted so that
    //zero is in the centre.
  }
}

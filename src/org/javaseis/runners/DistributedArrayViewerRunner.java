package org.javaseis.runners;

import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.util.SeisException;
import org.junit.Test;

//A wrapper script to run the example migration and save/visualize the results
public class DistributedArrayViewerRunner {

  private static final Logger LOGGER = Logger
      .getLogger(DistributedArrayViewerRunner.class.getName());

  private static ParameterService parms;

  @Test
  public void programTerminates() throws FileNotFoundException {
    String inputFileName = "seg45stack.js";

    parms = new FindTestData(inputFileName).getParameterService();

    try {
      DistributedArrayViewer.exec(parms, new DistributedArrayViewer());
    } catch (SeisException e) {
      LOGGER.log(Level.SEVERE, e.getMessage(), e);
    }
  }
}

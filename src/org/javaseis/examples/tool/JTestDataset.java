package org.javaseis.examples.tool;

import static org.junit.Assert.assertFalse;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.grid.GridDefinition;
import org.javaseis.io.Seisio;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.SynthDataset4D;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeInputTool;
import org.javaseis.tool.VolumeOutputTool;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JTestDataset {

  private static final Logger LOGGER = Logger.getLogger(JTestDataset.class.getName());

  private String dataLocation = "//tmp//";
  private String inputFileName = "tempIn.js";
  private String outputFileName = "tempOut.js";
  private SynthDataset4D testData;

  private GridDefinition inputGrid;
  private GridDefinition outputGrid;

  public JTestDataset() {
  }

  @Before
  public void makeTemporarySyntheticDataset() {
    try {
      SynthDataset4D testData = new SynthDataset4D(dataLocation + inputFileName);
      testData.create();
      testData.writeAllData();
      // testData.writeAlernatingData(0);
    } catch (SeisException e) {
      // e.printStackTrace();
      LOGGER.log(Level.SEVERE, e.getMessage(), e);
    }
  }

  @Test
  public void testNotes() {

    ParameterService parms = new ParameterService(new String[0]);
    parms.setParameter(ToolState.INPUT_FILE_SYSTEM, dataLocation);
    parms.setParameter(ToolState.INPUT_FILE_NAME, inputFileName);
    parms.setParameter(ToolState.OUTPUT_FILE_SYSTEM, dataLocation);
    parms.setParameter(ToolState.OUTPUT_FILE_NAME, outputFileName);
    parms.setParameter(VolumeDifferencingTool.COMPARE_FILE_NAME, inputFileName);

    Seisio sio;
    try {
      sio = new Seisio(dataLocation + inputFileName);
      sio.open("r");

      inputGrid = sio.getGridDefinition();
      // System.out.println(inputGrid.toString());
      LOGGER.fine(inputGrid.toString());

      String[] toolArray = new String[3];
      toolArray[0] = ExampleVolumeInputTool.class.getCanonicalName();
      toolArray[1] = VolumeDifferencingTool.class.getCanonicalName();
      toolArray[2] = TestDATool.class.getCanonicalName();

      VolumeToolRunner.exec(parms, toolArray);

    } catch (SeisException e) {
      // e.printStackTrace();
      LOGGER.log(Level.SEVERE, e.getMessage(), e);
    }

    /*
     * try { sio = new Seisio(dataLocation + outputFileName); sio.open("r");
     * outputGrid = sio.getGridDefinition();
     * System.out.println(outputGrid.toString()); } catch (SeisException e) { //
     * TODO Auto-generated catch block e.printStackTrace(); }
     */

  }

  @After
  public void deleteTemporarySyntheticDataset() {
    Seisio.delete(dataLocation + inputFileName);
    Seisio.delete(dataLocation + outputFileName);
  }
}

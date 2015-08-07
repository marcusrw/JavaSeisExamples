package org.javaseis.test.testdata;

import java.io.FileNotFoundException;

import org.javaseis.grid.GridFromHeaders;
import org.javaseis.grid.GridDefinition;
import org.javaseis.grid.ICheckedGrid;
import org.javaseis.services.ParameterService;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.javaseis.volume.SeismicVolume;

import beta.javaseis.distributed.FileSystemIOService;
import beta.javaseis.distributed.IDistributedIOService;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;

public class JTestSampleInputCreator {

  private IParallelContext pc;
  private ParameterService parms;
  private ToolContext toolContext;
  private ISeismicVolume seismicInput;
  private IDistributedIOService ipio = null;
  private GridDefinition globalGrid;
  private ICheckedGrid checkGrid;

  public JTestSampleInputCreator(boolean loop) {
    pc = new UniprocessorContext();

    // Specify which data to load
    String inputFileName = "segshotno1.js";
    try {
      // Use the FindTestData to populate your ParameterService with
      // IO info
      parms = new FindTestData(inputFileName).getParameterService();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

    // Push that ParameterService into a toolContext and add the
    // ParallelContext
    toolContext = new ToolContext(parms);
    toolContext.setParallelContext(pc);

    // start the file system IO service on the folder that FindTestData
    // found
    try {
      ipio = new FileSystemIOService(pc, toolContext.getParameter(ToolContext.INPUT_FILE_SYSTEM));
    } catch (SeisException e) {
      e.printStackTrace();
    }

    // Open your chosen file for reading
    try {
      ipio.open(toolContext.getParameter(ToolContext.INPUT_FILE_PATH));
    } catch (SeisException e) {
      e.printStackTrace();
    }

    // Get the global grid definition
    globalGrid = ipio.getGridDefinition();

    // Now you have enough to make a SeismicVolume
    ISeismicVolume inputVolume = new SeismicVolume(pc, globalGrid);
    seismicInput = inputVolume;

    // match the IO's DA with the Volume's DA
    ipio.setDistributedArray(inputVolume.getDistributedArray());

    if (loop) {
      while (ipio.hasNext()) {
        ipio.next();
        inputVolume.setVolumePosition(ipio.getFilePosition());
        try {
          ipio.read();
        } catch (SeisException e) {
          e.printStackTrace();
        }

        // and do something to it
        // ie, view it
        // DistributedArray da = inputVolume.getDistributedArray();
        // DistributedArrayMosaicPlot.showAsModalDialog(da, "Is it
        // loading?");

        // or call CheckGrids on it
        // this method needs a velocity model parameter
        String vModelFileName = "segsaltmodel.js";
        parms.setParameter("vModelFilePath", vModelFileName);
        try {
          checkGrid = new GridFromHeaders(inputVolume, toolContext);
        } catch (NullPointerException e) {
          System.out.println("It's possible that the current input has\n"
              + "no associated trace header file.");
          e.printStackTrace();
        }
        // System.out.println(Arrays.toString(checkGrid.getSourceXYZ()));

      }
    }
  }

  public IParallelContext getPc() {
    return pc;
  }

  public ParameterService getParms() {
    return parms;
  }

  public ToolContext getToolContext() {
    return toolContext;
  }

  public ISeismicVolume getSeismicInput() {
    return seismicInput;
  }

  public IDistributedIOService getIpio() {
    return ipio;
  }

  public GridDefinition getGlobalGrid() {
    return globalGrid;
  }

  public ICheckedGrid getCheckGrid() {
    return checkGrid;
  }

}

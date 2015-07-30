package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.Arrays;

import org.javaseis.grid.GridDefinition;

import beta.javaseis.distributed.DistributedArrayMosaicPlot;

import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.javaseis.volume.SeismicVolume;
import org.junit.Test;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.FileSystemIOService;
import beta.javaseis.distributed.IDistributedIOService;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;

public class JTestCheckGrids {

  IParallelContext pc;
  ParameterService parms;
  ToolContext toolContext;
  ISeismicVolume seismicInput;
  IDistributedIOService ipio = null;
  GridDefinition globalGrid;

  @Test
  public void loadDataIntoVolume() {
    pc = new UniprocessorContext();

    //Specify which data to load
    String inputFileName = "segshotno1.js";
    try {
      //Use the find test data to populate your ParameterService with
      //IO info
      parms = new FindTestData(inputFileName).getParameterService();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    //Push that parameterservice into a toolContext and add the parallelcontext
    toolContext = new ToolContext(parms);
    toolContext.setParallelContext(pc);

    //start the file system IO service on the folder that FindTestData found
    try {
      ipio = new FileSystemIOService(pc,
          toolContext.getParameter(ToolContext.INPUT_FILE_SYSTEM));
    } catch (SeisException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    //Open your chosen file for reading
    try {
      ipio.open(toolContext.getParameter(ToolContext.INPUT_FILE_PATH));
    } catch (SeisException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    //Get the global grid definition
    globalGrid = ipio.getGridDefinition();

    //Now you have enough to make a SeismicVolume
    ISeismicVolume inputVolume = new SeismicVolume(pc,globalGrid);

    //match the IO's DA with the Volume's DA
    ipio.setDistributedArray(inputVolume.getDistributedArray());

    while (ipio.hasNext()) {
      ipio.next();
      inputVolume.setVolumePosition(ipio.getFilePosition());
      try {
        ipio.read();
      } catch (SeisException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

      //and do something to it
      //ie, view it
      DistributedArray da = inputVolume.getDistributedArray();
      DistributedArrayMosaicPlot.showAsModalDialog(da, "Is it loading?");

      //or call CheckGrids on it
      //this guy needs a velocity model
      String vModelFileName = "segsaltmodel.js";
      parms.setParameter("vModelFilePath", vModelFileName);
      ICheckGrids checkGrid = new CheckGrids(inputVolume, toolContext);
      System.out.println(Arrays.toString(checkGrid.getSourceXYZ()));
    }
  }

}

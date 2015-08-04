package org.javaseis.examples.scratch;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import org.javaseis.grid.GridDefinition;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.javaseis.volume.SeismicVolume;
import org.junit.Before;
import org.junit.Test;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.distributed.FileSystemIOService;
import beta.javaseis.distributed.IDistributedIOService;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;

public class JTestVolumeEdgeVirtualization {

  ParameterService parms;
  ToolContext toolContext;
  ISeismicVolume seismicInput;
  IDistributedIOService ipio = null;
  ICheckGrids checkGrid;

  @Before
  public void loadDataIntoVolume() {
    JTestSampleInputCreator test = new JTestSampleInputCreator(false);
    this.toolContext = test.getToolContext();
    this.seismicInput = test.getSeismicInput();
    this.checkGrid = test.getCheckGrid();
    this.parms = test.getParms();
    this.ipio = test.getIpio();
  }

  @Test
  public void testVolumeEdges() {
    while (ipio.hasNext()) {
      ipio.next();
      seismicInput.setVolumePosition(ipio.getFilePosition());
      try {
        ipio.read();
      } catch (SeisException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

      // or call CheckGrids on it
      String vModelFileName = "segsaltmodel.js";
      parms.setParameter("vModelFilePath", vModelFileName);
      checkGrid = new CheckGrids(seismicInput, toolContext);
      // System.out.println(Arrays.toString(checkGrid.getSourceXYZ()));

      printVolumeEdges(checkGrid, seismicInput);

    }

  }

  public void printVolumeEdges(ICheckGrids CheckedGrid, ISeismicVolume input) {
    String path = "//tmp//vEdge.txt";
    // check if file exists

    File f = new File(path);
    if (f.exists() && !f.isDirectory()) {
      f.delete();
    }

    // File Writer
    PrintWriter out = null;
    try {
      FileWriter fWrtr = new FileWriter(path);
      out = new PrintWriter(fWrtr);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    // out is our file output stream

    // Iterate through the DistArray get the volumes
    DistributedArray curArray = input.getDistributedArray();

    // get the start of the current volume
    int[] globalPosIndex = input.getVolumePosition();
    int[] volumePosIndex = new int[3];

    // Iterate over the Volume - Scope = 3
    DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(curArray, volumePosIndex,
        DistributedArrayPositionIterator.FORWARD, 3);

    // source and receiver arrays
    double[] rXYZ = new double[3];
    double[] sXYZ = new double[3];

    out.println("[getVolumeEdges]: Start Source From Grid: " + Arrays.toString(CheckedGrid.getSourceXYZ()));

    // System.out.println("[getVolumeEdges]: " +
    // Arrays.toString(globalPosIndex));

    while (itrInputArr.hasNext()) {
      globalPosIndex[0] = 0;
      volumePosIndex = itrInputArr.next();
      for (int k = 1; k < 3; k++) {
        globalPosIndex[k] = volumePosIndex[k];
      }

      // Get the rec & source at [0,0,0,v]

      // Get the Receiver XYZ
      // jscs.getReceiverXYZ(globalPosIndex, rXYZ);

      rXYZ = CheckedGrid.getReceiverXYZ(globalPosIndex);

      out.println("[getVolumeEdges]: Position: " + Arrays.toString(globalPosIndex) + " ReceiverXYZ: "
          + Arrays.toString(rXYZ));

      // Get the Source XYZ
      // jscs.getSourceXYZ(globalPosIndex, sXYZ);

      // System.out.println("[getVolumeEdges]: " +
      // Arrays.toString(globalPosIndex));

      sXYZ = CheckedGrid.getSourceXYZ(globalPosIndex);

      // No way to determine this using the jscs
      /*
       * if (sXYZ == null){ out.println(
       * "[getVolumeEdges]: Assumed Position: " +
       * Arrays.toString(globalPosIndex) + " SourceXYZ: " +
       * Arrays.toString(rXYZ)); sXYZ = CheckedGrid.getSourceXYZ(); }
       */

      out.println("[getVolumeEdges]: Position: " + Arrays.toString(globalPosIndex) + " SourceXYZ: "
          + Arrays.toString(sXYZ));

      // Get the receiver & source at [0, axislength - 1, axislength - 1,
      // v]
      // last trace = the length of the XYaxis - 1
      long X = input.getGlobalGrid().getAxisLength(CheckedGrid.getAxisOrder()[0]) - 1;
      long Y = input.getGlobalGrid().getAxisLength(CheckedGrid.getAxisOrder()[1]) - 1;

      globalPosIndex[1] = (int) X;
      globalPosIndex[2] = (int) Y;

      // rXYZ now hold receiver at pos [0, axislength - 1, axislength - 1,
      // v]
      rXYZ = CheckedGrid.getReceiverXYZ(globalPosIndex);
      out.println("[getVolumeEdges]: Position: " + Arrays.toString(globalPosIndex) + " ReceiverXYZ: "
          + Arrays.toString(rXYZ));

      sXYZ = CheckedGrid.getSourceXYZ(globalPosIndex);
      out.println("[getVolumeEdges]: Position: " + Arrays.toString(globalPosIndex) + " SourceXYZ: "
          + Arrays.toString(sXYZ));
    }

    if (out != null) {
      out.close();
    }

  }

}

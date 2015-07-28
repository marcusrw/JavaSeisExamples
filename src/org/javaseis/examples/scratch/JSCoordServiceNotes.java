package org.javaseis.examples.scratch;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.grid.BinGrid;
import org.javaseis.grid.GridDefinition;
import org.javaseis.io.Seisio;
import org.javaseis.properties.PropertyDescription;
import org.javaseis.properties.TraceProperties;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.junit.Assert;
import org.junit.Test;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.services.CoordinateType;
import beta.javaseis.services.JSCoordinateService;

public class JSCoordServiceNotes {

  @Test
  public void test() {
    String testfile;
    //testfile = "/home/wilsonmr/javaseis/segsaltmodel.js";
    testfile = "/home/wilsonmr/javaseis/segshotno1.js";
    //testfile = "/home/wilsonmr/javaseis/100a-rawsynthpwaves.js";
    Seisio sio;
    JSCoordinateService jscs;
    try {
      sio = new Seisio(testfile);
      sio.open("r");
      sio.usesProperties(true);
      TraceProperties tp = sio.getTraceProperties();
      System.out.println(tp.getNumProperties());
      PropertyDescription[] tpd = tp.getTraceProperties();
      for (int k = 0 ; k < tp.getNumProperties() ; k++) {
        System.out.println(tpd[k].getLabel()
            + ": "
            + tpd[k].getDescription());
      }
      Assert.assertNotEquals("This file contains no properties",
          tp.getNumProperties(),0);
      GridDefinition grid = sio.getGridDefinition();
      Assert.assertNotNull(grid);
      System.out.println(grid.toString());
      int xdim = 2;  //3rd array index
      int ydim = 1;  //2nd array index
      BinGrid bingrid = new BinGrid(grid,xdim,ydim);
      Assert.assertNotNull(bingrid);     
      //TODO  I'm not sure which is x and which is y here.

      String[] coordprops = new String[]
          {"SOU_XD","SOU_YD","SOU_ELEV","REC_XD","REC_YD","REC_ELEV"};
      //The JSCS source/javadoc should explain that ORDER MATTERS HERE.
      jscs = new JSCoordinateService(sio,bingrid,
          CoordinateType.SHOTRCVR,coordprops);
      Assert.assertEquals(grid,jscs.getDataGrid());

      int[] pos = new int[] {0,0,0,0};
      printSrcRcvrXYZ(jscs, pos); 

      pos = new int[] {1,0,0,0};
      printSrcRcvrXYZ(jscs, pos);

      pos = new int[] {0,1,0,0};
      printSrcRcvrXYZ(jscs, pos);

      pos = new int[] {0,0,1,0};
      printSrcRcvrXYZ(jscs, pos);




    } catch (SeisException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }   
  }

  private void getVolumeEdges(JSCoordinateService jscs, ISeismicVolume input, int [] AXIS_ORDER){
    String path = "//tmp//vEdge.txt";
    //check if file exists
    File f = new File(path);
    if(f.exists() && !f.isDirectory()) {
      f.delete();
    }

    //File Writer
    PrintWriter out = null;
    try {
      FileWriter fWrtr = new FileWriter(path);
      out = new PrintWriter(fWrtr);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    //out is our file output stream

    //Iterate through the DistArray get the volumes
    DistributedArray curArray = input.getDistributedArray();

    //get the start of the current volume
    int[] globalPosIndex = input.getVolumePosition();
    int[] volumePosIndex = new int[3];

    //Iterate over the Volume - Scope = 3
    DistributedArrayPositionIterator itrInputArr = 
        new DistributedArrayPositionIterator(curArray, volumePosIndex, 
            DistributedArrayPositionIterator.FORWARD, 3);

    //source and receiver arrays
    double [] rXYZ = new double[3];
    double [] sXYZ = new double[3];

    while (itrInputArr.hasNext()) {
      globalPosIndex[0] = 0;
      volumePosIndex = itrInputArr.next();
      for (int k = 1 ; k < 3 ; k++) {
        globalPosIndex[k] = volumePosIndex[k];
      }

      //Get the rec & source at [0,0,0,v] 

      //Get the Receiver XYZ
      jscs.getReceiverXYZ(globalPosIndex, rXYZ);

      out.println("[getVolumeEdges]: Position: " + Arrays.toString(globalPosIndex)
          + " ReceiverXYZ: " + Arrays.toString(rXYZ));

      //Get the Source XYZ
      jscs.getSourceXYZ(globalPosIndex, sXYZ);
      out.println("[getVolumeEdges]: Position: " + Arrays.toString(globalPosIndex)
          + " SourceXYZ: " + Arrays.toString(rXYZ));

      //Get the receiver & source at [0, axislength - 1, axislength - 1, v]
      //last trace = the length of the XYaxis - 1
      long X = input.getGlobalGrid().getAxisLength(AXIS_ORDER[0]) - 1;
      long Y = input.getGlobalGrid().getAxisLength(AXIS_ORDER[1]) - 1;

      globalPosIndex[1] = (int) X;
      globalPosIndex[2] = (int) Y;

      //rXYZ now hold receiver at pos [0, axislength - 1, axislength - 1, v]
      jscs.getReceiverXYZ(globalPosIndex, rXYZ);
      out.println("[getVolumeEdges]: Position: " + Arrays.toString(globalPosIndex)
          + " ReceiverXYZ: " + Arrays.toString(rXYZ));
    }

    if (out != null){
      out.close();
    }

  }

  private void printSrcRcvrXYZ(JSCoordinateService jscs, int[] pos) {
    double[] srxyz = new double[6];
    jscs.getSrcRcvrXYZ(pos, srxyz);
    System.out.println("SRXYZ for position "
        + Arrays.toString(pos) + ": "
        + Arrays.toString(srxyz));
  }
}

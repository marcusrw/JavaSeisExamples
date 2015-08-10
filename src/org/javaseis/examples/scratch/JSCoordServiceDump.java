package org.javaseis.examples.scratch;

import java.util.Arrays;

import org.javaseis.grid.BinGrid;
import org.javaseis.grid.GridDefinition;
import org.javaseis.io.Seisio;
import org.javaseis.properties.TraceProperties;
import org.javaseis.util.SeisException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import beta.javaseis.services.CoordinateType;
import beta.javaseis.services.JSCoordinateService;

public class JSCoordServiceDump {

  Seisio sio;
  JSCoordinateService jscs;
  String testfile;
  GridDefinition rawGrid;

  public JSCoordServiceDump() {}

  @Before
  public void loadTestProps() {
    testfile = "/home/wilsonmr/javaseis/seg45shot.js";


    try {
      sio = new Seisio(testfile);
      sio.open("r");
      sio.usesProperties(true);
      TraceProperties tp = sio.getTraceProperties();
      Assert.assertNotEquals("This file contains no properties",
          tp.getNumProperties(),0);
      rawGrid = sio.getGridDefinition();
      Assert.assertNotNull(rawGrid);
      System.out.println(rawGrid.toString());

      int xdim = 2;  //3rd array index
      int ydim = 1;  //2nd array index
      BinGrid bingrid = new BinGrid(rawGrid,xdim,ydim);
      Assert.assertNotNull(bingrid);     
      //TODO  I'm not sure which is x and which is y here.

      String[] coordprops = new String[]
          {"SOU_XD","SOU_YD","SOU_ELEV","REC_XD","REC_YD","REC_ELEV"};
      //The JSCS source/javadoc should explain that ORDER MATTERS HERE.
      jscs = new JSCoordinateService(sio,bingrid,
          CoordinateType.SHOTRCVR,coordprops);
      Assert.assertEquals(rawGrid,jscs.getDataGrid());

    } catch (SeisException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } 
  }
  
  @Test
  public void test() {
    int traceIndex = 1;
    int frameIndex = 2;
    int sourceIndex = 3;
    long traceAxisLength = rawGrid.getAxisLength(traceIndex);
    long frameAxisLength = rawGrid.getAxisLength(frameIndex);
    long sourceAxisLength = rawGrid.getAxisLength(sourceIndex);
    System.out.println(sourceAxisLength);
    
    for (int k = 0 ; k < sourceAxisLength ; k++) {
      System.out.println("Shot #" + k);
      int[] pos = new int[rawGrid.getNumDimensions()];
      pos[sourceIndex] = k;
      printSrcRcvrXYZ(jscs, pos);
      
      pos[traceIndex] = (int)traceAxisLength - 1;
      printSrcRcvrXYZ(jscs, pos);
      
      pos[traceIndex] = 0;
      pos[frameIndex] = (int)frameAxisLength - 1;
      printSrcRcvrXYZ(jscs, pos);
      
      System.out.println("");
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

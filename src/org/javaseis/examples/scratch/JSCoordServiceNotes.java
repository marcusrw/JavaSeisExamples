package org.javaseis.examples.scratch;

import java.util.Arrays;

import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.grid.BinGrid;
import org.javaseis.grid.GridDefinition;
import org.javaseis.io.Seisio;
import org.javaseis.properties.PropertyDescription;
import org.javaseis.properties.TraceProperties;
import org.javaseis.util.SeisException;
import org.junit.Assert;
import org.junit.Test;

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

  private void printSrcRcvrXYZ(JSCoordinateService jscs, int[] pos) {
    double[] srxyz = new double[6];
    jscs.getSrcRcvrXYZ(pos, srxyz);
    System.out.println("SRXYZ for position "
        + Arrays.toString(pos) + ": "
        + Arrays.toString(srxyz));
  }
}

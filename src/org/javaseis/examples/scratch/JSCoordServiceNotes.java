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
    String testfile = "/home/wilsonmr/javaseis/segsaltmodel.js";
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
      Assert.assertNotEquals("This file contains no properties",tp.getNumProperties(),0);
      GridDefinition grid = sio.getGridDefinition();
      Assert.assertNotNull(grid);
      System.out.println(grid.toString());
      int xdim = 2;  //3rd array index
      int ydim = 1;  //2nd array index
      BinGrid bingrid = new BinGrid(grid,xdim,ydim);
      Assert.assertNotNull(bingrid);
      //TODO  Note that the coordinates come out of jscs.getSrcRcvrXYZ in the 
      //      same order you put them in here.  It shouldn't work that way.
      //      jscs.getSrcRcvrXYZ should return the receiver coordinates,
      //      regardless of how you put them in.
      //TODO  Write a test that showcases this behaviour.  (ie make coordprops
      //      twice in different order, and show that SrcXYZ changes for the
      //      same trace.
      
      //TODO  I'm not sure which is x and which is y here.
      String[] coordprops = new String[]
          {"SOU_XD","SOU_YD","SOU_ELEV","REC_XD","REC_YD","REC_ELEV"};
      System.out.println("Fail here?");
      jscs = new JSCoordinateService(sio,bingrid,
          CoordinateType.SHOTRCVR,coordprops);
      System.out.println("No!");
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

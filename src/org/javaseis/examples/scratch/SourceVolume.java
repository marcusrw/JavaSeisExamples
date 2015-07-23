package org.javaseis.examples.scratch;

import java.io.File;
import java.util.Arrays;

import org.javaseis.grid.BinGrid;
import org.javaseis.grid.GridDefinition;
import org.javaseis.io.Seisio;
import org.javaseis.properties.PropertyDescription;
import org.javaseis.properties.TraceProperties;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.junit.Assert;

import beta.javaseis.services.CoordinateType;
import beta.javaseis.services.JSCoordinateService;

public class SourceVolume {

  private final int[] receiverVolumePosition;

  public SourceVolume(ToolContext toolContext,
      ISeismicVolume receiverVolume) {
    receiverVolumePosition = receiverVolume.getVolumePosition();
    System.out.println(Arrays.toString(receiverVolumePosition));

    String inputFilePath
    = toolContext.getParameterService().getParameter("inputFileSystem","null")
    + File.separator
    +toolContext.getParameterService().getParameter("inputFilePath","null");
    //System.out.println(inputFilePath);

    Seisio sio;
    JSCoordinateService jscs;

    try {
      sio = new Seisio(inputFilePath);
      sio.open("r");
      sio.usesProperties(true);
      TraceProperties tp = sio.getTraceProperties();
      PropertyDescription[] tpd = tp.getTraceProperties();
      GridDefinition grid = sio.getGridDefinition();
      int xdim = 1;  //2nd array index
      int ydim = 2;  //3rd array index
      BinGrid bingrid = new BinGrid(grid,xdim,ydim);
      Assert.assertNotNull(bingrid);     
      //TODO  I'm not sure which is x and which is y here.

      String[] coordprops = new String[]
          {"SOU_XD","SOU_YD","SOU_ELEV","REC_XD","REC_YD","REC_ELEV"};
      //The JSCS source/javadoc should explain that ORDER MATTERS HERE.
      jscs = new JSCoordinateService(sio,bingrid,
          CoordinateType.SHOTRCVR,coordprops);
      int[] pos = Arrays.copyOf(receiverVolumePosition,
          receiverVolumePosition.length);
      double[] srxyz = new double[6];
      double[] sxyz = new double[3];
      double[] rxyz = new double[3];
      double[] rxyz2 = new double[3];
      double[] sxyzFirst = new double[3];
      long[] volumeShape = receiverVolume.getLocalGrid().getAxisLengths();
      double DOUBLE_EPSILON = 1e-7;

      jscs.getSourceXYZ(pos, sxyzFirst);
      jscs.getReceiverXYZ(pos, rxyz);
      jscs.getReceiverXYZ(new int[] {0,1,1,0},rxyz2);
      for (int k = 0 ; k < rxyz2.length ; k++) {
        rxyz2[k] -= rxyz[k];
      }

      System.out.println("Physical Origins from data: " + Arrays.toString(rxyz));
      System.out.println("Physical Deltas from data: " + Arrays.toString(rxyz2));

      double[] physicalOrigins = grid.getAxisPhysicalOrigins();
      double[] physicalDeltas = grid.getAxisPhysicalDeltas();
      System.out.println("Physical Origins from grid: "
          + Arrays.toString(physicalOrigins));
      System.out.println("Physical Deltas from grid: "
          + Arrays.toString(physicalDeltas));

      if (physicalOrigins[xdim] != rxyz[0]) {
        System.out.println("Grid physical origin for dimension " 
            + xdim + " is " + physicalOrigins[xdim]
                + ".  Does not match the ReceiverX origin of " + rxyz[0]);
      }

      //check that SourceXYZ doesn't change between traces
      for (int xindx = 0 ; xindx < volumeShape[1] ; xindx++) {
        pos[1] = xindx;
        for (int yindx = 0 ; yindx < volumeShape[2] ; yindx++) {
          pos[2] = yindx;
          jscs.getSourceXYZ(pos, sxyz);
          Assert.assertArrayEquals(sxyzFirst,sxyz,DOUBLE_EPSILON);
        }
      }
    } catch (SeisException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }    
  }

  public boolean isFinished() {
    return false;
  }
}

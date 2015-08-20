package org.javaseis.grid;

import java.io.File;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.grid.BinGrid;
import org.javaseis.grid.GridDefinition;
import org.javaseis.io.Seisio;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.tool.ToolState;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.junit.Assert;

import beta.javaseis.services.CoordinateType;
import beta.javaseis.services.JSCoordinateService;

public class GridFromHeaders implements ICheckedGrid {

  private static final Logger LOGGER = Logger.getLogger(GridFromHeaders.class.getName());

  private ToolState toolContext;
  private ISeismicVolume input;
  private GridDefinition modifiedGrid;
  private Seisio sio = null;
  
  private int[] filePos;

  // Axis locations - Default
  private int Xindex = 2;
  private int Yindex = 1;
  private int Zindex = 0;

  // Axis Order [x,y,z]
  private int[] AXIS_ORDER;

  private JSCoordinateService jscs;

  // Source Position
  private double[] sourceXYZ;

  public GridFromHeaders(ISeismicVolume input, ToolState toolContext, int[] filePos) {
    this.toolContext = toolContext;
    this.input = input;
    this.filePos = filePos;

    try {
      this.jscs = openTraceHeadersFile(toolContext);
    } catch (SeisException e) {
      LOGGER.log(Level.INFO, e.getMessage(), e);
    }

    // Updates the Grid
    checkVolumeGridDefinition(this.input, this.toolContext);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#finalize() Close the file handles when grid goes out
   * of scope, maybe
   */
  @Override
  protected void finalize() throws Throwable {
    try {
      // free the file handle, maybe
      if (sio != null) {
        sio.close();
        sio = null;
      }
    } catch (Throwable e) {
      throw e;
    } finally {
      super.finalize();
    }
  }

  private JSCoordinateService openTraceHeadersFile(ToolState toolContext) throws SeisException {
    String inputFilePath = toolContext.getParameter("inputFileSystem") + File.separator
        + toolContext.getParameter("inputFilePath");
    try {
      sio = new Seisio(inputFilePath);
      sio.open("r");
      sio.usesProperties(true);
      GridDefinition grid = sio.getGridDefinition();
      BinGrid bingrid = new BinGrid(grid, Xindex, Yindex);
      Assert.assertNotNull(bingrid);
      String[] coordprops = new String[] { "SOU_XD", "SOU_YD", "SOU_ELEV", "REC_XD", "REC_YD", "REC_ELEV" };
      // The JSCS source/javadoc should explain that ORDER MATTERS HERE.
      return new JSCoordinateService(sio, bingrid, CoordinateType.SHOTRCVR, coordprops);
      // Note: This will throw a NullPointerException if the data has no
      // associated
      // Trace file, so catch that exception too.
    } catch (SeisException e) {
      if (sio != null) {
        sio = null;
      }
      LOGGER.log(Level.SEVERE, e.getMessage(), e);
      throw e;
    }
  }

  private void checkVolumeGridDefinition(ISeismicVolume input, ToolState toolContext) {
    modifiedGrid = updateVolumeGridDefinition(input, toolContext);
  }

  private GridDefinition updateVolumeGridDefinition(ISeismicVolume input, ToolState toolContext) {
    // Get the grid from SeismicVolume
    GridDefinition inputGrid = toolContext.getInputState().gridDefinition;
    long[] inputAxisLengths = inputGrid.getAxisLengths();

    // Get the Starting point in a Volume
    
    int[] VolPos = filePos;
    
    LOGGER.info("[updateVolumeGridDefinition] FilePos: " + Arrays.toString(VolPos));
    
    VolPos[2] = 0;
    
    LOGGER.info("[updateVolumeGridDefinition] Best Guess for VolumePos: " + Arrays.toString(VolPos));

    int[] pos = Arrays.copyOf(VolPos, VolPos.length);

    sourceXYZ = new double[3];
    double[] rxyz = new double[3];
    double[] rxyz2 = new double[3];
    double[] recXYZsrc = new double[3];

    AxisDefinition[] physicalOAxisArray = new AxisDefinition[inputAxisLengths.length];

    jscs.getReceiverXYZ(pos, rxyz);

    for (int k = 1; k < 3; k++) {
      pos[k]++;
      jscs.getReceiverXYZ(pos, rxyz2);
      LOGGER.fine("pos: " + Arrays.toString(pos));
      LOGGER.fine("rxyz: " + Arrays.toString(rxyz));
      LOGGER.fine("rxyz2: " + Arrays.toString(rxyz2));
      if (Math.abs(rxyz[0] - rxyz2[0]) > 0.5) {
        Xindex = k;
        LOGGER.fine("Xindex is " + Xindex);
      }
      pos[k]--;
    }
    for (int k = 1; k < 3; k++) {
      pos[k]++;
      jscs.getReceiverXYZ(pos, rxyz2);
      LOGGER.fine("pos: " + Arrays.toString(pos));
      LOGGER.fine("rxyz: " + Arrays.toString(rxyz));
      LOGGER.fine("rxyz2: " + Arrays.toString(rxyz2));
      if (Math.abs(rxyz[1] - rxyz2[1]) > 0.5) {
        Yindex = k;
        LOGGER.fine("Yindex is " + Yindex);
      }
      pos[k]--;
    }

    // sample axis is almost always time/depth
    Zindex = 0;

    AXIS_ORDER = new int[] { Xindex, Yindex, Zindex };

    jscs.getReceiverXYZ(pos, rxyz);
    LOGGER.fine("[updateVolumeGridDefinition] rec1 Pos: " + Arrays.toString(rxyz));
    pos[1]++;
    pos[2]++;
    jscs.getReceiverXYZ(pos, rxyz2);
    LOGGER.fine("[updateVolumeGridDefinition] rec2 Pos: " + Arrays.toString(rxyz2));

    // TODO hack
    pos[1] = 100;
    pos[2] = 100;

    jscs.getSourceXYZ(pos, sourceXYZ);

    jscs.getReceiverXYZ(pos, recXYZsrc);
    LOGGER.fine("[updateVolumeGridDefinition] sourceXYZ Pos: " + Arrays.toString(sourceXYZ));
    LOGGER.fine("[updateVolumeGridDefinition] sourceXYZ Pos Check: " + Arrays.toString(recXYZsrc));
    for (int k = 0; k < rxyz2.length; k++) {
      rxyz2[k] -= rxyz[k];
    }

    LOGGER.fine("[updateVolumeGridDefinition] New PhysO: " + Arrays.toString(rxyz));
    LOGGER.fine("[updateVolumeGridDefinition] New Deltas: " + Arrays.toString(rxyz2));
    LOGGER.fine("[updateVolumeGridDefinition] Axis Lengths: " + Arrays.toString(inputAxisLengths));

    for (int k = 0; k < inputAxisLengths.length; k++) {
      AxisDefinition inputAxis = inputGrid.getAxis(k);
      physicalOAxisArray[k] = new AxisDefinition(inputAxis.getLabel(), inputAxis.getUnits(), inputAxis.getDomain(),
          inputAxis.getLength(), inputAxis.getLogicalOrigin(), inputAxis.getLogicalDelta(),
          CalculateNewPhysicalOrigin(inputAxis, k, rxyz), CalculateNewDeltaOrigin(inputAxis, k, rxyz2));
    }

    // For debugging
    GridDefinition modifiedGrid = new GridDefinition(inputGrid.getNumDimensions(), physicalOAxisArray);
    LOGGER.info(modifiedGrid.toString());

    double[] physicalOrigins = modifiedGrid.getAxisPhysicalOrigins();
    double[] deltaA = modifiedGrid.getAxisPhysicalDeltas();

    LOGGER.fine("[updateVolumeGridDefinition] Physical Origins from data: " + Arrays.toString(rxyz));
    LOGGER.fine("[updateVolumeGridDefinition] Physical Origins from grid: " + Arrays.toString(physicalOrigins));

    LOGGER.fine("[updateVolumeGridDefinition] Physical Origins from data: " + Arrays.toString(rxyz2));
    LOGGER.fine("[updateVolumeGridDefinition] Physical Deltas from grid: " + Arrays.toString(deltaA));
    // DBG end

    return modifiedGrid;
  }

  // Adjust the new Physical Delta
  private double CalculateNewDeltaOrigin(AxisDefinition axis, int k, double[] data) {
    if (k == Xindex) {
      return data[0];
    } else if (k == Yindex) {
      return data[1];
    } else {
      return axis.getPhysicalDelta();
    }
  }

  // Adjust the new Physical Origin
  private double CalculateNewPhysicalOrigin(AxisDefinition axis, int k, double[] data) {
    if (k == Xindex) {
      return data[0];
    } else if (k == Yindex) {
      return data[1];
    } else {
      return axis.getPhysicalOrigin();
    }
  }

  // Get the Source Position
  public double[] getSourceXYZ(int[] gridPos) {
    double[] sXYZ = new double[3];
    jscs.getSourceXYZ(gridPos, sXYZ);
    return sXYZ;
  }

  /*
   * Get the Receiver Position
   */
  public double[] getReceiverXYZ(int[] gridPos) {
    double[] rXYZ = new double[3];
    // Get receiver from XYZ
    jscs.getReceiverXYZ(gridPos, rXYZ);
    return rXYZ;
  }

  public int[] getAxisOrder() {
    return AXIS_ORDER;
  }

  public GridDefinition getModifiedGrid() {
    return modifiedGrid;
  }

  public double[] getSourceXYZ() {
    return sourceXYZ;
  }

  public String toString() {
    StringBuffer buf = new StringBuffer(this.getClass().getCanonicalName());
    buf.append(": numDimensions " + modifiedGrid._numDimensions);
    for (int i = 0; i < modifiedGrid._numDimensions; i++) {
      buf.append("\n Axis[" + i + "]: " + modifiedGrid._axis[i]);
    }
    return buf.toString();
  }
}

package org.javaseis.examples.scratch;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.grid.BinGrid;
import org.javaseis.grid.GridDefinition;
import org.javaseis.io.Seisio;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.junit.Assert;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.services.CoordinateType;
import beta.javaseis.services.JSCoordinateService;

public class CheckGrids implements ICheckGrids {

  private static final Logger LOGGER = Logger.getLogger(CheckGrids.class.getName());

  private ISeismicVolume input;
  private ToolContext toolContext;
  private GridDefinition modifiedGrid;

  private int Xindex = 2;
  private int Yindex = 1;
  private int Zindex = 0;
  private int[] AXIS_ORDER;

  private JSCoordinateService jscs;

  // Source Position
  private double[] sourceXYZ;

  public CheckGrids(ISeismicVolume input, ToolContext toolContext) {
    this.input = input;
    this.toolContext = toolContext;

    try {
      this.jscs = openTraceHeadersFile(toolContext);
    } catch (SeisException e) {
      LOGGER.log(Level.INFO, e.getMessage(), e);
    }

    // Updates the Grid
    checkVolumeGridDefinition(this.toolContext, this.input);

    try {
      sourcesEqual();
      testCoords(input, this, toolContext);
    } catch (IllegalArgumentException e) {
      LOGGER.log(Level.INFO, e.getMessage(), e);
    }

  }

  private JSCoordinateService openTraceHeadersFile(ToolContext toolContext) throws SeisException {
    String inputFilePath = toolContext.getParameter("inputFileSystem") + File.separator
        + toolContext.getParameter("inputFilePath");

    Seisio sio;
    try {
      sio = new Seisio(inputFilePath);
      sio.open("r");
      sio.usesProperties(true);
      GridDefinition grid = sio.getGridDefinition();
      // TODO obvious logical failure here
      int xdim = Yindex;
      int ydim = Xindex;
      BinGrid bingrid = new BinGrid(grid, xdim, ydim);
      Assert.assertNotNull(bingrid);
      String[] coordprops = new String[] { "SOU_XD", "SOU_YD", "SOU_ELEV", "REC_XD", "REC_YD", "REC_ELEV" };
      // The JSCS source/javadoc should explain that ORDER MATTERS HERE.
      return new JSCoordinateService(sio, bingrid, CoordinateType.SHOTRCVR, coordprops);

    } catch (SeisException e) {
      LOGGER.log(Level.SEVERE, e.getMessage(), e);
      throw e;
    }
  }

  private void checkVolumeGridDefinition(ToolContext toolContext, ISeismicVolume input) {
    modifiedGrid = updateVolumeGridDefinition(toolContext, input);
  }

  private GridDefinition updateVolumeGridDefinition(ToolContext toolContext, ISeismicVolume input) {
    // Get the grid from SeismicVolume
    GridDefinition inputGrid = input.getGlobalGrid();
    long[] inputAxisLengths = inputGrid.getAxisLengths();

    // Get the Starting point in a Volume
    int[] VolPos = input.getVolumePosition();
    System.out.println("[updateVolumeGridDefinition] VolumePos: " + Arrays.toString(VolPos));

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
      System.out.println("pos: " + Arrays.toString(pos));
      System.out.println("rxyz: " + Arrays.toString(rxyz));
      System.out.println("rxyz2: " + Arrays.toString(rxyz2));
      if (Math.abs(rxyz[0] - rxyz2[0]) > 0.5) {
        Xindex = k;
        System.out.println("Xindex is " + Xindex);
      }
      pos[k]--;
    }
    for (int k = 1; k < 3; k++) {
      pos[k]++;
      jscs.getReceiverXYZ(pos, rxyz2);
      System.out.println("pos: " + Arrays.toString(pos));
      System.out.println("rxyz: " + Arrays.toString(rxyz));
      System.out.println("rxyz2: " + Arrays.toString(rxyz2));
      if (Math.abs(rxyz[1] - rxyz2[1]) > 0.5) {
        Yindex = k;
        System.out.println("Yindex is " + Yindex);
      }
      pos[k]--;
    }

    // sample axis is almost always time/depth
    Zindex = 0;

    AXIS_ORDER = new int[] { Xindex, Yindex, Zindex };

    jscs.getReceiverXYZ(pos, rxyz);
    System.out.println("[updateVolumeGridDefinition] rec1 Pos: " + Arrays.toString(rxyz));
    pos[1]++;
    pos[2]++;
    jscs.getReceiverXYZ(pos, rxyz2);
    System.out.println("[updateVolumeGridDefinition] rec2 Pos: " + Arrays.toString(rxyz2));

    // TODO hack
    pos[1] = 100;
    pos[2] = 100;

    jscs.getSourceXYZ(pos, sourceXYZ);

    jscs.getReceiverXYZ(pos, recXYZsrc);
    System.out.println("[updateVolumeGridDefinition] sourceXYZ Pos: " + Arrays.toString(sourceXYZ));
    System.out.println("[updateVolumeGridDefinition] sourceXYZ Pos Check: " + Arrays.toString(recXYZsrc));
    for (int k = 0; k < rxyz2.length; k++) {
      rxyz2[k] -= rxyz[k];
    }

    System.out.println("[updateVolumeGridDefinition] New PhysO: " + Arrays.toString(rxyz));
    System.out.println("[updateVolumeGridDefinition] New Deltas: " + Arrays.toString(rxyz2));
    System.out.println("[updateVolumeGridDefinition] Axis Lengths: " + Arrays.toString(inputAxisLengths));

    for (int k = 0; k < inputAxisLengths.length; k++) {
      AxisDefinition inputAxis = inputGrid.getAxis(k);
      physicalOAxisArray[k] = new AxisDefinition(inputAxis.getLabel(), inputAxis.getUnits(),
          inputAxis.getDomain(), inputAxis.getLength(), inputAxis.getLogicalOrigin(),
          inputAxis.getLogicalDelta(), CalculateNewPhysicalOrigin(inputAxis, k, rxyz),
          CalculateNewDeltaOrigin(inputAxis, k, rxyz2));
    }

    // For debugging
    GridDefinition modifiedGrid = new GridDefinition(inputGrid.getNumDimensions(), physicalOAxisArray);
    System.out.println(modifiedGrid.toString());

    double[] physicalOrigins = modifiedGrid.getAxisPhysicalOrigins();
    double[] deltaA = modifiedGrid.getAxisPhysicalDeltas();

    System.out.println("[updateVolumeGridDefinition] Physical Origins from data: " + Arrays.toString(rxyz));
    System.out.println(
        "[updateVolumeGridDefinition] Physical Origins from grid: " + Arrays.toString(physicalOrigins));

    System.out.println("[updateVolumeGridDefinition] Physical Origins from data: " + Arrays.toString(rxyz2));
    System.out.println("[updateVolumeGridDefinition] Physical Deltas from grid: " + Arrays.toString(deltaA));
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

  // Checks if all the sources are the same for the traces over a specific
  // volume
  private boolean sourcesEqual() {
    // check if the input DistArray Sources match the computed source
    DistributedArray inputDistArr = input.getDistributedArray();

    // Get the current volume
    int[] globalPosIndex = input.getVolumePosition();
    int[] volumePosIndex = new int[3];

    // Iterate over traces
    // TODO is iterating over positions (samples)
    // You can iterate over traces by setting the scope to 1.
    DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(inputDistArr,
        volumePosIndex, DistributedArrayPositionIterator.FORWARD);

    while (itrInputArr.hasNext()) {
      volumePosIndex = itrInputArr.next();

      globalPosIndex[0] = 0;
      for (int k = 1; k < 3; k++) {
        globalPosIndex[k] = volumePosIndex[k];
      }

      // Get the source positions at [0,?,?,v]
      double[] sXYZ = new double[3];
      jscs.getSourceXYZ(globalPosIndex, sXYZ);

      // check that this source is equal to the local source
      for (int i = 0; i < sourceXYZ.length; i++) {
        if (sourceXYZ[i] != sXYZ[i]) {
          // Sources don't match
          System.out
          .println("[sourcesEqual]: Sources Change at Location: " + Arrays.toString(globalPosIndex));
          System.out.println("\t[sourcesEqual]: " + "Expected: " + Arrays.toString(sourceXYZ) + "Given: "
              + Arrays.toString(sXYZ));

          throw new IllegalArgumentException("Sources Change Between Trace.");
          // return false;
        }
      }
    }
    return true;
  }

  // Check when Creating an object
  private void testCoords(ISeismicVolume input, CheckGrids CheckGrid, ToolContext toolContext) {
    LOGGER.info("[tesCoords]: ----- Called DEBUG TEST: -----");

    IVelocityModel vmff = getVelocityModelObject(this, toolContext);

    // Get the values from the distributed array and compare the jscs values

    // Grab Input ISeismicVolume -> DistributedArray
    DistributedArray inputDistArr = input.getDistributedArray();

    // index of trace [sample, trace, frame, volume]
    int[] globalPosIndex = input.getVolumePosition();
    int[] volumePosIndex = new int[3];

    // Iterate over the traces of the ISeismicVolume in the forward
    // direction (1)
    DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(inputDistArr,
        volumePosIndex, DistributedArrayPositionIterator.FORWARD);

    while (itrInputArr.hasNext()) {
      volumePosIndex = itrInputArr.next();

      // Don't compare anything about depth
      // globalPosIndex[0] = zindx;
      globalPosIndex[0] = 0; // 0 the depth field index

      for (int k = 1; k < 3; k++) {
        globalPosIndex[k] = volumePosIndex[k];
      }

      // rXYZ2 hold the receiver location for a given trace location
      // [0,?,?,0]
      double[] rXYZ = new double[3];

      // jscs.getReceiverXYZ(globalPosIndex, rXYZ);
      rXYZ = CheckGrid.getReceiverXYZ(globalPosIndex);

      // LOGGER.fine("[processVolume]: Pos: " +
      // Arrays.toString(globalPosIndex) +
      // " recXYZ jscs: " + Arrays.toString(rXYZ));

      /// TEST CODE///
      // Compare the jscs coords to the coord based on pOrigin + indx *
      /// pDelta

      // rXYZ value from the Grid instead of jscs
      double[] rXYZ2 = new double[3];

      // Check if both indexes are in range
      // double yIndex = globalPosIndex[Yindex];
      // double xIndex = globalPosIndex[Xindex];

      double yIndex = globalPosIndex[CheckGrid.getAxisOrder()[1]];
      double xIndex = globalPosIndex[CheckGrid.getAxisOrder()[0]];

      // Calculate position in Xline

      // int currentAxis = Yindex; //Trace axis (Y we believe)
      int currentAxis = CheckGrid.getAxisOrder()[1];

      double minPhys0 = CheckGrid.getModifiedGrid().getAxisPhysicalOrigin(currentAxis);
      double axisPhysDelta = CheckGrid.getModifiedGrid().getAxisPhysicalDelta(currentAxis);
      double yval = minPhys0 + yIndex * axisPhysDelta;

      // Calculate position in Iline
      // currentAxis = Xindex;
      currentAxis = CheckGrid.getAxisOrder()[0];
      minPhys0 = CheckGrid.getModifiedGrid().getAxisPhysicalOrigin(currentAxis);
      axisPhysDelta = CheckGrid.getModifiedGrid().getAxisPhysicalDelta(currentAxis);
      double xval = minPhys0 + xIndex * axisPhysDelta;

      // Set rXYZ2 Grids Calculations
      rXYZ2[0] = xval;
      rXYZ2[1] = yval;

      rXYZ2[2] = 0;
      // rXYZ2[2] = depth;

      // LOGGER.fine("[processVolume]: Values From Grid (rXYZ2):" +
      // Arrays.toString(rXYZ2));

      double[] vmodXYZ = vmff.getVelocityModelXYZ(globalPosIndex);
      // LOGGER.fine("Physical Location in VModel for Position: "
      // + Arrays.toString(globalPosIndex) + " is " +
      // Arrays.toString(vmff.getVelocityModelXYZ(globalPosIndex)));

      /*
       * System.out.println("AXIS_ORDER: " +
       * Arrays.toString(CheckGrid.getAxisOrder())); System.out.println(
       * "Global Position Index: " + Arrays.toString(globalPosIndex));
       * System.out.println("Receiver XYZ from RegularGrids: " +
       * Arrays.toString(rXYZ2)); System.out.println("Receiver XYZ: " +
       * Arrays.toString(rXYZ));
       */

      // Don't check 1st position that is depth
      for (int k = 0; k < 2; k++) {
        if (rXYZ2[k] - rXYZ[k] > 0.5 || rXYZ2[k] - rXYZ[k] < -0.5) {
          System.out.println("AXIS_ORDER: " + Arrays.toString(CheckGrid.getAxisOrder()));
          System.out.println("Global Position Index: " + Arrays.toString(globalPosIndex));
          System.out.println("Receiver XYZ from RegularGrids: " + Arrays.toString(rXYZ2));
          System.out.println("Receiver XYZ: " + Arrays.toString(rXYZ));
          throw new ArithmeticException("The origin/delta position doesn't match the getRXYZ position");
        }
      }

      // Don't check 1st position that is depth pos
      // vmodXYZ.length - 1 so that we don't check the depth
      for (int k = 0; k < vmodXYZ.length - 1; k++) {
        if (vmodXYZ[k] - rXYZ[k] > 0.5 || vmodXYZ[k] - rXYZ[k] < -0.5) {
          System.out.println("AXIS_ORDER: " + Arrays.toString(CheckGrid.getAxisOrder()));
          System.out.println("Global Position Index: " + Arrays.toString(globalPosIndex));
          System.out.println("Velocity Model XYZ: " + Arrays.toString(vmodXYZ));
          System.out.println("Receiver XYZ: " + Arrays.toString(rXYZ));
          throw new ArithmeticException("Seismic and VModel locations don't agree here.");
        }
      }

      // TODO:Test - remove this code!!
      /*
       * try { Thread.sleep(2500); } catch(InterruptedException ex) {
       * Thread.currentThread().interrupt(); }
       */
      // end
    }
    // LOGGER.info("[processVolume]: ----- DEBUG TEST ENDED -----");
  }

  private IVelocityModel getVelocityModelObject(CheckGrids CheckGrid, ToolContext toolContext) {
    IVelocityModel vmff = null;
    try {
      vmff = new VelocityModelFromFile(toolContext);
      // vmff = new VelocityModelFromFile(pc,folder,file);
    } catch (FileNotFoundException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    vmff.open("r");
    // System.out.println("[VelocityModelFromFile: Input grid");
    // System.out.println(inputGrid.toString());
    // System.out.println("Axis order: " + Arrays.toString(AXIS_ORDER));
    // vmff.orientSeismicVolume(inputGrid,AXIS_ORDER);
    vmff.orientSeismicVolume(CheckGrid.getModifiedGrid(), CheckGrid.getAxisOrder());
    return vmff;
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
}

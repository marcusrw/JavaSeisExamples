package org.javaseis.examples.tool;

import static org.javaseis.utils.Convert.listToArray;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.examples.scratch.ExampleMigration;
import org.javaseis.grid.GridDefinition;
import org.javaseis.grid.JTestCheckedGridNew;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.DataState;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.array.ITraceIterator;
import beta.javaseis.distributed.FileSystemIOService;
import beta.javaseis.distributed.IDistributedIOService;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;
import beta.javaseis.services.IPropertyService;
import beta.javaseis.services.VolumePropertyService;
import edu.mines.jtk.util.ArrayMath;

import org.junit.Assert;

/**
 * Example tool that modifies an input volume scalar multiplication.
 * <p>
 * Parameters:
 * <p>
 * org.javaseis.examples.tool.ExampleVolumeTool.scalarValue - scalar that will
 * be multiplied with input data to produce output data
 */
public class VolumeCorrectionTool implements IVolumeTool {

  private static final Logger LOGGER = Logger.getLogger(VolumeCorrectionTool.class.getName());

  private static final long serialVersionUID = 1L;

  IDistributedIOService ipio;
  VolumePropertyService vps;
  boolean usesProperties;
  String inputFileSystem, inputFileName;

  @Override
  public void serialInit(ToolState toolState) throws SeisException {
    inputFileSystem = toolState.getParameter(ToolState.INPUT_FILE_SYSTEM);
    toolState.log("Input file system: " + inputFileSystem);
    inputFileName = toolState.getParameter(ToolState.INPUT_FILE_NAME);
    toolState.log("Input file name: " + inputFileName);
    IParallelContext upc = new UniprocessorContext();
    ipio = new FileSystemIOService(upc, inputFileSystem);
    ipio.open(inputFileName);
    toolState.log("Opened file in serial mode");
    // toolState.setOutputState(new DataState(ipio,
    // toolState.getIntParameter(ToolState.TASK_COUNT, 1)));
    ipio.close();
  }

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState) throws SeisException {
    ipio = new FileSystemIOService(pc, inputFileSystem);
    ipio.open(inputFileName);
    ISeismicVolume outputVolume = (ISeismicVolume) toolState.getObject(ToolState.OUTPUT_VOLUME);
    ipio.setSeismicVolume(outputVolume);
    ipio.reset();
    /*
     * if (ipio.usesProperties()) { vps = (VolumePropertyService)
     * (ipio.getPropertyService()); pc.masterPrint("\n" + vps.listProperties() +
     * "\n"); }
     */
    pc.serialPrint("Re-opened file in parallel mode");
  }

  // Adjust the new Physical Delta
  private double CalculateNewDeltaOrigin(AxisDefinition axis, int k, double[] data) {
    if (k == 0) {
      if (axis.getPhysicalDelta() == 0) {
        // In a synthetic data set the physical delta on the Z axis can
        // be 0
        return 0.000001;
      }
      return axis.getPhysicalDelta();
    } else if (k == 1) {
      return data[1];
    } else if (k == 2) {
      return data[0];
    }
    return Double.MAX_VALUE;
  }

  // Adjust the new Physical Origin
  private double CalculateNewPhysicalOrigin(AxisDefinition axis, int k, double[] origins) {
    if (k == 2) {
      return origins[0];
    } else if (k == 1) {
      return origins[1];
    } else if (k == 0) {
      return axis.getPhysicalOrigin();
    }
    return Double.MAX_VALUE;
  }

  private GridDefinition updateVolumeGridDefinition(ISeismicVolume input, ToolState toolContext) {

    GridDefinition inputGrid = input.getGlobalGrid();
    long[] inputAxisLengths = inputGrid.getAxisLengths();

    double[] srcXYZ = new double[3];
    double[] recXYZ = new double[3];

    AxisDefinition[] physicalOAxisArray = new AxisDefinition[inputAxisLengths.length];

    // Always start at position 0,0,0
    int[] position = new int[] { 0, 0, 0 };

    input.getCoords(position, srcXYZ, recXYZ);

    // LOGGER.info("[PV updateVolumeGridDefinition] Source: " +
    // Arrays.toString(srcXYZ));

    // LOGGER.info("[PV updateVolumeGridDefinition] Reciever: " +
    // Arrays.toString(recXYZ));

    int[] position2 = new int[] { 0, 0, 0 };

    position2[1] = position[1] + 1;
    position2[2] = position[2] + 1;

    double[] srcXYZ2 = new double[3];
    double[] recXYZ2 = new double[3];

    input.getCoords(position2, srcXYZ2, recXYZ2);

    // LOGGER.info("[PV updateVolumeGridDefinition] Source: " +
    // Arrays.toString(srcXYZ2));

    // LOGGER.info("[PV updateVolumeGridDefinition] Reciever: " +
    // Arrays.toString(recXYZ2));

    for (int k = 0; k < recXYZ2.length; k++) {
      recXYZ2[k] -= recXYZ[k];
    }

    LOGGER.info("[PV updateVolumeGridDefinition] Reciever: " + Arrays.toString(recXYZ));

    LOGGER.info("[PV updateVolumeGridDefinition] Delta: " + Arrays.toString(recXYZ2));

    for (int gridDefIndex = 0; gridDefIndex < inputAxisLengths.length; gridDefIndex++) {
      AxisDefinition inputAxis = inputGrid.getAxis(gridDefIndex);
      physicalOAxisArray[gridDefIndex] = new AxisDefinition(inputAxis.getLabel(), inputAxis.getUnits(),
          inputAxis.getDomain(), inputAxis.getLength(), inputAxis.getLogicalOrigin(), inputAxis.getLogicalDelta(),
          CalculateNewPhysicalOrigin(inputAxis, gridDefIndex, recXYZ),
          CalculateNewDeltaOrigin(inputAxis, gridDefIndex, recXYZ2));
    }

    return new GridDefinition(inputGrid.getNumDimensions(), physicalOAxisArray);
  }

  /**
   * NOT RECOMMENDED
   * 
   * @return
   */
  @Deprecated
  private GridDefinition generateOutputGrid(ISeismicVolume input, double[] minPhyO, double[] Delta) {
    GridDefinition inputGrid = input.getGlobalGrid();
    long[] inputAxisLengths = inputGrid.getAxisLengths();

    AxisDefinition[] physicalOAxisArray = new AxisDefinition[inputAxisLengths.length];

    for (int k = 0; k < inputAxisLengths.length; k++) {
      AxisDefinition inputAxis = inputGrid.getAxis(k);
      physicalOAxisArray[k] = new AxisDefinition(inputAxis.getLabel(), inputAxis.getUnits(), inputAxis.getDomain(),
          inputAxis.getLength(), inputAxis.getLogicalOrigin(), inputAxis.getLogicalDelta(), minPhyO[k],
          Math.abs(Delta[k]));
    }

    return new GridDefinition(inputGrid.getNumDimensions(), physicalOAxisArray);
  }

  private int[] computeAxisOrder(ISeismicVolume input) {

    int[] strPos = new int[] { 0, 0, 0 };

    int[] Axis_Order = null;

    int Xindex = Integer.MAX_VALUE;
    int Yindex = Integer.MAX_VALUE;
    int Zindex = Integer.MAX_VALUE;

    double[] sXYZ = new double[3];
    double[] rXYZ = new double[3];

    input.getCoords(strPos, sXYZ, rXYZ);

    System.out.println("[computeAxisOrder]: " + Arrays.toString(strPos));
    System.out.println("[computeAxisOrder]: " + Arrays.toString(sXYZ));
    System.out.println("[computeAxisOrder]: " + Arrays.toString(rXYZ));

    double[] sXYZ2 = new double[3];
    double[] rXYZ2 = new double[3];

    for (int k = 1; k < 3; k++) {
      strPos[k]++;
      input.getCoords(strPos, sXYZ2, rXYZ2);
      LOGGER.fine("pos: " + Arrays.toString(strPos));
      LOGGER.fine("rxyz: " + Arrays.toString(rXYZ));
      LOGGER.fine("rxyz2: " + Arrays.toString(rXYZ2));
      if (Math.abs(rXYZ[0] - rXYZ2[0]) > 0.5) {
        Xindex = k;
        LOGGER.fine("Xindex is " + Xindex);
      }
      strPos[k]--;
    }
    for (int k = 1; k < 3; k++) {
      strPos[k]++;
      input.getCoords(strPos, sXYZ2, rXYZ2);
      LOGGER.fine("pos: " + Arrays.toString(strPos));
      LOGGER.fine("rxyz: " + Arrays.toString(rXYZ));
      LOGGER.fine("rxyz2: " + Arrays.toString(rXYZ2));
      if (Math.abs(rXYZ[1] - rXYZ2[1]) > 0.5) {
        Yindex = k;
        LOGGER.fine("Yindex is " + Yindex);
      }
      strPos[k]--;
    }

    // sample axis is almost always time/depth
    Zindex = 0;

    Axis_Order = new int[] { Xindex, Yindex, Zindex };

    System.out.println("[computeAxisOrder]: Axis Order: " + Arrays.toString(Axis_Order));
    return Axis_Order;
  }

  private int[] computeInverse(int[] Axis_Order) {
    int[] inverseOrder = new int[3];
    for (int i = 0; i < Axis_Order.length; i++) {
      int val = Axis_Order[i];
      inverseOrder[val] = i;
    }
    System.out.println("[computeInverse]: " + Arrays.toString(inverseOrder));
    return inverseOrder;
  }

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolState, ISeismicVolume input, ISeismicVolume output)
      throws SeisException {

    GridDefinition inputGrid = updateVolumeGridDefinition(input, toolState);

    System.out.println(inputGrid.toString());

    // Create a new Instance of VolumePropertyService
    VolumePropertyService vpsO = new VolumePropertyService(ipio);

    output.setPropertyService(vpsO);

    VolumePropertyService propsInput = (VolumePropertyService) input.getPropertyService();

    VolumePropertyService propsOutput = (VolumePropertyService) output.getPropertyService();

    // Set the actual Properties of the actual traces
    // Input should never be null
    Assert.assertNotNull(propsInput);

    ITraceIterator iti = input.getTraceIterator();
    ITraceIterator oti = output.getTraceIterator();

    GridDefinition outputGrid = null;

    double sampleAxisMin = (double) Double.MAX_VALUE;
    sampleAxisMin = inputGrid.getAxisPhysicalOrigin(0);
    double traceAxisMin = (double) Double.MAX_VALUE;
    double frameAxisMin = (double) Double.MAX_VALUE;
    double sampleDelta = (double) Double.MAX_VALUE;
    double traceDelta = (double) Double.MAX_VALUE;
    double frameDelta = (double) Double.MAX_VALUE;

    int[] axis_Order = computeAxisOrder(input);

    int[] desired_Axis_Order = new int[] { 2, 1, 0 };

    int[] inverse_Order = computeInverse(axis_Order);

    System.out.println(desired_Axis_Order[inverse_Order[0]]);
    System.out.println(desired_Axis_Order[inverse_Order[1]]);
    System.out.println(desired_Axis_Order[inverse_Order[2]]);

    /*
     * try { System.in.read(); } catch (IOException e1) { // TODO Auto-generated
     * catch block e1.printStackTrace(); }
     */

    while (iti.hasNext()) {

      iti.next();

      // Clone the Trace
      float[] actualTrace = iti.getTrace();

      int[] tracePos = iti.getPosition().clone();

      LOGGER.info("Input Iterator Pos: " + Arrays.toString(tracePos));

      propsInput.setPosition(tracePos);

      int sampleIndex = tracePos[desired_Axis_Order[inverse_Order[0]]];
      int traceIndex = tracePos[desired_Axis_Order[inverse_Order[1]]];
      int frameIndex = tracePos[desired_Axis_Order[inverse_Order[2]]];

      int sampleLen = (int) (inputGrid.getAxis(desired_Axis_Order[inverse_Order[0]]).getLength() - 1);
      int traceLen = (int) (inputGrid.getAxis(desired_Axis_Order[inverse_Order[1]]).getLength() - 1);
      int frameLen = (int) (inputGrid.getAxis(desired_Axis_Order[inverse_Order[2]]).getLength() - 1);

      double samplePhysicalOrigin = inputGrid.getAxis(desired_Axis_Order[inverse_Order[0]]).getPhysicalOrigin();
      double tracePhysicalOrigin = inputGrid.getAxis(desired_Axis_Order[inverse_Order[1]]).getPhysicalOrigin();
      double framePhyscialOrigin = inputGrid.getAxis(desired_Axis_Order[inverse_Order[2]]).getPhysicalOrigin();

      sampleDelta = inputGrid.getAxis(desired_Axis_Order[inverse_Order[0]]).getPhysicalDelta();
      traceDelta = inputGrid.getAxis(desired_Axis_Order[inverse_Order[1]]).getPhysicalDelta();
      frameDelta = inputGrid.getAxis(desired_Axis_Order[inverse_Order[2]]).getPhysicalDelta();

      if (traceDelta < 0) {
        traceAxisMin = tracePhysicalOrigin + traceDelta * (traceLen);
      } else {
        traceAxisMin = tracePhysicalOrigin;
      }
      if (frameDelta < 0) {
        frameAxisMin = framePhyscialOrigin + frameDelta * (frameLen);
      } else {
        frameAxisMin = framePhyscialOrigin;
      }

      Double ftIndex = (tracePhysicalOrigin - traceAxisMin) / traceDelta;
      Double ffIndex = (framePhyscialOrigin - frameAxisMin) / frameDelta;

      Integer ftIndexInt = (int) Math.round(ftIndex);
      Integer ffIndexInt = (int) Math.round(ffIndex);

      int[] finalPosition = null;
      // swao only one axis
      if (axis_Order[0] == 2 && axis_Order[1] == 1 && axis_Order[2] == 0) {
        finalPosition = new int[] { 0, 200 - traceIndex, frameIndex };
      }
      // swap the x and y axis of data
      else if (axis_Order[0] == 1 && axis_Order[1] == 2 && axis_Order[2] == 0) {
        finalPosition = new int[] { 0, 200 - frameIndex, 200 - traceIndex };
      }

      // Don't want to say this is not how it should work
      // but set position should internally call next() in order
      // to put it the itrator at the right location.
      // TODO: Change the way trace itr works
      oti.setPosition(finalPosition);
      oti.next();

      LOGGER.info("Output Trace Location: " + Arrays.toString(oti.getPosition().clone()));

      oti.putTrace(actualTrace);

      propsOutput.setPosition(finalPosition);

      double[] sc = new double[3];
      double[] rc = new double[3];
      output.getCoords(finalPosition, sc, rc);

      LOGGER.fine("Before Trace Copy: " + Arrays.toString(rc));

      try {
        propsOutput.copyTrcProps(propsInput, tracePos, finalPosition);
      } catch (IllegalArgumentException e) {
        LOGGER.log(Level.SEVERE, e.getMessage(), e);
      }

      LOGGER.info("Before Trace Copy: " + Arrays.toString(rc));

      output.getCoords(finalPosition, sc, rc);

      LOGGER.info("After Trace Copy: " + Arrays.toString(rc));

      LOGGER.info("Before Value Editing: " + Arrays.toString(rc));

      // Swap the X and Y axis of the coordinates
      if (axis_Order[0] == 1 && axis_Order[1] == 2 && axis_Order[2] == 0) {
        propsOutput.setValue("REC_YD", rc[0]);
        propsOutput.setValue("REC_XD", rc[1]);
      }

      output.getCoords(finalPosition, sc, rc);
      LOGGER.info("After Value Editing: " + Arrays.toString(rc));

    }

    double[] minPhys = new double[] { sampleAxisMin, traceAxisMin, frameAxisMin };
    double[] Delta = new double[] { sampleDelta, traceDelta, frameDelta };

    /*
     * int[] posTest1 = new int[] { 0, 0, 0 };
     * 
     * LOGGER.info("ATTEMPTING TO GET POS: " + Arrays.toString(posTest1));
     * 
     * double[] sr = new double[3]; double[] rc = new double[3];
     * 
     * output.getCoords(posTest1, sr, rc);
     * 
     * LOGGER.info("Source Trace: " + Arrays.toString(sr)); LOGGER.info(
     * "Reciever Trace: " + Arrays.toString(rc));
     * 
     * int[] posTest2 = new int[] { 0, 200, 200 };
     * 
     * LOGGER.info("ATTEMPTING TO GET POS: " + Arrays.toString(posTest2));
     * 
     * output.getCoords(posTest2, sr, rc);
     * 
     * LOGGER.info("Source Trace: " + Arrays.toString(sr)); LOGGER.info(
     * "Reciever Trace: " + Arrays.toString(rc));
     */

    outputGrid = generateOutputGrid(input, minPhys, Delta);

    System.out.println(outputGrid.toString());

    GridDefinition outG2 = updateVolumeGridDefinition(output, toolState);

    System.out.println(outG2.toString());

    setOutgoingDataStateGrid(toolState, outputGrid);

    if (ipio.hasNext()) {
      ipio.next();
    }

    return true;
  }

  private void setOutgoingDataStateGrid(ToolState toolState, GridDefinition outputGrid) {
    // toolState.getOutputState().gridDefinition = outputGrid;
    DataState outputState = toolState.getOutputState();
    outputState.gridDefinition = outputGrid;
    toolState.setOutputState(outputState);
  }

  @Override
  public boolean outputVolume(IParallelContext pc, ToolState toolContext, ISeismicVolume output) {
    // No additional output
    return false;
  }

  @Override
  public void parallelFinish(IParallelContext pc, ToolState toolContext) {
    // Nothing to clean up for parallel tasks
  }

  @Override
  public void serialFinish(ToolState toolContext) {
    // Nothing to clean up in serial mode
  }

  private void writeObject(ObjectOutputStream oos) throws IOException {
    // default serialization
    oos.writeObject(inputFileSystem);
    oos.writeObject(inputFileName);
  }

  private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
    // default de-serialization
    inputFileSystem = (String) ois.readObject();
    inputFileName = (String) ois.readObject();
  }

  private static ParameterService basicParameters() {
    String inputFileName = "seg45shot.js";
    String outputFileName = "fishfish.js";
    ParameterService parms = null;
    try {
      parms = new FindTestData(inputFileName, outputFileName).getParameterService();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    parms.setParameter(ToolState.TASK_COUNT, "1");
    return parms;
  }

  public static void main(String[] args) {
    ParameterService parms = basicParameters();

    List<String> toolList = new ArrayList<String>();

    toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
    toolList.add(VolumeCorrectionTool.class.getCanonicalName());
    toolList.add(VolumeToolCheckerTool.class.getCanonicalName());

    String[] toolArray = listToArray(toolList);

    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      e.printStackTrace();
    }
  }
}
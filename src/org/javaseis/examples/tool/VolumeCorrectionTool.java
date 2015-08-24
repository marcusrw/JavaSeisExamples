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

  private static final Logger LOGGER = Logger.getLogger(VolumeCorrectionTool.class.getName());;

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
      if (data[2] == 0) {
        // In a synthetic data set the physical delta on the Z axis can
        // be 0
        return 0.000001;
      }
      return data[2];
    } else if (k == 1) {
      return data[1];
    } else if (k == 2) {
      return data[0];
    }
    return Double.MAX_VALUE;
  }

  // Adjust the new Physical Origin
  private double CalculateNewPhysicalOrigin(AxisDefinition axis, int k, double[] data) {
    if (k == 2) {
      return data[0];
    } else if (k == 1) {
      return data[1];
    } else if (k == 0) {
      return data[2];
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

    // LOGGER.info("[PV updateVolumeGridDefinition] Reciever: " +
    // Arrays.toString(recXYZ));

    // LOGGER.info("[PV updateVolumeGridDefinition] Delta: " +
    // Arrays.toString(recXYZ2));

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

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolState, ISeismicVolume input, ISeismicVolume output)
      throws SeisException {

    GridDefinition inputGrid = updateVolumeGridDefinition(input, toolState);

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

    double traceAxisMin = (double) Double.MAX_VALUE - 1;
    double frameAxisMin = (double) Double.MAX_VALUE - 1;
    double traceDelta = (double) Double.MAX_VALUE - 1;
    double frameDelta = (double) Double.MAX_VALUE - 1;

    while (iti.hasNext()) {

      iti.next();

      // Clone the Trace
      float[] actualTrace = iti.getTrace();

      int[] tracePos = iti.getPosition().clone();

      LOGGER.info("Input Iterator Pos: " + Arrays.toString(tracePos));

      propsInput.setPosition(tracePos);

      int traceIndex = tracePos[1];
      int frameIndex = tracePos[2];

      double traceLen = inputGrid.getAxis(1).getLength() - 1;
      double frameLen = inputGrid.getAxis(2).getLength() - 1;

      double tracePhysicalOrigin = inputGrid.getAxis(1).getPhysicalOrigin();
      double framePhyscialOrigin = inputGrid.getAxis(2).getPhysicalOrigin();

      traceDelta = inputGrid.getAxis(1).getPhysicalDelta();
      frameDelta = inputGrid.getAxis(2).getPhysicalDelta();

      double traceAxisMinAccum;
      double frameAxisMinAccum;

      if (traceDelta < 0) {
        traceAxisMinAccum = tracePhysicalOrigin + traceDelta * (traceLen - traceIndex);
        traceAxisMin = tracePhysicalOrigin + traceDelta * (traceLen - 0);
      } else {
        traceAxisMinAccum = tracePhysicalOrigin + traceDelta * traceIndex;
        traceAxisMin = tracePhysicalOrigin;
      }
      if (frameDelta < 0) {
        frameAxisMinAccum = framePhyscialOrigin + frameDelta * (frameLen - frameIndex);
        frameAxisMin = framePhyscialOrigin + frameDelta * (frameLen - 0);
      } else {
        frameAxisMinAccum = framePhyscialOrigin + frameDelta * frameIndex;
        frameAxisMin = framePhyscialOrigin;
      }

      Double ftIndex = Math.abs((tracePhysicalOrigin - traceAxisMinAccum) / (traceDelta));
      Double ffIndex = Math.abs((framePhyscialOrigin - frameAxisMinAccum) / (frameDelta));

      Integer ftIndexInt = (int) Math.round(ftIndex);
      Integer ffIndexInt = (int) Math.round(ffIndex);

      int[] finalPosition = new int[] { 0, ftIndexInt, ffIndexInt };

      // Don't want to say this is not how it should work
      // but set position should internally call next() in order
      // to put it the itrator at the right location.
      // TODO: Change the way trace itr works
      oti.setPosition(finalPosition);
      oti.next();

      LOGGER.info("Output Trace Location: " + Arrays.toString(oti.getPosition().clone()));

      oti.putTrace(actualTrace);

      Assert.assertNotNull(propsOutput);

      propsOutput.setPosition(finalPosition);

      double[] sc = new double[3];
      double[] rc = new double[3];
      output.getCoords(finalPosition, sc, rc);

      LOGGER.info("Before Trace Copy: " + Arrays.toString(rc));

      try {
        propsOutput.copyTrcProps(propsInput, tracePos, finalPosition);
      } catch (IllegalArgumentException e) {
        LOGGER.log(Level.SEVERE, e.getMessage(), e);
      }

      LOGGER.info("Before Trace Copy: " + Arrays.toString(rc));

      output.getCoords(finalPosition, sc, rc);

      LOGGER.info("After Trace Copy: " + Arrays.toString(rc));

      // Testing Sleep Function
      // try {
      // Thread.sleep(2500);
      // } catch (InterruptedException e) {
      // }

    }

    double[] minPhys = new double[] { 0, traceAxisMin, frameAxisMin };
    double[] Delta = new double[] { 0.00001, traceDelta, frameDelta };

    int[] posTest1 = new int[] { 0, 0, 0 };

    LOGGER.info("ATTEMPTING TO GET POS: " + Arrays.toString(posTest1));

    double[] sr = new double[3];
    double[] rc = new double[3];

    output.getCoords(posTest1, sr, rc);

    LOGGER.info("Source Trace: " + Arrays.toString(sr));
    LOGGER.info("Reciever Trace: " + Arrays.toString(rc));

    int[] posTest2 = new int[] { 0, 200, 200 };

    LOGGER.info("ATTEMPTING TO GET POS: " + Arrays.toString(posTest2));

    output.getCoords(posTest2, sr, rc);

    LOGGER.info("Source Trace: " + Arrays.toString(sr));
    LOGGER.info("Reciever Trace: " + Arrays.toString(rc));

    outputGrid = generateOutputGrid(input, minPhys, Delta);
    setOutgoingDataStateGrid(toolState, outputGrid);

    if (ipio.hasNext()) {
      ipio.next();
    }

    return true;
  }

  private void setOutgoingDataStateGrid(ToolState toolState, GridDefinition outputGrid) {
    toolState.getOutputState().gridDefinition = outputGrid;
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
    String inputFileName = "segshotno1.js";
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
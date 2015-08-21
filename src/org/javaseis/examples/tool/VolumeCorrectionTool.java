package org.javaseis.examples.tool;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.javaseis.grid.GridDefinition;
import org.javaseis.grid.JTestCheckedGridNew;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.array.ITraceIterator;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.services.IPropertyService;
import edu.mines.jtk.util.ArrayMath;
import junit.framework.Assert;

/**
 * Example tool that modifies an input volume scalar multiplication.
 * <p>
 * Parameters:
 * <p>
 * org.javaseis.examples.tool.ExampleVolumeTool.scalarValue - scalar that will
 * be multiplied with input data to produce output data
 */
public class VolumeCorrectionTool implements IVolumeTool {
  private static final long serialVersionUID = 1L;

  int Xindex = 2;
  int Yindex = 1;
  int Zindex = 0;

  @Override
  public void serialInit(ToolState toolState) {
    // Get the scalar multiplier
    // scalarValue = toolState.getFloatParameter("scalarValue", 0f);
    // toolState.log("Scalar value for multiplication: " + scalarValue);
  }

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState) {
    // pc.serialPrint("Entering parallelInit: scalarValue = " + scalarValue);
  }

  // Adjust the new Physical Delta
  private double CalculateNewDeltaOrigin(AxisDefinition axis, int k, double[] data) {
    if (k == Xindex) {
      return data[0];
    } else if (k == Yindex) {
      return data[1];
    } else {
      if (data[2] == 0) {
        // In a synthetic data set the physical delta on the Z axis can be 0
        return 0.008;
      }
      return data[2];
    }
  }

  // Adjust the new Physical Origin
  private double CalculateNewPhysicalOrigin(AxisDefinition axis, int k, double[] data) {
    if (k == Xindex) {
      return data[0];
    } else if (k == Yindex) {
      return data[1];
    } else {
      return data[2];
    }
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

    // System.out.println("[PV updateVolumeGridDefinition] Source: " +
    // Arrays.toString(srcXYZ));
    // System.out.println("[PV updateVolumeGridDefinition] Reciever: " +
    // Arrays.toString(recXYZ));

    int[] position2 = new int[] { 0, 0, 0 };

    position2[1] = position[1] + 1;
    position2[2] = position[2] + 1;

    double[] srcXYZ2 = new double[3];
    double[] recXYZ2 = new double[3];

    // System.out.println(Arrays.toString(position2));

    input.getCoords(position2, srcXYZ2, recXYZ2);

    // System.out.println("[PV updateVolumeGridDefinition] Source 2: " +
    // Arrays.toString(srcXYZ2));
    // System.out.println("[PV updateVolumeGridDefinition] Reciever 2: " +
    // Arrays.toString(recXYZ2));

    for (int k = 0; k < recXYZ2.length; k++) {
      recXYZ2[k] -= recXYZ[k];
    }

    // System.out.println("[PV updateVolumeGridDefinition] Reciever Delta: " +
    // Arrays.toString(recXYZ2));

    for (int k = 0; k < inputAxisLengths.length; k++) {
      AxisDefinition inputAxis = inputGrid.getAxis(k);
      physicalOAxisArray[k] = new AxisDefinition(inputAxis.getLabel(), inputAxis.getUnits(), inputAxis.getDomain(),
          inputAxis.getLength(), inputAxis.getLogicalOrigin(), inputAxis.getLogicalDelta(),
          CalculateNewPhysicalOrigin(inputAxis, k, recXYZ), CalculateNewDeltaOrigin(inputAxis, k, recXYZ2));
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
  public boolean processVolume(IParallelContext pc, ToolState toolState, ISeismicVolume input, ISeismicVolume output) {
    // Get trace iterators for input and output

    output.copyVolume(input);
    output.setPropertyService(input.getPropertyService());

    GridDefinition inputGrid = updateVolumeGridDefinition(input, toolState);

    ITraceIterator iti = input.getTraceIterator();
    ITraceIterator oti = output.getTraceIterator();

    GridDefinition outputGrid = null;

    // Remove Eventually
    double traceAxisMin = (double) Double.MAX_VALUE - 1;
    double frameAxisMin = (double) Double.MAX_VALUE - 1;
    double traceDelta = (double) Double.MAX_VALUE - 1;
    double frameDelta = (double) Double.MAX_VALUE - 1;
    // End

    while (iti.hasNext()) {

      iti.next();

      // Clone the Trace
      float[] actualTrace = iti.getTrace().clone();

      int[] tracePos = iti.getPosition().clone();

      //System.out.println("Input Iterator Pos: " + Arrays.toString(tracePos));

      // Get rec position
      double[] srcXYZ = new double[3];
      double[] recXYZ = new double[3];
      input.getCoords(tracePos, srcXYZ, recXYZ);

      // System.out.println("Reciever Cords: " + Arrays.toString(recXYZ));

      int traceIndex = tracePos[1];
      int frameIndex = tracePos[2];

      double traceLen = inputGrid.getAxis(1).getLength() - 1;
      double frameLen = inputGrid.getAxis(2).getLength() - 1;

      double tracePhysicalOrigin = inputGrid.getAxis(1).getPhysicalOrigin();
      double framePhyscialOrigin = inputGrid.getAxis(2).getPhysicalOrigin();
      // Move Here
      // double traceDelta = inputGrid.getAxis(1).getPhysicalDelta();
      // double frameDelta = inputGrid.getAxis(2).getPhysicalDelta();
      traceDelta = inputGrid.getAxis(1).getPhysicalDelta();
      frameDelta = inputGrid.getAxis(2).getPhysicalDelta();
      // end
      double traceAxisMinAccum;
      double frameAxisMinAccum;

      if (traceDelta < 0) {
        traceAxisMinAccum = tracePhysicalOrigin + traceDelta * (traceLen - traceIndex);
        traceAxisMin = tracePhysicalOrigin + traceDelta * (traceLen - 0);
      } else {
        traceAxisMinAccum = tracePhysicalOrigin + traceDelta * traceIndex;
        traceAxisMin = tracePhysicalOrigin + traceDelta * traceIndex;
      }
      if (frameDelta < 0) {
        frameAxisMinAccum = framePhyscialOrigin + frameDelta * (frameLen - frameIndex);
        frameAxisMin = framePhyscialOrigin + frameDelta * (frameLen - 0);
      } else {
        frameAxisMinAccum = framePhyscialOrigin + frameDelta * frameIndex;
        frameAxisMin = framePhyscialOrigin + frameDelta * frameIndex;
      }

      //System.out.println("MinValue of Trace Axis: " + traceAxisMinAccum);
      //System.out.println("MinValue of Frame Axis: " + frameAxisMinAccum);

      Double ftIndex = Math.abs((tracePhysicalOrigin - traceAxisMinAccum) / (traceDelta));
      Double ffIndex = Math.abs((framePhyscialOrigin - frameAxisMinAccum) / (frameDelta));

      Integer ftIndexInt = (int) Math.round(ftIndex);
      Integer ffIndexInt = (int) Math.round(ffIndex);

      int[] finalPosition = new int[] { 0, ftIndexInt, ffIndexInt };
      // System.out.println("Volume Shape: " +
      // Arrays.toString(output.getVolumeShape()));
      // System.out.println("Itr Shape: " + Arrays.toString(oti.getShape()));
      //System.out.println("Final Trace Location: " + Arrays.toString(finalPosition));
      oti.next();
      oti.setPosition(finalPosition);
      oti.putTrace(actualTrace);

      // Set the actual Properties of the actual traces
      // IPropertyService propsInput = input.getPropertyService();
      // Assert.assertNotNull(propsInput);

      // IPropertyService propsOutput = output.getPropertyService();
      // Assert.assertNotNull(propsOutput);
      // propsInput.copyTrcProps(propsInput, tracePos);
      // propsOutput;
      // propsOutput.copyTrcProps(propsInput, tracePos, finalPosition);
      // output.setPropertyService(propsOutput);

      // System.out.println("Output Itr Pos: " +
      // Arrays.toString(oti.getPosition().clone()));

      // try { Thread.sleep(5000); } catch (InterruptedException e) { // TODO
      // Auto-generated catch block e.printStackTrace();
      // }

    }

    // System.out.println(Arrays.toString(output.getVolumeShape()));

    // GridDefinition outputGrid = updateVolumeGridDefinition(output,
    // toolState);
    double[] minPhys = new double[] { 0, traceAxisMin, frameAxisMin };
    double[] Delta = new double[] { 0.00001, traceDelta, frameDelta };

    outputGrid = generateOutputGrid(input, minPhys, Delta);
    Assert.assertNotNull(outputGrid);
    System.out.println(outputGrid.toString());

    toolState.getOutputState().gridDefinition = outputGrid;

    // TODO:SET TRUE
    return true;
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

  private static String[] listToArray(List<String> list) {
    String[] array = new String[list.size()];
    for (int k = 0; k < list.size(); k++) {
      array[k] = list.get(k);
    }
    return array;
  }

  private static ParameterService basicParameters() {
    String inputFileName = "seg45shot.js";
    // String outputFileName = "fishfish.js";
    ParameterService parms = null;
    try {
      parms = new FindTestData(inputFileName).getParameterService();
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

    String[] toolArray = listToArray(toolList);

    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      e.printStackTrace();
    }
  }
}

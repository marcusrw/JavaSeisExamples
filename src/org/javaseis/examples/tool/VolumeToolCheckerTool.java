package org.javaseis.examples.tool;

import java.awt.Color;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.javaseis.grid.GridDefinition;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.array.ITraceIterator;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.plot.PlotScatterPoints;
import beta.javaseis.plot.PointSet;
import edu.mines.jtk.mosaic.PointsView.Mark;
import edu.mines.jtk.util.Array;

public class VolumeToolCheckerTool implements IVolumeTool {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  @Override
  public void serialInit(ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub

  }

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub

  }

  private void checkSourceGrid(GridDefinition grid, double[] srcXYZ) {

    int[] Axis_Order = { 2, 1, 0 };

    boolean status = true;

    double checkNumber = Double.MAX_VALUE;
    for (int i = 1; i < grid.getNumDimensions(); i++) {
      // checkNumber = srcXYZ[];

      double srcIndex = (srcXYZ[Axis_Order[i]] - grid.getAxisPhysicalOrigin(i)) / grid.getAxisPhysicalDelta(i);

      if (srcIndex < 0 || srcIndex > grid.getAxisLength(i)) {
        throw new IllegalArgumentException("Array Index out of bounds." + srcIndex);
      }

    }
    // return status;
  }

  private boolean deepCompareArrays(double[] array1, double[] array2) {
    if (array1.length != array2.length) {
      throw new IllegalArgumentException("[deepCompareArrays]: Arrays Do Not Match");
    }

    for (int i = 0; i < array1.length; i++) {
      double epsilon = Math.abs(array1[i] - array2[i]);
      if (epsilon > 0.1) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check that the source does not change within a single volume
   * 
   * Done by iterating over all traces and checking the source via the
   * coordinate service
   * 
   * @param input
   *          ISeismicVolume input
   */
  public void checkSourceWithinVolume(ISeismicVolume input) {
    // Trace iterator for input
    ITraceIterator iti = input.getTraceIterator();

    double[] firstSrc = new double[3];
    double[] firstRec = new double[3];

    if (iti.hasNext()) {
      iti.next();

      int[] firstPosition = iti.getPosition().clone();

      input.getCoords(firstPosition, firstSrc, firstRec);
    }

    // Check that the source is the same for every trace
    while (iti.hasNext()) {
      iti.next();

      double[] newSRC = new double[3];
      double[] newREC = new double[3];

      int[] nextPosition = iti.getPosition().clone();

      input.getCoords(nextPosition, newSRC, newREC);

      if (!deepCompareArrays(firstSrc, newSRC)) {
        throw new IllegalArgumentException(
            "Source Pos Change Within Volume./n" + "/n" + "Position: " + Arrays.toString(nextPosition) + "/n"
                + "Expected: " + Arrays.toString(firstSrc) + " Got: " + Arrays.toString(newSRC));
      }
    }
  }

  /**
   * Checks the Coordinates received from the Input service against the
   * coordinates in the Input grid
   * 
   * @param recGrid
   *          GridDefinition receiverGrid
   * @param input
   *          ISeismicVolume input
   * @param axis_order
   *          The order that is expected of the input data
   */
  public void checkCoordRecGridRec(GridDefinition recGrid, ISeismicVolume input, int[] axis_order) {
    // Trace iterator for input
    ITraceIterator iti = input.getTraceIterator();

    while (iti.hasNext()) {
      iti.next();
      int[] pos = iti.getPosition().clone();

      double[] srcXYZ = new double[3];
      double[] recXYZ = new double[3];

      // Compute the recXYZ from the input coordinate service
      input.getCoords(pos, srcXYZ, recXYZ);

      // TODO: check that I am representing acis order right in correction
      // service

      // Compute the rec from the grid at this trace position
      double X = recGrid.getAxisPhysicalOrigin(axis_order[0])
          + recGrid.getAxisPhysicalDelta(axis_order[0]) * pos[axis_order[0]];
      double Y = recGrid.getAxisPhysicalOrigin(axis_order[1])
          + recGrid.getAxisPhysicalDelta(axis_order[1]) * pos[axis_order[1]];
      double Z = 0; // TODO: Not important for this check

      double[] gridRecXYZ = new double[] { X, Y, Z };

      // Compare newRecXYZ to recXYZ except for Z which is sample
      for (int i = 0; i < recXYZ.length - 1; i++) {
        if (Math.abs(gridRecXYZ[i] - recXYZ[i]) > 0.1) {
          throw new IllegalArgumentException("Rec Pos Change Within Volume./n" + "Position: " + Arrays.toString(pos)
              + "/n" + "Expected: " + Arrays.toString(gridRecXYZ) + " Got: " + Arrays.toString(recXYZ));
        }
      }
    }
  }

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolState, ISeismicVolume input, ISeismicVolume output)
      throws SeisException {
    GridDefinition grid = toolState.getInputState().gridDefinition;

    int[] position = new int[] { 0, 0, 0 };

    double[] srcXYZ = new double[3];
    double[] recXYZ = new double[3];

    input.getCoords(position, srcXYZ, recXYZ);

    float[] sx = new float[1];
    float[] sy = new float[1];
    float[] rx = new float[1];
    float[] ry = new float[1];

    sx[0] = (float) srcXYZ[0];
    sy[0] = (float) srcXYZ[1];
    rx[0] = (float) recXYZ[0];
    ry[0] = (float) recXYZ[1];

    PlotScatterPoints psp = new PlotScatterPoints("Source at " + Arrays.toString(srcXYZ), "X coord", "Y coord");

    PointSet pss = new PointSet(sx, sy, Mark.FILLED_SQUARE, 5f, Color.RED);
    PointSet psr = new PointSet(rx, ry, Mark.CROSS, 5f, Color.BLUE);
    psp.addPointSet(pss);
    psp.addPointSet(psr);
    psp.display();

    System.out.print(Arrays.toString(srcXYZ));

    checkSourceGrid(grid, srcXYZ);
    checkSourceWithinVolume(input);
    checkCoordRecGridRec(grid, input, new int[] { 2, 1, 0 });

    /*
     * while (true) {
     * 
     * }
     */

    return true;
  }

  @Override
  public boolean outputVolume(IParallelContext pc, ToolState toolState, ISeismicVolume output) throws SeisException {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void parallelFinish(IParallelContext pc, ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub

  }

  @Override
  public void serialFinish(ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub

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
    toolList.add(VolumeToolCheckerTool.class.getCanonicalName());

    String[] toolArray = listToArray(toolList);

    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      e.printStackTrace();
    }
  }

}
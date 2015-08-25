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

import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.plot.PlotScatterPoints;
import beta.javaseis.plot.PointSet;
import edu.mines.jtk.mosaic.PointsView.Mark;

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

  private boolean checkSource(GridDefinition grid, double[] srcXYZ) {

    int[] Axis_Order = {2,1,0};

    boolean status = true;

    double checkNumber = -999999;
    for (int i = 1; i < grid.getNumDimensions(); i++) {
      //checkNumber = srcXYZ[];

      double srcIndex = (srcXYZ[Axis_Order[i]] - grid.getAxisPhysicalOrigin(i))/grid.getAxisPhysicalDelta(i);

      if ( srcIndex < 0 || srcIndex > grid.getAxisLength(i)){
        throw new IllegalArgumentException("Array Index out of bounds." + srcIndex);
        //return false;
      }

    }
    return status;
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
    PointSet psr = new PointSet( rx, ry, Mark.CROSS, 5f, Color.BLUE  );
    psp.addPointSet(pss);
    psp.addPointSet(psr);
    psp.display();

    System.out.print(Arrays.toString(srcXYZ));

    checkSource(grid, srcXYZ);

    /*
    while (true) {

    }
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
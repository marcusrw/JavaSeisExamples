package org.javaseis.examples.tool;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.javaseis.examples.scratch.ExampleImageOutput;
import org.javaseis.grid.GridDefinition;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.IntervalTimer;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.parallel.IParallelContext;

public class ExtractPWaveData implements IVolumeTool {

  int volumeCount;
  IParallelContext pc;
  IntervalTimer compTime, totalTime;

  private int componentAxis;
  private int pwaveComponentNumber;

  private static String[] listToArray(List<String> list) {
    String[] array = new String[list.size()];
    for (int k = 0; k < list.size(); k++) {
      array[k] = list.get(k);
    }
    return array;
  }

  private static ParameterService basicParameters() {
    String inputFilePath = "100-rawsyntheticdata.js";
    ParameterService parms = null;
    try {
      parms = new FindTestData(inputFilePath).getParameterService();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    parms.setParameter(ToolState.TASK_COUNT, "1");
    return parms;
  }

  public static void main(String[] args) throws FileNotFoundException {

    ParameterService parms = basicParameters();

    List<String> toolList = new ArrayList<String>();

    toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
    toolList.add(ExtractPWaveData.class.getCanonicalName());
    // toolList.add(ExampleVolumeOutputTool.class.getCanonicalName());

    String[] toolArray = listToArray(toolList);

    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      e.printStackTrace();
    }

  }

  private int determinePWaveHeaderValue(ToolState toolContext) {
    // TODO figure out a nice way to do this in general (ask the user probably)
    return 3;
  }

  private void findAndRemoveComponentAxisFromGrid(ToolState toolContext) {

    componentAxis = findComponentAxis(toolContext);

    if (dataIsMulticomponent(toolContext) && componentAxis > 2) {
      System.out.println("Input Data is multicomponent.  The component axis is #" + (componentAxis + 1));
      removeComponentAxisFromOutputGrid(toolContext);
    } else {
      if (componentAxis == -1) {
        System.out.println("Input Data is not multicomponent");
      } else {
        System.out.println("Input Data is multicomponent, " + "but the components differ within volumes");
      }
      // TODO: Don't know if works
      toolContext.getOutputState().gridDefinition = new GridDefinition(toolContext.getOutputState().gridDefinition);
    }
  }

  private int findComponentAxis(ToolState toolContext) {

    String[] AxisLabels = toolContext.getInputState().gridDefinition.getAxisLabelsStrings();
    int componentAxis = -1;
    for (int axis = 0; axis < AxisLabels.length; axis++) {
      if (AxisLabels[axis].equals("GEO_COMP")) {
        return axis;
      }
    }
    return componentAxis;
  }

  private boolean dataIsMulticomponent(ToolState toolContext) {
    return (findComponentAxis(toolContext) != -1);
  }

  private void removeComponentAxisFromOutputGrid(ToolState toolContext) {
    int componentAxis = findComponentAxis(toolContext);

    GridDefinition inputGrid = toolContext.getInputState().gridDefinition;
    int outputNumDimensions = toolContext.getInputState().gridDefinition.getNumDimensions() - 1;

    AxisDefinition[] outputAxes = new AxisDefinition[outputNumDimensions];
    for (int dim = 0; dim < outputNumDimensions; dim++) {
      outputAxes[dim] = determineOutputAxis(inputGrid, dim, componentAxis);
    }

    // TODO: Don't know if works
    toolContext.getOutputState().gridDefinition = new GridDefinition(outputAxes.length, outputAxes);
  }

  private AxisDefinition determineOutputAxis(GridDefinition inputGrid, int dim, int axisToRemove) {

    if (dim < axisToRemove)
      return inputGrid.getAxis(dim);
    else
      return inputGrid.getAxis(dim + 1);
  }

  @Override
  public void serialInit(ToolState toolState) throws SeisException {
    findAndRemoveComponentAxisFromGrid(toolState);
    pwaveComponentNumber = determinePWaveHeaderValue(toolState);
  }

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState) throws SeisException {
    volumeCount = 0;
    this.pc = pc;
    this.pc.masterPrint("Input Grid Definition:\n" + toolState.getInputState().gridDefinition);
    this.pc.masterPrint("Output Grid Definition:\n" + toolState.getOutputState().gridDefinition);
    compTime = new IntervalTimer();
    totalTime = new IntervalTimer();
    totalTime.start();
  }

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolState, ISeismicVolume input, ISeismicVolume output)
      throws SeisException {
    compTime.start();
    /*
     * { //TODO extract these musings into a coherent set of tests
     * 
     * //Get the global grid position of the position {0,0,0} in the local grid
     * double[] volumePosition = new
     * double[toolContext.inputGrid.getNumDimensions()]; int[] pos = new int[]
     * {0,0,0}; input.worldCoords(pos, volumePosition); //returns 5 zeros for
     * every volume System.out.println(input.getNumDimensions());
     * System.out.println(Arrays.toString(volumePosition)); //This returns
     * {0,0,0,0,0}, but I would expect it to return (for example) // {0,0,0,2,3}
     * for the 15th volume from a global grid of size {t,x,y,4,4}
     * 
     * //Get some information about the input and output global grids.
     * GridDefinition inputGlobalGrid = input.getGlobalGrid();
     * System.out.println(inputGlobalGrid.getNumDimensions()); //looks right
     * System.out.println(Arrays.toString(inputGlobalGrid.getAxisLengths()));
     * //looks right
     * 
     * GridDefinition outputGlobalGrid = output.getGlobalGrid();
     * System.out.println(outputGlobalGrid.getNumDimensions()); //looks right
     * System.out.println(Arrays.toString(outputGlobalGrid.getAxisLengths()));
     * //looks right
     * 
     * int[] position = new int[] {43,2,4,2,3}; //This shouldn't always be true
     * System.out.println(input.isPositionLocal(position)); //This should return
     * true exactly when we're looking at the volume //corresponding to the
     * volume from {t,x,y,2,3}, and false otherwise.
     * 
     * System.out.println(output.isPositionLocal(position)); //I would expect
     * this to fail because the position is not the right size for the //output
     * grid. }
     */

    // TODO
    // Idea: copy every volume where the GEO_COMP index is equal to
    // pwaveComponentNumber-1.
    // It would be better if I could figure out the 4th index,
    // then use that to compute the value of GEO_COMP using
    // origin + index*delta, but I can't make that work right now,
    // so I'm just going to use the counter
    long numComponents = input.getGlobalGrid().getAxisLengths()[componentAxis];
    if (volumeCount % numComponents == pwaveComponentNumber - 1) {
      System.out.println("Saving P-waves from volume " + volumeCount++);
      output.copyVolume(input);
      compTime.stop();
      return true;
    } else {
      System.out.println("Trashing S-waves from volume " + volumeCount++);
      compTime.stop();
      return false;
    }
  }

  @Override
  public boolean outputVolume(IParallelContext pc, ToolState toolState, ISeismicVolume output) throws SeisException {
    return false;
  }

  @Override
  public void parallelFinish(IParallelContext pc, ToolState toolState) throws SeisException {
    totalTime.stop();
    pc.masterPrint("Computation Time: " + compTime.total() + "\nTotal Time: " + totalTime.total());
  }

  @Override
  public void serialFinish(ToolState toolState) throws SeisException {

  }
}

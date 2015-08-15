package org.javaseis.examples.tool;

import java.util.Arrays;

import org.javaseis.grid.GridDefinition;
import org.javaseis.services.ParameterService;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.parallel.ICollective.Operation;
import beta.javaseis.parallel.IParallelContext;

public class ExampleVolumeInspectorTool implements IVolumeTool {
  private static final long serialVersionUID = 1L;
  private double[] smin, smax, rmin, rmax;
  GridDefinition gridDefinition;

  @Override
  public void serialInit(ToolState ts) throws SeisException {
    ts.print(ts.toString());  }

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState) throws SeisException {
    toolState.print(toolState.toString());
    gridDefinition = toolState.getInputState().gridDefinition;
    smin = new double[3];
    Arrays.fill(smin, Double.MAX_VALUE);
    rmin = smin.clone();
    smax = new double[3];
    Arrays.fill(smax, -Double.MAX_VALUE);
    rmax = smax.clone();
  }

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolState, ISeismicVolume input, ISeismicVolume output)
      throws SeisException {
    int[] filePosition = toolState.getInputPosition();
    pc.masterPrint("\nCoordinate Ranges for volume at file position: " + Arrays.toString(filePosition) + 
                   "\n                     grid position: " + Arrays.toString(gridDefinition.indexToGrid(filePosition)));
    double[] sxyz = new double[3];
    double[] rxyz = new double[3];
    double[] sxyzMin = new double[3];
    Arrays.fill(sxyzMin,Double.MAX_VALUE);
    double[] sxyzMax = new double[3];
    Arrays.fill(sxyzMax,-Double.MAX_VALUE);
    double[] rxyzMin = new double[3];
    Arrays.fill(sxyzMin,Double.MAX_VALUE);
    double[] rxyzMax = new double[3];
    Arrays.fill(sxyzMax,-Double.MAX_VALUE);
    for (int[] pos : input) {
      if (input.isLive(pos)) {
      input.getCoords(pos, sxyz, rxyz );
      for (int i=0; i<3; i++) {
        sxyzMin[i] = Math.min(sxyz[i], sxyzMin[i]);
        sxyzMax[i] = Math.max(sxyz[i], sxyzMax[i]);
        rxyzMin[i] = Math.min(rxyz[i], rxyzMin[i]);
        rxyzMax[i] = Math.max(rxyz[i], rxyzMax[i]);
      }
      }
    }
    pc.serialPrint("  Local Minimum Values in volume:\n" + 
        "  Source XYZ: " + Arrays.toString(sxyzMin) + "  Receiver XYZ: " + Arrays.toString(rxyzMin));
    pc.serialPrint("  Local Maximum Values in volume:\n" + 
        "  Source XYZ: " + Arrays.toString(sxyzMax) + "  Receiver XYZ: " + Arrays.toString(rxyzMax));
    pc.reduceDouble(sxyzMin, 0, sxyzMin, 0, 3, Operation.MIN);
    pc.reduceDouble(sxyzMin, 0, sxyzMin, 0, 3, Operation.MIN);

    pc.serialPrint("Global Minimum Values in volume:\n" + 
        "  Source XYZ: " + Arrays.toString(sxyzMin) + "  Receiver XYZ: " + Arrays.toString(rxyzMin));
    pc.serialPrint("Global Maximum Values in volume:\n" + 
        "  Source XYZ: " + Arrays.toString(sxyzMax) + "  Receiver XYZ: " + Arrays.toString(rxyzMax));
    if (pc.isMaster()) {
      for (int i=0; i<3; i++) {
        smin[i] = Math.min(smin[i], sxyzMin[i]);
        smax[i] = Math.max(smax[i], sxyzMax[i]);
        rmin[i] = Math.min(rmin[i], rxyzMin[i]);
        rmax[i] = Math.max(rmax[i], rxyzMax[i]);
      }
    }
    output.copyVolume(input);
    return true;
  }

  @Override
  public boolean outputVolume(IParallelContext pc, ToolState toolState, ISeismicVolume output) throws SeisException {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void parallelFinish(IParallelContext pc, ToolState toolState) throws SeisException {
    toolState.print("\nGlobal Minimum Values in flow:\n" + 
        "  Source XYZ: " + Arrays.toString(smin) + "  Receiver XYZ: " + Arrays.toString(rmin) +
        "\nGlobal Maximum Values in flow:\n" + 
        "  Source XYZ: " + Arrays.toString(smax) + "  Receiver XYZ: " + Arrays.toString(rmax));
  }

  @Override
  public void serialFinish(ToolState toolState) throws SeisException {
  }  

  public static void main(String[] args) {
    String[] toolList = new String[2];
    toolList[0] = ExampleVolumeInputTool.class.getCanonicalName();
    toolList[1] = ExampleVolumeInspectorTool.class.getCanonicalName();
    ParameterService parms = new ParameterService(args);
    if (parms.getParameter(ToolState.INPUT_FILE_SYSTEM) == "null") {
      parms.setParameter(ToolState.INPUT_FILE_SYSTEM, "/Data/Projects/SEG-ACTI");
      //parms.setParameter(ToolState.INPUT_FILE_SYSTEM, System.getProperty("java.io.tmpdir"));
    }
    if (parms.getParameter(ToolState.INPUT_FILE_NAME) == "null") {
      parms.setParameter(ToolState.INPUT_FILE_NAME, "SegActiShotNo1.js");
      //parms.setParameter(ToolState.INPUT_FILE_NAME, "temp.js");
    }
    parms.setParameter(ToolState.TASK_COUNT, "2");
    try {
      VolumeToolRunner.exec(parms, toolList);
    } catch (SeisException e) {
      e.printStackTrace();
    }
  }

}

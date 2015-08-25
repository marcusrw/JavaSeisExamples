package org.javaseis.grid;

import org.javaseis.grid.GridDefinition;
import org.javaseis.tool.ToolState;
import org.javaseis.volume.ISeismicVolume;

public class ManualOverrideGrid implements ICheckedGrid {

  final ISeismicVolume input;
  final ToolState toolContext;

  //this is arbitrary and irrelevant
  int[] axisOrder = new int[] {2,1,0};
  double[][] sourceLocations = new double[][] 
      {
      {290,290,0},
      {690,290,0},
      {290,690,0},
      {690,690,0}
      };

  public ManualOverrideGrid(ISeismicVolume input,ToolState toolContext) {
    this.input = input;
    this.toolContext = toolContext;
  }

  @Override
  public GridDefinition getModifiedGrid() {
    return input.getGlobalGrid();
  }

  @Override
  public double[] getSourceXYZ() {
    //int[] volPos = input.getVolumePosition();
    //return sourceLocations[volPos[3]];
    int [] sourceOnGrid = new int[] {0, 0, 0,};
    double [] sxyz = new double[3];
    double [] rxyz = new double[3];
    input.getCoords(sourceOnGrid, sxyz, rxyz);
    return sxyz;
  }

  @Override
  public double[] getSourceXYZ(int[] gridPos) {
    int volumeNumber = gridPos[3];
    return sourceLocations[volumeNumber];
  }

  @Override
  public double[] getReceiverXYZ(int[] gridPos) {
    return new double[] {gridPos[axisOrder[0]]*20,
        gridPos[axisOrder[1]]*20,
        0};
  }

  @Override
  public int[] getAxisOrder() {
    return axisOrder;
  }

}

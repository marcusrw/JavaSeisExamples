package org.javaseis.grid;

import org.javaseis.grid.GridDefinition;
import org.javaseis.tool.ToolContext;
import org.javaseis.volume.ISeismicVolume;

public class ManualOverrideGrid implements ICheckedGrid {

  final ISeismicVolume input;
  final ToolContext toolContext;

  //this is arbitrary and irrelevant
  int[] axisOrder = new int[] {2,1,0};
  double[][] sourceLocations = new double[][] 
      {
      {290,290,0},
      {690,290,0},
      {290,690,0},
      {690,690,0}
      };

  public ManualOverrideGrid(ISeismicVolume input,ToolContext toolContext) {
    this.input = input;
    this.toolContext = toolContext;
  }

  @Override
  public GridDefinition getModifiedGrid() {
    return input.getGlobalGrid();
  }

  @Override
  public double[] getSourceXYZ() {
    int[] volPos = input.getVolumePosition();
    return sourceLocations[volPos[3]];
  }

  @Override
  public double[] getSourceXYZ(int[] gridPos) {
    int volumeNumber = gridPos[3];
    return sourceLocations[volumeNumber];
  }

  @Override
  public double[] getReceiverXYZ(int[] gridPos) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public int[] getAxisOrder() {
    return axisOrder;
  }

}

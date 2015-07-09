package org.javaseis.volume;

import org.javaseis.array.ElementType;
import org.javaseis.grid.BinGrid;
import org.javaseis.grid.GridDefinition;
import org.javaseis.properties.AxisDefinition;

import beta.javaseis.distributed.Decomposition;
import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.regulargrid.IRegularGrid;
import beta.javaseis.regulargrid.OrientationType;
import beta.javaseis.regulargrid.RegularGrid;

public class SeismicVolume implements ISeismicVolume {

  private static final int VOLUME_NUM_DIMENSIONS = 3;

  GridDefinition globalGrid, localGrid;

  //TODO  This calls for a rename.  volumeGrid and localGrid sound like they
  //      describe the same thing.  I think the only reason this guy is here
  //      is so you can call his methods (since ISeismicVolume extends
  //      IRegularGrid.  There should be a better way to do this.
  IRegularGrid volumeGrid;

  BinGrid binGrid;

  DistributedArray volume;

  ElementType elementType;

  int elementCount;

  int decompType;

  int[] volumeShape;

  //TODO  I hate the way this is implemented.  I want this to be final
  //      and declared during construction.  Does that mean I need a second
  //      copy of each constructor that takes it as an argument?
  int[] globalVolumePosition;

  IParallelContext pc;

  public SeismicVolume(IParallelContext parallelContext,
      GridDefinition globalGridDefinition) {

    setParallelContextAndGlobalGrid(parallelContext, globalGridDefinition);
    setLocalGridAndVolume();
    binGrid = createDefaultBinGrid();
    volumeGrid = new RegularGrid(volume,localGrid,binGrid);
    elementType = ElementType.FLOAT;
    elementCount = 1;
    decompType = Decomposition.BLOCK;
  }

  private BinGrid createDefaultBinGrid() {
    int[] volumeShape = volume.getShape();
    return BinGrid.simpleBinGrid(volumeShape[1], volumeShape[2]);
  }

  public SeismicVolume(IParallelContext parallelContext,
      GridDefinition globalGridDefinition,
      BinGrid binGridIn) {

    setParallelContextAndGlobalGrid(parallelContext, globalGridDefinition);
    setLocalGridAndVolume();
    binGrid = binGridIn;
    volumeGrid = new RegularGrid(volume,localGrid,binGrid);
    elementType = ElementType.FLOAT;
    elementCount = 1;
    decompType = Decomposition.BLOCK;
  }

  public SeismicVolume(IParallelContext parallelContext,
      GridDefinition globalGridDefinition,
      BinGrid binGridIn,
      ElementType volumeElementType,
      int volumeElementCount, int volumeDecompType ) {

    setParallelContextAndGlobalGrid(parallelContext, globalGridDefinition);
    setLocalGridAndVolume();
    binGrid = binGridIn;
    volumeGrid = new RegularGrid(volume,localGrid,binGrid);
    elementType = volumeElementType;
    elementCount = volumeElementCount;
    decompType = volumeDecompType;
  }

  private void setParallelContextAndGlobalGrid(
      IParallelContext parallelContext, GridDefinition globalGridDefinition) {
    pc = parallelContext;
    globalGrid = globalGridDefinition;
  }

  private void setLocalGridAndVolume() {
    AxisDefinition[] axis = new AxisDefinition[VOLUME_NUM_DIMENSIONS];
    int[] volumeShape = new int[VOLUME_NUM_DIMENSIONS];
    for (int i = 0; i < VOLUME_NUM_DIMENSIONS; i++) {
      axis[i] = globalGrid.getAxis(i);
      volumeShape[i] = (int) axis[i].getLength();
    }
    localGrid = new GridDefinition(axis.length, axis);
    volume = new DistributedArray(pc, volumeShape);
  }

  public void allocate(long maxLength) {
    volume = new DistributedArray(pc, elementType.getClass(),
        VOLUME_NUM_DIMENSIONS, elementCount, volumeShape, decompType,maxLength);
    volume.allocate();
  }

  @Override
  public long shapeLength() {
    long length = elementCount;
    for (int i=0; i<VOLUME_NUM_DIMENSIONS; i++) {
      length *= volumeShape[i];
    }
    return length;
  }

  @Override
  public DistributedArray getDistributedArray() {
    return volume;
  }

  @Override
  public OrientationType getOrientation() {
    return volumeGrid.getOrientation();
  }

  @Override
  public int getNumDimensions() {
    return volumeGrid.getNumDimensions();
  }

  @Override
  public int[] getLengths() {
    return volumeGrid.getLengths();
  }

  @Override
  public int[] getLocalLengths() {
    return volumeGrid.getLocalLengths();
  }

  @Override
  public double[] getDeltas() {
    return volumeGrid.getDeltas();
  }
  
  /**
   * @return The index of the first position of the local volume
   * within the larger globalGrid.
   */
  @Override
  public int[] getVolumePosition() {
    return globalVolumePosition;
  }
  
  public void setVolumePosition(int[] globalVolumePosition) {
    this.globalVolumePosition = globalVolumePosition;
  }

  @Override
  public boolean isPositionLocal(int[] position) {
    return volumeGrid.isPositionLocal(position);
  }

  @Override
  public float getSample(int[] position) {
    return volumeGrid.getSample(position);
  }

  @Override
  public float getFloat(int[] position) {
    return volumeGrid.getFloat(position);
  }

  @Override
  public int getInt(int[] position) {
    return volumeGrid.getInt(position);
  }

  @Override
  public double getDouble(int[] position) {
    return volumeGrid.getDouble(position);
  }

  @Override
  public void putSample(float val, int[] position) {
    volumeGrid.putSample(val, position);
  }

  @Override
  public void putSample(double val, int[] position) {
    volumeGrid.putSample(val, position);
  }

  @Override
  public int localToGlobal(int dimension, int index) {
    return volumeGrid.localToGlobal(dimension, index);
  }

  @Override
  public int globalToLocal(int dimension, int index) {
    return volumeGrid.globalToLocal(dimension, index);
  }

  @Override
  public int[] localPosition(int[] pos) {
    return volumeGrid.localPosition(pos);
  }

  @Override
  public void worldCoords(int[] pos, double[] wxyz) {
    volumeGrid.worldCoords(pos, wxyz);
  }

  @Override
  public IRegularGrid createCopy() {
    return volumeGrid.createCopy();
  }

  @Override
  public void copyVolume(ISeismicVolume source) {
    if (!localGrid.matches(source.getLocalGrid()))
      throw new IllegalArgumentException(
          "Source volume and this volume do not match");
    this.getDistributedArray().copy(source.getDistributedArray());
  }

  @Override
  public GridDefinition getGlobalGrid() {
    return globalGrid;
  }

  @Override
  public GridDefinition getLocalGrid() {
    return localGrid;
  } 

  @Override
  public boolean matches(ISeismicVolume seismicVolume) {
    return globalGrid.matches(seismicVolume.getGlobalGrid());
  }

  @Override
  public int getElementCount() {
    return elementCount;
  }

  @Override
  public ElementType getElementType() {
    return elementType;
  }
}

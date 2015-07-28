package org.javaseis.volume;

import org.javaseis.array.ElementType;
import org.javaseis.grid.BinGrid;
import org.javaseis.grid.GridDefinition;
import org.javaseis.properties.AxisDefinition;
import org.junit.Assert;

import beta.javaseis.array.ITraceIterator;
import beta.javaseis.distributed.Decomposition;
import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.regulargrid.IRegularGrid;
import beta.javaseis.regulargrid.OrientationType;
import beta.javaseis.regulargrid.RegularGrid;

public class SeismicVolume implements ISeismicVolume {

  private static final int VOLUME_NUM_DIMENSIONS = 3;

  GridDefinition globalGridDef, localGridDef;

  //TODO does it make sense for a class to contain AND extend another class?
  IRegularGrid volumeGrid;

  BinGrid binGrid;

  DistributedArray volume;

  ElementType elementType;

  int elementCount;

  int decompType;

  int[] volumeShape;

  //TODO  I don't like the way this is implemented.  I want this to be final
  //      and declared during construction.  Does that mean I need a second
  //      copy of each constructor that takes it as an argument? - Marcus
  int[] globalVolumePosition;

  IParallelContext pc;

  public SeismicVolume(IParallelContext parallelContext, GridDefinition globalGridDefinition) {
    this(parallelContext, globalGridDefinition, BinGrid.simpleBinGrid(
        (int) globalGridDefinition.getAxisLength(1), (int) globalGridDefinition.getAxisLength(1)),
        ElementType.FLOAT, 1, Decomposition.BLOCK, 0);
  }

  public SeismicVolume(IParallelContext parallelContext, GridDefinition globalGridDefinition, long maxlength) {
    this(parallelContext, globalGridDefinition, BinGrid.simpleBinGrid(
        (int) globalGridDefinition.getAxisLength(1), (int) globalGridDefinition.getAxisLength(1)),
        ElementType.FLOAT, 1, Decomposition.BLOCK, maxlength);
  }

  public SeismicVolume(IParallelContext parallelContext,
      GridDefinition globalGridDefinition,
      BinGrid binGridIn) {
    this(parallelContext, globalGridDefinition, binGridIn, ElementType.FLOAT, 1, Decomposition.BLOCK, 0);
  }

  public SeismicVolume(IParallelContext parallelContext, GridDefinition globalGridDefinition,
      BinGrid binGridIn, long maxlength) {
    this(parallelContext, globalGridDefinition, binGridIn, ElementType.FLOAT, 1, Decomposition.BLOCK,
        maxlength);
  }

  public SeismicVolume(IParallelContext parallelContext, GridDefinition globalGridDefinition,
      BinGrid binGridIn, ElementType volumeElementType, int volumeElementCount, int volumeDecompType,
      long maxLength) {
    pc = parallelContext;
    AxisDefinition[] axis = new AxisDefinition[3];
    volumeShape = new int[3];
    long length = 1;
    for (int i = 0; i < VOLUME_NUM_DIMENSIONS; i++) {
      axis[i] = globalGridDefinition.getAxis(i);
      volumeShape[i] = (int) axis[i].getLength();
      length *= volumeShape[i];
    }
    maxLength = Math.max(maxLength, length);
    globalGridDef = globalGridDefinition;
    localGridDef = new GridDefinition(3, axis);
    binGrid = binGridIn;
    elementType = volumeElementType;
    elementCount = volumeElementCount;
    decompType = volumeDecompType;
    allocate(maxLength);
    volumeGrid = new RegularGrid(volume,localGridDef,binGrid);
  }

  @Override
  public void allocate(long maxLength) {
    //TODO this float.class elementType should be reconciled with
    // the element types above.
    volume = new DistributedArray(pc, float.class, VOLUME_NUM_DIMENSIONS,
        elementCount,volumeShape, decompType, maxLength);
    volume.allocate();
    //volumeGrid = new RegularGrid(volume);
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
    return globalVolumePosition.clone();
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
    if (!localGridDef.matches(source.getLocalGrid()))
      throw new IllegalArgumentException(
          "Source volume and this volume do not match");
    this.getDistributedArray().copy(source.getDistributedArray());
  }

  @Override
  public GridDefinition getGlobalGrid() {
    return globalGridDef;
  }

  @Override
  public GridDefinition getLocalGrid() {
    return localGridDef;
  } 

  @Override
  public boolean matches(ISeismicVolume seismicVolume) {
    return globalGridDef.matches(seismicVolume.getGlobalGrid());
  }

  @Override
  public int getElementCount() {
    return elementCount;
  }

  @Override
  public ElementType getElementType() {
    return elementType;
  }

  @Override
  public ITraceIterator getTraceIterator() {
    return volumeGrid.getTraceIterator();
  }
}

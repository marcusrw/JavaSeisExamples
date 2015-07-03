package org.javaseis.volume.test;

import org.javaseis.array.ElementType;
import org.javaseis.grid.BinGrid;
import org.javaseis.grid.GridDefinition;

import beta.javaseis.distributed.Decomposition;
import beta.javaseis.distributed.DecompositionType;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;

import org.javaseis.test.testdata.ExampleRandomDataset;
import org.javaseis.util.SeisException;
import org.javaseis.volume.SeismicVolume;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.logging.Level;
import java.util.logging.Logger;


public class JTestSeismicVolume {
  
  public static final Logger LOGGER =
      Logger.getLogger(JTestSeismicVolume.class.getName());
  
  //Some input parameters
  static IParallelContext pc;
  static ExampleRandomDataset randomInput;
  static ExampleRandomDataset exampleOutput;
  static GridDefinition exampleGrid;
  static BinGrid binGrid;

  final ElementType FLOAT_TYPE = ElementType.FLOAT;
  final ElementType DOUBLE_TYPE = ElementType.DOUBLE;
  final int REAL_NUMBERS = 1;
  final int COMPLEX_NUMBERS = 2;
  final int BLOCK_DECOMP = Decomposition.BLOCK;
  final int CIRC_DECOMP = Decomposition.CIRCULAR;
  
  @BeforeClass
  //Create input and output datasets that have different gridDefinitions
  public static void setDefaultParameters() {
    pc = new UniprocessorContext();
    randomInput = new ExampleRandomDataset();
    exampleGrid = randomInput.gridDefinition;
    binGrid = createDefaultBinGrid(exampleGrid.getAxisLengths());
  }

  private static BinGrid createDefaultBinGrid(long[] volumeShape) {
    return BinGrid.simpleBinGrid((int)volumeShape[1],(int)volumeShape[2]);
  }

  @AfterClass
  public static void deleteTestDataset() {
    try {
      randomInput.deleteJavaSeisData();
    } catch (SeisException e) {
      System.out.println("Unable to delete temporary data at " + randomInput.dataFullPath);
      e.printStackTrace();
    }
  }
  
  @Test
  public void testSimplestConstructorSucceeds() {
    new SeismicVolume(pc,exampleGrid);    
  }
  
  @Test
  public void testSimplestConstructorSetsVolume() {
    SeismicVolume simplestVolume = new SeismicVolume(pc,exampleGrid);
    try {
      simplestVolume.getDistributedArray();
    } catch (NullPointerException e) {
      LOGGER.log(Level.INFO,e.getMessage(),e);
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testMiddleConstructorSucceeds() {
    new SeismicVolume(pc,exampleGrid,binGrid);    
  }
  
  @Test
  public void testMiddleConstructorSetsVolume() {
    SeismicVolume middleVolume = new SeismicVolume(pc,exampleGrid,binGrid);
    try {
      middleVolume.getDistributedArray();
    } catch (NullPointerException e) {
      LOGGER.log(Level.INFO,e.getMessage(),e);
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testMostDetailedConstructorSucceeds() {
    new SeismicVolume(pc,
        exampleGrid,
        binGrid,
        FLOAT_TYPE,
        REAL_NUMBERS,
        BLOCK_DECOMP);
  }
 
  @Test
  public void testMostDetailedConstructorWithOtherInputs() {
    new SeismicVolume(pc,
        exampleGrid,
        binGrid,
        DOUBLE_TYPE,
        COMPLEX_NUMBERS,
        CIRC_DECOMP);
  }
}

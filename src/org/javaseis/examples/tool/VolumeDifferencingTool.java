package org.javaseis.examples.tool;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import org.javaseis.services.ParameterService;
import org.javaseis.tool.DataState;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.array.ITraceIterator;
import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.distributed.FileSystemIOService;
import beta.javaseis.distributed.IDistributedIOService;
import beta.javaseis.parallel.ICollective.Operation;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.ReduceScalar;
import beta.javaseis.parallel.UniprocessorContext;
import beta.javaseis.services.VolumePropertyService;
import edu.mines.jtk.util.ArrayMath;

/**
 * Example Volume Input Tool
 * <p>
 * Parameters:
 * <P>
 * inputFileSystem - input file system where datatsets are stored
 * <p>
 * inputFileName - file name to be opened and read
 *
 */
public class VolumeDifferencingTool implements IVolumeTool {
  private static final long serialVersionUID = 1L;
  private static final String COMPARE_FILE_NAME = "compareFileName";
  private static final int SAMPLE_SCOPE = 0;
  IDistributedIOService comparePIO;
  VolumePropertyService vps;
  boolean usesProperties;
  String inputFileSystem, inputFileName, compareFileName;

  @Override
  public void serialInit(ToolState toolState) throws SeisException {
    inputFileSystem = toolState.getParameter(ToolState.INPUT_FILE_SYSTEM);
    toolState.log("Input file system: " + inputFileSystem);
    //inputFileName = toolState.getParameter(ToolState.INPUT_FILE_NAME);
    compareFileName = toolState.getParameter(COMPARE_FILE_NAME);
    IParallelContext upc = new UniprocessorContext();

    comparePIO = new FileSystemIOService(upc, inputFileSystem);
    toolState.log("Input file #2: " + compareFileName);
    comparePIO = new FileSystemIOService(upc, inputFileSystem);
    comparePIO.open(compareFileName);
    toolState.log("Opened file #2 in serial mode");
    comparePIO.close();    
  }

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState) throws SeisException {
    comparePIO = new FileSystemIOService(pc, inputFileSystem);
    compareFileName = toolState.getParameter(COMPARE_FILE_NAME);
    comparePIO.open(compareFileName);
    ISeismicVolume outputVolume = (ISeismicVolume) toolState.getObject(ToolState.OUTPUT_VOLUME);
    comparePIO.setSeismicVolume(outputVolume);
    comparePIO.reset();
    if (comparePIO.usesProperties()) {
      vps = (VolumePropertyService) (comparePIO.getPropertyService());
      pc.masterPrint("\n" + vps.listProperties() + "\n");
    }
    pc.serialPrint("Re-opened file in parallel mode");
  }

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolState,
      ISeismicVolume input,ISeismicVolume output) throws SeisException {

    System.out.println("Calling processVolume in " + ExampleVolumeInputTool.class.getName());
    System.out.println("Returning the difference between volume #1 and volume #2");

    if (comparePIO.hasNext() == false) {
      toolState.log("Read complete");
      comparePIO.close();
      return false;
    }
    pc.serialPrint("Read next volume ...");
    comparePIO.next();
    comparePIO.read();
    pc.serialPrint("Read complete for position " + Arrays.toString(comparePIO.getFilePosition()));

    //String inputFileName = toolState.getParameter(ToolState.INPUT_FILE_NAME);
    //String compareFileName = toolState.getParameter(COMPARE_FILE_NAME);

    //DistributedArrayMosaicPlot.showAsModalDialog(
    //    input.getDistributedArray(),inputFileName);
    //DistributedArrayMosaicPlot.showAsModalDialog(
    //    output.getDistributedArray(),compareFileName);

    int direction = DistributedArrayPositionIterator.FORWARD;
    int scope = SAMPLE_SCOPE;
    DistributedArrayPositionIterator dapi =
        new DistributedArrayPositionIterator(output.getDistributedArray(),
            direction, scope);

    //TODO, before checking the actual data, do some checks to make sure
    //      the volumes are indeed meaningfully comparable
    //      i.e.  sizes are conformable, same axes, etc etc.
    DistributedArray inputDA = input.getDistributedArray();
    DistributedArray outputDA = output.getDistributedArray();

    float maxDifference = 0;
    int[] position;
    int elementCount = inputDA.getElementCount();
    float[] sampleIn = new float[elementCount];
    float[] sampleOut = new float[elementCount];
    while (dapi.hasNext()) {
      position = dapi.next();
      inputDA.getSample(sampleIn,position);
      outputDA.getSample(sampleOut,position);
      sampleOut = arraySubtract(sampleIn,sampleOut);
      outputDA.putSample(sampleOut, position);
      maxDifference = Math.max(maxDifference,sampleOut[0]);
    }

    System.out.println("Max Difference: " + maxDifference);

    return true;
  }

  private float[] arraySubtract(float[] array1, float[] array2) {
    assert array1.length == array2.length;
    float[] result = new float[array1.length];
    for (int k = 0 ; k < array1.length ; k++) {
      result[k] = array1[k] - array2[k];
    }
    return result;
  }

  @Override
  public boolean outputVolume(IParallelContext pc, ToolState toolContext, ISeismicVolume output)
      throws SeisException {
    return false;
  }

  @Override
  public void parallelFinish(IParallelContext pc, ToolState toolContext) throws SeisException {
    //
  }

  @Override
  public void serialFinish(ToolState toolContext) {
    // Nothing to clean up in serial mode
  }

  private void writeObject(ObjectOutputStream oos) throws IOException {
    // default serialization
    oos.writeObject(inputFileSystem);
    oos.writeObject(inputFileName);
  }

  private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
    // default de-serialization
    inputFileSystem = (String) ois.readObject();
    inputFileName = (String) ois.readObject();
  }

  public static void main(String[] args) {
    String[] toolList = new String[3];
    toolList[0] = ExampleVolumeInputTool.class.getCanonicalName();
    toolList[1] = VolumeDifferencingTool.class.getCanonicalName();
    toolList[2] = ExampleVolumeOutputTool.class.getCanonicalName();
    ParameterService parms = new ParameterService(args);

    //TODO change these to some randomly generated files

    // 1 compare a file to itself to get zero
    // 2 compare a file to copy of itself to get zero/machine precision
    // 3 compare two different files to get not zero.

    String testDataFolder = "/home/wilsonmr/javaseis";

    if (parms.getParameter(ToolState.INPUT_FILE_SYSTEM) == "null") {
      parms.setParameter(ToolState.INPUT_FILE_SYSTEM, testDataFolder);
    }
    if (parms.getParameter(ToolState.INPUT_FILE_NAME) == "null") {
      parms.setParameter(ToolState.INPUT_FILE_NAME, "teststack.js");
      parms.setParameter(COMPARE_FILE_NAME,"seg45shot.js");
      parms.setParameter(ToolState.OUTPUT_FILE_SYSTEM, testDataFolder);
      parms.setParameter(ToolState.OUTPUT_FILE_NAME,"testCompare.js");
    }
    parms.setParameter(ToolState.TASK_COUNT, "1");
    try {
      VolumeToolRunner.exec(parms, toolList);
    } catch (SeisException e) {
      e.printStackTrace();
    }
  }
}

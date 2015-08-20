package org.javaseis.grid;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.javaseis.examples.scratch.ExampleImageOutput;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.DataState;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.distributed.FileSystemIOService;
import beta.javaseis.distributed.IDistributedIOService;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;
import beta.javaseis.services.VolumePropertyService;

public class JTestCheckedGridNew implements IVolumeTool{

  static ParameterService parms;
  
  IDistributedIOService ipio;
  VolumePropertyService vps;
  boolean usesProperties;
  String inputFileSystem, inputFileName;
  
  private static String[] listToArray(List<String> list) {
    String[] array = new String[list.size()];
    for (int k = 0 ; k < list.size() ; k++) {
      array[k] = list.get(k);
    }
    return array;
  }
  
  private static ParameterService basicParameters() {
    String inputFileName = "seg45shot.js";
    //String outputFileName = "fishfish.js";
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

  public static void main(String[] args) throws FileNotFoundException {
    
    ParameterService parms = basicParameters();

    List<String> toolList = new ArrayList<String>();

    toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
    toolList.add(JTestCheckedGridNew.class.getCanonicalName());

    String[] toolArray = listToArray(toolList);

    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      e.printStackTrace();
    }

  }
  
  @Override
  public void serialInit(ToolState toolState) throws SeisException {
    inputFileSystem = toolState.getParameter(ToolState.INPUT_FILE_SYSTEM);
    toolState.log("Input file system: " + inputFileSystem);
    inputFileName = toolState.getParameter(ToolState.INPUT_FILE_NAME);
    toolState.log("Input file name: " + inputFileName);
    IParallelContext upc = new UniprocessorContext();
    ipio = new FileSystemIOService(upc, inputFileSystem);
    ipio.open(inputFileName);
    toolState.log("Opened file in serial mode");
    toolState.setOutputState(new DataState(ipio, toolState.getIntParameter(ToolState.TASK_COUNT, 1)));
    ipio.close();
  }

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState) throws SeisException {
    ipio = new FileSystemIOService(pc, inputFileSystem);
    ipio.open(inputFileName);
    ISeismicVolume outputVolume = (ISeismicVolume) toolState.getObject(ToolState.OUTPUT_VOLUME);
    ipio.setSeismicVolume(outputVolume);
    ipio.reset();
    if (ipio.usesProperties()) {
      vps = (VolumePropertyService) (ipio.getPropertyService());
      pc.masterPrint("\n" + vps.listProperties() + "\n");
    }
    pc.serialPrint("Re-opened file in parallel mode");    
  }

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolState, ISeismicVolume input, ISeismicVolume output)
      throws SeisException {
    
    if (ipio.hasNext() == false) {
      ipio.close();
      return false;
    }
    
    ipio.next();
    ipio.read();
    
    System.out.println("[JTESTCHECKEDGRID]:" + Arrays.toString(ipio.getFilePosition()));
    
    int[] filePosition = ipio.getFilePosition();
    
    new GridFromHeaders(input, toolState, filePosition);
    
    return false;
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

}

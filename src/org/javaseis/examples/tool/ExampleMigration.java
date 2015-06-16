package org.javaseis.examples.tool;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.fft.SeisFft3d;
import beta.javaseis.parallel.IParallelContext;

import org.javaseis.grid.GridDefinition;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.services.ParameterService;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.IntervalTimer;
import org.javaseis.volume.ISeismicVolume;

public class ExampleMigration extends StandAloneVolumeTool {
  
  int volumeCount;
  IParallelContext pc;
  IntervalTimer compTime, totalTime;

  SeisFft3d fft3d;

  public static void main(String[] args) {
    //This is basically a test harness.
    //The functionality of finding the data and setting the parameters should
    //be externalized to a GUI, JTest or a script eventually.
    ParameterService parms = new ParameterService(args);
    try {
      String inputFileName = "100a-rawsynthpwaves.js";
      String dataFolder = findTestDataLocation(parms,inputFileName);
      setParameterIfUnset(parms,"inputFileSystem",dataFolder);
      setParameterIfUnset(parms,"inputFilePath",inputFileName);
      setParameterIfUnset(parms,"outputFileSystem",dataFolder);
      setParameterIfUnset(parms,"outputFilePath","testFFT.js");
      //TODO if you set threadCount to 2, half of the data will be missing
      // the task fails outright if you set it higher than 2.
      setParameterIfUnset(parms,"threadCount","1");
      exec(parms, new ExampleMigration());
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  public static String findTestDataLocation(ParameterService parms,String filename) throws FileNotFoundException {

    if (parameterIsSet(parms,"inputFileSystem")) {
      return parms.getParameter("inputFileSystem");
    }
    String[] candidates = new String[] {
        System.getProperty("user.home") + File.separator + "javaseis",
        "/home/seisspace/data"
    };

    for (String candidate : candidates) {
      System.out.println("Searching for " + candidate);
      if (new File(candidate).isDirectory()) {
        File test = new File(candidate + File.separator + filename);
        if (test.exists()) {
          System.out.println("JavaSeis folder located at " + test.getAbsolutePath()+ "\n");
          return candidate;
        }
      }
    }
    throw new FileNotFoundException("Unable to locate input data directory.");

  }

  private static boolean parameterIsSet(ParameterService parameterService,
      String parameterName) {
    return parameterService.getParameter(parameterName) != "null";
  }

  private static void setParameterIfUnset(ParameterService parameterService,
      String parameterName, String parameterValue) {
    if (!parameterIsSet(parameterService,parameterName)) {
      parameterService.setParameter(parameterName, parameterValue);
    }
  }

  @Override
  public void serialInit(ToolContext toolContext) {
    GridDefinition inputGrid = toolContext.getInputGrid();
    long[] inputAxisLengths = inputGrid.getAxisLengths();
    if (inputAxisLengths.length < 3) {
      throw new IllegalArgumentException("Input dataset is not big enough for a Volumetool");
    }
    long[] outputAxisLengths = Arrays.copyOf(inputAxisLengths,inputAxisLengths.length);
    int[] inputVolumeLengths = new int[3];
    for (int k = 0 ; k < 3 ; k++) {
      if (inputVolumeLengths[k] > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Input array dimension is way too big.");
      }
      inputVolumeLengths[k] = (int)inputAxisLengths[k];
    }

    pc = toolContext.getParallelContext();
    fft3d = new SeisFft3d(pc,inputVolumeLengths,new float[] {0,0,0},new int[] {-1,1,1});

    //determine shape of output
    for (int k = 0 ; k < 3 ; k++) {
      outputAxisLengths[k] = fft3d.getFftShape()[k];
    }

    //copy rest of AxisDefinitions for now
    AxisDefinition[] outputAxes = new AxisDefinition[inputAxisLengths.length];
    for (int k = 0 ; k < inputAxisLengths.length ; k++) {
      AxisDefinition in = inputGrid.getAxis(k);
      outputAxes[k] = new AxisDefinition(in.getLabel(),
          in.getUnits(),
          in.getDomain(),
          outputAxisLengths[k],
          in.getLogicalOrigin(),
          in.getLogicalDelta(),
          in.getPhysicalOrigin(),
          in.getPhysicalDelta());
    }

    GridDefinition outputGrid = new GridDefinition(
        inputGrid.getNumDimensions(),outputAxes);
    toolContext.setOutputGrid(outputGrid);
    
    
  }
  
  @Override
  public void parallelInit(ToolContext toolContext) {
    volumeCount = 0;
    pc = toolContext.getParallelContext();
    pc.masterPrint("Input Grid Definition:\n" + toolContext.getInputGrid());
    pc.masterPrint("Output Grid Definition:\n" + toolContext.getOutputGrid());
    compTime = new IntervalTimer();
    totalTime = new IntervalTimer();
    totalTime.start();
  }

  @Override
  public boolean processVolume(ToolContext toolContext, ISeismicVolume input,
      ISeismicVolume output) {
    int[] shape = input.getLengths();
    SeisFft3d fft3d = new SeisFft3d(pc,shape,new float[] {0,0,0},new int[] {-1,1,1});
    DistributedArray ida = input.getDistributedArray();
    DistributedArray oda = output.getDistributedArray();
    oda.setElementCount(2);
    //TODO begin info dump
    System.out.println("FFT shape:         " + Arrays.toString(fft3d.getFftShape()));
    System.out.println("FFT lengths:       " + Arrays.toString(fft3d.getFftLengths()));
    System.out.println("Shape:             " + Arrays.toString(fft3d.getShape()));
    int[] FFTshape = fft3d.getTransformShape(input.getLengths(),new float[] {0,0,0},pc);
    int[] odashape = oda.getShape();
    System.out.println("getTransformShape: " + Arrays.toString(FFTshape));
    System.out.println("Output DA Shape:   " + Arrays.toString(odashape));
    fft3d.forward(input.getDistributedArray(),output.getDistributedArray());


    //output.copyVolume(input);
    return false;
  }

}

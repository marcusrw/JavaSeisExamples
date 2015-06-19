package org.javaseis.examples.scratch;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;

import beta.javaseis.array.TransposeType;
import beta.javaseis.complex.ComplexArrays;
import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.distributed.DistributedTraceIterator;
import beta.javaseis.fft.SeisFft3d;
import beta.javaseis.parallel.IParallelContext;

import org.javaseis.array.ElementType;
import org.javaseis.examples.plot.JavaSeisMovieRunner;
import org.javaseis.grid.GridDefinition;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.services.ParameterService;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.IntervalTimer;
import org.javaseis.volume.ISeismicVolume;

/**
 * @author Marcus Wilson 2015
 *
 */
public class ExampleMigration extends StandAloneVolumeTool {

  int volumeCount;
  IParallelContext pc;
  IntervalTimer compTime, totalTime;

  SeisFft3d fft3d;

  public ExampleMigration() {

  }

  public ExampleMigration(ParameterService parms) {
    exec(parms,new ExampleMigration());
  }

  //allows running this tool from the command line, using key/value pairs to
  //fill in the necessary parameters.
  public static void main(String[] args) {
    ParameterService parms = new ParameterService(args);
    exec(parms, new ExampleMigration());
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
    //TODO change output domains to frequency, units to hertz etc.
    AxisDefinition[] outputAxes = new AxisDefinition[inputAxisLengths.length];
    for (int k = 0 ; k < inputAxisLengths.length ; k++) {
      AxisDefinition inputAxis = inputGrid.getAxis(k);
      outputAxes[k] = new AxisDefinition(inputAxis.getLabel(),
          inputAxis.getUnits(),
          inputAxis.getDomain(),
          outputAxisLengths[k],
          inputAxis.getLogicalOrigin(),
          inputAxis.getLogicalDelta(),
          inputAxis.getPhysicalOrigin(),
          inputAxis.getPhysicalDelta());
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
    DistributedArray inputDA = input.getDistributedArray();
    DistributedArray outputDA = output.getDistributedArray();
    fft3d = new SeisFft3d(pc,shape,new float[] {0,0,0},new int[] {-1,1,1});
    DistributedArray fft3dDA = fft3d.getArray();


    double[] sampleRates = new double[] {0.002,100,100};
    fft3d.setTXYSampleRates(sampleRates);
    double[] buf = new double[3];
    fft3d.getKyKxFCoordinatesForPosition(new int[] {53, 28, 85},buf);
    System.out.println("KyKxF: " + Arrays.toString(buf));


    System.out.println("fft3DDA Shape:     "
        + Arrays.toString(fft3dDA.getShape()));
    fft3dDA.copy(inputDA);
    //outputDA.setElementCount(2);
    fft3d.forward();

    fft3d.getArray().transpose(TransposeType.T321);
    DistributedArray test = cabs(fft3d.getArray());
    //DistributedArrayMosaicPlot.showAsModalDialog(test,"Magnitude Test");

    if (DAcontainsNegativeSample(test)) {
      System.out.println("Output contains a negative sample.  "
          + "/nMagnitude calculation is not working.");
    }

    //TODO begin info dump
    System.out.println("FFT shape:         " + Arrays.toString(fft3d.getFftShape()));
    System.out.println("FFT lengths:       " + Arrays.toString(fft3d.getFftLengths()));
    System.out.println("Shape:             " + Arrays.toString(fft3d.getShape()));
    System.out.println("getTransformShape: " + Arrays.toString(SeisFft3d.getTransformShape(input.getLengths(),new float[] {0,0,0},pc)));
    System.out.println("Output DA Shape:   " + Arrays.toString(outputDA.getShape()));
    System.out.println("fft3d Array size:  " + fft3d.getArray().getArrayLength());
    System.out.println("input Array size:  " + inputDA.getArrayLength());
    System.out.println("output Array size: " + outputDA.getArrayLength());
    System.out.println("Current FFT3D shape: " + Arrays.toString(fft3d.getShape()));
    System.out.println();

    //DistributedArrayMosaicPlot.showAsModalDialog(inputDA,"Input Data");
    //DistributedArrayMosaicPlot.showAsModalDialog(outputDA,"Output Data");
    outputDA.copy(test);

    return true;
  }

  private boolean DAcontainsNegativeSample(DistributedArray da) {
    DistributedArrayPositionIterator dapi = 
        new DistributedArrayPositionIterator(da,1,0);
    float[] buffer = new float[1];
    int[] position;
    while (dapi.hasNext()) {
      position = dapi.next();
      da.getSample(buffer,position);
      //System.out.println("Position: " + Arrays.toString(position));
      //System.out.println("Value: " + Arrays.toString(buffer));
      if (buffer[0] < 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Return the magnitude of every element in a complex distributed array.
   * @param da - The Distributed Array
   */
  public static DistributedArray cabs(DistributedArray da) {
    //Make a new DA that's the same shape as the input...
    IParallelContext pc = da.getParallelContext();
    ElementType type = da.getElementType();
    int[] lengths = da.getShape();
    DistributedArray absDA = new DistributedArray(pc,type,lengths);
    //...because this didn't work for whatever reason.
    //DistributedArray absDA = new DistributedArray(da);
    //absDA.setElementCount(1);
    int direction = 1; //forward
    int scope = 0; //iterate over samples
    DistributedArrayPositionIterator dapi =
        new DistributedArrayPositionIterator(da,direction,scope);
    float[] inputBuffer = new float[2]; //complex numbers
    float outputBuffer = 0;
    int numiterates = 0;
    while (dapi.hasNext()) {
      numiterates++;
      int[] position = dapi.next();
      //System.out.println(Arrays.toString(position));
      da.getSample(inputBuffer, position);
      //System.out.println(Arrays.toString(inputBuffer));
      outputBuffer = (float)Math.hypot(inputBuffer[0], inputBuffer[1]);
      //System.out.println(outputBuffer);
      absDA.putSample(outputBuffer, position);
    }
    System.out.println("Number of Iterates: " + numiterates);

    System.out.println("Input Array Length: " + da.getArrayLength());
    System.out.println("Input Total Sample Count: " + da.getTotalSampleCount());
    System.out.println("Input Element Count: " + da.getElementCount());
    System.out.println("Output Array Length: " + absDA.getArrayLength());
    System.out.println("Output Total Sample Count: " + absDA.getTotalSampleCount());
    System.out.println("Output Element Count: " + absDA.getElementCount());
    return absDA;
  }

  @Override
  public void serialFinish(ToolContext toolContext) {
    ParameterService parms = toolContext.getParameterService();
    String inputJS = parms.getParameter("inputFileSystem") + File.separator + parms.getParameter("inputFilePath");
    String outputJS = parms.getParameter("outputFileSystem") + File.separator + parms.getParameter("outputFilePath");
    System.out.println("Displaying Input File:  " + inputJS);
    JavaSeisMovieRunner.showMovie(inputJS);
    System.out.println("Displaying Output File: " + outputJS);
    JavaSeisMovieRunner.showMovie(outputJS);
  }
}

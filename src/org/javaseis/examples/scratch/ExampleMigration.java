package org.javaseis.examples.scratch;

import java.io.File;
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
import org.javaseis.examples.plot.SingleVolumeDAViewer;
import org.javaseis.grid.GridDefinition;
import org.javaseis.parallel.DistributedArrayTraceIterator;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.properties.DataDomain;
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
  private long[] transformAxisLengths;
  private DataDomain[] transformDomains;
  private AxisDefinition[] transformAxes;
  private GridDefinition transformGrid;
  private SingleVolumeDAViewer display;

  public ExampleMigration() {
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
    pc = toolContext.getParallelContext();
    transformGrid = computeTransformAxes(inputGrid);
    toolContext.setOutputGrid(inputGrid);
  }

  private GridDefinition computeTransformAxes(GridDefinition inputGrid) {
    long[] inputAxisLengths = inputGrid.getAxisLengths();
    if (inputAxisLengths.length < 3) {
      throw new IllegalArgumentException("Input dataset is not big "
          + "enough for a Volumetool");
    }
    transformAxisLengths = Arrays.copyOf(inputAxisLengths,
        inputAxisLengths.length);
    int[] inputVolumeLengths = new int[3];
    for (int k = 0 ; k < 3 ; k++) {
      inputVolumeLengths[k] = (int)inputAxisLengths[k];
    }

    fft3d = new SeisFft3d(pc,inputVolumeLengths,
        new float[] {0,0,0},new int[] {-1,1,1});

    //determine shape of output
    for (int k = 0 ; k < 3 ; k++) {
      transformAxisLengths[k] = fft3d.getFftShape()[k];
    }

    transformAxes = new AxisDefinition[inputAxisLengths.length];
    transformDomains = findTransformDomains(inputGrid.getAxisDomains());
    for (int k = 0 ; k < inputAxisLengths.length ; k++) {
      AxisDefinition inputAxis = inputGrid.getAxis(k);
      transformAxes[k] = new AxisDefinition(inputAxis.getLabel(),
          inputAxis.getUnits(),
          transformDomains[k],
          transformAxisLengths[k],
          inputAxis.getLogicalOrigin(),
          inputAxis.getLogicalDelta(),
          inputAxis.getPhysicalOrigin(),
          inputAxis.getPhysicalDelta());
    }

    return new GridDefinition(inputGrid.getNumDimensions(),transformAxes);
  }

  private DataDomain[] findTransformDomains(DataDomain[] inputAxisDomains) {
    for (int k = 0 ; k < inputAxisDomains.length ; k++) {
      switch (inputAxisDomains[k].toString()) {
      case "time":
        inputAxisDomains[k] = new DataDomain("frequency");
        break;
      case "frequency":
        inputAxisDomains[k] = new DataDomain("time");
        break;
      case "space":
        inputAxisDomains[k] = new DataDomain("wavenumber");
        break;
      case "wavenumber":
        inputAxisDomains[k] = new DataDomain("space");
        break;
      }
    }
    return inputAxisDomains;
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

    int[] inputShape = input.getLengths();
    DistributedArray inputDA = input.getDistributedArray();
    fft3d = new SeisFft3d(pc,inputShape,new float[] {0,0,0},new int[] {-1,1,1});
    fft3d.getArray().copy(inputDA);;
    fft3d.forward();

    //Get the DA to visualize
    DistributedArray test = fft3d.getArray();
    //test.transpose(TransposeType.T321);
    //display = new SingleVolumeDAViewer(test,transformGrid);
    //display.showAsModalDialog();
    //test.transpose(TransposeType.T321);
    
    //Find the Source Location, assume we have SOU_XYZ
    //For now we're just going to use the globalGrid and our prior knowledge
    //then refactor it into an auto/manual source field generator.
    int SOU_X = 0;
    int SOU_Y = 0;
    int SOU_X_INDX = -1;
    int SOU_Y_INDX = -1;
    GridDefinition globalGrid = input.getGlobalGrid();
    String[] axisLabels = globalGrid.getAxisLabelsStrings();
    System.out.println(Arrays.toString(axisLabels));
    for (int k = 0 ; k < axisLabels.length ; k++) {
      if (axisLabels[k] == "SOURCE") {
        SOU_X_INDX = k % 2;
        SOU_Y_INDX = k / 2;
        if (SOU_X_INDX == 0) {
          SOU_X = 14;
        }
        if (SOU_X_INDX == 1) {
          SOU_X = 34;
        }
        if (SOU_Y_INDX == 0) {
          SOU_Y = 14;
        }
        if (SOU_Y_INDX == 1) {
          SOU_Y = 34;
        }
      }      
    }
    System.out.println("Source location: " + "(" + SOU_X + "," + SOU_Y + ")");
    if (SOU_X == 0 || SOU_Y == 0) {
      throw new IllegalArgumentException("Unable to find source location.");
    }
    
    //Build the Source signature by finding the best array index 
    //for the source location, then stick a little gaussian thing there
    

    //phase shift
    float V = 2000f;
    float dz = -1000;
    float EPS = 1E-12f;

    int[] position = new int[test.getDimensions()];
    int direction = 1; //forward
    int scope = 0; //samples
    DistributedArrayPositionIterator dapi =
        new DistributedArrayPositionIterator(test,position,direction,scope);

    float[] sample = new float[test.getElementCount()];
    float[] outsample = new float[test.getElementCount()];
    double[] coords = new double[position.length];
    fft3d.setTXYSampleRates(new double[] {0.002,100,100});
    DistributedArray operator = new DistributedArray(test);
    while (dapi.hasNext()) {
      position = dapi.next();
      test.getSample(sample, position);
      fft3d.getKyKxFCoordinatesForPosition(position, coords);
      //System.out.println(Arrays.toString(position) 
      //    + " " + Arrays.toString(coords));
      double Ky = coords[0];
      double Kx = coords[1];
      double F = coords[2];
      double Kz2 = (F/V)*(F/V) - Kx*Kx - Ky*Ky;
      double shift = 0;
      if (Kz2 > EPS) {
        shift = (2*Math.PI*dz * Math.sqrt(Kz2));
      }
      float shift2 = (float)(Math.atan(shift));
      operator.putSample(shift2, position);
      outsample[0] = (float) (sample[0]*Math.cos(shift) - sample[1]*Math.sin(shift));
      outsample[1] = (float) (sample[1]*Math.cos(shift) + sample[0]*Math.sin(shift));
      test.putSample(outsample,position);
    }

    //operator.transpose(TransposeType.T321);
    //display = new SingleVolumeDAViewer(operator,transformGrid);
    //display.showAsModalDialog();
    //operator.transpose(TransposeType.T321);

    fft3d.inverse();
    test = fft3d.getArray();
    display = new SingleVolumeDAViewer(test,output.getLocalGrid());
    display.showAsModalDialog();

    DistributedArray outputDA = output.getDistributedArray();
    outputDA.setElementCount(test.getElementCount());
    outputDA.copy(test);

    return true;
  }

  @Override
  public void serialFinish(ToolContext toolContext) {
  }
}

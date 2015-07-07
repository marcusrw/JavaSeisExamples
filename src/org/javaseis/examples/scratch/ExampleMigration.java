package org.javaseis.examples.scratch;

import java.util.Arrays;
import java.util.logging.Logger;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import org.javaseis.examples.scratch.SeisFft3dNew;
import beta.javaseis.parallel.IParallelContext;

import org.javaseis.examples.plot.SingleVolumeDAViewer;
import org.javaseis.grid.GridDefinition;
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

  private static final Logger LOGGER = 
      Logger.getLogger(ExampleMigration.class.getName());

  int volumeCount;
  IParallelContext pc;
  IntervalTimer compTime, totalTime;

  SeisFft3dNew rcvr,srce;
  private long[] transformAxisLengths;
  private DataDomain[] transformDomains;
  private AxisDefinition[] transformAxes;
  //TODO only for visual checks.  Delete later.
  private GridDefinition transformGrid;

  //viewer for checking your work.
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

    rcvr = new SeisFft3dNew(pc,inputVolumeLengths,
        new float[] {0,0,0},new int[] {-1,1,1});

    //determine shape of output
    for (int k = 0 ; k < 3 ; k++) {
      transformAxisLengths[k] = rcvr.getFftShape()[k];
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
    //compTime = new IntervalTimer();
    //totalTime = new IntervalTimer();
    //totalTime.start();
  }

  @Override
  public boolean processVolume(ToolContext toolContext, ISeismicVolume input,
      ISeismicVolume output) {

    float[] sourceXYZ = locateSourceXYZ(input);
    assert sourceXYZ.length == 3;

    int[] inputShape = input.getLengths();
    DistributedArray inputDA = input.getDistributedArray();
    rcvr = new SeisFft3dNew(pc,inputShape,new float[] {0,0,0},new int[] {-1,1,1});
    srce = new SeisFft3dNew(pc,inputShape,new float[] {0,0,0},new int[] {-1,1,1});
    rcvr.getArray().copy(inputDA);

    DistributedArray rcvrDA = rcvr.getArray();
    DistributedArray srceDA = srce.getArray();

    //rcvrDA = rcvr.getArray();
    //display = new SingleVolumeDAViewer(rcvrDA,output.getLocalGrid());
    //display.showAsModalDialog();

    rcvr.forwardTemporal();
    srce.forwardTemporal();
    //Build the Source signature by finding the best array index 
    //for the source location, then stick a little hat function there
    generateSourceSignature(sourceXYZ);
    //visual check of source signature.
    //display = new SingleVolumeDAViewer(srceDA,input.getLocalGrid());
    //display.showAsModalDialog();

    //rcvrDA.transpose(TransposeType.T321);
    //display = new SingleVolumeDAViewer(rcvrDA,transformGrid);
    //display.showAsModalDialog();
    //rcvrDA.transpose(TransposeType.T321);

    //srceDA.transpose(TransposeType.T321);
    //display = new SingleVolumeDAViewer(srceDA,transformGrid);
    //display.showAsModalDialog();
    //srceDA.transpose(TransposeType.T321);

    //phase shift
    float V = 2000f;
    float EPS = 1E-12f;

    float zmax = 1000;
    float dz = 100;
    for (int zindx = 0 ; zindx*dz < zmax ; zindx++) {
      rcvr.forwardSpatial2D();
      srce.forwardSpatial2D();

      int[] position = new int[rcvrDA.getDimensions()];
      int direction = 1; //forward
      int scope = 0; //samples
      DistributedArrayPositionIterator dapi =
          new DistributedArrayPositionIterator(rcvrDA,position,direction,scope);

      float[] recInSample = new float[rcvrDA.getElementCount()];
      float[] souInSample = new float[srceDA.getElementCount()];
      float[] recOutSample = new float[rcvrDA.getElementCount()];
      float[] souOutSample = new float[rcvrDA.getElementCount()];
      double[] coords = new double[position.length];
      double[] sampleRates = {0.002,100,100};
      rcvr.setTXYSampleRates(sampleRates);
      srce.setTXYSampleRates(sampleRates);
      while (dapi.hasNext()) {
        position = dapi.next();
        rcvrDA.getSample(recInSample, position);
        srceDA.getSample(souInSample, position);
        rcvr.getKyKxFCoordinatesForPosition(position, coords);
        //LOGGER.info(Arrays.toString(position) 
        //    + " " + Arrays.toString(coords));
        double Ky = coords[0];
        double Kx = coords[1];
        double F = coords[2];
        double Kz2 = (F/V)*(F/V) - Kx*Kx - Ky*Ky;
        double shift = 0;
        if (Kz2 > EPS && zindx > 0) {
          shift = (2*Math.PI*dz * Math.sqrt(Kz2));
        }
        if (Kz2 > EPS) {
          recOutSample[0] = (float) (recInSample[0]*Math.cos(-shift) - recInSample[1]*Math.sin(-shift));
          recOutSample[1] = (float) (recInSample[1]*Math.cos(-shift) + recInSample[0]*Math.sin(-shift));
          souOutSample[0] = (float) (souInSample[0]*Math.cos(shift) - souInSample[1]*Math.sin(shift));
          souOutSample[1] = (float) (souInSample[1]*Math.cos(shift) + souInSample[0]*Math.sin(shift));
        } else {
          recOutSample = new float[] {0,0};
          souOutSample = new float[] {0,0};
        }
        rcvrDA.putSample(recOutSample, position);
        srceDA.putSample(souOutSample, position);
      }
      //rcvrDA.transpose(TransposeType.T321);
      //display = new SingleVolumeDAViewer(rcvrDA,transformGrid);
      //display.showAsModalDialog();
      //rcvrDA.transpose(TransposeType.T321);      
      
      //srceDA.transpose(TransposeType.T321);
      //display = new SingleVolumeDAViewer(srceDA,transformGrid);
      //display.showAsModalDialog();  
      //srceDA.transpose(TransposeType.T321); 
      
      rcvr.inverseSpatial2D();
      srce.inverseSpatial2D();

      //Now image here
      position = new int[rcvrDA.getDimensions()];
      dapi = new DistributedArrayPositionIterator(rcvrDA,position,
          direction,scope);
      
      int[] outputPosition = position.clone();
      outputPosition[0] = zindx;
      
      
    }


    rcvr.inverseTemporal();
    srce.inverseTemporal();

    
    display = new SingleVolumeDAViewer(rcvrDA,output.getLocalGrid());
    display.showAsModalDialog();

    display = new SingleVolumeDAViewer(srceDA,output.getLocalGrid());
    display.showAsModalDialog();    

    DistributedArray outputDA = output.getDistributedArray();
    outputDA.setElementCount(rcvrDA.getElementCount());
    outputDA.copy(rcvrDA);

    return true;
  }

  private void generateSourceSignature(float[] sourceXYZ) {
    if (sourceXYZ.length != 3)
      throw new IllegalArgumentException("Wrong number of elements for sourceXYZ");
    if (sourceXYZ[2] != 0)
      throw new UnsupportedOperationException("Sources at Depths besides zero not yet implemented");

    int sourceX = (int) Math.floor(sourceXYZ[0]);
    while (sourceX < sourceXYZ[0] + 1) {
      int sourceY = (int) Math.floor(sourceXYZ[1]);
      while (sourceY < sourceXYZ[1] + 1) {
        float weight = Math.max(0, 1-euclideanDistance(sourceX,sourceY,sourceXYZ));
        putWhiteSpectrum(srce,sourceX,sourceY,weight);
        sourceY++;
      }
      sourceX++;
    }
  }
  
  /**
   * @param sourceX - Target X index in the grid
   * @param sourceY - Target Y index in the grid
   * @param sourceXYZ - Actual Source position in the grid
   * @return The  Euclidean distance between the current array index
   *           and the input source.
   */
  private float euclideanDistance(float sourceX, float sourceY, float[] sourceXYZ) {
    float dx2 = (sourceX - sourceXYZ[0])*(sourceX - sourceXYZ[0]);
    float dy2 = (sourceY - sourceXYZ[1])*(sourceY - sourceXYZ[1]);
    return (float)Math.sqrt(dx2+dy2);
  }

  private void putWhiteSpectrum(SeisFft3dNew source,int sourceX,int sourceY,float amplitude) {
    int[] position = new int[] {0,sourceX,sourceY};
    int[] volumeShape = source.getArray().getShape();
    float[] sample = new float[] {amplitude,0}; //amplitude+0i complex
    DistributedArray sourceDA = source.getArray();
    while (position[0] < volumeShape[0]) {
      sourceDA.putSample(sample, position);
      position[0]++;
    }
  }

  private float[] locateSourceXYZ(ISeismicVolume input) {
    LOGGER.info("Volume Index: " + Arrays.toString(input.getVolumePosition()));
    //Find the Source Location, assume we have SOU_XYZ
    //For now we're just going to use the globalGrid and our prior knowledge
    //then refactor it into an auto/manual source field generator.

    float[][] sourceLocations = new float[][]
        {
        {14.5F,14.5F,0},
        {34.5F,14.5F,0},
        {14.5F,34.5F,0},
        {34.5F,34.5F,0}
        };

    int volumeArrayIndex;
    GridDefinition globalGrid = input.getGlobalGrid();
    String[] axisLabels = globalGrid.getAxisLabelsStrings();
    LOGGER.info(Arrays.toString(axisLabels));
    for (int k = 0 ; k < axisLabels.length ; k++) {
      if (axisLabels[k] == "SOURCE") {
        volumeArrayIndex = input.getVolumePosition()[k];
        LOGGER.info("Source location: " + Arrays.toString(sourceLocations[volumeArrayIndex]));
        return sourceLocations[volumeArrayIndex];
      }
    }
    throw new IllegalArgumentException("Unable to find source location.");
  }

  @Override
  public void serialFinish(ToolContext toolContext) {
  }
}

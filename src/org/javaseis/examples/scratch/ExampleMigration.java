package org.javaseis.examples.scratch;

import java.util.Arrays;
import java.util.logging.Logger;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;

import org.javaseis.examples.scratch.SeisFft3dNew;

import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.synthetic.CosineBellWavelet;

import org.javaseis.examples.plot.SingleVolumeDAViewer;
import org.javaseis.grid.GridDefinition;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.properties.AxisLabel;
import org.javaseis.properties.DataDomain;
import org.javaseis.properties.Units;
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

  SeisFft3dNew rcvr,shot;
  private long[] transformAxisLengths;
  private DataDomain[] transformDomains;
  private AxisDefinition[] transformAxes;
  private GridDefinition imageGrid;
  //TODO only for visual checks.  Delete later.
  private GridDefinition transformGrid;

  //viewer for checking your work.
  private SingleVolumeDAViewer display;

  static final float[] PAD = new float[] {0,0,0};


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
    ParameterService parms = toolContext.getParameterService();
    imageGrid = computeImageGrid(inputGrid,parms);
    pc = toolContext.getParallelContext();
    transformGrid = computeTransformAxes(inputGrid);
    toolContext.setOutputGrid(imageGrid);
  }

  private GridDefinition computeImageGrid(GridDefinition inputGrid,
      ParameterService parms) {

    AxisDefinition[] imageAxes = new AxisDefinition[inputGrid.getNumDimensions()];

    float zmin = Float.parseFloat(parms.getParameter("ZMIN","0"));
    float zmax = Float.parseFloat(parms.getParameter("ZMAX","2000"));
    float delz = Float.parseFloat(parms.getParameter("DELZ","50"));

    long depthAxisLength;
    if (delz == 0 || (zmax - zmin) < delz)
      depthAxisLength = 1;
    else
      depthAxisLength = (long) Math.floor((zmax-zmin)/delz);

    for (int axis = 0 ; axis < imageAxes.length ; axis++) {
      if (axis == 0) {
        imageAxes[axis] = new AxisDefinition(
            AxisLabel.DEPTH,
            Units.METERS,
            DataDomain.SPACE,
            depthAxisLength,
            0,1,
            zmin,delz
            );
      } else {
        imageAxes[axis] = inputGrid.getAxis(axis);
      }
    }

    return new GridDefinition(imageAxes.length,imageAxes);
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
        PAD,new int[] {-1,1,1});

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

    //TODO temporary flag to only process the first shot
    if (input.getVolumePosition()[3] > 0) return false;

    float[] sourceXYZ = locateSourceXYZ(input);
    assert sourceXYZ.length == 3;

    int[] inputShape = input.getLengths();
    DistributedArray inputDA = input.getDistributedArray();
    rcvr = new SeisFft3dNew(pc,inputShape,PAD,new int[] {-1,1,1});
    shot = new SeisFft3dNew(pc,inputShape,PAD,new int[] {-1,1,1});
    rcvr.getArray().copy(inputDA);

    DistributedArray rcvrDA = rcvr.getArray();
    DistributedArray shotDA = shot.getArray();

    //rcvrDA = rcvr.getArray();
    //display = new SingleVolumeDAViewer(rcvrDA,output.getLocalGrid());
    //display.showAsModalDialog();

    rcvr.forwardTemporal();
    shot.forwardTemporal();
    //Build the Source signature by finding the best array index 
    //for the source location, then stick a little hat function there
    generateSourceSignature(sourceXYZ);
    //visual check of source signature.
    display = new SingleVolumeDAViewer(shotDA,input.getLocalGrid());
    display.showAsModalDialog();
    display = new SingleVolumeDAViewer(rcvrDA,input.getLocalGrid());
    display.showAsModalDialog();

    //rcvrDA.transpose(TransposeType.T321);
    rcvr.inverseTemporal();
    display = new SingleVolumeDAViewer(rcvrDA,input.getLocalGrid());
    display.showAsModalDialog();
    rcvr.forwardTemporal();
    //rcvrDA.transpose(TransposeType.T321);

    //srceDA.transpose(TransposeType.T321);
    shot.inverseTemporal();
    display = new SingleVolumeDAViewer(shotDA,input.getLocalGrid());
    display.showAsModalDialog();
    shot.forwardTemporal();
    //srceDA.transpose(TransposeType.T321);

    shot.forwardSpatial2D();
    display = new SingleVolumeDAViewer(shotDA,transformGrid);
    display.showAsModalDialog();

    shot.inverseSpatial2D();
    display = new SingleVolumeDAViewer(shotDA,input.getLocalGrid());
    display.showAsModalDialog();


    //phase shift
    float eps = 1E-12f;
    float V;

    DistributedArray image = output.getDistributedArray();

    double zmin = output.getLocalGrid().getAxisPhysicalOrigin(0);
    double delz = output.getLocalGrid().getAxisPhysicalDelta(0);
    long numz = output.getLocalGrid().getAxisLength(0);
    System.out.println("zmin: " + zmin);
    System.out.println("delz: " + delz);
    System.out.println("numz: " + numz);

    double[] sampleRates = {0.002,100,100};
    rcvr.setTXYSampleRates(sampleRates);
    shot.setTXYSampleRates(sampleRates);

    for (int zindx = 0 ; zindx < numz ; zindx++) {
      System.out.println("Depth: " + (zmin+delz*zindx));
      if (zmin+delz*zindx <= 1000) V = 2000;
      else V = 3800;

      rcvr.forwardSpatial2D();
      shot.forwardSpatial2D();

      int[] position = new int[rcvrDA.getDimensions()];
      int direction = 1; //forward
      int scope = 0; //samples
      DistributedArrayPositionIterator dapi =
          new DistributedArrayPositionIterator(
              rcvrDA,position,direction,scope);

      float[] recInSample = new float[rcvrDA.getElementCount()];
      float[] souInSample = new float[shotDA.getElementCount()];
      float[] recOutSample = new float[rcvrDA.getElementCount()];
      float[] souOutSample = new float[rcvrDA.getElementCount()];
      double[] coords = new double[position.length];
      while (dapi.hasNext()) {
        position = dapi.next();
        //System.out.println("Position in " + Arrays.toString(position));
        rcvrDA.getSample(recInSample, position);
        shotDA.getSample(souInSample, position);
        rcvr.getKyKxFCoordinatesForPosition(position, coords);
        //LOGGER.info(Arrays.toString(position) 
        //    + " " + Arrays.toString(coords));
        double Ky = coords[0];
        double Kx = coords[1];
        double F = coords[2];
        double Kz2 = (F/V)*(F/V) - Kx*Kx - Ky*Ky;
        double shift = 0;
        if (Kz2 > eps && zindx > 0) {
          shift = (2*Math.PI*delz * Math.sqrt(Kz2));
        }
        //TODO should be Kz2 > eps
        if (Kz2 > Float.NEGATIVE_INFINITY) {
          //TODO temporary "no nothing" extrapolation
          recOutSample[0] = recInSample[0];
          recOutSample[1] = recInSample[1];
          souOutSample[0] = souInSample[0];
          souOutSample[1] = souInSample[1];

          //recOutSample[0] = (float) (recInSample[0]*Math.cos(-shift) - recInSample[1]*Math.sin(-shift));
          //recOutSample[1] = (float) (recInSample[1]*Math.cos(-shift) + recInSample[0]*Math.sin(-shift));
          //souOutSample[0] = (float) (souInSample[0]*Math.cos(shift) - souInSample[1]*Math.sin(shift));
          //souOutSample[1] = (float) (souInSample[1]*Math.cos(shift) + souInSample[0]*Math.sin(shift));
        } else {
          recOutSample = new float[] {0,0};
          souOutSample = new float[] {0,0};
        }
        //System.out.println("Position out: " + Arrays.toString(position));
        rcvrDA.putSample(recOutSample, position);
        shotDA.putSample(souOutSample, position);
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
      shot.inverseSpatial2D();

      //Now image here
      position = new int[rcvrDA.getDimensions()];

      //TODO Trick.  Hide the high frequencies from the iterator
      // so that it doesn't waste time accumulating a bunch of zeros.
      /*
      int[] DALengths = rcvrDA.getShape().clone();
      System.out.println(Arrays.toString(DALengths));

      double fMax = 60;
      double fNY = 1/(2*0.002);
      double delf = fNY/DALengths[0];
      int maxFindx = (int) (fMax/delf);

      DALengths[0] = maxFindx;
      System.out.println("Max F index: " + maxFindx);
      rcvrDA.setShape(DALengths);
      System.out.println(Arrays.toString(DALengths));     
       */

      //rcvrDA.setShape();

      dapi = new DistributedArrayPositionIterator(rcvrDA,position,
          direction,scope);

      //Get the source and receiver samples
      System.out.println(image.getElementCount());
      System.out.println("shot: "
          + Arrays.toString(shotDA.getShape())
          + " "
          + shotDA.getTotalSampleCount());
      System.out.println("rcvr: "
          + Arrays.toString(rcvrDA.getShape()) 
          + " "
          + rcvrDA.getTotalSampleCount());
      System.out.println("image: " 
          + Arrays.toString(image.getShape()) 
          + " " 
          + image.getTotalSampleCount());

      float[] imageSample = new float[image.getElementCount()];
      while (dapi.hasNext()) {
        position = dapi.next();
        int[] outputPosition = position.clone();
        outputPosition[0] = zindx;
        //System.out.println("Position in: "
        //    + Arrays.toString(position)
        //    + " position out: "
        //    + Arrays.toString(outputPosition));
        rcvrDA.getSample(recInSample, position);
        shotDA.getSample(souInSample, position);

        image.getSample(imageSample, outputPosition);
        imageSample[0] += recInSample[0]*souInSample[0]
            + recInSample[1]*souInSample[1];
        //imageSample[1] += recInSample[0]*souInSample[1]
        //    - recInSample[1]*souInSample[0];
        image.putSample(imageSample, outputPosition);
      }

      rcvr.inverseTemporal();
      display = new SingleVolumeDAViewer(rcvrDA,input.getLocalGrid());
      display.showAsModalDialog();
      rcvr.forwardTemporal();

      shot.inverseTemporal();
      display = new SingleVolumeDAViewer(shotDA,input.getLocalGrid());
      display.showAsModalDialog();
      shot.forwardTemporal();      

      //display = new SingleVolumeDAViewer(image,output.getLocalGrid());
      //display.showAsModalDialog();
    }

    //rcvr.inverseTemporal();
    //shot.inverseTemporal();

    //display = new SingleVolumeDAViewer(rcvrDA,input.getLocalGrid());
    //display.showAsModalDialog();

    //display = new SingleVolumeDAViewer(shotDA,input.getLocalGrid());
    //display.showAsModalDialog();    

    //DistributedArray outputDA = output.getDistributedArray();
    //outputDA.setElementCount(rcvrDA.getElementCount());
    //outputDA.copy(rcvrDA);


    return true;
  }

  private void generateSourceSignature(float[] sourceXYZ) {
    if (sourceXYZ.length != 3)
      throw new IllegalArgumentException("Wrong number of elements for sourceXYZ");
    if (sourceXYZ[2] != 0)
      throw new UnsupportedOperationException("Sources at Depths besides zero not yet implemented");

    int sourceX = (int) Math.floor(sourceXYZ[0]);
    while (sourceX < sourceXYZ[0] + 0.4) {
      int sourceY = (int) Math.floor(sourceXYZ[1]);
      while (sourceY < sourceXYZ[1] + 0.4) {
        float weight = Math.max(0, 1-euclideanDistance(sourceX,sourceY,sourceXYZ));
        putWhiteSpectrum(shot,sourceX,sourceY,weight);
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

  /*
  private void putCosineBellWavelet(SeisFft3dNew source,int sourceX,int sourceY,float amplitude) {
    int[] position = new int[] {0,sourceX,sourceY};
    int[] volumeShape = source.getArray().getShape();
    int nf = source.getFftShape()[0];
    int if0 = 0;
    int df = 0;
    float flc = 5;
    float flp = 10;
    float fhp = 40;
    float fhc = 60;
    float[] cbw = CosineBellWavelet.createComplexWavelet(nf,if0,df,flc,flp,fhp,fhc);
    float[] sample = new float[] {amplitude,0}; //amplitude+0i complex
    DistributedArray sourceDA = source.getArray();
    while (position[0] < volumeShape[0]) {
      sample = new float[] {cbw[2*position[0]],cbw[2*position[0]+1]};
      sourceDA.putSample(sample, position);
      position[0]++;
    }
  }
   */

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

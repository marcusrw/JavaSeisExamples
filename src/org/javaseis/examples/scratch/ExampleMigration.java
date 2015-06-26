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
    long[] inputAxisLengths = inputGrid.getAxisLengths();
    if (inputAxisLengths.length < 3) {
      throw new IllegalArgumentException("Input dataset is not big "
          + "enough for a Volumetool");
    }
    long[] outputAxisLengths = Arrays.copyOf(inputAxisLengths,
        inputAxisLengths.length);
    int[] inputVolumeLengths = new int[3];
    for (int k = 0 ; k < 3 ; k++) {
      if (inputVolumeLengths[k] > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Input array dimension "
            + "is way too big.");
      }
      inputVolumeLengths[k] = (int)inputAxisLengths[k];
    }

    pc = toolContext.getParallelContext();
    fft3d = new SeisFft3d(pc,inputVolumeLengths,
        new float[] {0,0,0},new int[] {-1,1,1});

    //determine shape of output
    for (int k = 0 ; k < 3 ; k++) {
      outputAxisLengths[k] = fft3d.getFftShape()[k];
    }

    //copy rest of AxisDefinitions for now
    //TODO change output domains to frequency, units to hertz etc.
    AxisDefinition[] outputAxes = 
        new AxisDefinition[inputAxisLengths.length];
    DataDomain[] outputAxisDomains = 
        FindOutputDomains(inputGrid.getAxisDomains());
    for (int k = 0 ; k < inputAxisLengths.length ; k++) {
      AxisDefinition inputAxis = inputGrid.getAxis(k);
      outputAxes[k] = new AxisDefinition(inputAxis.getLabel(),
          inputAxis.getUnits(),
          outputAxisDomains[k],
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

  private DataDomain[] FindOutputDomains(DataDomain[] inputAxisDomains) {
    for (int k = 0 ; k < 3 ; k++) {
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

    //visualize
    fft3d.getArray().transpose(TransposeType.T321);
    DistributedArray test = fft3d.getArray();

    //So far this is the only way to test the DAViewer on complex data,
    //since we can't properly load a complex dataset into a SeismicVolume object
    SingleVolumeDAViewer display = new SingleVolumeDAViewer(test,output.getLocalGrid());
    display.showAsModalDialog();

    //don't be saving stuff complex.  It doesn't really work.
    DistributedArray outputDA = output.getDistributedArray();
    outputDA.setElementCount(test.getElementCount());
    outputDA.copy(test);

    return true;
  }

  @Override
  public void serialFinish(ToolContext toolContext) {
  }
}

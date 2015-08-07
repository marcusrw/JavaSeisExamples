package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.javaseis.examples.plot.DAFrontendViewer;
import org.javaseis.services.ParameterService;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;

public class ExampleImageOutput extends StandAloneVolumeTool {

  private static final Logger LOGGER = 
      Logger.getLogger(ExampleImageOutput.class.getName());

  static ParameterService parms;

  public static void main(String[] args) throws FileNotFoundException {
    String inputFileName = "segshotno1.js";
    parms = new FindTestData(inputFileName).getParameterService();

    try {
      ExampleImageOutput.exec(parms, new ExampleImageOutput());
    } catch (SeisException e) {
      // TODO Auto-generated catch block
      LOGGER.log(Level.SEVERE,e.getMessage(),e);
    }

  }

  public ExampleImageOutput()  {
    // TODO Auto-generated constructor stub
  }

  @Override
  public void serialInit(ToolContext serialToolContext) {
    // TODO Auto-generated method stub

  }

  @Override
  public void parallelInit(ToolContext toolContext) {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean processVolume(ToolContext toolContext, ISeismicVolume input,
      ISeismicVolume output) {

    DistributedArray data = input.getDistributedArray();

    //DistributedArrayMosaicPlot.showAsModalDialog(
    //    data,"Basic Implementation - The whole volume");
    //Notice execution pauses here until you close the window.

    DAFrontendViewer display1 = new DAFrontendViewer(data);
    display1.show("New Implementation - The whole volume");

    //Zoom in on the middle 1/3
    int[] daShape = input.getLengths();
    int depthAxis = 0;
    int traceAxis = 1;
    int frameAxis = 2;

    System.out.println(Arrays.toString(daShape));

    long[] newOrigins = new long[] {100,100,100};
    long[] newDeltas = new long[] {1,1,1};

    //zoom first panel
    //display.setLogicalFrame(daShape[frameAxis]/3, 2*daShape[frameAxis]/3);
    //zoom second panel
    //display.setLogicalTraces(daShape[traceAxis]/3, 2*daShape[traceAxis]/3);
    //zoom third panel
    //display.setLogicalDepth(daShape[depthAxis]/3, 2*daShape[depthAxis]/3);
    DAFrontendViewer display2 = new DAFrontendViewer(data);    
    //display2.setLogicalTraces(100, 200);
    //display2.setLogicalDepth(300, 625);
    display2.setLogicalFrame((int)newOrigins[2], 200);

    display2.show("New Implementation - The whole volume");

    return false;
  }

  @Override
  public boolean outputVolume(ToolContext toolContext, ISeismicVolume output) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void parallelFinish(ToolContext toolContext) {
    // TODO Auto-generated method stub

  }

  @Override
  public void serialFinish(ToolContext toolContext) {
    // TODO Auto-generated method stub

  }

}

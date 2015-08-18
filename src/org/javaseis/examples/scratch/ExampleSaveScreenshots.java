package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;

import org.javaseis.examples.plot.DAFrontendViewer;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.distributed.DistributedArrayMosaicPlot;

public class ExampleSaveScreenshots extends StandAloneVolumeTool {

  public ExampleSaveScreenshots() {
    // TODO Auto-generated constructor stub
  }

  static ParameterService parms;

  public static void main(String[] args) throws FileNotFoundException, SeisException {
    String inputFileName = "segshotno1.js";
    String outputFileName = "testimg.js";
    parms = new FindTestData(inputFileName, outputFileName).getParameterService();
    parms.setParameter("outputFileMode", "create");
    ExampleSaveScreenshots.exec(parms, new ExampleSaveScreenshots());
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
  public boolean processVolume(ToolContext toolContext, ISeismicVolume input, ISeismicVolume output) {

    // DistributedArrayMosaicPlot.showAsModalDialog(
    // input.getDistributedArray(),"title");

    // save a screenshot of each volume in the .js folder
    DAFrontendViewer A = new DAFrontendViewer(input.getDistributedArray(), toolContext);

    A.setSliders(75, 25, 100);

    //To set ampFactor you must set the clip range
    
    A.setClipRange(-10, 10);
    A.setAmpFactor(1.0f);
    
    A.show("TEST");
    
    A.show("TEST");

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

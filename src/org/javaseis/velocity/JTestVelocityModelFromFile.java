package org.javaseis.velocity;

import java.io.FileNotFoundException;

import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.ToolState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;

public class JTestVelocityModelFromFile {

  ParameterService parms = null;
  IParallelContext pc = null;
  ToolState toolState = null;

  @Before
  public void InitializeDefaultInputs() {
    String file = "segsaltmodel.js";
    parms = null;
    try {
      parms = new FindTestData(file).getParameterService();
      parms.setParameter("vModelFilePath",file);
    } catch (FileNotFoundException e1) {
      Assert.fail("Unable to locate test datasets");
    }
    pc = new UniprocessorContext();
    toolState = new ToolState(parms,VelocityModelFromFile.class);  
  }

  @Test
  public void TestStringConstructor() {

    IVelocityModel vmff = null;
    try {
      String folder = parms.getParameter("inputFileSystem","null");
      String file = parms.getParameter("inputFileName","null");
      vmff = new VelocityModelFromFile(pc,
          folder,file);
    } catch (FileNotFoundException e) {
      Assert.fail("Reading dataset failed.");
    }
    Assert.assertNotNull(vmff);
  }

  @Test
  public void TestToolContextConstructor() {

    IVelocityModel vmff = null;
    try {
      vmff = new VelocityModelFromFile(pc,toolState);
    } catch (FileNotFoundException e) {
      System.out.println("Reading dataset failed.");
      e.printStackTrace();
    }
    Assert.assertNotNull(vmff);
  }
}

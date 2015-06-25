package org.javaseis.examples.tool.test;

import java.io.FileNotFoundException;

import org.javaseis.examples.tool.ExtractPWaveData;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

//TODO Simple tests to make sure the sub-setting method does what you expect.
public class JTestExtractPWaveData {
  
  private static final Logger LOGGER = 
      Logger.getLogger(JTestExtractPWaveData.class.getName());
  
  ParameterService parms;
  
  @Test
  public void toolExecutes() {
    String inputFileName = "100-rawsyntheticdata.js";
    String outputFileName = "100a-rawsynthpwaves.js";
    try {
      parms = new FindTestData(inputFileName,outputFileName).getParameterService();
      ExtractPWaveData.exec(parms,new ExtractPWaveData());
    } catch (FileNotFoundException e) {
      LOGGER.log(Level.INFO, "Unable to open test dataset",e);
      Assert.fail(e.getMessage());
    }
  }
  
  @Before
  public void generateTestData() {
    
  }
  
  @After
  public void removeTestDataAndOutput() {
    
  }
  
  @Test
  public void testSingleThread() {
    
  }
  
  @Test
  public void testDoubleThread() {
    
  }
  
  @Test
  public void testTripleThread() {
    
  }
  
}

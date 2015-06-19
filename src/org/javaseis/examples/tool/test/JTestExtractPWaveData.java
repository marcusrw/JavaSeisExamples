package org.javaseis.examples.tool.test;

import java.io.FileNotFoundException;

import org.javaseis.examples.tool.ExtractPWaveData;
import org.javaseis.test.testdata.FindTestData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

//TODO Simple tests to make sure the subsetting method does what you expect.
public class JTestExtractPWaveData {
  
  public static void main(String[] args) {
    String inputFileName = "100-rawsyntheticdata.js";
    String outputFileName = "100a-rawsynthpwaves.js";
    try {
      FindTestData ftd = new FindTestData(inputFileName,outputFileName);
      new ExtractPWaveData(ftd.getParameterService());
    } catch (FileNotFoundException e) {
      //TODO handle properly, more detail
      e.printStackTrace();
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

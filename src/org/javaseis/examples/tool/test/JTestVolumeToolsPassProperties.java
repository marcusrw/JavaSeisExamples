package org.javaseis.examples.tool.test;

import static org.javaseis.utils.Convert.*;
import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.examples.scratch.ExampleMigration;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.examples.tool.ExampleVolumeOutputTool;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.junit.Test;

public class JTestVolumeToolsPassProperties {

  private static final Logger LOGGER =
      Logger.getLogger(JTestVolumeToolsPassProperties.class.getName());

  private static ParameterService basicParameters() {
    String inputFileName = "segshotno1.js";
    String outputFileName = "fishfish.js";
    ParameterService parms = null;
    try {
      parms = new FindTestData(inputFileName,outputFileName).getParameterService();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    parms.setParameter(ToolState.TASK_COUNT, "1");
    return parms;
  }

  @Test
  public void test() {
    ParameterService parms = basicParameters();

    List<String> toolList = new ArrayList<String>();

    toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
    toolList.add(ExampleVolumeOutputTool.class.getCanonicalName());

    String[] toolArray = listToArray(toolList);

    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      fail("Tool failed to execute");
    }
  }
  
  @Test
  public void testReadAndWrite() {
    ParameterService parms = basicParameters();

    List<String> toolList = new ArrayList<String>();

    toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
    toolList.add(DistributedArrayViewer.class.getCanonicalName());
    toolList.add(ExampleVolumeOutputTool.class.getCanonicalName());

    String[] toolArray = listToArray(toolList);

    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      e.printStackTrace();
      LOGGER.log(Level.SEVERE,e.getMessage(),e);
      fail("Tool failed to execute");
    }
    
    String inputFileName = "fishfish.js";
    try {
      parms = new FindTestData(inputFileName).getParameterService();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      LOGGER.log(Level.SEVERE,e.getMessage(),e);
      fail("Failed to find TestData");
    }
    
    toolList = new ArrayList<String>();
    toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
    toolList.add(DistributedArrayViewer.class.getCanonicalName());
    toolArray = listToArray(toolList);
    
    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      e.printStackTrace();
      LOGGER.log(Level.SEVERE,e.getMessage(),e);
      fail("Second Execution failed");
    } 
  }
}

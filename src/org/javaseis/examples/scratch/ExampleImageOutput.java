package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.javaseis.examples.plot.DAFrontendViewer;
import org.javaseis.examples.plot.DistributedArrayViewer;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.examples.tool.ExampleVolumeOutputTool;
import org.javaseis.services.ParameterService;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.parallel.IParallelContext;

public class ExampleImageOutput implements IVolumeTool {

	private static final Logger LOGGER = Logger
			.getLogger(ExampleImageOutput.class.getName());

	static ParameterService parms;
	
	private static String[] listToArray(List<String> list) {
    String[] array = new String[list.size()];
    for (int k = 0 ; k < list.size() ; k++) {
      array[k] = list.get(k);
    }
    return array;
  }
	
	private static ParameterService basicParameters() {
    String inputFileName = "segshotno1.js";
    String outputFileName = "fishfish.js";
    ParameterService parms = null;
    try {
      parms = new FindTestData(inputFileName).getParameterService();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    parms.setParameter(ToolState.TASK_COUNT, "1");
    return parms;
  }

	public static void main(String[] args) throws FileNotFoundException {
		//String inputFileName = "segshotno1.js";
		//parms = new FindTestData(inputFileName).getParameterService();

		/*try {
			ExampleImageOutput.exec(parms, new ExampleImageOutput());
		} catch (SeisException e) {
			// TODO Auto-generated catch block
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}*/
		
		ParameterService parms = basicParameters();

    List<String> toolList = new ArrayList<String>();

    toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
    toolList.add(ExampleImageOutput.class.getCanonicalName());
    //toolList.add(ExampleVolumeOutputTool.class.getCanonicalName());

    String[] toolArray = listToArray(toolList);

    try {
      VolumeToolRunner.exec(parms, toolArray);
    } catch (SeisException e) {
      e.printStackTrace();
    }

	}

	public ExampleImageOutput() {
		// TODO Auto-generated constructor stub
	}
  @Override
  public void serialInit(ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolState, ISeismicVolume input, ISeismicVolume output)
      throws SeisException {
    DistributedArray data = input.getDistributedArray();

    // DistributedArrayMosaicPlot.showAsModalDialog(
    // data,"Basic Implementation - The whole volume");
    // Notice execution pauses here until you close the window.

    DAFrontendViewer display1 = new DAFrontendViewer(data, toolState);
    display1.show("New Implementation - The whole volume");

    // Zoom in on the middle 1/3
    int[] daShape = input.getLengths();
    int depthAxis = 0;
    int traceAxis = 1;
    int frameAxis = 2;

    System.out.println(Arrays.toString(daShape));

    long[] newOrigins = new long[] { 100, 100, 100 };
    long[] newDeltas = new long[] { 1, 1, 1 };

    // zoom first panel
    // display.setLogicalFrame(daShape[frameAxis]/3, 2*daShape[frameAxis]/3);
    // zoom second panel
    // display.setLogicalTraces(daShape[traceAxis]/3, 2*daShape[traceAxis]/3);
    // zoom third panel
    // display.setLogicalDepth(daShape[depthAxis]/3, 2*daShape[depthAxis]/3);
    DAFrontendViewer display2 = new DAFrontendViewer(data, toolState);
    // display2.setLogicalTraces(100, 200);
    // display2.setLogicalDepth(300, 625);
    //display2.setLogicalFrame((int) newOrigins[2], 200);

    display2.show("New Implementation - The whole volume");

    return false;
  }

  @Override
  public boolean outputVolume(IParallelContext pc, ToolState toolState, ISeismicVolume output) throws SeisException {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void parallelFinish(IParallelContext pc, ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void serialFinish(ToolState toolState) throws SeisException {
    // TODO Auto-generated method stub
    
  }

}

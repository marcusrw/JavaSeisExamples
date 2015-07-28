package org.javaseis.tool;

import java.util.concurrent.ExecutionException;

import org.javaseis.grid.GridDefinition;
import org.javaseis.services.ParameterService;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.javaseis.volume.SeismicVolume;

import beta.javaseis.distributed.FileSystemIOService;
import beta.javaseis.distributed.IDistributedIOService;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.ParallelTask;
import beta.javaseis.parallel.ParallelTaskExecutor;
import beta.javaseis.parallel.UniprocessorContext;

/**
 * StandAlone volume processing tool handler
 *
 * @author chuck
 *
 */
public class StandAloneVolumeTool implements IVolumeTool {

  public StandAloneVolumeTool() {
    // TODO Need default constructor so implementors don't have to provide one
  }

  public static ToolContext toolContext;

  public static IVolumeTool tool;

  public static IDistributedIOService ipio, opio;

  public static String inputFileSystem, inputFilePath, outputFileSystem,
  outputFilePath;

  public static boolean output = false;

  public static void exec(ParameterService parms, IVolumeTool standAloneTool) {
    // Tool to be run
    tool = standAloneTool;
    // Set a uniprocessor context
    IParallelContext upc = new UniprocessorContext();
    toolContext = new ToolContext(parms);
    toolContext.setParallelContext(upc);
    ipio = null;
    // Open input file if it is requested
    inputFileSystem = parms.getParameter("inputFileSystem", "null");
    if (inputFileSystem != "null") {
      inputFilePath = parms.getParameter("inputFilePath");
      try {
        ipio = new FileSystemIOService(upc, inputFileSystem);
        ipio.open(inputFilePath);
        toolContext.setInputGrid(ipio.getGridDefinition());
        ipio.close();
      } catch (SeisException ex) {
        ex.printStackTrace();
        throw new RuntimeException("Could not open inputPath: " + inputFilePath
            + "\n" + "    on inputFileSystem: " + ipio, ex.getCause());
      }

    }
    // Run the tool serial initialization step with the provided input
    // GridDefinition
    toolContext.setParameterService(parms);
    tool.serialInit(toolContext);
    // Get the output grid definition set by the tool
    GridDefinition outputGrid = toolContext.getOutputGrid();
    // Create or open output file if it was requested
    outputFileSystem = parms.getParameter("outputFileSystem", "null");
    output = false;
    // If no output specified, don't use
    if (outputGrid != null && outputFileSystem != "null") {
      output = true;
      outputFilePath = parms.getParameter("outputFilePath");
      String outputMode = parms.getParameter("outputMode", "create");
      // For create, make the file and then close it
      if (outputMode == "create") {
        try {
          opio = new FileSystemIOService(upc, outputFileSystem);
          opio.create(outputFilePath, outputGrid);
          opio.close();
        } catch (SeisException ex) {
          ex.printStackTrace();
          throw new RuntimeException("Could not create outputPath: "
              + outputFilePath + "\n" + "    on outputFileSystem: " + opio,
              ex.getCause());
        }
      }
      // Open for both open and create
      try {
        opio.open(outputFilePath);
        GridDefinition currentGrid = opio.getGridDefinition();
        opio.close();
        if (currentGrid.matches(outputGrid) == false)
          throw new RuntimeException("outputFilePath GridDefinition: "
              + outputGrid + "\n does not match toolContext GridDefinition: "
              + currentGrid);
      } catch (SeisException ex) {
        ex.printStackTrace();
        throw new RuntimeException("Could not open outputPath: "
            + outputFilePath + "\n" + "    on outputFileSystem: " + opio,
            ex.getCause());
      }
    }
    // Now run the tool handler which calls the implementor's methods
    int ntask = Integer.parseInt(parms.getParameter("threadCount", "1"));
    try {
      ParallelTaskExecutor.runTasks(StandAloneVolumeTask.class, ntask);
    } catch (ExecutionException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
    // Call the implementor's serial finish method to release any global
    // resources
    tool.serialFinish(toolContext);
  }

  /**
   * StandAlone tool handler for processing regular volumes from JavaSeis
   * data sets
   *
   * @author Chuck Mosher for JavaSeis.org
   */
  public static class StandAloneVolumeTask extends ParallelTask {
    @Override
    public void run() {
      // Get the parallel context
      IParallelContext pc = this.getParallelContext();
      // Add the parallel context to the toolContext
      toolContext.setParallelContext(pc);
      // Open the input and output file systems - should have been checked by
      // main
      try {
        ipio = new FileSystemIOService(pc, inputFileSystem);
        if (output) {
          opio = new FileSystemIOService(pc, outputFileSystem);
        }
        ipio.open(inputFilePath);
        if (output) {
          opio.open(outputFilePath);
        }
      } catch (SeisException ex) {
        throw new RuntimeException(ex.getCause());
      }
      // Get the input and output grids and store in the tool context
      toolContext.setInputGrid(ipio.getGridDefinition());
      if (output) {
        toolContext.setOutputGrid(opio.getGridDefinition());
      }
      // Call the implementing method for parallel initialization
      tool.parallelInit(toolContext);
      // Create the input and output seismic volumes
      ISeismicVolume inputVolume = new SeismicVolume(pc,
          ipio.getGridDefinition());
      //TODO toolContext.setInputVolume(inputVolume);
      //There is some class tangle here.  The toolcontext contains
      //the input/output volumes but they're never set, and the grid
      //definition, which the volumes already contain.  It's a challenge
      //to keep all of these items properly updated.
      //I believe this problem is related to the trouble with having
      //public static methods shared unnecessarily between tasks.
      ipio.setDistributedArray(inputVolume.getDistributedArray());
      ISeismicVolume outputVolume = inputVolume;
      if (output) {
        outputVolume = new SeismicVolume(pc,
            opio.getGridDefinition());
        //TODO  same as above.  This output volume is only settable here,
        //      and is never needed.
        //toolContext.setOutputVolume(outputVolume);
        opio.setDistributedArray(outputVolume.getDistributedArray());
      }
      // Loop over input volumes
      while (ipio.hasNext()) {
        // Get the next input volume
        ipio.next();
        inputVolume.setVolumePosition(ipio.getFilePosition());
        try {
          ipio.read();
        } catch (SeisException e) {
          if (pc.isMaster()) {
            e.printStackTrace();
          }
          throw new RuntimeException(e.getCause());
        }
        boolean hasOutput = tool.processVolume(toolContext, inputVolume,
            outputVolume);
        if (output && hasOutput) {
          opio.next();
          try {
            opio.write();
          } catch (SeisException e) {
            if (pc.isMaster()) {
              e.printStackTrace();
            }
            throw new RuntimeException(e.getCause());
          }
        }
      }
      if (output) {
        // Process any remaining output
        while (tool.outputVolume(toolContext, outputVolume)) {
          opio.next();
          try {
            opio.write();
          } catch (SeisException e) {
            if (pc.isMaster()) {
              e.printStackTrace();
            }
            throw new RuntimeException(e.getCause());
          }
        }
      }
      // Call the implementor's parallel finish method to release any local
      // resources
      
      //TODO hack.  throw away the outputVolume
      outputVolume = null;
      tool.parallelFinish(toolContext);
    }
  }

  @Override
  public void serialInit(ToolContext toolContext) {
    System.out.println("Executing StandAloneVolumeTool.serialInit() " 
        + "on task number " + toolContext.getParallelContext().rank()
        + "\nYou should override this method with serial initialization "
        + "steps, and/or to suppress this message.");
  }

  @Override
  public void parallelInit(ToolContext toolContext) {
    System.out.println("Executing StandAloneVolumeTool.parallelInit() " 
        + "on task number " + toolContext.getParallelContext().rank()
        + "\nYou should override this method with parallel initialization "
        + "steps, and/or to suppress this message.");
  }

  @Override
  public boolean processVolume(ToolContext toolContext, ISeismicVolume input,
      ISeismicVolume output) {
    System.out.println("Executing StandAloneVolumeTool.processVolume() " 
        + "on task number " + toolContext.getParallelContext().rank()
        + "\nYou should override this method if you want "
        + "your tool to do anything useful, or output any data\n");
    return false;
  }

  @Override
  public boolean outputVolume(ToolContext toolContext, ISeismicVolume output) {
    System.out.println("Executing StandAloneVolumeTool.outputVolume() " 
        + "on task number " + toolContext.getParallelContext().rank()
        + "\nYou should override this method if you want "
        + "your tool to do any post processing on the output, such as stacking."
        + "\n");
    return false;
  }

  @Override
  public void parallelFinish(ToolContext toolContext) {
    System.out.println("Executing StandAloneVolumeTool.parallelFinish() " 
        + "on task number " + toolContext.getParallelContext().rank()
        + "\nYou should override this method with parallel cleanup "
        + "steps, and/or to suppress this message.");
  }

  @Override
  public void serialFinish(ToolContext toolContext) {
    System.out.println("Executing StandAloneVolumeTool.parallelFinish() " 
        + "on task number " + toolContext.getParallelContext().rank()
        + "\nYou should override this method with serial cleanup "
        + "steps, and/or to suppress this message.");
  }
}

package org.javaseis.examples.scratch;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import org.javaseis.grid.GridDefinition;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.javaseis.volume.SeismicVolume;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.distributed.FileSystemIOService;
import beta.javaseis.distributed.IDistributedIOService;
import beta.javaseis.parallel.IParallelContext;

public class VolumeEdgeIO {

  private static int volumeNumber = 0; // Start at 0 volume
  private ToolContext toolContext;
  private String originalPN;
  private IDistributedIOService ipio = null;
  private VelocityModelFromFile vff = null;
  private ISeismicVolume seismicInput;
  private ICheckGrids checkGrid;
  private PrintWriter out = null;;

  public VolumeEdgeIO(IParallelContext pc, ToolContext toolContext) {

    try {
      ipio = new FileSystemIOService(pc, toolContext.getParameter(ToolContext.INPUT_FILE_SYSTEM));
    } catch (SeisException e) {
      e.printStackTrace();
    }

    // Open your chosen file for reading
    try {
      ipio.open(toolContext.getParameter(ToolContext.INPUT_FILE_PATH));
    } catch (SeisException e) {
      e.printStackTrace();
    }

    originalPN = toolContext.getParameter(ToolContext.OUTPUT_FILE_SYSTEM)
        + "//"
        + toolContext.getParameter(ToolContext.OUTPUT_FILE_PATH)
        + "//vEdge";

    //System.out.println(originalPN);

    // Get the global grid definition
    GridDefinition globalGrid = ipio.getGridDefinition();

    // Now you have enough to make a SeismicVolume
    ISeismicVolume inputVolume = new SeismicVolume(pc, globalGrid);
    this.seismicInput = inputVolume;

    // match the IO's DA with the Volume's DA
    ipio.setDistributedArray(inputVolume.getDistributedArray());

    // Get the Input and tool context
    this.toolContext = toolContext;

  }

  // Generate the file path for each volume
  private String generateNextPath() {
    // take the original file path and set it to the new one
    String newPN = originalPN;

    // the VolNum
    newPN += volumeNumber;

    // Inc the global volume number
    volumeNumber++;

    // Make it a txt file
    newPN += ".txt";

    // Expected string form [generateNextPath(0) = //tmp//vEdge0.txt]
    return newPN;
  }

  // Write the grid data to output specific file
  private void writeGridInformation(PrintWriter out) {
    // Iterate through the DistArray get the volumes
    DistributedArray curArray = seismicInput.getDistributedArray();

    // get the start of the current volume
    int[] globalPosIndex = seismicInput.getVolumePosition();
    int[] volumePosIndex = new int[3];

    // Iterate over the Volume - Scope = 3
    DistributedArrayPositionIterator itrInputArr 
    = new DistributedArrayPositionIterator(curArray, volumePosIndex,
        DistributedArrayPositionIterator.FORWARD, 3);

    // source and receiver arrays
    double[] rXYZ = new double[3];

    out.println("[getVolumeEdges]: Volume Index: "
        + Arrays.toString(globalPosIndex));

    out.println("[getVolumeEdges]: Axis Order: "
        + Arrays.toString(checkGrid.getAxisOrder()));

    // Start location of volume
    out.println("[getVolumeEdges]: Volume Source Location: "
        + Arrays.toString(checkGrid.getSourceXYZ()));

    while (itrInputArr.hasNext()) {
      globalPosIndex[0] = 0;
      volumePosIndex = itrInputArr.next();
      for (int k = 1; k < 3; k++) {
        globalPosIndex[k] = volumePosIndex[k];
      }

      // Get the receiver & source at [0, axislength - 1, axislength - 1,
      // v]
      long X = seismicInput.getGlobalGrid().getAxisLength(checkGrid.getAxisOrder()[0]) - 1;
      long Y = seismicInput.getGlobalGrid().getAxisLength(checkGrid.getAxisOrder()[1]) - 1;

      // Example x = 0, y = 0;
      globalPosIndex[1] = 0;
      globalPosIndex[2] = 0;

      rXYZ = checkGrid.getReceiverXYZ(globalPosIndex);

      out.println("[getVolumeEdges]: [s,t,f,v]: " + Arrays.toString(globalPosIndex) + " \tReceiverXYZ: "
          + Arrays.toString(rXYZ));

      // Example x = 200, y = 0
      globalPosIndex[1] = (int) X;
      globalPosIndex[2] = 0;

      rXYZ = checkGrid.getReceiverXYZ(globalPosIndex);

      // Get bottom right coords
      out.println("[getVolumeEdges]: [s,t,f,v]: " 
          + Arrays.toString(globalPosIndex) + " \tReceiverXYZ: "
          + Arrays.toString(rXYZ));

      // Get top left
      // Example x = 0, y = 200
      globalPosIndex[1] = 0;
      globalPosIndex[2] = (int) Y;

      rXYZ = checkGrid.getReceiverXYZ(globalPosIndex);
      out.println("[getVolumeEdges]: [s,t,f,v]: " 
          + Arrays.toString(globalPosIndex) + " \tReceiverXYZ: "
          + Arrays.toString(rXYZ));

      // Get top right
      // Example x = 200, y = 200
      globalPosIndex[1] = (int) X;
      globalPosIndex[2] = (int) Y;

      rXYZ = checkGrid.getReceiverXYZ(globalPosIndex);
      out.println("[getVolumeEdges]: [s,t,f,v]: " 
          + Arrays.toString(globalPosIndex) + " \tReceiverXYZ: "
          + Arrays.toString(rXYZ));

    }

  }

  // Write the Velo data to output specific file
  private void writeVelocityInformation(PrintWriter out) {
    GridDefinition vmodelGrid = vff.vmodelGrid;
    // System.out.println("[VelocityInformation:] " +
    // vmodelGrid.toString());
    // System.out.println("[VelocityInformation:] " +
    // vmodelGrid.getNumDimensions());

    int[] veloPos = new int[3];
    double[] veloValue = new double[3];

    int XAxis = 2;
    int YAxis = 1;
    int ZAxis = 0;

    long lengthX = vmodelGrid.getAxisLength(XAxis);
    long lengthY = vmodelGrid.getAxisLength(YAxis);

    veloPos[0] = 0; // velocity time axis
    veloPos[1] = 0; // velocity cross axis - y
    veloPos[2] = 0; // velocity inline axis - x

    out.println("Velocity Grid Information");

    // Velo at [0,0,0]
    veloValue[0] = vmodelGrid.getAxisPhysicalOrigin(XAxis) + veloPos[2] * vmodelGrid.getAxisPhysicalDelta(XAxis);
    veloValue[1] = vmodelGrid.getAxisPhysicalOrigin(YAxis) + veloPos[1] * vmodelGrid.getAxisPhysicalDelta(YAxis);
    veloValue[2] = vmodelGrid.getAxisPhysicalOrigin(ZAxis) + veloPos[0] * vmodelGrid.getAxisPhysicalDelta(ZAxis);

    out.println("[VelocityInformation]: " + "[s,t,f]: " + Arrays.toString(veloPos) + " \t[x, y, d]: "
        + Arrays.toString(veloValue));

    // Velo at [0, 675, 0]
    veloPos[1] = (int) (lengthY - 1); // velocity cross axis - y
    veloPos[2] = 0; // velocity inline axis - x
    veloValue[0] = vmodelGrid.getAxisPhysicalOrigin(XAxis) + veloPos[2] * vmodelGrid.getAxisPhysicalDelta(XAxis);
    veloValue[1] = vmodelGrid.getAxisPhysicalOrigin(YAxis) + veloPos[1] * vmodelGrid.getAxisPhysicalDelta(YAxis);
    veloValue[2] = vmodelGrid.getAxisPhysicalOrigin(ZAxis) + veloPos[0] * vmodelGrid.getAxisPhysicalDelta(ZAxis);

    out.println("[VelocityInformation]: " + "[s,t,f]: " + Arrays.toString(veloPos) + " \t[x, y, d]: "
        + Arrays.toString(veloValue));

    // Velo at [0, 0, 675]
    veloPos[1] = 0; // velocity cross axis - y
    veloPos[2] = (int) (lengthX - 1); // velocity inline axis - x
    veloValue[0] = vmodelGrid.getAxisPhysicalOrigin(XAxis) + veloPos[2] * vmodelGrid.getAxisPhysicalDelta(XAxis);
    veloValue[1] = vmodelGrid.getAxisPhysicalOrigin(YAxis) + veloPos[1] * vmodelGrid.getAxisPhysicalDelta(YAxis);
    veloValue[2] = vmodelGrid.getAxisPhysicalOrigin(ZAxis) + veloPos[0] * vmodelGrid.getAxisPhysicalDelta(ZAxis);

    out.println("[VelocityInformation]: " + "[s,t,f]: " + Arrays.toString(veloPos) + " \t[x, y, d]: "
        + Arrays.toString(veloValue));

    // Velo at [0, 675, 675]
    veloPos[1] = (int) (lengthY - 1); // velocity cross axis - y
    veloPos[2] = (int) (lengthX - 1); // velocity inline axis - x
    veloValue[0] = vmodelGrid.getAxisPhysicalOrigin(XAxis)
        + veloPos[2] * vmodelGrid.getAxisPhysicalDelta(XAxis);
    veloValue[1] = vmodelGrid.getAxisPhysicalOrigin(YAxis)
        + veloPos[1] * vmodelGrid.getAxisPhysicalDelta(YAxis);
    veloValue[2] = vmodelGrid.getAxisPhysicalOrigin(ZAxis)
        + veloPos[0] * vmodelGrid.getAxisPhysicalDelta(ZAxis);

    out.println("[VelocityInformation]: " + "[s,t,f]: "
    + Arrays.toString(veloPos) + " \t[x, y, d]: "
        + Arrays.toString(veloValue));

    out.println(" ");

  }

  private void initalizeWriter(){
    File f = new File(originalPN);
    if (f.exists() && !f.isDirectory()) {
      f.delete();
    }
    try {
      FileWriter fWrtr = new FileWriter(generateNextPath());
      out = new PrintWriter(fWrtr);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void closeWriter(){
    if (out != null) {
      out.close();
    }
  }

  // Write to external file
  public void write() {
    initalizeWriter();

    // Write the Volume Information to the file
    try {
      vff = new VelocityModelFromFile(toolContext);
      vff.open("r");
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    writeVelocityInformation(out);

    while (ipio.hasNext()) {
      ipio.next();
      seismicInput.setVolumePosition(ipio.getFilePosition());

      try {
        ipio.read();
      } catch (SeisException e) {
        e.printStackTrace();
      }

      checkGrid = new CheckGrids(seismicInput, toolContext);

      // Write the Grid Information to the file
      writeGridInformation(out);

      // for Readability
      out.println(" ");
    }

    closeWriter();
  }

}
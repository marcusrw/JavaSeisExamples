package org.javaseis.grid;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

import org.javaseis.grid.GridDefinition;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.properties.AxisLabel;
import org.javaseis.properties.DataDomain;
import org.javaseis.properties.Units;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;
import org.javaseis.velocity.VelocityModelFromFile;
import org.javaseis.volume.ISeismicVolume;
import org.javaseis.volume.SeismicVolume;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.distributed.FileSystemIOService;
import beta.javaseis.distributed.IDistributedIOService;
import beta.javaseis.parallel.IParallelContext;

public class VolumeEdgeIO {

  private int volumeNumber = 0; // Start at 0 volume
  private ToolContext toolContext;
  private String originalPN;
  private IDistributedIOService ipio = null;
  private VelocityModelFromFile vff = null;
  private ISeismicVolume seismicInput;
  private ICheckedGrid checkGrid;

  // Writer vars
  private PrintWriter out = null;

  // Reader vars
  private FileReader FiletoRead = null;
  private BufferedReader bufRead = null;
  private ArrayList<AxisLabel> ListofAxisLabels = new ArrayList<AxisLabel>();

  public VolumeEdgeIO() {

  }

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

    originalPN = toolContext.getParameter(ToolContext.OUTPUT_FILE_SYSTEM) + "//"
        + toolContext.getParameter(ToolContext.OUTPUT_FILE_PATH) + "//vEdge";

    // System.out.println(originalPN);

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
  // Change volumeNumber to static if you want this functionallity
  private String generateNextPath() {
    // take the original file path and set it to the new one
    String newPN = originalPN;

    // the VolNum
    newPN += volumeNumber;

    // Inc in writeGridInfo
    // volumeNumber++;

    // Make it a txt file
    newPN += ".txt";

    // Expected string form [generateNextPath(0) = //tmp//vEdge0.txt]
    // generatedFilePaths.add(newPN);
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
    DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(curArray, volumePosIndex,
        DistributedArrayPositionIterator.FORWARD, 3);

    // source and receiver arrays
    // double[] rXYZ = new double[3];

    out.println("<VolumeGlobalGridstr>");

    out.println("{ getVolumeEdges } : Volume Index: " + Arrays.toString(globalPosIndex));

    out.println("{ getVolumeEdges } : Axis Order: " + Arrays.toString(checkGrid.getAxisOrder()));

    // Start location of volume
    // out.println("{ getVolumeEdges } : Volume Source Location: " +
    // Arrays.toString(checkGrid.getSourceXYZ()));

    while (itrInputArr.hasNext()) {
      // out.println("<VolumeGridstr>");
      globalPosIndex[0] = 0;
      volumePosIndex = itrInputArr.next();
      for (int k = 1; k < 3; k++) {
        globalPosIndex[k] = volumePosIndex[k];
      }

      out.println(checkGrid.toString());
      // out.println("<VolumeGridend>");

    }

    out.println("<VolumeGlobalGridend>");

  }

  // Write the Velo data to output specific file
  private void writeVelocityInformation(PrintWriter out) {
    GridDefinition vmodelGrid = vff.getVModelGrid();
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

    // out.println("VolumeNum : " + " [ " + Arrays.toString(globalPosIndex) + "
    // ] ");
    out.println("<VelocityGridstr>\n" + vmodelGrid.toString());
    out.println("<VelocityGridend>");

    // volumeNumber++;
  }

  private void initalizeWriter() {
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

  private void closeWriter() {
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

      checkGrid = new GridFromHeaders(seismicInput, toolContext);

      // Write the Grid Information to the file
      writeGridInformation(out);

      // for Readability
      out.println(" ");
    }

    closeWriter();
  }

  private void initalizeReader() {
    try {
      FiletoRead = new FileReader(generateNextPath());
      bufRead = new BufferedReader(FiletoRead);
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void closeReader() {
    try {
      bufRead.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    try {
      FiletoRead.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  /**
   * This function read the total number of volumes
   * 
   * @return Total Number of Volumes
   */
  public int readVolume() {
    initalizeReader();

    String myLine = null;
    int vNum = 0;
    try {
      while ((myLine = bufRead.readLine()) != null) {

        String[] array1 = myLine.split(" ");

        // System.out.println(Arrays.toString(array1));
        for (int i = 0; i < array1.length; i++) {
          if (array1[i].equalsIgnoreCase("VolumeNum")) {
            vNum = Integer.parseInt(array1[4]);
          }
        }
      }

    } catch (NumberFormatException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    closeReader();
    return vNum;
  }

  private int[] strArrayToIntArray(String[] A) {
    int[] results = new int[A.length];

    for (int i = 0; i < A.length; i++) {
      try {
        results[i] = Integer.parseInt(A[i]);
      } catch (NumberFormatException nfe) {
      }
      ;
    }

    return results;

  }

  private boolean deepCompare(int[] A, int[] B) {
    Object[] arr1 = { A };
    Object[] arr2 = { B };
    if (Arrays.deepEquals(arr1, arr2)) {
      return true;
    } else {
      return false;
    }

  }

  /**
   * This function sets your read pointer to the Volume Index line
   * 
   * @param vNm
   *          Volume Index
   */
  private BufferedReader setReadPtr(int[] vNm, BufferedReader readBuffer1) {
    initalizeReader();
    String myLine = null;
    try {
      while ((myLine = readBuffer1.readLine()) != null) {
        String[] array1 = myLine.split(" ");
        String[] array2 = "<VolumeGlobalGridstr>".split(" ");

        for (int i = 0, j = 0; i < array1.length && j < array2.length; i++, j++) {
          if (array1[i].equalsIgnoreCase(array2[j])) {
            // location is found
            // Volume Index Line
            myLine = readBuffer1.readLine();
            String[] volPosCurStr = myLine.replace("{ getVolumeEdges } : Volume Index: ", "").replaceAll(" ", "")
                .replaceAll("\\[", "").replaceAll("\\]", "").split(",");
            int[] volPosCur = strArrayToIntArray(volPosCurStr);
            if (deepCompare(volPosCur, vNm)) {
              // return readBuffer
              // System.out.println("Element FOUND");
              return readBuffer1;
            }

          }
        }
      }

      // If volume specified does not exist in file
      if (myLine == null) {
        throw new ArithmeticException("Volume Specified Cannot Be Found!");
      }

    } catch (NumberFormatException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      closeReader();
    }
    return null;
  }

  /**
   * Returns the Axis Order for a Specific Volume
   * 
   * @param vNm
   *          Volume Index
   * @return int[]
   */
  public int[] getAxisOrder(int[] vNm) {
    initalizeReader();
    String myLine = null;

    // This Sets your pointer to the volume index vNm
    bufRead = setReadPtr(vNm, bufRead);

    try {
      myLine = bufRead.readLine();
      String[] axisOrderStr = myLine.replace("{ getVolumeEdges } : Axis Order: ", "").replaceAll(" ", "")
          .replaceAll("\\[", "").replaceAll("\\]", "").split(",");
      int[] axisOrderInt = strArrayToIntArray(axisOrderStr);
      return axisOrderInt;
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      closeReader();
    }

    // Should never reach here
    return null;
  }

  /**
   * Returns a GridDefinition of N-Dimensions
   * 
   * @param bufferReader
   *          - Reader
   * @param numDims
   *          - Number of Dimensions
   * @return Volume GridDefinition
   * @throws IOException
   */
  private GridDefinition readAxisDefinitionFromFile(BufferedReader bufferReader, int numDims) throws IOException {
    // Axes of the Grid
    ArrayList<AxisDefinition> Axes = new ArrayList<AxisDefinition>();
    for (int k = 0; k < numDims; k++) {

      // Since Axis has 2 line per axis in file
      String axisline1 = bufferReader.readLine();
      String axisline2 = bufferReader.readLine();

      String[] myLineA = axisline1.split("=? ");
      String[] myLineB = axisline2.split("=? ");
      String[] myLineC = null;
      String[] myLineD = null;

      ArrayList<String> AxisElementList = new ArrayList<String>();

      for (int ii = 0; ii < myLineA.length; ii++) {
        myLineC = myLineA[ii].split("=");
        for (int jj = 0; jj < myLineC.length - 1; jj++) {
          // System.out.println(myLineC[jj+1]);
          AxisElementList.add(myLineC[jj + 1]);
        }
      }

      for (int jj = 0; jj < myLineB.length; jj++) {
        myLineD = myLineB[jj].split("=");
        for (int ii = 0; ii < myLineD.length - 1; ii++) {
          // System.out.println(myLineD[ii+1]);
          AxisElementList.add(myLineD[ii + 1]);
        }
      }

      // Create the Axis here
      AxisLabel label = covertStringtoAxisLabel(AxisElementList.get(0));
      Units units = Units.get(AxisElementList.get(1));
      DataDomain datadomain = DataDomain.get(AxisElementList.get(2));
      long length = Long.parseLong(AxisElementList.get(3));
      long logicalOrigin = Long.parseLong(AxisElementList.get(4));
      long logicalDelta = Long.parseLong(AxisElementList.get(5));
      double physicalOrigin = Double.parseDouble(AxisElementList.get(6));
      double physicalDelta = Double.parseDouble(AxisElementList.get(7));

      Axes.add(new AxisDefinition(label, units, datadomain, length, logicalOrigin, logicalDelta, physicalOrigin,
          physicalDelta));
    }
    AxisDefinition[] arrayAxis = new AxisDefinition[Axes.size()];
    for (int yy = 0; yy < Axes.size(); yy++) {
      arrayAxis[yy] = Axes.get(yy);
    }

    return new GridDefinition(numDims, arrayAxis);
  }

  /**
   * Returns you the grid for a specific Volume Index
   * 
   * @param vNm
   *          Volume Index
   * @return GridDefinition for that specific volume
   */
  public GridDefinition readVolumeGrid(int[] vNm) {
    initalizeReader();
    String myLine = null;
    // Dims of the Grid
    int numOfDims = -1;

    // Set the read ptr to the volume index specified
    bufRead = setReadPtr(vNm, bufRead);

    try {
      myLine = bufRead.readLine();
      String numDimLine = bufRead.readLine();
      String[] dimStrArray = numDimLine.split(" ");
      // Assumed that this is where dim number is stored based on toString
      numOfDims = Integer.parseInt(dimStrArray[2]);

      return readAxisDefinitionFromFile(bufRead, numOfDims);

    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      closeReader();
    }

    return null;

  }

  /**
   * Read Velocity Grid from file
   * 
   * Assumed Velocity is at the start of file
   * 
   * @return GridDefinition of Velocity Grid
   */
  public GridDefinition readVelocityGrid() {
    initalizeReader();
    String myLine = null;
    int numOfDims = -1;

    // Don't set any pointers as we only have one velo model
    // setVolumePtr(vN, bufRead);

    try {
      // First line of file
      myLine = bufRead.readLine();

      String[] array1 = myLine.split(" ");
      String[] array2 = "<VelocityGridstr>".split(" ");

      // numOfDims to be loaded
      for (int i = 0, j = 0; i < array1.length && j < array2.length; i++, j++) {
        if (array1[i].equalsIgnoreCase(array2[j])) {
          // Second line is the start of our Velocity Grid Def
          String secondLine = bufRead.readLine();

          String[] dimStrArray = secondLine.split(" ");
          numOfDims = Integer.parseInt(dimStrArray[2]);

          return readAxisDefinitionFromFile(bufRead, numOfDims);
        }
      }

    } catch (NumberFormatException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      closeReader();
    }
    return null;
  }

  private AxisLabel covertStringtoAxisLabel(String string) {

    ListofAxisLabels.add(AxisLabel.UNDEFINED);
    ListofAxisLabels.add(AxisLabel.TIME);
    ListofAxisLabels.add(AxisLabel.DEPTH);
    ListofAxisLabels.add(AxisLabel.OFFSET);
    ListofAxisLabels.add(AxisLabel.OFFSET_BIN);
    ListofAxisLabels.add(AxisLabel.CROSSLINE);
    ListofAxisLabels.add(AxisLabel.INLINE);
    ListofAxisLabels.add(AxisLabel.SOURCE);
    ListofAxisLabels.add(AxisLabel.CMP);
    ListofAxisLabels.add(AxisLabel.RECEIVER);
    ListofAxisLabels.add(AxisLabel.RECEIVER_LINE);
    ListofAxisLabels.add(AxisLabel.CHANNEL);
    ListofAxisLabels.add(AxisLabel.SAIL_LINE);
    ListofAxisLabels.add(AxisLabel.VOLUME);
    ListofAxisLabels.add(AxisLabel.ANGLE_BIN);
    // TODO: find out what is discript actually is
    ListofAxisLabels.add(new AxisLabel("R_XLINE", "Fill this"));
    ListofAxisLabels.add(new AxisLabel("R_ILINE", "Fill this"));

    for (int i = 0; i < ListofAxisLabels.size(); i++) {
      String AxisString = ListofAxisLabels.get(i).getName();
      if (AxisString.equalsIgnoreCase(string)) {
        return ListofAxisLabels.get(i);
      }
    }
    return null;
  }

  public void read() throws IOException {
    initalizeReader();

    closeReader();
  }

}
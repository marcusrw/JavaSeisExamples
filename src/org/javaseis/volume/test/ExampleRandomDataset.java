package org.javaseis.volume.test;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;

import org.javaseis.io.Seisio;
import org.javaseis.array.MultiArray;
import org.javaseis.examples.plot.JavaSeisMovieRunner;
import org.javaseis.grid.GridDefinition;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.properties.AxisLabel;
import org.javaseis.properties.DataDomain;
import org.javaseis.util.SeisException;
import org.javaseis.properties.Units;

import edu.mines.jtk.util.ArrayMath;

/**
 * Create an example JavaSeis data set for testing purposes.
 * 
 * This class only makes and cleans up the data set.
 * 
 * @author Marcus Wilson 2015
 *
 */
public class ExampleRandomDataset {

  private static final int DEFAULT_NUM_DIMENSIONS = 5;
  public static final String defaultPath = defaultDataLocation();
  private double[] defaultPhysicalDeltas = new double[] {2, 12, 100, 1, 1};
  private double[] defaultPhysicalOrigins = new double[] {0, 0, 0, 1, 1};
  private long[] defaultLogicalDeltas = new long[] {1, 1, 1, 1, 1};
  private long[] defaultLogicalOrigins = new long[] {0, 1, 1, 1, 1};
  private long[] defaultGridDimensions = new long[] {900, 64, 47, 9, 4};

  public String dataFullPath;

  public Seisio seisio;
  public GridDefinition gridDefinition;

  //basic sanity check only.  The rest of the tests are in the corresponding JTest
  public static void main(String[] args) {
    ExampleRandomDataset test = new ExampleRandomDataset();
    JavaSeisMovieRunner.showMovie(defaultPath);
    try {
      test.deleteJavaSeisData();
    } catch (SeisException e) {
      System.out.println("Unable to delete dataset");
      e.printStackTrace();
    }

  }

  //Noarg constructor
  public ExampleRandomDataset() {
    dataFullPath = defaultPath;
    exceptionIfFileAlreadyExists();
    AxisDefinition[] axes = defaultAxisDefinitions();
    gridDefinition = makeGridDefinition(axes);
    try {
      seisio = createSeisIO(gridDefinition);
      createJavaSeisData(seisio);
      insertRandomData(seisio);
    } catch (SeisException e) {
      e.printStackTrace();
    }
  }

  private static String defaultDataLocation() {
    String dataFolder = System.getProperty("java.io.tmpdir");
    String dataFileName = "temp1758383.js";
    String dataFullPath = dataFolder + File.separator + dataFileName;
    return dataFullPath;
  }

  //TODO replace UnsupportedOperationException with a javaseis specific exception
  // say DatasetAlreadyExistsException
  private void exceptionIfFileAlreadyExists() {
    assert dataFullPath != null;
    if (dataSetExists(dataFullPath)) {
      throw new UnsupportedOperationException(
          "Unable to create data.  File already exists");
    }
  }

  private boolean dataSetExists(String path) {
    File datapath = new File(path);
    return (datapath.exists());
  }

  private AxisDefinition[] defaultAxisDefinitions() {

    AxisLabel[] labels = defaultAxisLabels();
    Units[] units = defaultUnits();
    DataDomain[] domains = defaultDomains();
    long[] gridSize = defaultGridDimensions;
    long[] lorigins = defaultLogicalOrigins;
    long[] ldeltas = defaultLogicalDeltas;
    double[] porigins = defaultPhysicalOrigins;
    double[] pdeltas = defaultPhysicalDeltas;

    int numDimensions = checkDimensions();

    AxisDefinition[] axisDefinitions = new AxisDefinition[numDimensions];
    for (int k = 0 ; k < numDimensions ; k++) {
      axisDefinitions[k] = new AxisDefinition(
          labels[k],
          units[k],
          domains[k],
          gridSize[k],
          lorigins[k],
          ldeltas[k],
          porigins[k],
          pdeltas[k]);
    }
    return axisDefinitions;
  }

  private int checkDimensions() {
    //TODO fix later.  Check all argument arrays have same length
    return DEFAULT_NUM_DIMENSIONS;
  }

  private AxisLabel[] defaultAxisLabels() {
    return AxisLabel.getDefault(DEFAULT_NUM_DIMENSIONS);
  }

  private Units[] defaultUnits() {
    return new Units[] {Units.MS,Units.M,Units.M,Units.NULL,Units.NULL};
  }

  private DataDomain[] defaultDomains() {
    return new DataDomain[] {
        DataDomain.TIME,
        DataDomain.SPACE,
        DataDomain.SPACE,
        DataDomain.NULL,
        DataDomain.NULL};
  }

  private GridDefinition makeGridDefinition(AxisDefinition[] axes) {
    return new GridDefinition(axes.length,axes);   
  }

  private Seisio createSeisIO(GridDefinition grid) throws SeisException {
    return new Seisio(dataFullPath,grid);
  }

  private void createJavaSeisData(Seisio sio) throws SeisException {
    sio.create();
    //TODO convert printlns into logger info
    //System.out.println("Created new JavaSeis file at " + dataFullPath);
  }

  private void insertRandomData(Seisio sio) throws SeisException {
    MultiArray workFrame = initializeWorkFrame();
    Iterator<int[]> frameIterator = frameIterator();
    while (frameIterator.hasNext()) {
      int[] nextIndex = frameIterator.next();
      generateRandomData(nextIndex,workFrame);
    }
  }

  private MultiArray initializeWorkFrame() {
    long[] axisLengths = gridDefinition.getAxisLengths();
    MultiArray workFrame = new MultiArray(2,float.class,
        new int[] {(int)axisLengths[0],(int)axisLengths[1]});
    workFrame.allocate();
    return workFrame;
  }

  public Iterator<int[]> frameIterator() {
    return new FrameIterator();
  }

  private void generateRandomData(int[] index,MultiArray workFrame) throws SeisException {
    int[] framesize = workFrame.getShape();
    //System.out.println("Populating Frame at index " + Arrays.toString(index));
    float[] trc = new float[framesize[0]];
    for (int trcIndex = 0; trcIndex < framesize[1]; trcIndex++) {
      ArrayMath.rand(trc);
      index[1] = trcIndex;
      workFrame.putTrace(trc, index);
    }
    seisio.writeMultiArray(workFrame,index);

  }

  public void deleteJavaSeisData() throws SeisException {
    seisio.delete();
    seisio.close();
  }

  //making my own frame iterator because I don't think the one in
  //Seisio works.  It looks like it populates the dataset, but
  //the indices it returns don't look right.
  private class FrameIterator implements Iterator<int[]> {

    private static final int FRAME_INDEX = 2;
    private int[] currentIndex;
    private long[] arraySize;

    private FrameIterator() {
      arraySize = gridDefinition.getAxisLengths();
      currentIndex = new int[arraySize.length];
      currentIndex[FRAME_INDEX] = -1;
      //System.out.println("Initial Index: " + Arrays.toString(currentIndex));
    }

    @Override
    public boolean hasNext() {
      for (int level = FRAME_INDEX ; level < arraySize.length ; level++) {
        if (currentIndex[level] < arraySize[level] - 1) return true;
      }
      return false;
    }

    @Override
    public int[] next() {
      incrementIndex(FRAME_INDEX);
      for (int level = 0 ; level < FRAME_INDEX ; level++) {
        currentIndex[level] = 0;
      }
      return currentIndex;
    }

    private void incrementIndex(int level) {
      if (level >= arraySize.length) {
        throw new ArrayIndexOutOfBoundsException(
            "Attempt to access dataset dimension that doesn't exist");
      }
      if (currentIndex[level] < arraySize[level]-1) {
        currentIndex[level]++;
      } else {
        //reset the current level to 0, and carry to the next level
        currentIndex[level] = 0;
        incrementIndex(level+1);
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException(
          "Remove is not supported by this Iterator.");      
    }
  }
}

package org.javaseis.volume.test;

import java.io.File;

import org.javaseis.io.Seisio;
import org.javaseis.examples.plot.JavaSeisMovieRunner;
import org.javaseis.grid.GridDefinition;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.properties.AxisLabel;
import org.javaseis.properties.DataDomain;
import org.javaseis.util.SeisException;
import org.javaseis.properties.Units;

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
    ExampleRandomDataset dataset = new ExampleRandomDataset();
    JavaSeisMovieRunner.showMovie(defaultPath);
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
  }
  
  private void insertRandomData(Seisio sio) {
    //TODO implement
  }

  public void deleteJavaSeisData() {
    seisio.delete();
  }
}

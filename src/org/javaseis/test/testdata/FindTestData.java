package org.javaseis.test.testdata;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.services.ParameterService;

/**
 * Convenience class to automate the task of finding the path to a requested
 * dataset. For test automation purposes.
 * 
 * @author Marcus Wilson
 *
 */
public class FindTestData {

  private static final Logger LOGGER =
      Logger.getLogger(FindTestData.class.getName());


  private static final String INPUT_FILE_SYSTEM = "inputFileSystem";
  private static final String INPUT_FILE_PATH = "inputFilePath";
  private static final String INPUT_FILE_NAME = "inputFileName";

  private static final String OUTPUT_FILE_SYSTEM = "outputFileSystem";
  private static final String OUTPUT_FILE_PATH = "outputFilePath";
  private static final String OUTPUT_FILE_NAME = "outputFileName";
  
  //NOTE:  People tend to change what they want these fields to be called,
  //       so I overloaded it.

  private ParameterService parms;

  // List of possible folders where data can be found.
  private final String[] candidates = new String[] {
      "/",
      System.getProperty("user.home") + File.separator + "javaseis",
      System.getProperty("java.io.tmpdir"),
      "/home/seisspace/data/testarea",
      "/Data/Projects/SEG-ACTI"
  };

  public FindTestData(ParameterService parms) throws NoSuchFieldException, FileNotFoundException {
    this.parms = parms;
    if (!parameterIsSet(parms, INPUT_FILE_PATH)) {
      throw new NoSuchFieldException(
          "You need to specify an input filename" + "\n in the Parameter Service field " + INPUT_FILE_PATH);
    }
    findAndSetDataFolder(parms, parms.getParameter(INPUT_FILE_PATH));
  }

  public FindTestData(String inputFilePath) throws FileNotFoundException {
    initializeParameterServiceAndFindInput(inputFilePath);
    //unset the output file parameters so nobody thinks we have output
    parms.setParameter(OUTPUT_FILE_SYSTEM, "null");
    parms.setParameter(OUTPUT_FILE_PATH,"null");
    parms.setParameter(OUTPUT_FILE_NAME,"null");
  }

  public FindTestData(String inputFilePath, String outputFilePath) throws FileNotFoundException {
    initializeParameterServiceAndFindInput(inputFilePath);
    setParameterIfUnset(parms, OUTPUT_FILE_PATH, outputFilePath);
    setParameterIfUnset(parms, OUTPUT_FILE_NAME, outputFilePath);    
  }

  private void initializeParameterServiceAndFindInput(String inputFilePath) throws FileNotFoundException {
    parms = new ParameterService(new String[0]);
    setParameterIfUnset(parms, INPUT_FILE_PATH, inputFilePath);
    setParameterIfUnset(parms, INPUT_FILE_NAME, inputFilePath);
    findAndSetDataFolder(parms, inputFilePath);
  }

  private boolean parameterIsSet(ParameterService parameterService, String parameterName) {
    return parameterService.getParameter(parameterName) != "null";
  }

  private void setParameterIfUnset(ParameterService parameterService, String parameterName, String parameterValue) {
    if (!parameterIsSet(parameterService, parameterName)) {
      parameterService.setParameter(parameterName, parameterValue);
    }
  }

  private void findAndSetDataFolder(ParameterService parms, String filename) throws FileNotFoundException {

    if (parameterIsSet(parms, INPUT_FILE_SYSTEM)) {
      LOGGER.info("Getting input directory from Parameter Service.");
      return;
    }

    for (String candidate : candidates) {
      LOGGER.fine("Searching for " + filename + " in " + candidate);
      if (new File(candidate).isDirectory()) {
        File file = new File(candidate + File.separator + filename);
        if (file.exists()) {
          LOGGER.fine("JavaSeis folder located at " + file.getAbsolutePath() + "\n");
          setParameterIfUnset(parms, INPUT_FILE_SYSTEM, candidate);
          setParameterIfUnset(parms, OUTPUT_FILE_SYSTEM, candidate);
          return;
        }
      }
    }
    throw new FileNotFoundException("Unable to locate input data directory.");
  }

  public ParameterService getParameterService() {
    return parms;
  }
}

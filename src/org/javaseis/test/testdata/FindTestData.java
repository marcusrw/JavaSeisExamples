package org.javaseis.test.testdata;

import java.io.File;
import java.io.FileNotFoundException;

import org.javaseis.services.ParameterService;

public class FindTestData {

  private ParameterService parms;

  public FindTestData(ParameterService parms)
      throws NoSuchFieldException, FileNotFoundException {
    this.parms = parms;
    if (!parameterIsSet(parms,"inputFilePath")) {
      throw new NoSuchFieldException("You need to specify an input filename"
          + "\n in the Parameter Service field \"inputFilePath\"");
    }
    findAndSetDataFolder(parms,parms.getParameter("inputFilePath"));
  }

  public FindTestData(String inputFilePath) throws FileNotFoundException {
    initializeParameterServiceAndFindInput(inputFilePath);
  }

  public FindTestData(String inputFilePath,
      String outputFilePath) throws FileNotFoundException {
    initializeParameterServiceAndFindInput(inputFilePath);
    setParameterIfUnset(parms,"outputFilePath",outputFilePath);
  }

  private void initializeParameterServiceAndFindInput(
      String inputFilePath) throws FileNotFoundException {
    parms = new ParameterService(new String[0]);
    setParameterIfUnset(parms,"inputFilePath",inputFilePath);
    findAndSetDataFolder(parms,inputFilePath);
  }

  private boolean parameterIsSet(ParameterService parameterService,
      String parameterName) {
    return parameterService.getParameter(parameterName) != "null";
  }

  private void setParameterIfUnset(ParameterService parameterService,
      String parameterName, String parameterValue) {
    if (!parameterIsSet(parameterService,parameterName)) {
      parameterService.setParameter(parameterName, parameterValue);
    }
  }

  private void findAndSetDataFolder(
      ParameterService parms,String filename) throws FileNotFoundException {

    if (parameterIsSet(parms,"inputFileSystem")) {
      System.out.println("Getting input directory from Parameter Service.");
      return;
    }

    //If the file system is not set, try to find it by searching this list
    //Add to this list if you want to keep your data somewhere else.
    String[] candidates = new String[] {
        System.getProperty("user.home") + File.separator + "javaseis",
        System.getProperty("java.io.tmpdir"),
        "/home/seisspace/data"
    };

    for (String candidate : candidates) {
      System.out.println("Searching for " + filename + " in " + candidate);
      if (new File(candidate).isDirectory()) {
        File file = new File(candidate + File.separator + filename);
        if (file.exists()) {
          System.out.println("JavaSeis folder located at "
              + file.getAbsolutePath()+ "\n");
          setParameterIfUnset(parms,"inputFileSystem",candidate);
          setParameterIfUnset(parms,"outputFileSystem",candidate);
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

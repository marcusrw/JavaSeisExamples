package org.javaseis.examples.plot;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.util.SeisException;

public class JavaSeisMovieRunner {
  
  private static final Logger LOGGER =
      Logger.getLogger(JavaSeisMovieRunner.class.getName());

  static JavaSeisMovieApplet movie;
  static ParameterService parms;

  public static void showMovie(String dataset) {
    try {
      parms = new FindTestData(dataset).getParameterService();
      String fullpath = parms.getParameter("inputFileSystem") + File.separator + dataset;
      movie = new JavaSeisMovieApplet(fullpath);
    } catch (FileNotFoundException e) {
      LOGGER.log(Level.INFO, "Unable to locate javaseis data folder " + dataset,e);
    } catch (SeisException e) {
      LOGGER.log(Level.INFO,"Unable to open javaseis data set " + dataset,e);
    }
  }

  public static void main(String[] args) {
    //String dataset = "inputpwaves.VID";
    String dataset = "testFFT.js";
    //String dataset = "100a-rawsynthpwaves.js";
    //String dataset = "100-rawsyntheticdata.js";
    //String dataset = "seg_salt_vrms.VEL";

    if (args.length > 0) {
      dataset = args[0];
    }
    JavaSeisMovieRunner.showMovie(dataset);
  }
}

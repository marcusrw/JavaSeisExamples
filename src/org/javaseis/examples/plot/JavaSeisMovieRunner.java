package org.javaseis.examples.plot;

import org.javaseis.examples.plot.test.JTestDistributedArrayViewer;
import org.javaseis.util.SeisException;

import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaSeisMovieRunner {
  
  private static final Logger LOGGER =
      Logger.getLogger(JTestDistributedArrayViewer.class.getName());

  static JavaSeisMovieApplet movie;

  public static void showMovie(String pathToDataset) {
    try {
      movie = new JavaSeisMovieApplet(pathToDataset);
    } catch (SeisException e) {
      LOGGER.log(Level.INFO, "Unable to open the dataset at " + pathToDataset,e);
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    //String pathToDataset = "/home/wilsonmr/javaseis/inputpwaves.VID";
    //String pathToDataset = "/home/wilsonmr/javaseis/100-rawsyntheticdata.js";
    //String pathToDataset = "/home/seisspace/data/testFFT.js";
    String pathToDataset = "/home/wilsonmr/javaseis/testFFT.js";
    //String pathToDataset = "/home/seisspace/data/100a-rawsynthpwaves.js";
    //String pathToDataset = "/home/seisspace/data/100-rawsyntheticdata.js";
    //String pathToDataset = "/home/wilsonmr/javaseis/seg_salt_vrms.VEL";	 

    if (args.length > 0) {
      pathToDataset = args[0];
    }
    JavaSeisMovieRunner.showMovie(pathToDataset);
  }
}

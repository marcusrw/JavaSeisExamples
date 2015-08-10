package org.javaseis.runners;

import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javaseis.examples.scratch.ExampleMigration;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.util.SeisException;
import org.junit.Test;


//A wrapper script to run the example migration and save/visualize the results
public class Seg45ShotMigrationRunner {

  private static final Logger LOGGER = 
      Logger.getLogger(Seg45ShotMigrationRunner.class.getName());

  private static ParameterService parms;

  @Test
  public void programTerminates() throws FileNotFoundException {
    String inputFileName = "seg45shot.js";
    String outputFileName = "seg45image.js";
    String vModelFileName = "segsaltmodel.js";

    parms = new FindTestData(inputFileName,outputFileName).getParameterService();
    //set basic user inputs
    basicParameters(inputFileName,vModelFileName);

    try {
      ExampleMigration.exec(parms,new ExampleMigration());
    } catch (SeisException e) {
      LOGGER.log(Level.SEVERE,e.getMessage(),e);
    }
  }

  private void basicParameters(String inputFileName,String vModelFileName) {
    parms.setParameter("ZMIN","0");
    parms.setParameter("ZMAX","4000");
    parms.setParameter("DELZ","20");
    parms.setParameter("PADT","20");
    parms.setParameter("PADX","5");
    parms.setParameter("PADY","5");
    parms.setParameter("FMAX","6000");
    parms.setParameter("taskCount", "1");
    parms.setParameter("vModelFilePath",vModelFileName);
    parms.setParameter("outputFileMode","create");
  }
}

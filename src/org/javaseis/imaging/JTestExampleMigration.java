package org.javaseis.imaging;

import java.io.FileNotFoundException;

import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.ExampleRandomDataset;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.util.SeisException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JTestExampleMigration {

  private static final Logger LOGGER = 
      Logger.getLogger(JTestExampleMigration.class.getName());

  public JTestExampleMigration() {};

  //test harness to see if the process runs
  public static void main(String[] args) throws FileNotFoundException {
    JTestExampleMigration Test = new JTestExampleMigration();

    //Test.runManualTest();
    Test.generateTestData();
    //Test.testComputeDepthAxis();

  }

  public void runManualTest() throws FileNotFoundException{
    String inputFileName = "100a-rawsynthpwaves.js";
    //String inputFileName = "segshotno1.js";
    String outputFileName = "test100m.js";

    ParameterService parms =
        new FindTestData(inputFileName,outputFileName).getParameterService();
    parms.setParameter("ZMIN","0");
    parms.setParameter("ZMAX","2000");
    parms.setParameter("DELZ","100");
    parms.setParameter("PADT","50");
    parms.setParameter("PADX","50");
    parms.setParameter("PADY","50");
    //parms.setParameter("DEBUG","TRUE");    

    //parms.setParameter("threadCount", "1");
    try {
      ExampleMigration.exec(parms,new ExampleMigration());
    } catch (SeisException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  //Create a random string of a specific length over a given character set
  public String randomString(String characters, int length)
  {
    Random r = new Random();
    char[] text = new char[length];
    for (int i = 0; i < length; i++)
    {
      text[i] = characters.charAt(r.nextInt(characters.length()));
    }
    return new String(text);
  }

  //@Test
  //TODO this test is not finished yet.
  public void generateTestData() {	  
    String path1 = "/tmp/tempin.js";
    String path2 = "/tmp/tempout.js";

    //Input File object
    File inputfile = new File(path1);
    File outputfile = new File(path2);

    //random file path if exists already on disk
    if (inputfile.exists()){
      path1 = "/tmp/";
      path1 += randomString("abcdefg", 5);
    }
    if (outputfile.exists()){
      path2 = "/tmp/";
      path2 += randomString("abcdefg", 5);
    }

    //Input File object
    //File inputfile = new File(path1);
    //File outputfile = new File(path2);

    //Check to see that this file actually exists on the disk
    if (inputfile.exists())
      Assert.fail();

    ExampleRandomDataset testdata1 = new ExampleRandomDataset(path1);
    ExampleRandomDataset testdata2 = new ExampleRandomDataset(path2);

    ParameterService parms = null;



    try {
      parms = new FindTestData(testdata1.dataFullPath, testdata2.dataFullPath).getParameterService();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      LOGGER.log(Level.INFO,e.getMessage(),e);
      Assert.fail();
    }    

    try {
      ExampleMigration.exec(parms,new ExampleMigration());
    } catch (SeisException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }

    try {
      testdata1.deleteJavaSeisData();
      testdata2.deleteJavaSeisData();
    } catch (SeisException e) {
      LOGGER.log(Level.INFO,e.getMessage(),e);
    }

    try {
      inputfile.delete();
      outputfile.delete();
    }
    catch (Exception SecurityException){
      throw new UnsupportedOperationException("Unable to delete data.");
    }

  }

  @Test
  //Tests for computeDepthAxis
  public void testComputeDepthAxis(){
    //init a generic ExampleMigObject
    ExampleMigration TestOBJ = new ExampleMigration();

    //Bound Conditions
    //Test when Delta is larger or - than the iteration interval 
    Assert.assertEquals(1, TestOBJ.computeDepthAxis(50, 0, 55));
    Assert.assertEquals(1, TestOBJ.computeDepthAxis(50, -1, 55));
    Assert.assertEquals(1, TestOBJ.computeDepthAxis(50, 100, 55));

    //Test when Delta is smaller than the iteration interval
    Assert.assertEquals(2, TestOBJ.computeDepthAxis(0, 51, 100));
    Assert.assertEquals(4, TestOBJ.computeDepthAxis(0, 3, 10));
  }

}
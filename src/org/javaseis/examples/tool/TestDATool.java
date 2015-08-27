package org.javaseis.examples.tool;

import java.util.Arrays;
import java.util.logging.Logger;

import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;

import beta.javaseis.array.ITraceIterator;
import beta.javaseis.parallel.IParallelContext;

public class TestDATool implements IVolumeTool {

  private static final Logger LOGGER = Logger.getLogger(TestDATool.class.getName());

  private static int itrNum;

  @Override
  public void serialInit(ToolState toolState) throws SeisException {
    itrNum = 0;
  }

  @Override
  public void parallelInit(IParallelContext pc, ToolState toolState) throws SeisException {
  }

  @Override
  public boolean processVolume(IParallelContext pc, ToolState toolState, ISeismicVolume input, ISeismicVolume output)
      throws SeisException {
    itrNum++;

    // System.out.println(Arrays.toString(input.getVolumeShape()));

    ITraceIterator iti = input.getTraceIterator();
    ITraceIterator oti = output.getTraceIterator();

    while (iti.hasNext()) {
      iti.next();
      System.out.println(Arrays.toString(input.getVolumeShape()));

      int[] pos = new int[iti.getPosition().length];

      pos = iti.getPosition().clone();

      oti.setPosition(pos);
      oti.next();

      float[] acTrace = iti.getTrace().clone();

      System.out.println(Arrays.toString(acTrace));

      for (int i = 0; i < acTrace.length; i++) {
        acTrace[i] = itrNum;
      }

      System.out.println(Arrays.toString(acTrace));
      oti.putTrace(acTrace);

    }
    // output.copyVolume(input);
    return true;
  }

  @Override
  public boolean outputVolume(IParallelContext pc, ToolState toolState, ISeismicVolume output) throws SeisException {
    return false;
  }

  @Override
  public void parallelFinish(IParallelContext pc, ToolState toolState) throws SeisException {

  }

  @Override
  public void serialFinish(ToolState toolState) throws SeisException {
  }

}

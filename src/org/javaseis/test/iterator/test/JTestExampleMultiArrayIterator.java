package org.javaseis.test.iterator.test;

import java.util.Arrays;

import org.javaseis.test.iterator.ExampleMultiArrayIterator;
import org.junit.Assert;
import org.junit.Test;

public class JTestExampleMultiArrayIterator {

  int[] exampleGrid = new int[] {5,6,4,3,2};

  @Test
  public void testNoArgConstructor() {
    ExampleMultiArrayIterator test = new ExampleMultiArrayIterator();
    Assert.assertNotNull(test);
  }

  @Test
  public void testOtherConstructor() {
    int[] testGridSize = exampleGrid;
    int unitSize = 3;
    ExampleMultiArrayIterator test = new ExampleMultiArrayIterator(testGridSize,unitSize);
    Assert.assertNotNull(test);
  }

  @Test
  public void testIteratorCarry() {
    ExampleMultiArrayIterator test = new ExampleMultiArrayIterator();
    Assert.assertTrue(test.hasNext());
    Assert.assertArrayEquals(new int[] {0,0,0,0,0},test.nextIndex());
    Assert.assertArrayEquals(new int[] {1,0,0,0,0},test.nextIndex());
    Assert.assertArrayEquals(new int[] {0,1,0,0,0},test.nextIndex());   
  }

  @Test
  public void testNumberOfElements() {
    for (int k = 0 ; k <= exampleGrid.length ; k++) {
      int expectedNumElements = computeExpectedNumberOfElements(exampleGrid,k);
      int actualNumElements = computeActualNumberOfElements(exampleGrid,k);
      Assert.assertEquals(expectedNumElements,actualNumElements);
    }    
  }

  private int computeExpectedNumberOfElements(int[] exampleGrid, int unitSize) {
    int numelements = 1;
    for (int k = unitSize ; k < exampleGrid.length ; k++) {
      numelements *= exampleGrid[k];
    }
    return numelements;
  }

  private int computeActualNumberOfElements(int[] exampleGrid, int unitSize) {
    int numelements = 0;
    ExampleMultiArrayIterator test = new ExampleMultiArrayIterator(exampleGrid,unitSize);
    while (test.hasNext()) {
      numelements++;
      test.nextIndex();
    }
    return numelements;
  }
}

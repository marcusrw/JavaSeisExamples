package org.javaseis.test.iterator;

import java.util.Arrays;

/**
 * Example demonstrating the specification for how an iterator over a multiarray
 * should work.
 * 
 * @author Marcus Wilson 2015
 *
 */
public class ExampleMultiArrayIterator {
  int[] multiArraySizes = new int[] {2,3,2,1,4};
  int[] index;
  int unitSize;
  boolean initialPosition;

  //Go through the iterator for subarrays of each number of dimensions,
  //from 0 to the whole multiarray.
  public static void main(String[] args) {
    int[] testSizes = new int[] {2,3,2,1,4};
    for (int unitSize = 0 ; unitSize <= testSizes.length ; unitSize++) {
      System.out.println("Iterate size: " + unitSize);
      ExampleMultiArrayIterator test = new ExampleMultiArrayIterator(testSizes,unitSize);
      test.initializeIndex(test.unitSize);
      int numIndices = 0;
      while (test.hasNext()) {
        test.nextIndex();
        System.out.println(Arrays.toString(test.index) +
            " out of " + Arrays.toString(test.multiArraySizes));
        numIndices++;
      }
      System.out.println("Total number of elements: " + numIndices);
      System.out.println();
    }
  }

  /**
   * Example no argument constructor.  Creates an iterator over a multiarray of
   * size {2,3,2,1,4}, iterating over the smallest dimension.  You should be able to
   * iterate over 2*3*2*1*4 = 48 valid indices.
   */
  public ExampleMultiArrayIterator() {
    multiArraySizes = new int[] {2,3,2,1,4};
    initializeIndex(0);
  }

  /**
   * Create an example iterator over the indices of an array with dimensions
   * given by arrayLength, where each element is of size unitSize.
   * 
   * For example, arrayLength = {2,2,3,1,4} and unitSize = 2 will iterate over
   * the 2 dimensional arrays, giving {0,0,0,0,0},{0,0,1,0,0},{0,0,2,0,0},{0,0,0,0,1}
   * and so on, giving 12 elements in total.
   * 
   * @param arrayLength
   * @param unitSize
   */
  public ExampleMultiArrayIterator(int[] arrayLength , int unitSize) {
    multiArraySizes = arrayLength;
    initializeIndex(unitSize);
  }

  public void initializeIndex(int unitSize) {
    index = new int[multiArraySizes.length];
    this.unitSize = unitSize;
    initialPosition = true;
  }

  public int[] nextIndex() {
    if (initialPosition) {
      initialPosition = false;
      return index;
    } else {
      incrementIndex(unitSize);
      return index;
    }
  }

  public void incrementIndex(int k) {
    if (k >= multiArraySizes.length) {
      throw new ArrayIndexOutOfBoundsException();
    }
    if (index[k] < multiArraySizes[k]-1) {
      index[k]++;
    } else {
      assert k < multiArraySizes.length;
      index[k] = 0;
      incrementIndex(k+1);
    }
  }

  public boolean hasNext() {
    if (initialPosition) return true;
    for (int i = unitSize ; i < multiArraySizes.length ; i++) {
      if (index[i] < multiArraySizes[i]-1) return true;
    }
    return false;
  }

  //TODO add a recursive method to use the index to access the actual array.
}

package org.javaseis.utils;

import java.util.List;

public class Convert {

  public Convert() {}

  public static String[] listToArray(List<String> list) {
    String[] array = new String[list.size()];
    for (int k = 0 ; k < list.size(); k++ ) {
      array[k] = list.get(k);
    }
    return array;
  }

  /*
    public static <T> T[] listToArray(List<T> list) {
      Box<T>[] array = new Box<T>[list.size()];
      for (int k = 0; k < list.size(); k++) {
        array[k] = new Box<T>(list.get(k));
      }
      return array;
    }


  class Box<T> {
    final T x;
    Box(T x) {
      this.x = x;
    }
  }
   */
}

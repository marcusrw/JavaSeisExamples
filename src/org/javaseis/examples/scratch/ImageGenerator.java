package org.javaseis.examples.scratch;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.List;
import java.util.Arrays;
import javax.imageio.*;
import javax.swing.*;

public class ImageGenerator {
  private static List<String> filetypes = Arrays.asList(ImageIO.getWriterFileSuffixes());

  public static BufferedImage createImage(JComponent component) {
    Dimension dim = component.getSize();

    if (dim.width == 0 || dim.height == 0) {
      dim = component.getPreferredSize();
      component.setSize(dim);
    }

    Rectangle region = new Rectangle(0, 0, dim.width, dim.height);
    return ImageGenerator.createImage(component, region);
  }

  public static BufferedImage createImage(JComponent component, Rectangle region) {
    if (!component.isDisplayable()) {
      Dimension dim = component.getSize();

      if (dim.width == 0 || dim.height == 0) {
        dim = component.getPreferredSize();
        component.setSize(dim);
      }

      // layoutComponent(component);
    }

    BufferedImage image = new BufferedImage(region.width, region.height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = image.createGraphics();

    if (!component.isOpaque()) {
      g2d.setColor(component.getBackground());
      g2d.fillRect(region.x, region.y, region.width, region.height);
    }

    g2d.translate(-region.x, -region.y);
    component.paint(g2d);
    g2d.dispose();
    return image;
  }

  public static BufferedImage createImage(Component component) throws AWTException {
    Point p = new Point(0, 0);
    SwingUtilities.convertPointToScreen(p, component);
    Rectangle region = component.getBounds();
    region.x = p.x;
    region.y = p.y;
    return ImageGenerator.createImage(region);
  }

  public static BufferedImage createImage(Rectangle region) throws AWTException {
    BufferedImage image = new Robot().createScreenCapture(region);
    return image;
  }

  public static String genNewStr(String A, String Type) {
    File X = new File(A);
    int index = 0;
    while (X.exists()) {
      int offset2 = A.lastIndexOf("//");
      String fileN = A.substring(0, offset2);
      fileN += "//";
      fileN += index;
      fileN += ".";
      fileN += Type;
      index++;
      // System.out.println(fileN);
      X = new File(fileN);
    }

    return X.getName();
  }

  public static void writeImage(BufferedImage img, String path) throws IOException {
    if (path == null)
      return;

    int offset = path.lastIndexOf(".");

    if (offset == -1) {
      String sType = "No file type";
      throw new IOException(sType);
    }

    String type = path.substring(offset + 1);

    // System.out.println(path);
    int offset2 = path.lastIndexOf("//");

    String strtoOffset = path.substring(0, offset2);

    path = genNewStr(path, type);

    strtoOffset += "//";
    strtoOffset += path;

    path = strtoOffset;
    //System.out.println(path);

    if (filetypes.contains(type)) {
      ImageIO.write(img, type, new File(path));
    } else {
      String strMsg = "Image Failed";
      throw new IOException(strMsg);
    }
  }

  static void layoutComponent(Component component) {
    synchronized (component.getTreeLock()) {
      component.doLayout();

      if (component instanceof Container) {
        for (Component child : ((Container) component).getComponents()) {
          layoutComponent(child);
        }
      }
    }
  }
}

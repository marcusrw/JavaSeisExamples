package org.javaseis.examples.plot;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.javaseis.examples.scratch.ImageGenerator;
import org.javaseis.tool.ToolContext;

import beta.javaseis.distributed.Decomposition;
import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayGlobalTraceAccessor;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;
import edu.mines.jtk.dsp.Sampling;
import edu.mines.jtk.mosaic.PixelsView;
import edu.mines.jtk.mosaic.PlotPanel;
import edu.mines.jtk.mosaic.Projector;
import edu.mines.jtk.mosaic.Tile;
import edu.mines.jtk.mosaic.Transcaler;

/**
 * Provides a simple means of stepping through the 3 lowest data dimensions in a
 * DistributedArray. User selects a frame of interest using the JSlider bar.
 * Uses DistributedArrayGlobalTraceAccessor to achieve remote trace access. The
 * 'accessor' is used thusly: -- instantiate it -- call its trace access methods
 * e.g. getRemoteTrace() -- call its killWorkers() method when done
 */
public class DABackendViewer extends JPanel implements ActionListener, ChangeListener {

  private static final long serialVersionUID = 1L;

  /** These are not declared 'final' so callers can turn them off. */
  public static boolean INCLUDE_SHAPE_INFORMATION_IN_TITLE = true;

  public static boolean INCLUDE_PLOT_COUNTER_IN_TITLE = true;

  public static boolean SHOW_FRAME_VIEW = true;

  public static boolean SHOW_CROSSFRAME_VIEW = true;

  public static boolean SHOW_SLICE_VIEW = true;

  public static final int ORIENTATION_FRAME = 2; // Don't change these --

  public static final int ORIENTATION_CROSSFRAME = 1; // they correspond to

  public static final int ORIENTATION_SLICE = 0; // array indices.

  private int _plotOrientation = ORIENTATION_FRAME;

  private static final boolean VERBOSE = false;

  private DistributedArray _a;

  private DistributedArrayGlobalTraceAccessor _traceAccessor;

  private final int _elementOffset;

  private static int _plotCounter;

  private final IParallelContext _parallelContext;

  private final int _rank;

  private int _nFrames, _nTraces, _nSamples, _elementCount;

  private MyPlotPanel _plotPanel;

  private JSlider _slider;

  private MyIntegerTextField _planeValueField;

  private float[][] _plotData;

  private Sampling _xSampling, _ySampling;

  private float _minAmpFrame, _maxAmpFrame;

  private float _minAmpCrossframe, _maxAmpCrossframe;

  private float _minAmpSlice, _maxAmpSlice;

  private static MyControlPanel _myControlPanel;

  private static Point _saveWindowLocation;

  private static Dimension _saveWindowSize;

  private final long[] _axisLogicalOrigins;

  private final long[] _axisLogicalDeltas;

  private JTextField _mouseTrackingField;

  /** Don't use this. */
  private DABackendViewer() {
    throw new RuntimeException("don't use this constructor");
  }

  /**
   * Constructor.
   */
  private DABackendViewer(DistributedArray a, DistributedArrayGlobalTraceAccessor traceAccessor, int plotOrientation,
      long[] axisLogicalOrigins, long[] axisLogicalDeltas, int elementOffset) {

    if (a.getDimensions() < 3) {
      throw new IllegalArgumentException("DistributedArray dimensions must be >= 3");
    }

    if (a.getElementCount() > 2) {
      throw new IllegalArgumentException("DistributedArray element count must be <= 2");
    }

    if (elementOffset + 1 > a.getElementCount()) {
      throw new IllegalArgumentException("illegal elementOffset = " + elementOffset);
    }

    MyControlPanel.getAmplitudeJTextField().addActionListener(this);
    MyControlPanel.getAmplitudeClipMinJTextField().addActionListener(this);
    MyControlPanel.getAmplitudeClipMaxJTextField().addActionListener(this);
    MyControlPanel.getRangeScalingButton().addActionListener(this);
    MyControlPanel.getScaleFactorButton().addActionListener(this);

    _a = a;
    _plotOrientation = plotOrientation;
    _axisLogicalOrigins = axisLogicalOrigins;
    _axisLogicalDeltas = axisLogicalDeltas;

    _elementOffset = elementOffset;

    _parallelContext = a.getParallelContext();
    _rank = _parallelContext.rank();

    // TODO CHANGE

    // TODO: CHANGE
    int[] tempshape = a.getShape();
    int[] shape = tempshape;

    // int[] shape = _a.getShape();
    _nFrames = shape[2];
    _nTraces = shape[1];
    _nSamples = shape[0];
    _elementCount = _a.getElementCount();

    // Fire up the trace accessor with option to prepare for min, max
    // retrieval.
    // This "accessor" needs to be running on all modes (caller needs to
    // know this).
    _traceAccessor = traceAccessor;

    if (_rank == 0) {

      // This needs to come after assigning values to _nFrames, _nTraces,
      // _nSamples.
      _plotPanel = new MyPlotPanel();
      _plotPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 15));
      _plotPanel.getTile(0, 0).addMouseMotionListener(new MyMouseMotionHandler("tile00"));

      // Set up GUI.
      setUpGUI();

      // Show a center cut.
      switch (_plotOrientation) {
      case ORIENTATION_FRAME:
        _slider.setValue(_nFrames / 2);
        break;
      case ORIENTATION_CROSSFRAME:
        _slider.setValue(_nTraces / 2);
        break;
      case ORIENTATION_SLICE:
        _slider.setValue(_nSamples / 2);
        break;
      }
    }
  }

  /**
   * Creates graphical stuff.
   */
  private void setUpGUI() {
    setLayout(new BorderLayout());

    setBorder(BorderFactory.createLoweredBevelBorder());

    int N = _a.getShape()[_plotOrientation] - 1;

    int MAX_TICKS = 12;
    int majorTickSpacing = 10;
    int minorTickSpacing;

    if (_axisLogicalOrigins != null && _axisLogicalDeltas != null) {
      int annotationStart = (int) _axisLogicalOrigins[_plotOrientation];
      int annotationEnd = (int) (_axisLogicalOrigins[_plotOrientation] + _axisLogicalDeltas[_plotOrientation] * N);

      // Make sure we don't have too many ticks.
      while ((annotationEnd - annotationStart) / majorTickSpacing > MAX_TICKS) {
        majorTickSpacing += 10;
      }
      minorTickSpacing = majorTickSpacing / 10;

      _slider = new JSlider(SwingConstants.HORIZONTAL, annotationStart, annotationEnd, annotationStart);
      _slider.setPaintLabels(true);
      _slider.setPaintTicks(true);
      _slider.setPaintTrack(false);
      _slider.setMajorTickSpacing(majorTickSpacing);
      _slider.setMinorTickSpacing(minorTickSpacing);

      if (minorTickSpacing <= _axisLogicalDeltas[_plotOrientation]) {
        _slider.setSnapToTicks(true);
      } else {
        _slider.setSnapToTicks(false);
      }
    } else {

      // Make sure we don't have too many ticks.
      while (N / majorTickSpacing > MAX_TICKS) {
        majorTickSpacing += 10;
      }
      minorTickSpacing = majorTickSpacing / 10;

      _slider = new JSlider(SwingConstants.HORIZONTAL, 0, N, 0);
      _slider.setPaintLabels(true);
      _slider.setPaintTicks(true);
      _slider.setPaintTrack(false);
      _slider.setMajorTickSpacing(majorTickSpacing);
      _slider.setMinorTickSpacing(minorTickSpacing);

      if (minorTickSpacing <= 1) {
        _slider.setSnapToTicks(true);
      } else {
        _slider.setSnapToTicks(false);
      }
    }
    _slider.addChangeListener(this);

    // Add text field for plane value
    JPanel planeValuePanel = new JPanel();
    _planeValueField = new MyIntegerTextField(_slider.getValue(), _slider.getMinimum(), _slider.getMaximum(), 10);
    _planeValueField.setHorizontalAlignment(SwingConstants.CENTER);
    _planeValueField.addActionListener(this);
    switch (_plotOrientation) {
    case ORIENTATION_FRAME:
      planeValuePanel.add(new JLabel("Go To Frame:"));
      break;
    case ORIENTATION_CROSSFRAME:
      planeValuePanel.add(new JLabel("Go To Crossframe:"));
      break;
    case ORIENTATION_SLICE:
      planeValuePanel.add(new JLabel("Go To Slice:"));
      break;
    default:
      planeValuePanel.add(new JLabel("Value:"));
      break;
    }
    planeValuePanel.add(_planeValueField);

    // Add display of mouse tracking
    JPanel mouseTrackingPanel = new JPanel();
    _mouseTrackingField = new JTextField(25);
    _mouseTrackingField.setHorizontalAlignment(SwingConstants.LEFT);
    mouseTrackingPanel.add(new JLabel("Position:"));
    mouseTrackingPanel.add(_mouseTrackingField);

    // Create panel to hold control widgets
    JPanel widgetPanel = new JPanel(new BorderLayout());
    JPanel accessoryPanel = new JPanel(new BorderLayout());
    accessoryPanel.add(BorderLayout.NORTH, planeValuePanel);
    accessoryPanel.add(BorderLayout.SOUTH, mouseTrackingPanel);

    add(BorderLayout.CENTER, _plotPanel);
    add(BorderLayout.SOUTH, widgetPanel);
    widgetPanel.add(BorderLayout.NORTH, _slider);
    widgetPanel.add(BorderLayout.SOUTH, accessoryPanel);
  }

  /**
   * Requirement of ActionListener (we're listening to the amplitude
   * JTextField).
   */
  public void actionPerformed(ActionEvent e) {
    Object src = e.getSource();
    if (src == MyControlPanel.getAmplitudeJTextField()) {
      _plotPanel.updatePlot();
    } else if (src == MyControlPanel.getAmplitudeClipMinJTextField()) {
      _plotPanel.updatePlot();
    } else if (src == MyControlPanel.getAmplitudeClipMaxJTextField()) {
      _plotPanel.updatePlot();
    } else if (src == MyControlPanel.getRangeScalingButton()) {
      MyControlPanel.setScaleType(MyControlPanel.getRangeScalingButton().getActionCommand());
      _plotPanel.updatePlot();
    } else if (src == MyControlPanel.getScaleFactorButton()) {
      MyControlPanel.setScaleType(MyControlPanel.getScaleFactorButton().getActionCommand());
      _plotPanel.updatePlot();
    } else if (src == _planeValueField) {
      // setPlaneValue(Integer.parseInt(_planeValueField.getText()));
      _slider.setValue(_planeValueField.getValue());
    } else if (src == _mouseTrackingField) {
      // _mouseTrackingField.setText("");
    }
  }

  /**
   * Requirement of ChangeListener (we're listening to the slider).
   */
  public void stateChanged(ChangeEvent e) {
    Object src = e.getSource();
    if (src == _slider) {
      boolean isAdjusting = _slider.getValueIsAdjusting();
      int val = _slider.getValue();
      if (isAdjusting) {
        return;
      }
      try {
        _planeValueField.setValue(val);
        setPlaneValue(val);
      } catch (Exception ex) {
        System.out.println("setPlaneIndex exception = " + ex.getMessage());
        ex.printStackTrace();
      } catch (Error er) {
        System.out.println("setPlaneIndex error = " + er.getMessage());
        er.printStackTrace();
      }
    }
  }

  public DistributedArrayGlobalTraceAccessor getGlobalTraceAccessor() {
    return _traceAccessor;
  }

  /**
   * Reads the data and updates the plot such that the frame, crossframe, or
   * slice matches what's defined by the slider position.
   */
  public synchronized void setPlaneValue(int sliderVal) {

    // Zero-based slider values.
    if (_axisLogicalOrigins == null || _axisLogicalDeltas == null) {
      setPlaneIndex(sliderVal);

      // Logical-based slider values.
    } else {
      long index = (sliderVal - _axisLogicalOrigins[_plotOrientation]) / _axisLogicalDeltas[_plotOrientation];
      setPlaneIndex((int) index);
    }
  }

  /**
   * Reads the data and updates the plot such that the planeIndex is the
   * frameIndex (for iline case), traceIndex (for xline case), or sliceIndex
   * (for slice case).
   *
   * Synchronization is an effort to prevent simultaneous mpi send/receive
   * action to/from the global trace accessor.
   */
  public synchronized void setPlaneIndex(int planeIndex) {
    switch (_plotOrientation) {
    case ORIENTATION_FRAME:
      setFrame(planeIndex);
      break;
    case ORIENTATION_CROSSFRAME:
      setCrossframe(planeIndex);
      break;
    case ORIENTATION_SLICE:
      setSlice(planeIndex);
      break;
    }
  }

  /**
   * Reads a frame of data and updates the frame plot.
   */
  public void setFrame(int frameIndex) {

    long t0 = System.currentTimeMillis();

    float[][] traceBuf = new float[_nTraces][_nSamples * _elementCount];

    int[] position = new int[3];
    position[2] = frameIndex;

    for (int traceIndex = 0; traceIndex < _nTraces; traceIndex++) {
      position[1] = traceIndex;
      position[0] = 0; // should not be needed but what if a call changes
      // it somehow

      // Get the array data for this position.
      _traceAccessor.getGlobalTrace(traceBuf[traceIndex], position);
    }
    plotPlane(traceBuf);

    long t1 = System.currentTimeMillis();
    if (VERBOSE) {
      System.out.printf("DistributedArrayMosaicPlot: time for setFrame was %.2f seconds\n", (t1 - t0) / 1000f);
    }
  }

  /**
   * Reads a crossframe of data and updates the crossframe plot.
   */
  public void setCrossframe(int traceIndex) {

    long t0 = System.currentTimeMillis();

    float[][] traceBuf = new float[_nFrames][_nSamples * _elementCount];

    int[] position = new int[3];
    position[1] = traceIndex;

    for (int frameIndex = 0; frameIndex < _nFrames; frameIndex++) {
      position[2] = frameIndex;
      position[0] = 0; // should not be needed but what if a call changes
      // it somehow

      // Get the array data for this position.
      _traceAccessor.getGlobalTrace(traceBuf[frameIndex], position);
    }
    plotPlane(traceBuf);

    long t1 = System.currentTimeMillis();
    if (VERBOSE) {
      System.out.printf("DistributedArrayMosaicPlot: time for setCrossframe was %.2f seconds\n", (t1 - t0) / 1000f);
    }
  }

  /**
   * Reads a crossframe of data and updates the crossframe plot. TODO: As of
   * this writing the DistributedArrayGlobalTraceAccessor is not able to access
   * subtraces directly. We read the whole trace and throw most of it away.
   */
  public void setSlice(int sliceIndex) {

    long t0 = System.currentTimeMillis();

    float[][] traceBuf;
    float[] tmpBuf = new float[_elementCount]; // we don't need a full trace
    if (_elementCount == 2) { // complex numbers
      traceBuf = new float[_nFrames][_nTraces * 2];
    } else {
      traceBuf = new float[_nFrames][_nTraces];
    }

    int[] position = new int[3];

    for (int frameIndex = 0; frameIndex < _nFrames; frameIndex++) {
      position[2] = frameIndex;

      for (int traceIndex = 0; traceIndex < _nTraces; traceIndex++) {
        position[1] = traceIndex;
        position[0] = sliceIndex; // retrieve directly from slice
        // position

        // Just read a single sample (a partial trace).
        _traceAccessor.getGlobalSubTrace(tmpBuf, position, 0, _elementCount);
        if (_elementCount == 2) { // complex numbers
          traceBuf[frameIndex][2 * traceIndex] = tmpBuf[0];
          traceBuf[frameIndex][2 * traceIndex + 1] = tmpBuf[1];
        } else {
          traceBuf[frameIndex][traceIndex] = tmpBuf[0];
        }
      }
    }
    plotPlane(traceBuf);

    long t1 = System.currentTimeMillis();
    if (VERBOSE) {
      System.out.printf("DistributedArrayMosaicPlot: time for setSlice was %.2f seconds", (t1 - t0) / 1000f);
    }
  }

  /**
   * Reads the (possibly complex) data and updates the plot.
   */
  public void plotPlane(float[][] data) {

    if (_elementCount == 2 && _elementOffset < 0) {
      _plotData = new float[data.length][data[0].length / 2];
    } else {
      _plotData = new float[data.length][data[0].length];
    }

    int imax = _plotData.length;
    int jmax = _plotData[0].length;

    // Put into our 2-dimensional plot buf and find amplitude range.
    float min = Float.MAX_VALUE;
    float max = -Float.MAX_VALUE;
    for (int i = 0; i < imax; i++) {
      for (int j = 0; j < jmax; j++) {

        float plotVal;
        if (_elementCount == 1) {
          plotVal = data[i][j];
        } else {
          if (_elementOffset < 0) {
            float val0 = data[i][2 * j];
            float val1 = data[i][2 * j + 1];
            plotVal = (float) Math.sqrt(val0 * val0 + val1 * val1);
          } else {
            plotVal = data[i][_elementCount * j + _elementOffset];
          }
        }

        if (plotVal < min) {
          min = plotVal;
        }
        if (plotVal > max) {
          max = plotVal;
        }

        _plotData[i][j] = plotVal;
      }
    }

    switch (_plotOrientation) {
    case ORIENTATION_FRAME:
      _minAmpFrame = min;
      _maxAmpFrame = max;
      break;
    case ORIENTATION_CROSSFRAME:
      _minAmpCrossframe = min;
      _maxAmpCrossframe = max;
      break;
    case ORIENTATION_SLICE:
      _minAmpSlice = min;
      _maxAmpSlice = max;
      break;
    }

    // The plotBuf array now should contain updated data.
    _xSampling = new Sampling(_plotData[0].length, 1, 0);
    _ySampling = new Sampling(_plotData.length, 1, 0);
    _plotPanel.updatePlot();
  }

  /**
   * PlotPanel where data is rendered.
   */
  private class MyPlotPanel extends PlotPanel {

    private static final long serialVersionUID = 1L;

    private IndexColorModel _indexColorModel;

    private PixelsView _pixelsView;

    /**
     * Use BorderLayout.
     */
    public MyPlotPanel() {

      // Prepare the color model.
      makeColorModel();

      switch (_plotOrientation) {
      case ORIENTATION_FRAME:
        setTitle("Frames");
        break;
      case ORIENTATION_CROSSFRAME:
        setTitle("Crossframes");
        break;
      case ORIENTATION_SLICE:
        setTitle("Slices");
        break;
      }

    }

    /**
     * Makes a simple gray-scale IndexColorModel.
     */
    private void makeColorModel() {
      byte[] r = new byte[256];
      byte[] g = new byte[256];
      byte[] b = new byte[256];
      for (int i = 0; i < 256; i++) {
        r[i] = g[i] = b[i] = (byte) (255 - i);
      }
      _indexColorModel = new IndexColorModel(8, 256, r, g, b);
    }

    /**
     * Adds new PixelsView or resets the data in it.
     */
    public void updatePlot() {

      if (_pixelsView == null) {
        _pixelsView = addPixels(_xSampling, _ySampling, _plotData);
      } else {
        _pixelsView.set(_xSampling, _ySampling, _plotData);
      }

      _pixelsView.setOrientation(PixelsView.Orientation.X1DOWN_X2RIGHT);

      float min, max;
      switch (_plotOrientation) {
      case ORIENTATION_FRAME:
        min = _minAmpFrame;
        max = _maxAmpFrame;
        break;
      case ORIENTATION_CROSSFRAME:
        min = _minAmpCrossframe;
        max = _maxAmpCrossframe;
        break;
      case ORIENTATION_SLICE:
        min = _minAmpSlice;
        max = _maxAmpSlice;
        break;
      default:
        throw new RuntimeException("unknown _plotOrientation");
      }

      // My recollection is that mosaic doesn't like min == max == 0.
      if (min == 0f && max == 0f) {
        min = -1f;
        max = 1f;
      } else {
        min = -Math.abs(max); // seismic - trough deflection equals peak
        // deflection
      }
      // _pixelsView.setClips(
      // min / MyControlPanel.getAmplitudeScaleFactor(),
      // max / MyControlPanel.getAmplitudeScaleFactor());
      _pixelsView.setClips(MyControlPanel.getClipMin(min), MyControlPanel.getClipMax(max));

      // Standard settings.
      _pixelsView.setInterpolation(PixelsView.Interpolation.LINEAR);
      _pixelsView.setColorModel(_indexColorModel);

      repaint();
    }

  }

  /**
   * Here's a spot for amplitude control widgets and stuff.
   */
  private static class MyControlPanel extends JPanel {

    public final static String SCALE_FACTOR = "scaleFactor";

    public final static String RANGE_SCALING = "rangeScaling";

    private static final long serialVersionUID = 1L;

    //Need to be able to change these but this is not the best option
    private static JTextField _ampScaleField = new JTextField(8);
    private static JTextField _ampClipMinField = new JTextField(8);
    private static JTextField _ampClipMaxField = new JTextField(8);

    private final static JRadioButton _scaleFactorButton = new JRadioButton("Use amplitude scale factor:");

    private final static JRadioButton _rangeScalingButton = new JRadioButton("Use clip range:");

    private static String _scaleType;

    public MyControlPanel() {

      ButtonGroup group = new ButtonGroup();
      group.add(_scaleFactorButton);
      group.add(_rangeScalingButton);

      _scaleFactorButton.setActionCommand(SCALE_FACTOR);
      _scaleFactorButton.setSelected(true);
      setScaleType(SCALE_FACTOR);
      _rangeScalingButton.setActionCommand(RANGE_SCALING);

      JPanel scalePanel = new JPanel();
      scalePanel.add(_scaleFactorButton);
      scalePanel.add(_ampScaleField);
      scalePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 30));

      JPanel clipPanel = new JPanel();
      clipPanel.add(_rangeScalingButton);
      clipPanel.add(new JLabel("Min:"));
      clipPanel.add(_ampClipMinField);
      clipPanel.add(new JLabel("Max:"));
      clipPanel.add(_ampClipMaxField);
      clipPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

      _ampScaleField.setText("1");
      _ampScaleField.setHorizontalAlignment(SwingConstants.HORIZONTAL);
      _ampClipMinField.setText("-1");
      _ampClipMaxField.setText("1");

      add(scalePanel);
      add(clipPanel);
    }

    public final static float getAmplitudeScaleFactor() {
      try {
        float scaleFactor = new Float(_ampScaleField.getText());
        if (scaleFactor != 0f) {
          return scaleFactor;
        }
      } catch (Exception e) {
        // do nothing
      }
      return 1f; // fallback
    }

    public final static JTextField getAmplitudeJTextField() {
      return _ampScaleField;
    }

    public final static float getAmplitudeClipMin() {
      try {
        return Float.parseFloat(_ampClipMinField.getText());
      } catch (NumberFormatException e) {
        // do nothing
      }
      return -1f; // fallback
    }

    public final static JTextField getAmplitudeClipMinJTextField() {
      return _ampClipMinField;
    }

    public final static float getAmplitudeClipMax() {
      try {
        return Float.parseFloat(_ampClipMaxField.getText());
      } catch (NumberFormatException e) {
        // do nothing
      }
      return 1f; // fallback
    }

    public final static JTextField getAmplitudeClipMaxJTextField() {
      return _ampClipMaxField;
    }

    public final static JRadioButton getRangeScalingButton() {
      return _rangeScalingButton;
    }

    public final static JRadioButton getScaleFactorButton() {
      return _scaleFactorButton;
    }

    public final static void setScaleType(String type) {
      if (type.equalsIgnoreCase(RANGE_SCALING)) {
        _scaleType = RANGE_SCALING;
        _ampScaleField.setEnabled(false);
        _ampClipMinField.setEnabled(true);
        _ampClipMaxField.setEnabled(true);
      } else if (type.equalsIgnoreCase(SCALE_FACTOR)) {
        _scaleType = SCALE_FACTOR;
        _ampScaleField.setEnabled(true);
        _ampClipMinField.setEnabled(false);
        _ampClipMaxField.setEnabled(false);
      }
    }

    public final static String getScaleType() {
      return _scaleType;
    }

    public final static float getClipMin(float min) {
      float clipMin;
      if (_scaleType.equalsIgnoreCase(RANGE_SCALING)) {
        clipMin = MyControlPanel.getAmplitudeClipMin();
      } else if (_scaleType.equalsIgnoreCase(SCALE_FACTOR)) {
        clipMin = min / MyControlPanel.getAmplitudeScaleFactor();
      } else {
        clipMin = min;
      }
      return clipMin;
    }

    public final static float getClipMax(float max) {
      float clipMax;
      if (_scaleType.equalsIgnoreCase(RANGE_SCALING)) {
        clipMax = MyControlPanel.getAmplitudeClipMax();
      } else if (_scaleType.equalsIgnoreCase(SCALE_FACTOR)) {
        clipMax = max / MyControlPanel.getAmplitudeScaleFactor();
      } else {
        clipMax = max;
      }
      return clipMax;
    }
  }

  /**
   * Handle mouse motion events.
   * 
   * @author bkmacy
   *
   */
  private class MyMouseMotionHandler implements MouseMotionListener {

    private String _name = "";

    public MyMouseMotionHandler() {
      this("");
    }

    public MyMouseMotionHandler(String name) {
      super();
      _name = name;
    }

    public void mouseMoved(MouseEvent event) {
      updateLocation(event);
      // System.out.println(_name + ": mouseMoved: x,y = (" + x + "," + y
      // + ")");
    }

    public void mouseDragged(MouseEvent event) {
      updateLocation(event);
      // System.out.println(_name + ": mouseDragged: x,y = (" + x + "," +
      // y + ")");
    }

    private void updateLocation(MouseEvent event) {
      int x = event.getX();
      int y = event.getY();
      if (event.getSource().getClass() == Tile.class) {
        // System.out.println("source = " +
        // event.getSource().toString());
        // System.out.println("component = " +
        // event.getComponent().toString());
        // System.out.println("class = " + event.getClass().toString());
        Tile tile = (Tile) event.getSource();
        Transcaler t = tile.getTranscaler();
        Projector hp = tile.getHorizontalProjector();
        Projector vp = tile.getVerticalProjector();
        double xVal;
        double yVal;
        double zVal;
        // switch (_plotMode) {
        // case XY:
        xVal = hp.v(t.x(x));
        yVal = vp.v(t.y(y));
        int xpos = (int) Math.round(xVal);
        int ypos = (int) Math.round(yVal);
        if (xpos >= 0 && xpos < _plotData.length && ypos >= 0 && ypos < _plotData[xpos].length) {
          String s = String.format("(x,y,val) = (%d, %d, %f)", xpos, ypos, _plotData[xpos][ypos]);
          _mouseTrackingField.setText(s);
          // s = String.format("(x,y,val) = (%f, %f, %f)", xVal, yVal,
          // _plotData[xpos][ypos]);
        } else {
          // s = String.format("(x,y) = (%d, %d)", xpos, ypos);
          // s = String.format("(x,y) = (%f, %f)", xVal, yVal);
        }
        // zVal = _oz + _dz*_sliceValue.getValue();
        // break;
        // case ZX:
        // xVal = hp.v(t.x(x));
        // yVal = _oy + _dy*_sliceValue.getValue();
        // zVal = vp.v(t.y(y));
        // break;
        // case ZY:
        // default:
        // xVal = _ox + _dx*_sliceValue.getValue();
        // yVal = hp.v(t.x(x));
        // zVal = vp.v(t.y(y));
        // break;
        // }
        int n1 = _plotData[0].length;
        int n2 = _plotData.length;
        int i1;
        int i2;
        // switch (_pixelsView.getOrientation()) {
        // case X1DOWN_X2RIGHT:
        // i1 = Math.round((n1-1)*((float)t.y(y)));
        // i2 = Math.round((n2-1)*((float)t.x(x)));
        // break;
        // case X1RIGHT_X2UP:
        // default:
        // i1 = Math.round((n1-1)*((float)t.x(x)));
        // i2 = Math.round((n2-1)*((float)t.y(y)));
        // break;
        // }
        // _xLocationTextField.setText("" + xVal);
        // _yLocationTextField.setText("" + yVal);
        // _zLocationTextField.setText("" + zVal);
        // _velocityTextField.setText("" + _plotData[i2][i1]);
      }
    }
  }

  /**
   * Here's a static method to add a JFrame around a DistributedArrayMosaicPlot
   * using linear scaling.
   * 
   * @param a
   *          the data to be plotted
   * @param title
   *          JFrame title
   */
  public static void showAsModalDialog(DistributedArray a, String title, ToolContext toolContext, int[] sliderArray, int [] clipRange, float ampFactor) {
    showAsModalDialog(a, null, null, title, a.getParallelContext().rank(), -1, toolContext, sliderArray, clipRange, ampFactor);
  }

  /**
   * Here's a static method to add a JFrame around a DistributedArrayMosaicPlot
   * using linear scaling.
   * 
   * @param a
   *          the data to be plotted
   * @param title
   *          JFrame title
   * @param rank
   *          mpi task #
   */
  public static void showAsModalDialog(DistributedArray a, String title, int rank, ToolContext toolContext,
      int[] sliderArray, int [] clipRange, float ampFactor) {
    showAsModalDialog(a, null, null, title, rank, -1, toolContext, sliderArray, clipRange, ampFactor);
  }

  /**
   * Here's a static method to add a JFrame around a DistributedArrayMosaicPlot
   * using linear scaling.
   * 
   * @param a
   *          the data to be plotted
   * @param title
   *          JFrame title
   * @param rank
   *          mpi task #
   */
  public static void showAsModalDialog(DistributedArray a, String title, int rank, int elementOffset,
      ToolContext toolContext, int[] sliderArray, int [] clipRange, float ampFactor) {
    showAsModalDialog(a, null, null, title, rank, elementOffset, toolContext, sliderArray, clipRange, ampFactor);
  }

  /**
   * Here's a static method to add a JFrame around a DistributedArrayMosaicPlot
   * using linear scaling.
   * 
   * @param a
   *          the data to be plotted
   * @param axisLogicalOrigins
   *          affects annotation
   * @param axisLogicalDeltas
   *          affects annotation
   * @param title
   *          JFrame title
   * @param rank
   *          mpi task #
   */
  public static void showAsModalDialog(DistributedArray a, long[] axisLogicalOrigins, long[] axisLogicalDeltas,
      String title, int rank, ToolContext toolContext, int[] sliderArray, int [] clipRange, float ampFactor) {
    showAsModalDialog(a, axisLogicalOrigins, axisLogicalDeltas, title, rank, -1, toolContext, sliderArray, clipRange, ampFactor);
  }

  /**
   * Don't ever use this method directly use FrontendViewer
   * 
   * Here's a static method to add a JFrame around a DistributedArrayMosaicPlot.
   * 
   * @param a
   *          the data to be plotted
   * @param axisLogicalOrigins
   *          affects annotation
   * @param axisLogicalDeltas
   *          affects annotation
   * @param title
   *          JFrame title
   * @param rank
   *          mpi task #
   * @param elementOffset
   *          use this element or, if negative, plot envelope amplitude
   * @param toolContext
   *          user specified params
   */
  public static void showAsModalDialog(DistributedArray a, long[] axisLogicalOrigins, long[] axisLogicalDeltas,
      String title, int rank, int elementOffset, ToolContext toolContext, int[] sliderArray, int [] clipRange, float ampFactor) {

    _plotCounter++;

    // No sense in going much further until everyone's caught up?
    a.getParallelContext().barrier();

    // We instantiate this and pass it in so there's only one per... (jvm?).
    // We do NOT prepare min-max information (we're using local, not global
    // scaling).
    DistributedArrayGlobalTraceAccessor traceAccessor = new DistributedArrayGlobalTraceAccessor(
        "DistributedArrayMosaicPlot", 999, a, true);

    // Plots share common control panel.
    if (rank == 0 && _myControlPanel == null) {
      _myControlPanel = new MyControlPanel();
    }

    int nviews = 0;
    DABackendViewer[] plot = new DABackendViewer[3];
    if (SHOW_FRAME_VIEW) {
      if (VERBOSE) {
        System.out.println("DistributedArrayMosaicPlot: adding frame view");
      }

      plot[nviews] = new DABackendViewer(a, traceAccessor, ORIENTATION_FRAME, axisLogicalOrigins, axisLogicalDeltas,
          elementOffset);
      if (sliderArray != null) {
        plot[nviews]._slider.setValue(sliderArray[0]);
      }
      if (clipRange != null){
        plot[nviews]._plotPanel._pixelsView.setClips(clipRange[0], clipRange[1]);
        Integer minC = clipRange[0];
        Integer maxC = clipRange[1];
        _myControlPanel._ampClipMinField.setText(minC.toString());
        _myControlPanel._ampClipMaxField.setText(maxC.toString());
        if (ampFactor != 1 && ampFactor > 0){
          plot[nviews]._plotPanel._pixelsView.setClips(clipRange[0]/ampFactor, clipRange[1]/ampFactor);
        }
      }
      nviews++;
    }
    if (SHOW_CROSSFRAME_VIEW) {
      if (VERBOSE) {
        System.out.println("DistributedArrayMosaicPlot: adding crossframe view");
      }

      plot[nviews] = new DABackendViewer(a, traceAccessor, ORIENTATION_CROSSFRAME, axisLogicalOrigins,
          axisLogicalDeltas, elementOffset);
      if (sliderArray != null) {
        plot[nviews]._slider.setValue(sliderArray[1]);
      }
      if (clipRange != null){
        plot[nviews]._plotPanel._pixelsView.setClips(clipRange[0], clipRange[1]);
        Integer minC = clipRange[0];
        Integer maxC = clipRange[1];
        _myControlPanel._ampClipMinField.setText(minC.toString());
        _myControlPanel._ampClipMaxField.setText(maxC.toString());
        if (ampFactor != 1 && ampFactor > 0){
          plot[nviews]._plotPanel._pixelsView.setClips(clipRange[0]/ampFactor, clipRange[1]/ampFactor);
        }
      }
      nviews++;
    }
    if (SHOW_SLICE_VIEW) {
      if (VERBOSE) {
        System.out.println("DistributedArrayMosaicPlot: adding slice view (this can be slow...)");
      }

      plot[nviews] = new DABackendViewer(a, traceAccessor, ORIENTATION_SLICE, axisLogicalOrigins, axisLogicalDeltas,
          elementOffset);
      if (sliderArray != null) {
        plot[nviews]._slider.setValue(sliderArray[2]);
      }
      if (clipRange != null){
        plot[nviews]._plotPanel._pixelsView.setClips(clipRange[0], clipRange[1]);
        Integer minC = clipRange[0];
        Integer maxC = clipRange[1];
        _myControlPanel._ampClipMinField.setText(minC.toString());
        _myControlPanel._ampClipMaxField.setText(maxC.toString());
        if (ampFactor != 1 && ampFactor > 0){
          plot[nviews]._plotPanel._pixelsView.setClips(clipRange[0]/ampFactor, clipRange[1]/ampFactor);
        }
      }
      nviews++;
    }

    if (rank == 0) {
      if (INCLUDE_SHAPE_INFORMATION_IN_TITLE) {
        title = _plotCounter + ":    " + title;
      }
      if (INCLUDE_SHAPE_INFORMATION_IN_TITLE) {
        int[] tempshape = a.getShape();
        int[] shape = tempshape;
        title += String.format("    Shape = %d %d %d", shape[0], shape[1], shape[2]);
      }

      // JFrame A = new JFrame();
      // JDialog f = new JDialog(A, title, false);
      JWindow f = new JWindow();
      // f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

      JPanel gridPanel = new JPanel(new GridLayout(1, nviews));
      for (int i = 0; i < nviews; i++) {
        gridPanel.add(plot[i]);
      }
      JPanel mainPanel = new JPanel(new BorderLayout());
      mainPanel.add(BorderLayout.CENTER, gridPanel);

      mainPanel.add(BorderLayout.SOUTH, _myControlPanel);
      f.getContentPane().add(mainPanel);

      // There might have a previous preferred location.
      if (_saveWindowLocation != null && _saveWindowSize != null) {
        f.setLocation(_saveWindowLocation);
        f.setSize(_saveWindowSize);
      } else {
        f.setSize(new Dimension(1500, 500));
      }

      f.pack();
      f.setVisible(false);

      try {
        BufferedImage img = ImageGenerator.createImage(mainPanel);
        if (toolContext == null) {
          ImageGenerator.writeImage(img, "tmp//0.png");
        } else {
          String outLocation = toolContext.getParameter(ToolContext.OUTPUT_FILE_SYSTEM) + "//"
              + toolContext.getParameter(ToolContext.OUTPUT_FILE_PATH);
          outLocation += "//";
          outLocation += "Images";

          // Create a new folder
          new File(outLocation).mkdir();

          // Create the path to images
          outLocation += "//";
          outLocation += "0.png";

          // Output image
          ImageGenerator.writeImage(img, outLocation);
        }

      } catch (IOException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      
      f.dispose();

      _saveWindowLocation = f.getLocation();
      _saveWindowSize = f.getSize();

      plot[0].getGlobalTraceAccessor().killWorkers(0);
      
      //gridPanel = null;
      //mainPanel = null;
    }
  }

  public class MyIntegerTextField extends JTextField {

    private static final int _defaultColumns = 10;

    private int _minValue;

    private int _maxValue;

    public MyIntegerTextField(int value) {
      this(value, _defaultColumns);
    }

    public MyIntegerTextField(int value, int columns) {
      this(value, columns, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public MyIntegerTextField(int value, int min, int max, int columns) {
      super(Integer.toString(value), columns);
      _minValue = min;
      _maxValue = max;
    }

    public void setMinValue(int value) {
      _minValue = value;
    }

    public void setMaxValue(int value) {
      _maxValue = value;
    }

    public void setValue(int value) {
      super.setText(Integer.toString(Math.max(_minValue, Math.min(_maxValue, value))));
    }

    public int getValue() {
      return Integer.parseInt(super.getText());
    }
  }

  public static void main(String[] args) {
    IParallelContext pc = new UniprocessorContext();

    // Create distributed array (not safe for transposes)
    int[] lengths = new int[] { 201, 301, 401 };
    int[] decompTypes = { Decomposition.NONE, Decomposition.BLOCK, Decomposition.BLOCK };
    long maxlen = DistributedArray.getShapeLength(3, 1, lengths);
    DistributedArray da = new DistributedArray(pc, float.class, 3, 1, lengths, decompTypes, maxlen);

    // Populate DA
    float[][] frame = new float[lengths[1]][lengths[0]];
    da.resetFrameIterator();
    double denominator = 0.5 * Math.sqrt(lengths[2] * lengths[2] + lengths[1] * lengths[1] + lengths[0] * lengths[0]);
    while (da.hasNext()) {
      da.next();
      da.getFrame(frame);
      int frameNum = da.getPosition()[2];
      double frameSq = frameNum - lengths[2] / 2;
      frameSq *= frameSq;
      for (int j = 0; j < lengths[1]; j++) {
        double traceSq = j - lengths[1] / 2;
        traceSq *= traceSq;
        for (int i = 0; i < lengths[0]; i++) {
          double sampleSq = i - lengths[0] / 2;
          sampleSq *= sampleSq;
          frame[j][i] = (float) (1.0 - Math.sqrt(frameSq + traceSq + sampleSq) / denominator);
          // frame[j][i] = frameNum*1000000 + j*1000 + i;
        }
      }
      da.putFrame(frame, da.getPosition());
    }

    // Pop up a plot of the current DistributedArray.
    long[] logicalOrigins = new long[] { 0, 0, 0 };
    long[] logicalDeltas = new long[] { 1, 1, 1 };
    DABackendViewer.showAsModalDialog(da, logicalOrigins, logicalDeltas, "Distributed Array Plot Test", pc.rank(), null,
        null, null, 1);
    DABackendViewer.showAsModalDialog(da, logicalOrigins, logicalDeltas, "Distributed Array Plot Test", pc.rank(), null,
        null, null, 1);
  }
}
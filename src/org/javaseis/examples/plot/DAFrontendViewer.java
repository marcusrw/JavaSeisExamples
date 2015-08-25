package org.javaseis.examples.plot;

import java.util.Arrays;

//import org.javaseis.parallel.DistributedArrayMosaicPlot;
import org.javaseis.test.testdata.SampleInputCreator;
import org.javaseis.tool.ToolState;
import org.junit.Assert;

import beta.javaseis.distributed.DistributedArray;
//import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;

/**
 * DistributedArray Viewer
 * 
 * Must call - Frame, Trace, Depth Order
 * 
 * Use setAll(ForSafety)
 * 
 * Works only on 3 dimensions
 */
public class DAFrontendViewer {
	private DistributedArray A;
	private DistributedArray B;
	private ToolState toolContext;
	private int[] sArray = null;
	private int[] cArray = null;
	private float ampFactor = 1.0f;

	private int[] zoomBegin;
	private int[] zoomEnd;

	public DAFrontendViewer() {

	}

	public DAFrontendViewer(DistributedArray A, ToolState toolContext) {
		this.A = A;
		//Don't clone here you will run out of memory
		//this.B = (DistributedArray) A.clone();
		this.B = A;
		this.toolContext = toolContext;

		// Set trivial zoom
		zoomBegin = new int[A.getShape().length];
		zoomEnd = A.getShape();
	}

	// Figure out the position in the outer array corresponding to the position
	// in the inner array.
	private int[] outerPosition(int[] innerPosition) {
		int[] outerPosition = innerPosition.clone();
		for (int k = 0; k < innerPosition.length; k++) {
			outerPosition[k] = zoomBegin[k] + innerPosition[k];
			if (outerPosition[k] >= A.getShape()[k]) {
				throw new ArrayIndexOutOfBoundsException("Computed outer position "
						+ "exceeds outer array size");
			}
		}
		return outerPosition;
	}

	public void setLogicalSize(int[] sIndices, int[] eIndices) {
		// check that s and e are both in range

		zoomBegin = sIndices;
		zoomEnd = eIndices;
		populateZoomedArray();
	}

	private void populateZoomedArray() {
		int[] innerArrayShape = new int[zoomEnd.length];
		for (int k = 0; k < innerArrayShape.length; k++) {
			innerArrayShape[k] = zoomBegin[k] - zoomEnd[k] + 1;
		}

		B.setShape(innerArrayShape);

		int sampleScope = 0;
		int[] innerPosition = new int[B.getShape().length];
		float[] sample = new float[B.getElementCount()];
		DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(
				B, innerPosition, DistributedArrayPositionIterator.FORWARD, sampleScope);

		while (itrInputArr.hasNext()) {
			innerPosition = itrInputArr.next();
			A.getSample(sample, outerPosition(innerPosition));
			B.putSample(sample, innerPosition);
		}
	}

	/**
	 * Select part of the image on logical depth
	 * 
	 * @param sDepthIndex
	 *          - Starting Depth Index Position
	 * @param eDepthIndex
	 *          - Ending Depth Index Position
	 */
	public void setLogicalDepth(int sDepthIndex, int eDepthIndex) {
		int[] shapeB = B.getShape();

		zoomBegin[0] = sDepthIndex;
		zoomEnd[0] = eDepthIndex;

		int diff = eDepthIndex - sDepthIndex;
		shapeB[0] = diff;
		B.setShape(shapeB);

		int[] pos = new int[A.getDimensions()];

		DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(
				A, pos, DistributedArrayPositionIterator.FORWARD, 0);

		while (itrInputArr.hasNext()) {
			pos = itrInputArr.next();
			int[] outputPosition = new int[] { 0, 0, 0 };

			if (pos[0] >= sDepthIndex && pos[0] < eDepthIndex
					&& pos[1] >= zoomBegin[1] && pos[1] < zoomEnd[1]
					&& pos[2] >= zoomBegin[2] && pos[2] < zoomEnd[2]) {
				float[] depth1 = new float[A.getElementCount()];
				A.getSample(depth1, pos);
				int[] pos2 = outputPosition;
				pos2[0] = pos[0] - zoomBegin[0];
				pos2[1] = pos[1] - zoomBegin[1];
				pos2[2] = pos[2] - zoomBegin[2];
				B.putSample(depth1, pos2);
			}
		}
	}

	/**
	 * Select part of the image on logical trace
	 * 
	 * @param sTraceIndex
	 *          - Starting Trace Index Position
	 * @param eTraceIndex
	 *          - Ending Trace Index Position
	 */
	public void setLogicalTrace(int sTraceIndex, int eTraceIndex) {
		zoomBegin[1] = sTraceIndex;
		zoomEnd[1] = eTraceIndex;

		int[] shapeB = B.getShape();

		int diff = eTraceIndex - sTraceIndex;
		shapeB[1] = diff;
		B.setShape(shapeB);

		int[] pos = new int[A.getDimensions()];

		DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(
				A, pos, DistributedArrayPositionIterator.FORWARD, 1);

		while (itrInputArr.hasNext()) {
			pos = itrInputArr.next();
			int[] outputPosition = new int[] { 0, 0, 0 };

			if (pos[1] >= sTraceIndex && pos[1] < eTraceIndex
					&& pos[2] >= zoomBegin[1] && pos[2] < zoomEnd[1]) {
				float[] trace1 = new float[shapeB[0]];
				A.getTrace(trace1, pos);
				int[] pos2 = outputPosition;
				pos2[1] = pos[1] - zoomBegin[1];
				pos2[2] = pos[2] - zoomBegin[2];
				B.putTrace(trace1, pos2);
			}
		}
	}

	/**
	 * Test this method not sure if right Select part of the image on logical
	 * frame
	 * 
	 * @param sFrameIndex
	 *          - Starting Frame Index Position
	 * @param eFrameIndex
	 *          - Ending Frame Index Position
	 */
	public void setLogicalFrame(int sFrameIndex, int eFrameIndex) {
		zoomBegin[2] = sFrameIndex;
		zoomEnd[2] = eFrameIndex;

		int[] shapeB = B.getShape();

		int diff = eFrameIndex - sFrameIndex;
		shapeB[2] = diff;
		B.setShape(shapeB);

		int[] pos = new int[A.getDimensions()];

		DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(
				A, pos, DistributedArrayPositionIterator.FORWARD, 2);

		while (itrInputArr.hasNext()) {
			pos = itrInputArr.next();
			int[] outputPosition = new int[] { 0, 0, 0 };

			if (pos[2] >= sFrameIndex && pos[2] < eFrameIndex) {
				float[] frame1 = new float[shapeB[2]];
				A.getFrame(frame1, pos);
				int[] pos2 = outputPosition;
				pos2[2] = pos[2] - zoomBegin[2];
				B.putFrame(frame1, pos2);
			}
		}
	}

	/**
	 * Set all the transformation parameters.
	 * 
	 * @param Array
	 *          - { {Frame}, {Trace}, {Depth} }
	 */
	public void setAll(int[][] Array) {
		setLogicalFrame(Array[0][0], Array[0][1]);
		setLogicalTrace(Array[1][0], Array[1][1]);
		setLogicalDepth(Array[2][0], Array[2][1]);
	}
	
	/**
	 * Sets the Slider Values of the Desired Image
	 * 
	 * @param Frame
	 * @param Crossframe
	 * @param Slice
	 */
	public void setSliders(int frame, int cross, int slice){
	  int[] nArray = new int[3];
	  nArray[0] = frame;
	  nArray[1] = cross;
	  nArray[2] = slice;	  
	  sArray = nArray;
	}
	
	public void setClipRange(int minClip, int maxClip){
    int[] nArray = new int[2];
    nArray[0] = minClip;
    nArray[1] = maxClip; 
    cArray = nArray;
  }
	
	public void setAmpFactor(float d){
	  ampFactor = d;
	}

	/**
	 * Shows you the modified DA
	 * 
	 * @param title
	 *          - Title of your plot
	 * @return
	 */
	public void show(String title) {
		DABackendViewer.showAsModalDialog(B, title, toolContext, sArray, cArray, ampFactor);
	  //DABackendViewer.showAsModalDialog(B, title, toolContext);
	  //DistributedArrayMosaicPlot.showAsModalDialog(B, title);
		
	}

	//TODO: Move tests
	/*
	private void checkFrame() {
		SampleInputCreator TestObject = new SampleInputCreator(true);
		DistributedArray TestArray = TestObject.getSeismicInput()
				.getDistributedArray();
		DAFrontendViewer viewObject = new DAFrontendViewer(TestArray, toolContext);

		// resize it along one axis to get B
		viewObject.setLogicalFrame(75, 125);

		// Check Get frame is correct
		int[] position = new int[viewObject.B.getShape().length];
		int direction = 1; // Forward
		int scope = 2; // Frame
		DistributedArrayPositionIterator viewObjItr = new DistributedArrayPositionIterator(
				viewObject.B, position, direction, scope);

		while (viewObjItr.hasNext()) {
			position = viewObjItr.next();
			float[] newFrame = new float[viewObject.A.getShape()[2]];
			float[] originalFrame = new float[viewObject.A.getShape()[2]];
			viewObject.B.getFrame(newFrame, position);

			int[] orignalPos = position.clone();
			orignalPos[2] += viewObject.zoomBegin[2];

			viewObject.A.getFrame(originalFrame, orignalPos);

			int i = 0;
			float delta = 0.1f;
			// check that frame for B is in A frame
			// loop through the frame
			while (newFrame[i] != 0) {
				if (Math.abs(newFrame[i] - originalFrame[i]) > delta) {
					Assert.fail();
				}
				i++;
			}
		}
		System.out.println("Frame Axis Correct!");
		TestObject = null;
		TestArray = null;
		viewObject = null;
		System.gc(); // Just Do It - Objects may be extremely large
	}

	private void checkTrace() {
		SampleInputCreator TestObject = new SampleInputCreator(true);
		DistributedArray TestArray = TestObject.getSeismicInput()
				.getDistributedArray();
		DAFrontendViewer viewObject = new DAFrontendViewer(TestArray, toolContext);

		// resize it along one axis to get B
		viewObject.setLogicalTrace(75, 125);

		// Check Get frame is correct
		int[] position = new int[viewObject.B.getShape().length];
		int direction = 1; // Forward
		int scope = 1; // Trace
		DistributedArrayPositionIterator viewObjItr = new DistributedArrayPositionIterator(
				viewObject.B, position, direction, scope);

		while (viewObjItr.hasNext()) {
			position = viewObjItr.next();
			float[] newO = new float[viewObject.A.getShape()[1]];
			float[] original = new float[viewObject.A.getShape()[1]];
			viewObject.B.getFrame(newO, position);

			int[] orignalPos = position.clone();
			orignalPos[1] += viewObject.zoomBegin[1];
			orignalPos[2] += viewObject.zoomBegin[2];

			viewObject.A.getFrame(original, orignalPos);

			int i = 0;
			float delta = 0.1f;
			while (newO[i] != 0) {
				if (Math.abs(newO[i] - original[i]) > delta) {
					Assert.fail();
				}
				i++;
			}
		}
		System.out.println("Trace Axis Correct!");
		TestObject = null;
		TestArray = null;
		viewObject = null;
		System.gc(); // Just Do It - Objects may be extremely large
	}

	private void checkDepth() {
		SampleInputCreator TestObject = new SampleInputCreator(true);
		DistributedArray TestArray = TestObject.getSeismicInput()
				.getDistributedArray();
		DAFrontendViewer viewObject = new DAFrontendViewer(TestArray, toolContext);

		// resize it along one axis to get B
		viewObject.setLogicalTrace(75, 125);

		// Check Get frame is correct
		int[] position = new int[viewObject.B.getShape().length];
		int direction = 1; // Forward
		int scope = 0; // Trace
		DistributedArrayPositionIterator viewObjItr = new DistributedArrayPositionIterator(
				viewObject.B, position, direction, scope);

		while (viewObjItr.hasNext()) {
			position = viewObjItr.next();
			float[] newO = new float[viewObject.A.getShape()[0]];
			float[] original = new float[viewObject.A.getShape()[0]];
			viewObject.B.getFrame(newO, position);

			int[] orignalPos = position.clone();
			orignalPos[1] += viewObject.zoomBegin[0];
			orignalPos[1] += viewObject.zoomBegin[1];
			orignalPos[2] += viewObject.zoomBegin[2];

			viewObject.A.getFrame(original, orignalPos);

			int i = 0;
			float delta = 0.1f;
			while (newO[i] != 0) {
				if (Math.abs(newO[i] - original[i]) > delta) {
					Assert.fail();
				}
				i++;
			}
		}
		System.out.println("Depth Axis Correct!");
		TestObject = null;
		TestArray = null;
		viewObject = null;
		System.gc(); // Just Do It - Objects may be extremely large
	}

	public static void main(String args[]) {

		IParallelContext pc = new UniprocessorContext();
		int[] lengths = new int[] { 50, 50, 50 };
		DistributedArray da = new DistributedArray(pc, lengths);
		DAFrontendViewer test = new DAFrontendViewer(da, null);

		// Test the outerPosition method first
		test.zoomBegin = new int[] { 10, 20, 30 };
		Assert.assertArrayEquals(new int[] { 20, 30, 40 },
				test.outerPosition(new int[] { 10, 10, 10 }));

		test.checkFrame();
		test.checkTrace();
		test.checkDepth();

	}*/

}

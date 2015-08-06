package org.javaseis.examples.scratch;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;

/**
 * Works only on 3 dimensions
 */
public class DAFrontendViewer {
	private DistributedArray A;
	private DistributedArray B;

	public DAFrontendViewer() {

	}

	public DAFrontendViewer(DistributedArray A) {
		this.A = A;
		this.B = (DistributedArray) A.clone();
	}
	
	/**
	 * Select part of the image on logical depth
	 * @param sDepthIndex - Starting Depth Index Position
	 * @param eDepthIndex - Ending Depth Index Position
	 */
	public void setLogicalDepth(int sDepthIndex, int eDepthIndex) {
		int[] shapeB = B.getShape();

		int diff = eDepthIndex - sDepthIndex;
		shapeB[0] = diff + 1;
		B.setShape(shapeB);

		float index = 0;
		int oldtrace = 0;

		int[] pos = new int[A.getDimensions()];

		DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(A, pos,
				DistributedArrayPositionIterator.FORWARD, 0);

		while (itrInputArr.hasNext()) {
			pos = itrInputArr.next();
			int[] outputPosition = pos.clone();

			if (pos[0] >= sDepthIndex && pos[0] < eDepthIndex) {
				float[] depth1 = new float[A.getElementCount()];
				A.getSample(depth1, pos);
				int[] pos2 = outputPosition;
				pos2[0] = (int) index;
				B.putSample(depth1, pos2);
				index++;

			}

			// Check if the current frame != old frame
			if (pos[1] != oldtrace) {
				index = 0;
			}

			// Set the frame
			oldtrace = pos[1];
		}
	}

	/**
	 * Select part of the image on logical trace
	 * @param sTraceIndex - Starting Trace Index Position
	 * @param eTraceIndex - Ending Trace Index Position
	 */
	public void setLogicalTraces(int sTraceIndex, int eTraceIndex) {
		int[] shapeB = B.getShape();

		int diff = eTraceIndex - sTraceIndex;
		shapeB[1] = diff + 1;
		B.setShape(shapeB);

		float index = 0;
		int oldframe = 0;

		int[] pos = new int[A.getDimensions()];

		DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(A, pos,
				DistributedArrayPositionIterator.FORWARD, 1);

		while (itrInputArr.hasNext()) {
			pos = itrInputArr.next();
			int[] outputPosition = pos.clone();

			if (pos[1] >= sTraceIndex && pos[1] < eTraceIndex) {
				float[] trace1 = new float[shapeB[0]];
				A.getTrace(trace1, pos);
				int[] pos2 = outputPosition;
				pos2[1] = (int) index;
				B.putTrace(trace1, pos2);
				index++;

			}

			// Check if the current frame != old frame
			if (pos[2] != oldframe) {
				index = 0;
			}

			// Set the frame
			oldframe = pos[2];
		}
	}
	
	/**
	 * Test this method not sure if right
	 * Select part of the image on logical frame
	 * @param sFrameIndex - Starting Frame Index Position
	 * @param eFrameIndex - Ending Frame Index Position
	 */
	public void setLogicalFrame(int sFrameIndex, int eFrameIndex) {
		int[] shapeB = B.getShape();

		int diff = eFrameIndex - sFrameIndex;
		shapeB[2] = diff + 1;
		B.setShape(shapeB);

		float index = 0;

		int[] pos = new int[A.getDimensions()];

		DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(A, pos,
				DistributedArrayPositionIterator.FORWARD, 2);

		while (itrInputArr.hasNext()) {
			pos = itrInputArr.next();
			int[] outputPosition = pos.clone();

			if (pos[2] >= sFrameIndex && pos[2] < eFrameIndex) {
				float[] frame1 = new float[shapeB[2]];
				A.getFrame(frame1, pos);
				int[] pos2 = outputPosition;
				pos2[2] = (int) index;
				B.putFrame(frame1, pos2);
				index++;

			}
		}
	}
	
	/**
	 * TODO:Implement
	 */
	public void setLogicalDepthAxis() {
		
	}

	/**
	 * TODO: Implement
	 */
	public void setLogicalTraceAxis() {
		
	}
	
	/*
	 * TODO: Implement
	 */
	public void setLogicalFrameAxis() {
		
	}
	
	
	/**
	 * Shows you the modified DA 
	 * @param title	- Title of your plot
	 */
	public void show(String title) {
		DABackendViewer.showAsModalDialog(B, title);
	}

}

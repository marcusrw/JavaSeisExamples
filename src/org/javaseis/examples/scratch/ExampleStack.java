package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.javaseis.array.ElementType;
import org.javaseis.examples.plot.DAFrontendViewer;
import org.javaseis.examples.tool.ExampleVolumeInputTool;
import org.javaseis.examples.tool.ExampleVolumeOutputTool;
import org.javaseis.examples.tool.VolumeCorrectionTool;
import org.javaseis.grid.GridDefinition;
import org.javaseis.grid.VolumeEdgeIO;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.DataState;
import org.javaseis.tool.IVolumeTool;
import org.javaseis.tool.StandAloneVolumeTool;
import org.javaseis.tool.ToolState;
import org.javaseis.tool.VolumeToolRunner;
import org.javaseis.util.SeisException;
import org.javaseis.utils.Convert;
import org.javaseis.velocity.VelocityModelFromFile;
import org.javaseis.volume.ISeismicVolume;
import org.junit.Assert;

import beta.javaseis.array.ITraceIterator;
import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;

public class ExampleStack implements IVolumeTool {

	private static final Logger LOGGER = Logger.getLogger(ExampleStack.class
			.getName());

	static ParameterService parms;
	static DistributedArray eda;

	public static void main(String[] args) throws FileNotFoundException,
			SeisException {
		String inputFileName = "seg45shot.js";
		String outputFileName = "newTestStack.js";
		String vModelFileName = "segsaltmodel.js";

		try {
			parms = new FindTestData(inputFileName, outputFileName)
					.getParameterService();

			parms.setParameter("vModelFilePath", vModelFileName);
			parms.setParameter("outputFileMode", "create");

			List<String> toolList = new ArrayList<String>();

			toolList.add(ExampleVolumeInputTool.class.getCanonicalName());
			toolList.add(VolumeCorrectionTool.class.getCanonicalName());
			toolList.add(ExampleStack.class.getCanonicalName());
			// toolList.add(ExampleVolumeOutputTool.class.getCanonicalName());

			String[] toolArray = Convert.listToArray(toolList);

			try {
				VolumeToolRunner.exec(parms, toolArray);
			} catch (SeisException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	private GridDefinition getVelocityGrid(IParallelContext pc,
			ToolState toolState) {
		VelocityModelFromFile vff = null;
		try {
			vff = new VelocityModelFromFile(pc, toolState);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		vff.open("r");

		return vff.getVModelGrid();
	}

	private void setOutgoingDataStateGrid(ToolState toolState,
			GridDefinition outputGrid) {
		DataState outputState = toolState.getOutputState();
		outputState.gridDefinition = outputGrid;
		toolState.setOutputState(outputState);
	}

	@Override
	public void serialInit(ToolState serialToolState) {
		IParallelContext upc = new UniprocessorContext();
		GridDefinition veloGrid = getVelocityGrid(upc, serialToolState);
		setOutgoingDataStateGrid(serialToolState, veloGrid);
		eda = new DistributedArray(upc, veloGrid.getShape());
	}

	@Override
	public void parallelInit(IParallelContext pc, ToolState toolState) {

	}

	public int[] convertVolPosToVModelPos(int[] dataPos, int[] axis_order,
			GridDefinition VeloGrid, GridDefinition VolGrid) {
		int[] vModelPos = new int[dataPos.length];

		int[] v_model_axis = { 2, 1, 0 };

		// Get Z Axis
		// One Axis from Volume
		AxisDefinition VolumeAxis = VolGrid.getAxis(axis_order[2]);
		// Same Axis from Velocity Model
		AxisDefinition VelocityAxis = VeloGrid.getAxis(v_model_axis[2]);

		// data physical origin + data delta * index data - velocity model
		// physical origin
		double DpoDDmultIndexDataminusVMo = VolumeAxis.getPhysicalOrigin()
				+ VolumeAxis.getPhysicalDelta() * dataPos[axis_order[2]]
				- VelocityAxis.getPhysicalOrigin();

		vModelPos[v_model_axis[2]] = (int) (DpoDDmultIndexDataminusVMo / VelocityAxis
				.getPhysicalDelta());

		// Get X Axis
		// One Axis from Volume
		VolumeAxis = VolGrid.getAxis(axis_order[0]);
		// Same Axis from Velocity Model
		VelocityAxis = VeloGrid.getAxis(v_model_axis[0]);

		// data physical origin + data delta * index data - velocity model
		// physical origin
		DpoDDmultIndexDataminusVMo = VolumeAxis.getPhysicalOrigin()
				+ VolumeAxis.getPhysicalDelta() * dataPos[axis_order[0]]
				- VelocityAxis.getPhysicalOrigin();

		vModelPos[v_model_axis[0]] = (int) (DpoDDmultIndexDataminusVMo / VelocityAxis
				.getPhysicalDelta());

		// Get Y Axis
		// One Axis from Volume
		VolumeAxis = VolGrid.getAxis(axis_order[1]);
		// Same Axis from Velocity Model
		VelocityAxis = VeloGrid.getAxis(v_model_axis[1]);

		// data physical origin + data delta * index data - velocity model
		// physical origin
		DpoDDmultIndexDataminusVMo = VolumeAxis.getPhysicalOrigin()
				+ VolumeAxis.getPhysicalDelta() * dataPos[axis_order[1]]
				- VelocityAxis.getPhysicalOrigin();

		vModelPos[v_model_axis[1]] = (int) (DpoDDmultIndexDataminusVMo / VelocityAxis
				.getPhysicalDelta());

		// TODO: No longer needed
		// Set the Volume to nth index
		// vModelPos[3] = dataPos[3];

		return vModelPos;
	}

	private int[] convertLongArrayToIntArray(long[] A) {
		int[] B = new int[A.length];
		for (int i = 0; i < A.length; i++) {
			B[i] = (int) A[i];
		}
		return B;
	}

	public void checkPublicGrids(ToolState toolState) {
		GridDefinition inputGrid = toolState.getInputState().gridDefinition;
		if (inputGrid == null) {
			throw new IllegalArgumentException("Input Grid is Null");
		}
		GridDefinition outputGrid = toolState.getOutputState().gridDefinition;
		if (outputGrid == null) {
			throw new IllegalArgumentException("Output Grid is Null");
		}
	}

	@Override
	public boolean processVolume(IParallelContext pc, ToolState toolState,
			ISeismicVolume input, ISeismicVolume output) {

		checkPublicGrids(toolState);

		GridDefinition velocityGrid = getVelocityGrid(pc, toolState);

		output.setDistributedArray(eda);

		ITraceIterator iti = input.getTraceIterator();
		ITraceIterator oti = output.getTraceIterator();

		GridDefinition volGrid = toolState.getInputState().gridDefinition;

		while (iti.hasNext()) {

			iti.next();

			float[] buf = iti.getTrace();
			float[] vmodbuf = new float[buf.length];

			int[] veloPos = convertVolPosToVModelPos(iti.getPosition().clone(),
					new int[] { 2, 1, 0 }, velocityGrid, volGrid);

			if (oti.hasNext()) {
				oti.setPosition(veloPos);
				oti.next();

				vmodbuf = oti.getTrace();
				vmodbuf = addSecondArgToFirst(vmodbuf, buf);
				oti.putTrace(vmodbuf);
			}
		}

		LOGGER.info("Volume Has Been Added To Global Stack.");

		return true;
	}

	private float[] addSecondArgToFirst(float[] trace1, float[] trace2) {
		Assert.assertTrue(trace1.length <= trace2.length);
		float[] out = new float[trace1.length];
		for (int k = 0; k < out.length; k++) {
			out[k] = trace1[k] + trace2[k];
		}
		return out;
	}

	@Override
	public boolean outputVolume(IParallelContext pc, ToolState toolState,
			ISeismicVolume output) throws SeisException {
		return false;
	}

	@Override
	public void parallelFinish(IParallelContext pc, ToolState toolState)
			throws SeisException {
	}

	@Override
	public void serialFinish(ToolState toolState) throws SeisException {
		DistributedArrayMosaicPlot.showAsModalDialog(eda, "Velo");
	}
}
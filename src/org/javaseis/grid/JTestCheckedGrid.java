package org.javaseis.grid;

import java.io.FileNotFoundException;

import beta.javaseis.distributed.DistributedArrayPositionIterator;

import org.javaseis.test.testdata.JTestSampleInputCreator;
import org.javaseis.tool.ToolContext;
import org.javaseis.velocity.IVelocityModel;
import org.javaseis.velocity.VelocityModelFromFile;
import org.javaseis.volume.ISeismicVolume;
import org.junit.Before;
import org.junit.Test;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.IDistributedIOService;

public class JTestCheckedGrid {
	ToolContext toolContext;
	ISeismicVolume seismicInput;
	IDistributedIOService ipio = null;
	ICheckedGrid checkGrid;

	@Before
	public void loadDataIntoVolume() {
		JTestSampleInputCreator test = new JTestSampleInputCreator(true);
		this.toolContext = test.getToolContext();
		this.seismicInput = test.getSeismicInput();
		this.checkGrid = test.getCheckGrid();
	}

	@Test
	public void testCoords() {
		try {
			testCoords(seismicInput, checkGrid, toolContext);
		} catch (IllegalArgumentException e) {
			e.getMessage();
		}
	}

	@Test
	public void sourcesEqual() {
		try {
			sourcesEqual(checkGrid, seismicInput);
		} catch (IllegalArgumentException e) {
			e.getMessage();
		}
	}

	/*
	 * Checks if all the sources are the same for all the traces over a specific
	 * volume.
	 */
	private boolean sourcesEqual(ICheckedGrid CheckGrid, ISeismicVolume input) {
		// check if the input DistArray Sources match the computed source
		DistributedArray inputDistArr = input.getDistributedArray();

		// Get the current volume
		int[] globalPosIndex = input.getVolumePosition();
		int[] volumePosIndex = new int[3];

		// Iterate over traces
		// You can iterate over traces by setting the scope to 1.
		DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(inputDistArr,
				volumePosIndex, DistributedArrayPositionIterator.FORWARD);

		// get the source from grid
		double[] sourceXYZ = CheckGrid.getSourceXYZ();

		while (itrInputArr.hasNext()) {
			volumePosIndex = itrInputArr.next();

			globalPosIndex[0] = 0;
			for (int k = 1; k < 3; k++) {
				globalPosIndex[k] = volumePosIndex[k];
			}

			// Get the source positions at [0,?,?,v]
			double[] sXYZ = new double[3];
			sXYZ = CheckGrid.getSourceXYZ(globalPosIndex);

			// check that this source is equal to the local source
			for (int i = 0; i < sourceXYZ.length; i++) {
				// if the sources vary by more 0.5
				if (Math.abs(sourceXYZ[i] - sXYZ[i]) > 0.5) {
					// System.out.println("[sourcesEqual]: Sources Change at
					// Location: " + Arrays.toString(globalPosIndex));
					// System.out.println("\t[sourcesEqual]: " + "Expected: " +
					// Arrays.toString(sourceXYZ) + "Given: "
					// + Arrays.toString(sXYZ));

					// Sources don't match
					throw new IllegalArgumentException("Sources Change Between Trace.");
				}
			}
		}
		return true;

	}

	// Helper method
	private IVelocityModel getVelocityModelObject(ICheckedGrid CheckGrid, ToolContext toolContext) {
		IVelocityModel vmff = null;
		try {
			vmff = new VelocityModelFromFile(toolContext);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		vmff.open("r");
		vmff.orientSeismicVolume(CheckGrid.getModifiedGrid(), CheckGrid.getAxisOrder());
		return vmff;
	}

	private boolean testCoords(ISeismicVolume input, ICheckedGrid CheckGrid, ToolContext toolContext) {

		IVelocityModel vmff = getVelocityModelObject(CheckGrid, toolContext);

		// Get the values from the distributed array and compare the jscs values

		// Grab Input ISeismicVolume -> DistributedArray
		DistributedArray inputDistArr = input.getDistributedArray();

		// index of trace [sample, trace, frame, volume]
		int[] globalPosIndex = input.getVolumePosition();
		int[] volumePosIndex = new int[3];

		// Iterate over the traces of the ISeismicVolume in the forward
		// direction (1)
		DistributedArrayPositionIterator itrInputArr = new DistributedArrayPositionIterator(inputDistArr,
				volumePosIndex, DistributedArrayPositionIterator.FORWARD);

		while (itrInputArr.hasNext()) {
			volumePosIndex = itrInputArr.next();

			// Don't compare anything about depth
			globalPosIndex[0] = 0; // 0 depth

			for (int k = 1; k < 3; k++) {
				globalPosIndex[k] = volumePosIndex[k];
			}

			// rXYZ2 hold the receiver location for a given trace location
			// [0,?,?,0]
			double[] rXYZ = new double[3];

			rXYZ = CheckGrid.getReceiverXYZ(globalPosIndex);

			// rXYZ value from the Grid instead of jscs
			double[] rXYZ2 = new double[3];

			// Check if both indexes are in range
			double yIndex = globalPosIndex[CheckGrid.getAxisOrder()[1]];
			double xIndex = globalPosIndex[CheckGrid.getAxisOrder()[0]];

			// Calculate position in Xline
			int currentAxis = CheckGrid.getAxisOrder()[1];
			double minPhys0 = CheckGrid.getModifiedGrid().getAxisPhysicalOrigin(currentAxis);
			double axisPhysDelta = CheckGrid.getModifiedGrid().getAxisPhysicalDelta(currentAxis);
			double yval = minPhys0 + yIndex * axisPhysDelta;

			// Calculate position in Iline
			currentAxis = CheckGrid.getAxisOrder()[0];
			minPhys0 = CheckGrid.getModifiedGrid().getAxisPhysicalOrigin(currentAxis);
			axisPhysDelta = CheckGrid.getModifiedGrid().getAxisPhysicalDelta(currentAxis);
			double xval = minPhys0 + xIndex * axisPhysDelta;

			// Set rXYZ2 Grids Calculations
			rXYZ2[0] = xval;
			rXYZ2[1] = yval;
			rXYZ2[2] = 0; //depth is 0

			double[] vmodXYZ = vmff.getVelocityModelXYZ(globalPosIndex);

			// Don't check last position that is depth
			for (int k = 0; k < 2; k++) {
				if (rXYZ2[k] - rXYZ[k] > 0.5 || rXYZ2[k] - rXYZ[k] < -0.5) {
					throw new ArithmeticException("The origin/delta position doesn't match the getRXYZ position");
				}
			}

			// Don't check last position that is depth position
			// vmodXYZ.length - 1 so that we don't check the depth
			for (int k = 0; k < vmodXYZ.length - 1; k++) {
				if (vmodXYZ[k] - rXYZ[k] > 0.5 || vmodXYZ[k] - rXYZ[k] < -0.5) {
					throw new ArithmeticException("Seismic and VModel locations don't agree here.");
				}
			}
		}
		return true;
	}
}
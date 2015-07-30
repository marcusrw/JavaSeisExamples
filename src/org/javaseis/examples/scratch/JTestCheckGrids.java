package org.javaseis.examples.scratch;

import java.io.FileNotFoundException;
import java.util.Arrays;

import org.javaseis.grid.GridDefinition;

import beta.javaseis.distributed.DistributedArrayMosaicPlot;
import beta.javaseis.distributed.DistributedArrayPositionIterator;

import org.javaseis.services.ParameterService;
import org.javaseis.test.testdata.FindTestData;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.javaseis.volume.SeismicVolume;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.FileSystemIOService;
import beta.javaseis.distributed.IDistributedIOService;
import beta.javaseis.parallel.IParallelContext;
import beta.javaseis.parallel.UniprocessorContext;

public class JTestCheckGrids {

	IParallelContext pc;
	ParameterService parms;
	ToolContext toolContext;
	ISeismicVolume seismicInput;
	IDistributedIOService ipio = null;
	GridDefinition globalGrid;
	ICheckGrids checkGrid;

	@Before
	public void loadDataIntoVolume() {
		pc = new UniprocessorContext();

		// Specify which data to load
		String inputFileName = "segshotno1.js";
		try {
			// Use the find test data to populate your parameterservice with
			// IO info
			parms = new FindTestData(inputFileName).getParameterService();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Push that parameterservice into a toolContext and add the
		// parallelcontext
		toolContext = new ToolContext(parms);
		toolContext.setParallelContext(pc);

		// start the file system IO service on the folder that FindTestData
		// found
		try {
			ipio = new FileSystemIOService(pc, toolContext.getParameter(ToolContext.INPUT_FILE_SYSTEM));
		} catch (SeisException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Open your chosen file for reading
		try {
			ipio.open(toolContext.getParameter(ToolContext.INPUT_FILE_PATH));
		} catch (SeisException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Get the global grid definition
		globalGrid = ipio.getGridDefinition();

		// Now you have enough to make a SeismicVolume
		ISeismicVolume inputVolume = new SeismicVolume(pc, globalGrid);
		seismicInput = inputVolume;

		// match the IO's DA with the Volume's DA
		ipio.setDistributedArray(inputVolume.getDistributedArray());

		while (ipio.hasNext()) {
			ipio.next();
			inputVolume.setVolumePosition(ipio.getFilePosition());
			try {
				ipio.read();
			} catch (SeisException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// and do something to it
			// ie, view it
			//DistributedArray da = inputVolume.getDistributedArray();
			//DistributedArrayMosaicPlot.showAsModalDialog(da, "Is it loading?");

			// or call CheckGrids on it
			String vModelFileName = "segsaltmodel.js";
			parms.setParameter("vModelFilePath", vModelFileName);
			checkGrid = new CheckGrids(inputVolume, toolContext);
			//System.out.println(Arrays.toString(checkGrid.getSourceXYZ()));
			
		}
	}
	
	@Test
	public void testCoords(){
		try{
			testCoords(seismicInput, checkGrid, toolContext);
		}catch(IllegalArgumentException e){
			e.getMessage();
		}
	}
	
	@Test 
	public void sourcesEqual(){
		try{
			sourcesEqual(checkGrid, seismicInput);
		} catch (IllegalArgumentException e){
			e.getMessage();
		}
	}
	

	/*
	 * Checks if all the sources are the same for all the traces over a specific
	 * volume.
	 */
	private boolean sourcesEqual(ICheckGrids CheckGrid, ISeismicVolume input) {
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
			// jscs.getSourceXYZ(globalPosIndex, sXYZ);
			sXYZ = CheckGrid.getSourceXYZ(globalPosIndex);

			// check that this source is equal to the local source
			for (int i = 0; i < sourceXYZ.length; i++) {
				if (sourceXYZ[i] != sXYZ[i]) {
					// Sources don't match
					System.out
							.println("[sourcesEqual]: Sources Change at Location: " + Arrays.toString(globalPosIndex));
					System.out.println("\t[sourcesEqual]: " + "Expected: " + Arrays.toString(sourceXYZ) + "Given: "
							+ Arrays.toString(sXYZ));

					throw new IllegalArgumentException("Sources Change Between Trace.");
					// return false;
				}
			}
		}
		return true;
	}

	// Helper method
	private IVelocityModel getVelocityModelObject(ICheckGrids CheckGrid, ToolContext toolContext) {
		IVelocityModel vmff = null;
		try {
			vmff = new VelocityModelFromFile(toolContext);
			// vmff = new VelocityModelFromFile(pc,folder,file);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		vmff.open("r");
		// System.out.println("[VelocityModelFromFile: Input grid");
		// System.out.println(inputGrid.toString());
		// System.out.println("Axis order: " + Arrays.toString(AXIS_ORDER));
		// vmff.orientSeismicVolume(inputGrid,AXIS_ORDER);
		vmff.orientSeismicVolume(CheckGrid.getModifiedGrid(), CheckGrid.getAxisOrder());
		return vmff;
	}

	private boolean testCoords(ISeismicVolume input, ICheckGrids CheckGrid, ToolContext toolContext) {

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
			// globalPosIndex[0] = zindx;
			globalPosIndex[0] = 0; // 0 the depth field index

			for (int k = 1; k < 3; k++) {
				globalPosIndex[k] = volumePosIndex[k];
			}

			// rXYZ2 hold the receiver location for a given trace location
			// [0,?,?,0]
			double[] rXYZ = new double[3];

			// jscs.getReceiverXYZ(globalPosIndex, rXYZ);
			rXYZ = CheckGrid.getReceiverXYZ(globalPosIndex);

			// LOGGER.fine("[processVolume]: Pos: " +
			// Arrays.toString(globalPosIndex) +
			// " recXYZ jscs: " + Arrays.toString(rXYZ));

			/// TEST CODE///
			// Compare the jscs coords to the coord based on pOrigin + indx *
			/// pDelta

			// rXYZ value from the Grid instead of jscs
			double[] rXYZ2 = new double[3];

			// Check if both indexes are in range
			// double yIndex = globalPosIndex[Yindex];
			// double xIndex = globalPosIndex[Xindex];

			double yIndex = globalPosIndex[CheckGrid.getAxisOrder()[1]];
			double xIndex = globalPosIndex[CheckGrid.getAxisOrder()[0]];

			// Calculate position in Xline

			// int currentAxis = Yindex; //Trace axis (Y we believe)
			int currentAxis = CheckGrid.getAxisOrder()[1];

			double minPhys0 = CheckGrid.getModifiedGrid().getAxisPhysicalOrigin(currentAxis);
			double axisPhysDelta = CheckGrid.getModifiedGrid().getAxisPhysicalDelta(currentAxis);
			double yval = minPhys0 + yIndex * axisPhysDelta;

			// Calculate position in Iline
			// currentAxis = Xindex;
			currentAxis = CheckGrid.getAxisOrder()[0];
			minPhys0 = CheckGrid.getModifiedGrid().getAxisPhysicalOrigin(currentAxis);
			axisPhysDelta = CheckGrid.getModifiedGrid().getAxisPhysicalDelta(currentAxis);
			double xval = minPhys0 + xIndex * axisPhysDelta;

			// Set rXYZ2 Grids Calculations
			rXYZ2[0] = xval;
			rXYZ2[1] = yval;

			rXYZ2[2] = 0;
			// rXYZ2[2] = depth;

			// LOGGER.fine("[processVolume]: Values From Grid (rXYZ2):" +
			// Arrays.toString(rXYZ2));

			double[] vmodXYZ = vmff.getVelocityModelXYZ(globalPosIndex);
			// LOGGER.fine("Physical Location in VModel for Position: "
			// + Arrays.toString(globalPosIndex) + " is " +
			// Arrays.toString(vmff.getVelocityModelXYZ(globalPosIndex)));

			/*
			 * System.out.println("AXIS_ORDER: " +
			 * Arrays.toString(CheckGrid.getAxisOrder())); System.out.println(
			 * "Global Position Index: " + Arrays.toString(globalPosIndex));
			 * System.out.println("Receiver XYZ from RegularGrids: " +
			 * Arrays.toString(rXYZ2)); System.out.println("Receiver XYZ: " +
			 * Arrays.toString(rXYZ));
			 */

			// Don't check 1st position that is depth
			for (int k = 0; k < 2; k++) {
				if (rXYZ2[k] - rXYZ[k] > 0.5 || rXYZ2[k] - rXYZ[k] < -0.5) {
					System.out.println("AXIS_ORDER: " + Arrays.toString(CheckGrid.getAxisOrder()));
					System.out.println("Global Position Index: " + Arrays.toString(globalPosIndex));
					System.out.println("Receiver XYZ from RegularGrids: " + Arrays.toString(rXYZ2));
					System.out.println("Receiver XYZ: " + Arrays.toString(rXYZ));
					throw new ArithmeticException("The origin/delta position doesn't match the getRXYZ position");
				}
			}

			// Don't check 1st position that is depth pos
			// vmodXYZ.length - 1 so that we don't check the depth
			for (int k = 0; k < vmodXYZ.length - 1; k++) {
				if (vmodXYZ[k] - rXYZ[k] > 0.5 || vmodXYZ[k] - rXYZ[k] < -0.5) {
					System.out.println("AXIS_ORDER: " + Arrays.toString(CheckGrid.getAxisOrder()));
					System.out.println("Global Position Index: " + Arrays.toString(globalPosIndex));
					System.out.println("Velocity Model XYZ: " + Arrays.toString(vmodXYZ));
					System.out.println("Receiver XYZ: " + Arrays.toString(rXYZ));
					throw new ArithmeticException("Seismic and VModel locations don't agree here.");
				}
			}

			// TODO:Test - remove this code!!
			/*
			 * try { Thread.sleep(2500); } catch(InterruptedException ex) {
			 * Thread.currentThread().interrupt(); }
			 */
			// end
		}
		// LOGGER.info("[processVolume]: ----- DEBUG TEST ENDED -----");
		return true;
	}

}

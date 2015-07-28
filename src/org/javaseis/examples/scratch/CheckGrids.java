package org.javaseis.examples.scratch;

import java.io.File;
import java.util.Arrays;
import java.util.logging.Level;

import org.javaseis.grid.BinGrid;
import org.javaseis.grid.GridDefinition;
import org.javaseis.io.Seisio;
import org.javaseis.properties.AxisDefinition;
import org.javaseis.tool.ToolContext;
import org.javaseis.util.SeisException;
import org.javaseis.volume.ISeismicVolume;
import org.junit.Assert;

import beta.javaseis.distributed.DistributedArray;
import beta.javaseis.distributed.DistributedArrayPositionIterator;
import beta.javaseis.services.CoordinateType;
import beta.javaseis.services.JSCoordinateService;

public class CheckGrids implements ICheckGrids {
	private ISeismicVolume input;
	private ToolContext toolContext;
	private GridDefinition modifiedGrid; 
	
	private int Xindex = 2;
	private int Yindex = 1;
	private int[] AXIS_ORDER;
	
	private JSCoordinateService jscs;
	
	//Source Position
	private double[] sourceXYZ;
	
	public CheckGrids(ISeismicVolume input, ToolContext toolContext){
		this.input = input;
		this.toolContext = toolContext;
		
		try {
		   this.jscs = openTraceHeadersFile(toolContext);
		} catch (SeisException e){
		   //LOGGER.log(Level.INFO,e.getMessage(),e);
		   e.getMessage();
		}
		
		
		//Updates the Grid
		checkVolumeGridDefinition(this.toolContext, this.input);
		
		try{
			sourcesEqual();
		}
		catch(IllegalArgumentException e){
			//LOGGER.log(Level.INFO,e.getMessage(),e);
			e.getMessage();
		}
	}
	
	private JSCoordinateService openTraceHeadersFile(ToolContext toolContext)
		    throws SeisException {
		    String inputFilePath
		    = toolContext.getParameter("inputFileSystem")
		    //= toolContext.getParameterService().getParameter("inputFileSystem","null")
		    + File.separator
		    + toolContext.getParameter("inputFilePath");
		    //+toolContext.getParameterService().getParameter("inputFilePath","null");

		    Seisio sio;

		    try {
		      sio = new Seisio(inputFilePath);
		      sio.open("r");
		      sio.usesProperties(true);
		      GridDefinition grid = sio.getGridDefinition();
		      int xdim = Yindex;  //2nd array index
		      int ydim = Xindex;  //3rd array index
		      BinGrid bingrid = new BinGrid(grid,xdim,ydim);
		      Assert.assertNotNull(bingrid);     
		      String[] coordprops = new String[]
		          {"SOU_XD","SOU_YD","SOU_ELEV","REC_XD","REC_YD","REC_ELEV"};
		      //The JSCS source/javadoc should explain that ORDER MATTERS HERE.
		      return new JSCoordinateService(sio,bingrid,
		          CoordinateType.SHOTRCVR,coordprops);

		    } catch (SeisException e) {
		      //LOGGER.log(Level.SEVERE,e.getMessage(),e);
		      //LOGGER.severe("Something is very wrong if you're seeing this.");
		      throw e;
		    }
		  }
	
	private void checkVolumeGridDefinition(ToolContext toolContext, ISeismicVolume input){
		modifiedGrid = updateVolumeGridDefinition(toolContext,input);
	}
	
	private GridDefinition updateVolumeGridDefinition(ToolContext toolContext, ISeismicVolume input) {
		//Get the grid from SeismicVolume
		GridDefinition inputGrid = input.getGlobalGrid();
		long[] inputAxisLengths = inputGrid.getAxisLengths();

		//Get the Starting point in a Volume
	    int [] VolPos = input.getVolumePosition();
	    System.out.println("[updateVolumeGridDefinition] VolumePos: " + Arrays.toString(VolPos));

	    int[] pos = Arrays.copyOf(VolPos, VolPos.length);

	    sourceXYZ = new double[3];
	    double[] rxyz = new double[3];
	    double[] rxyz2 = new double[3];
	    double[] recXYZsrc = new double[3];

	    AxisDefinition[] physicalOAxisArray =
	        new AxisDefinition[inputAxisLengths.length];

	    jscs.getReceiverXYZ(pos, rxyz);

	    for (int k = 1 ; k < 3 ; k++) {
	      pos[k]++;
	      jscs.getReceiverXYZ(pos, rxyz2);
	      System.out.println("pos: " + Arrays.toString(pos));
	      System.out.println("rxyz: " + Arrays.toString(rxyz));
	      System.out.println("rxyz2: " + Arrays.toString(rxyz2));
	      if (Math.abs(rxyz[0] - rxyz2[0]) > 0.5) {
	        Xindex = k;
	        System.out.println("Xindex is " + Xindex);
	      }
	      pos[k]--;
	    }
	    for (int k = 1 ; k < 3 ; k++) {
	      pos[k]++;
	      jscs.getReceiverXYZ(pos, rxyz2);
	      System.out.println("pos: " + Arrays.toString(pos));
	      System.out.println("rxyz: " + Arrays.toString(rxyz));
	      System.out.println("rxyz2: " + Arrays.toString(rxyz2));
	      if (Math.abs(rxyz[1] - rxyz2[1]) > 0.5) {
	        Yindex = k;
	        System.out.println("Yindex is " + Yindex);
	      }
	      pos[k]--;
	    }
	    
	    AXIS_ORDER = new int[] {Xindex,Yindex,0};

	    jscs.getReceiverXYZ(pos, rxyz);
	    System.out.println("[updateVolumeGridDefinition] rec1 Pos: " + Arrays.toString(rxyz));
	    pos[1]++;
	    pos[2]++;
	    jscs.getReceiverXYZ(pos,rxyz2);
	    System.out.println("[updateVolumeGridDefinition] rec2 Pos: " + Arrays.toString(rxyz2));
	    
	    //TODO hack
	    pos[1] = 100;
	    pos[2] = 100;
	    
	    jscs.getSourceXYZ(pos, sourceXYZ);	    
	    
	    jscs.getReceiverXYZ(pos,recXYZsrc);
	    System.out.println("[updateVolumeGridDefinition] sourceXYZ Pos: " + Arrays.toString(sourceXYZ));
	    System.out.println("[updateVolumeGridDefinition] sourceXYZ Pos Check: " + Arrays.toString(recXYZsrc));
	    for (int k = 0 ; k < rxyz2.length ; k++) {
	      rxyz2[k] -= rxyz[k];
	    }

	    System.out.println("[updateVolumeGridDefinition] New PhysO: " + Arrays.toString(rxyz));
	    System.out.println("[updateVolumeGridDefinition] New Deltas: " + Arrays.toString(rxyz2));
	    System.out.println("[updateVolumeGridDefinition] Axis Lengths: " + Arrays.toString(inputAxisLengths));

	    for (int k = 0; k < inputAxisLengths.length ; k++) {
	      AxisDefinition inputAxis = inputGrid.getAxis(k);
	      physicalOAxisArray[k] = new AxisDefinition(inputAxis.getLabel(),
	          inputAxis.getUnits(),
	          inputAxis.getDomain(),
	          inputAxis.getLength(),
	          inputAxis.getLogicalOrigin(),
	          inputAxis.getLogicalDelta(),
	          CalculateNewPhysicalOrigin(inputAxis, k, rxyz),
	          CalculateNewDeltaOrigin(inputAxis, k, rxyz2));
	    }

	    //For debugging
	    GridDefinition modifiedGrid = new GridDefinition(inputGrid.getNumDimensions(),physicalOAxisArray);
	    System.out.println(modifiedGrid.toString());

	    double[] physicalOrigins = modifiedGrid.getAxisPhysicalOrigins();
	    double[] deltaA = modifiedGrid.getAxisPhysicalDeltas();

	    System.out.println("[updateVolumeGridDefinition] Physical Origins from data: " + Arrays.toString(rxyz));
	    System.out.println("[updateVolumeGridDefinition] Physical Origins from grid: " + Arrays.toString(physicalOrigins));

	    System.out.println("[updateVolumeGridDefinition] Physical Origins from data: " + Arrays.toString(rxyz2));
	    System.out.println("[updateVolumeGridDefinition] Physical Deltas from grid: " + Arrays.toString(deltaA));
	    //DBG end


	    return modifiedGrid;
	}
	
	//Adjust the new Physical Delta
	private double CalculateNewDeltaOrigin(AxisDefinition axis, int k, double[] data) {
	    if (k == Xindex){
	      return data[0];
	    }
	    else if (k == Yindex){
	      return data[1];
	    }
	    else{
	      return axis.getPhysicalDelta();
	    }
	}
	
	//Adjust the new Physical Origin
	private double CalculateNewPhysicalOrigin(AxisDefinition axis, int k, double [] data) {
	    if (k == Xindex){
	      return data[0];
	    }
	    else if (k == Yindex){
	      return data[1];
	    }
	    else{
	      return axis.getPhysicalOrigin();
	    }
	}
	
	//Checks if all the sources are the same for the traces over a specific volume
	private boolean sourcesEqual(){
		//check if the input DistArray Sources match the computed source
		DistributedArray inputDistArr = input.getDistributedArray();
		
		//Get the current volume
	    int[] globalPosIndex = input.getVolumePosition();
	    int[] volumePosIndex = new int[3];
		
	    //Iterate over traces
	    DistributedArrayPositionIterator itrInputArr = 
	        new DistributedArrayPositionIterator(inputDistArr, volumePosIndex, 
	            DistributedArrayPositionIterator.FORWARD);
	  
	    while (itrInputArr.hasNext()) {
	        volumePosIndex = itrInputArr.next();

	        globalPosIndex[0] = 0;
	        for (int k = 1 ; k < 3 ; k++) {
	          globalPosIndex[k] = volumePosIndex[k];
	        }
	        
	        //Get the source positions at [0,?,?,v]
	        double [] sXYZ = new double[3];
	        jscs.getSourceXYZ(globalPosIndex, sXYZ);
	      
	        //check that this source is equal to the local source
	        for (int i = 0; i < sourceXYZ.length; i++){
	        	if (sourceXYZ[i] != sXYZ[i]){
		        	//Sources don't match
	        		System.out.println("[sourcesEqual]: Sources Change at Location: " 
	        				+ Arrays.toString(globalPosIndex));
	        		System.out.println("\t[sourcesEqual]: " + "Expected: " 
	        				+ Arrays.toString(sourceXYZ)
	        				+ "Given: " + Arrays.toString(sXYZ));
	        		
	        		throw new IllegalArgumentException("Sources Change Between Trace.");
	        		//return false;
	        	}
	        }
	    }
	    return true;
	}

	//Get the Source Position
	public double[] getSourceXYZ(int[] gridPos){
		double [] sXYZ = new double[3];
		jscs.getSourceXYZ(gridPos, sXYZ);
		return sXYZ;
	}
	
	/*
	 * Get the Receiver Position
	 */
	public double[] getReceiverXYZ(int[] gridPos){
		double [] rXYZ = new double[3];
		//Get receiver from XYZ
		jscs.getReceiverXYZ(gridPos, rXYZ);		
		return rXYZ;
	}
	
	public int[] getAxisOrder(){
		return AXIS_ORDER;
	}
	
	public GridDefinition getGlobalGrid(){
		return modifiedGrid;
	}
	
}

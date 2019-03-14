package car.ray;

import com.jme3.math.Vector3f;

import helper.Log;

public class RayWheel {

	protected final int num;
	protected final WheelDataConst data;
	
	public boolean inContact;
	public Vector3f curBasePosWorld;
	public Vector3f hitNormalInWorld;
	public float susRayLength; //length from start of sus vector
	public float susForce;
	
	public float steering;
	public float radSec;
	public float skidFraction; //was 'skid'
	public Vector3f gripDir;
	
	public final float maxLong;
	public final float maxLat;
	public final float maxLongSat;
	public final float maxLatSat;
	
	public RayWheel(int num, WheelDataConst data, Vector3f offset) {
		this.num = num;
		this.data = data;
		this.gripDir = new Vector3f();
		
		//generate the slip* max force from the car wheel data
		//TODO precompute in the yaml file?
		maxLat = RayCar.GripHelper.calcSlipMax(data.pjk_lat);
		maxLong = RayCar.GripHelper.calcSlipMax(data.pjk_long);
		
		maxLatSat = RayCar.GripHelper.calcSlipMax(data.pjk_lat_sat);
		maxLongSat = RayCar.GripHelper.calcSlipMax(data.pjk_long_sat);
		
		try {
			if (Float.isNaN(maxLat))
				throw new Exception("maxLat was: '" + maxLat +"'.");
			if (Float.isNaN(maxLong))
				throw new Exception("maxLong was: '" + maxLong +"'.");
			
			if (Float.isNaN(maxLatSat))
				throw new Exception("maxLatSat was: '" + maxLatSat +"'.");
			if (Float.isNaN(maxLongSat))
				throw new Exception("maxLongSat was: '" + maxLongSat +"'.");
		} catch (Exception e) {
			e.printStackTrace();
			Log.p("error in calculating max(lat|long) values of: " + num);
			System.exit(1);
		}
	}
	
	//[softly] should this class do anything else?
}

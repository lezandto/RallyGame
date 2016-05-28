package car;

import com.bulletphysics.dynamics.vehicle.VehicleTuning;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

public abstract class CarData {
	
	public static final String dir = "assets/models/";

	//List of not primitives:
	public VehicleTuning vt = null; //the original code doesn't even use it
	//grip constants
	public CarWheelData w_flatdata = new NormalLatData();
	public CarWheelData w_flongdata = new NormalLongData();
	
	//model strings (can be xx.obj or xx.blend)
	public String carModel = dir+"car4.obj";
	public String wheelModel = dir+"wheel.obj";
	
	//camera options
	public Vector3f cam_lookAt = new Vector3f(0,1.3f,0); //top of car usually
	public Vector3f cam_offset = new Vector3f(0,2.1f,-6); //where the camera is
	
	public float rollFraction = 0.5f; //1 = full into roll, 0 = no roll
	
	//physical things
	public float mass = 1200; //kg (total)
	public float width = 1.4f; //x size meter, door handle to door handle
	public float height = 1f; //y size meter, roof to ground
	public float length = 3f; //z size meter, from front to back
	
	//wheels axles directions
	public float w_steerAngle = 0.5f; //radians
	
	public Vector3f w_vertical = new Vector3f(0, -1, 0); //vertical
	public Vector3f w_axle = new Vector3f(-1, 0, 0); //horizontal

	public float w_width = 0.15f; //m
	public float w_radius = 0.3f; //m
	public float w_mass = 75; //kg
	
	//TODO make front and back independant (maybe even each wheel)
	public float w_xOff = 0.68f; //wheels x offset (side), meters
	public float w_yOff = 0f; //wheels y offest (height), meters
	public float w_zOff = 1.1f; //wheels z offset (front and back), meters
	
	//suspension
	//see for details: https://docs.google.com/Doc?docid=0AXVUZ5xw6XpKZGNuZG56a3FfMzU0Z2NyZnF4Zmo&hl=en
	public float sus_stiffness = 60.0f; //200=f1 car
	public float sus_compValue = 0.6f; //(should be lower than damp)
	public float sus_dampValue = 0.7f;
	public float sus_restLength = 0f;
	
	public float susCompression() { return sus_compValue * 2 * FastMath.sqrt(sus_stiffness); }
	public float susDamping() { return sus_dampValue * 2 * FastMath.sqrt(sus_stiffness); }
	public float sus_maxForce = 25*mass; //TODO '25' is a random number.
	public float sus_maxTravel = 50; //cms
	
	//jme3 grip constants
	//my physics works with 0f, but feels tighter with: 1.0f
	public float wheelBasicSlip = 0;
	
	//drag constants
	public float drag = 1.0f; //squared component
	public float lineardrag = 0.02f;
	public float resistance() {//linear component (https://en.wikipedia.org/wiki/Rolling_resistance)
		return 9.81f*mass*lineardrag/w_radius;
	}

	public float brakeMaxTorque = 4000; 
	public Vector3f JUMP_FORCE = new Vector3f(0, 5*mass, 0);
	
	public boolean driveFront = false, driveRear = true; //this would be rear wheel drive
	
	//this one is from the notes, is a ~1999 corvette c6 
	public float[] e_torque = new float[]{0,390,445,460,480,475,360,10}; //starts at 0 rpm, steps every 1000rpm (until done)
		//TODO maybe 500 rpm splits (will get better peaks)
	
	public int auto_gearDown = 2400; //rpm triggering a gear down
	public int auto_gearUp = 5500;
	public int e_redline = 6500;
	
	public float e_compression = 0.1f; //is going to be multiplied by the RPM
	public float e_mass = 100; //kg
	public float e_inertia() { 
		float wheels = (w_mass*w_radius*w_radius/2);
		if (driveFront && driveRear) {
			return e_mass + wheels*4;
		}
		return e_mass + wheels*2;
	}
	
	
	public float trans_effic = 0.75f; //TODO apparently 0.7 is common (power is lost to rotating things)
	public float trans_finaldrive = 2.5f; //helps set the total drive ratio
	public float[] trans_gearRatios = new float[]{-2.9f,3.40f,2.5f,1.8f,1.3f,1.0f,0.74f}; //reverse,gear1,gear2,g3,g4,g5,g6,...
	
	//TODO i found a porsche boxter engine curve:
//	public float[] torque = new float[]{0,223,250,280,300,310,280,245,10};

	public boolean nitro_on = true;
	public float nitro_force = 300; //TODO find a good number
	public float nitro_rate = 1;
	public float nitro_max = 15;
	
	///////////////////
	//usefulMethods
	
	//get the max power
	public float[] getMaxPower() {
		float max = 0;
		float maxrpm = 0;
		for (int i = 0; i < e_torque.length; i++) {
			float prevmax = max;
			max = Math.max(max, e_torque[i]*(1000*i)/9549);
			if (prevmax != max) maxrpm = i;
		} //http://www.autospeed.com/cms/article.html?&title=Power-versus-Torque-Part-1&A=108647
		return new float[]{max, maxrpm*1000};
	}
}



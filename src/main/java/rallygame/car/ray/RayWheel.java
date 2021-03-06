package rallygame.car.ray;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;

import rallygame.car.data.WheelDataConst;

/** Class to store real-time data of this wheel */
public class RayWheel {

    protected final int num;
    protected final WheelDataConst data;
    
    public Vector3f rayStartWorld;
    public Vector3f rayDirWorld;

    public boolean inContact;
    public Vector3f curBasePosWorld;
    public Vector3f hitNormalInWorld;
    public PhysicsRigidBody collisionObject;

    public float susRayLength; //length from start of sus vector
    public float susForce;
    
    public float steering;
    public float radSec;
    
    public float skidFraction; //was 'skid'
    public Vector3f gripDir;
    
    public float slipAngle;
    public float slipRatio;
    public float rollingResistance;

    public RayWheel(int num, WheelDataConst data, Vector3f offset) {
        this.num = num;
        this.data = data;
        this.gripDir = new Vector3f();
        this.curBasePosWorld = new Vector3f();
        this.hitNormalInWorld = new Vector3f();
    }
    
    //[softly] should this class do anything
    
    //consider an inteface to give to CarUITelemetry
}

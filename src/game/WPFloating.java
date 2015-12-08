package game;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;


public enum WPFloating implements WP {
	STRAIGHT("straight.blend", new Vector3f(20,0,0), Quaternion.IDENTITY),
	
	//these weird angles are just to show i can do it...
//	STRAIGHT_UP("straight.blend", new Vector3f(20,0,0), new Quaternion(0, 0, 0.098f, 0.9952f)),
//	STRAIGHT_DOWN("straight.blend", new Vector3f(20,0,0), new Quaternion(0, 0, -0.098f, 0.9952f)),
	
//	LEFT_CURVE("left.blend", new Vector3f(30,0,-30), new Quaternion(0, 0.7071f, 0, 0.7071f)),
//	RIGHT_CURVE("right.blend", new Vector3f(30,0,30), new Quaternion(0, -0.7071f, 0, 0.7071f)),
	;
	
	private static String folder = "wbfloating/";
	
	String name;
	Vector3f newPos; //what the piece does to the track
	Quaternion newRot;

	WPFloating(String s, Vector3f a, Quaternion g) {
		this.name = s;
		this.newPos = a;
		this.newRot = g;
	}
	public float getScale() { return 1; }
	public String getName() { return folder+name; }
	public Vector3f getNewPos() { return new Vector3f(newPos); }
	public Quaternion getNewAngle() { return new Quaternion(newRot); }
}

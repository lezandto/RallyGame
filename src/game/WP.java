package game;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

public interface WP {
	
	public float getScale();
	public String getName();
	public Vector3f getNewPos();
	public Quaternion getNewAngle();
}

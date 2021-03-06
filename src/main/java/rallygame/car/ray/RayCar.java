package rallygame.car.ray;

import java.util.function.Consumer;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import rallygame.car.data.CarDataConst;
import rallygame.car.data.CarSusDataConst;
import rallygame.service.ray.IPhysicsRaycaster;
import rallygame.service.ray.PhysicsRaycaster;
import rallygame.service.ray.RaycasterResult;

//doesn't extend anything, but here are some reference classes
//https://github.com/jMonkeyEngine/jmonkeyengine/blob/master/jme3-core/src/main/java/com/jme3/scene/Spatial.java
//https://github.com/jMonkeyEngine/jmonkeyengine/blob/master/jme3-core/src/main/java/com/jme3/math/Transform.java
//https://github.com/jMonkeyEngine/jmonkeyengine/blob/master/jme3-bullet/src/common/java/com/jme3/bullet/control/RigidBodyControl.java
//https://github.com/jMonkeyEngine/jmonkeyengine/blob/master/jme3-jbullet/src/main/java/com/jme3/bullet/objects/PhysicsVehicle.java
//https://github.com/bubblecloud/jbullet/blob/master/src/main/java/com/bulletphysics/dynamics/vehicle/RaycastVehicle.java


//Extending traction model:
//add in a quadratic normal force forumla normal(x) = NK1 + N^2*K2 + K3
//ideally replaces E in the base formla from: https://www.edy.es/dev/docs/pacejka-94-parameters-explained-a-comprehensive-guide/
//Example given: https://github.com/chrisoco/M120/blob/master/RaceCar/RCAS/src/rcas/model/MagicFormulaTireModel.java

/** Handles suspension/traction/drag and real-time data of this car */
public class RayCar implements PhysicsTickListener {

	private static final Vector3f localDown = new Vector3f(0, -1, 0);

	protected CarDataConst carData;
	private IPhysicsRaycaster raycaster;
	protected final RigidBodyControl rbc;
	protected boolean rbEnabled() { return rbc.isEnabled() && rbc.isInWorld(); }

	// simulation variables
	private float steeringCur;
	private float brakingCur;
	private boolean handbrakeCur;
	protected final RayWheel[] wheels;
	protected final float[] wheelTorque;

	// debug values
	protected Vector3f dragDir;
	protected float driftAngle;
	public final Vector3f planarGForce;
	protected float travelledDistance;

	// hacks
	protected boolean tractionEnabled = true;

	public RayCar(CollisionShape shape, CarDataConst carData) {
		this.carData = carData;

		this.planarGForce = new Vector3f();

		this.wheels = new RayWheel[carData.wheelData.length];
		for (int i = 0; i < wheels.length; i++) {
			wheels[i] = new RayWheel(i, carData.wheelData[i], carData.wheelOffset[i]);
		}
		this.wheelTorque = new float[4];
		this.rbc = new RigidBodyControl(shape, carData.mass);
	}

	@Override
	public void physicsTick(PhysicsSpace space, float tpf) {
		// this is intentionally left blank
	}

	@Override
	public void prePhysicsTick(PhysicsSpace space, float tpf) {
		if (this.raycaster == null || raycaster.getPhysicsSpace() != space)
			this.raycaster = new PhysicsRaycaster(space);

		travelledDistance += rbc.getLinearVelocity().length() * tpf;

		if (!rbEnabled())
			return;

		applySuspension(space, tpf);

		// TODO apply the midpoint formula or any kind of actual physics stepped simulation method
		// https://en.wikipedia.org/wiki/Midpoint_method
		if (tractionEnabled)
			applyTraction(space, tpf);

		applyDrag(space, tpf);
	}

	private void applySuspension(PhysicsSpace space, float tpf) {
		Quaternion w_angle = rbc.getPhysicsRotation();

		// Do suspension ray cast
		doForEachWheel((w_id) -> {
			Vector3f localPos = carData.wheelOffset[w_id];

			CarSusDataConst sus = carData.susByWheelNum(w_id);
			float susTravel = sus.travelTotal();

			// cast ray from suspension min, to max + radius (radius because bottom out check might as well be the size of the wheel)
			wheels[w_id].rayStartWorld = vecLocalToWorld(localPos.add(localDown.mult(sus.min_travel)));
			wheels[w_id].rayDirWorld = w_angle.mult(localDown.mult(susTravel + carData.wheelData[w_id].radius));
			RaycasterResult col = raycaster.castRay(wheels[w_id].rayStartWorld, wheels[w_id].rayDirWorld, rbc);

			if (col == null) { // suspension ray found nothing, extend all the way and don't apply a force (set to 0)
				wheels[w_id].inContact = false;
				wheels[w_id].susRayLength = susTravel;
				wheels[w_id].curBasePosWorld = localDown.mult(susTravel + carData.wheelData[w_id].radius);
				return; // no force
			}

			// TODO calculate rebound using wheel mass and suspension properties
			// this should allow you to get air off of very small ramps (for a very short time)

			wheels[w_id].hitNormalInWorld = col.hitNormalInWorld;
			wheels[w_id].collisionObject = col.obj;
			wheels[w_id].susRayLength = col.dist - carData.wheelData[w_id].radius; // remove the wheel radius
			wheels[w_id].curBasePosWorld = col.pos;
			wheels[w_id].inContact = true; // wheels are still touching..
		});

		// Do suspension forces
		doForEachWheel((w_id) -> {
			CarSusDataConst sus = carData.susByWheelNum(w_id);
			if (!wheels[w_id].inContact) {
				wheels[w_id].susForce = 0;
				return;
			}

			if (wheels[w_id].susRayLength < 0) { // suspension bottomed out, apply max
				wheels[w_id].susForce = (sus.preload_force + sus.travelTotal()) * sus.stiffness * 1000;
				Vector3f f = w_angle.inverse().mult(wheels[w_id].hitNormalInWorld.mult(wheels[w_id].susForce * tpf));
				applyWheelForce(f, wheels[w_id]);
				return;
			}

			float denominator = wheels[w_id].hitNormalInWorld.dot(w_angle.mult(localDown)); // loss due to difference between collision and localdown (cos ish)
			Vector3f velAtContactPoint = getVelocityInWorldPoint(rbc, wheels[w_id].curBasePosWorld); // get sus vel at point on ground
			Vector3f otherVelAtContactPoint = new Vector3f();
			if (wheels[w_id].collisionObject != null)
				otherVelAtContactPoint = getVelocityInWorldPoint(wheels[w_id].collisionObject, wheels[w_id].curBasePosWorld);

			// percentage of normal force that applies to the current motion
			float projVel = wheels[w_id].hitNormalInWorld.dot(velAtContactPoint.subtract(otherVelAtContactPoint));
			float projected_rel_vel = 0;
			float clippedInvContactDotSuspension = 0;
			if (denominator >= -0.1f) {
				projected_rel_vel = 0f;
				clippedInvContactDotSuspension = 1f / 0.1f;
			} else {
				float inv = -1f / denominator;
				projected_rel_vel = projVel * inv;
				clippedInvContactDotSuspension = inv;
			}

			// Calculate spring distance from its zero length, as it should be outside the suspension range
			float springDiff = sus.travelTotal() - wheels[w_id].susRayLength;

			// Spring
			wheels[w_id].susForce = (sus.preload_force + sus.stiffness * springDiff) * clippedInvContactDotSuspension;

			// Damper
			float susp_damping = (projected_rel_vel < 0f) ? sus.compression() : sus.relax();
			wheels[w_id].susForce -= susp_damping * projected_rel_vel;

			// Sway bars https://forum.miata.net/vb/showthread.php?t=25716
			int w_id_other = w_id == 0 ? 1 : w_id == 1 ? 0 : w_id == 2 ? 3 : 2; // fetch the index of the other side
			float swayDiff = wheels[w_id_other].susRayLength - wheels[w_id].susRayLength;
			wheels[w_id].susForce += swayDiff * sus.antiroll;

			wheels[w_id].susForce *= 1000; // kN

			// applyImpulse (force = world space, pos = relative to local)
			Vector3f f = wheels[w_id].hitNormalInWorld.mult(wheels[w_id].susForce * tpf);
			applyWheelForce(f, wheels[w_id]);
		});
	}

	private void applyTraction(PhysicsSpace space, float tpf) {
		Quaternion w_angle = rbc.getPhysicsRotation();
		Vector3f w_velocity = rbc.getLinearVelocity();
		Vector3f w_angVel = rbc.getAngularVelocity();

		planarGForce.set(Vector3f.ZERO); // reset

		Vector3f velocity = w_angle.inverse().mult(w_velocity);
		if (velocity.z == 0) // NaN on divide avoidance strategy
			velocity.z += 0.0001f;

		float steeringCur = this.steeringCur;
		if (velocity.z < 0) { // to flip the steering on moving in reverse
			steeringCur *= -1;
		}

		final float slip_div = velocity.length();
		final float steeringFake = steeringCur;
		doForEachWheel((w_id) -> {
			float lastSlipAngle = wheels[w_id].slipAngle;
			float lastSlipRatio = wheels[w_id].slipRatio;
			Vector3f wheel_force = new Vector3f();

			// Linear Accelerations: = player.car.length * player.car.yawrate (in rad/sec)
			float angVel = 0;
			if (!Float.isNaN(w_angVel.y))
				angVel = w_angVel.y;

			Vector3f objectRelVelocity = new Vector3f();
			if (wheels[w_id].collisionObject != null) // convert contact object to local co-ords
				objectRelVelocity = w_angle.inverse().mult(wheels[w_id].collisionObject.getLinearVelocity());

			float slipr = wheels[w_id].radSec * carData.wheelData[w_id].radius - (velocity.z - objectRelVelocity.z);
			wheels[w_id].slipRatio = slipr / slip_div;

			if (handbrakeCur && !isFrontWheel(w_id)) // rearwheels only
				wheels[w_id].radSec = 0;

			wheels[w_id].slipAngle = 0;
			if (isFrontWheel(w_id)) {
				float slipa_front = (velocity.x - objectRelVelocity.x) + carData.wheelOffset[w_id].z * angVel;
				wheels[w_id].slipAngle = FastMath.atan2(slipa_front, slip_div) - steeringFake;
			} else { // so rear
				float slipa_rear = (velocity.x - objectRelVelocity.x) + carData.wheelOffset[w_id].z * angVel;
				driftAngle = slipa_rear; // set drift angle as the rear amount
				wheels[w_id].slipAngle = FastMath.atan2(slipa_rear, slip_div); // slip_div is questionable here
			}

			slipSimulationHacks(w_id, lastSlipAngle, lastSlipRatio);

			// merging the forces into a traction circle
			// normalise based on their independant max values
			float ratiofract = wheels[w_id].slipRatio / carData.wheelData[w_id].maxLong;
			float anglefract = wheels[w_id].slipAngle / carData.wheelData[w_id].maxLat;
			float p = FastMath.sqrt(ratiofract * ratiofract + anglefract * anglefract);
			if (p == 0) {
				// if p is zero then both anglefract and ratiofract are 0. So to prevent a 'div 0' we just make the denominator 1
				p = 1;
			}

			wheels[w_id].skidFraction = p;

			// calc the longitudinal force from the slip ratio
			wheel_force.z = (ratiofract / p)
					* GripHelper.tractionFormula(carData.wheelData[w_id].pjk_long, p * carData.wheelData[w_id].maxLong)
					* GripHelper.loadFormula(carData.wheelData[w_id].pjk_long, this.wheels[w_id].susForce);
			// calc the latitudinal force from the slip angle
			wheel_force.x = -(anglefract / p)
					* GripHelper.tractionFormula(carData.wheelData[w_id].pjk_lat, p * carData.wheelData[w_id].maxLat)
					* GripHelper.loadFormula(carData.wheelData[w_id].pjk_lat, this.wheels[w_id].susForce);

			// braking and abs
			float brakeCurrent2 = brakingCur;
			if (Math.abs(ratiofract) >= 1 && velocity.length() > 10 && brakingCur == 1)
				brakeCurrent2 *= 0.5f; // abs (which i think works a bit well)

			// add the wheel force after merging the forces
			float totalLongForce = wheelTorque[w_id] - wheel_force.z
					- (brakeCurrent2 * carData.brakeMaxTorque * Math.signum(wheels[w_id].radSec));
			float totalLongForceTorque = tpf * totalLongForce / (carData.e_inertia()) * carData.wheelData[w_id].radius;
			if (brakingCur != 0
					&& Math.signum(wheels[w_id].radSec) != Math.signum(wheels[w_id].radSec + totalLongForceTorque))
				wheels[w_id].radSec = 0; // maxed out the forces with braking, so prevent wheels from moving
			else
				wheels[w_id].radSec += totalLongForceTorque; // so the radSec can be used next frame, to calculate slip ratio

			wheels[w_id].gripDir = wheel_force;
			applyWheelForce(w_angle.mult(wheel_force).mult(tpf), wheels[w_id]);

			planarGForce.addLocal(wheel_force);
		});

		planarGForce.multLocal(1 / carData.mass); // F=m*a => a=F/m
	}

	private void slipSimulationHacks(int w_id, float lastSlipAngle, float lastSlipRatio) {
		// Hack1: prevent 'losing' traction through a large integration step, by detecting jumps past the curve peak
		// this should only affect this class as its only affecting the force by affecting where on curve the current state is
		if (Math.abs(lastSlipAngle) < carData.wheelData[w_id].maxLat
				&& Math.abs(wheels[w_id].slipAngle) > carData.wheelData[w_id].maxLat) {
			wheels[w_id].slipAngle = carData.wheelData[w_id].maxLat * FastMath.sign(wheels[w_id].slipAngle);
		}
		if (Math.abs(lastSlipAngle) > carData.wheelData[w_id].maxLat
				&& Math.abs(wheels[w_id].slipAngle) < carData.wheelData[w_id].maxLat) {
			wheels[w_id].slipAngle = carData.wheelData[w_id].maxLat * FastMath.sign(lastSlipAngle);
		}
		if (brakingCur == 0) { // needs to be disabled during braking as it prevents you from stopping
			if (Math.abs(lastSlipRatio) < carData.wheelData[w_id].maxLong
					&& Math.abs(wheels[w_id].slipRatio) > carData.wheelData[w_id].maxLong) {
				wheels[w_id].slipRatio = carData.wheelData[w_id].maxLong * FastMath.sign(wheels[w_id].slipRatio);
			}
			if (Math.abs(lastSlipRatio) > carData.wheelData[w_id].maxLong
					&& Math.abs(wheels[w_id].slipRatio) < carData.wheelData[w_id].maxLong) {
				wheels[w_id].slipRatio = carData.wheelData[w_id].maxLong * FastMath.sign(lastSlipRatio);
			}
		}
		// Hack2: prevent flipping traction over 0 too fast, by always applying 0
		// inbetween
		if (Math.abs(lastSlipAngle) * carData.wheelData[w_id].maxLat < 0) { // will be negative if they have both signs
			wheels[w_id].slipAngle = 0;
		}
		if (Math.abs(lastSlipRatio) * carData.wheelData[w_id].maxLong < 0) { // will be negative if they have both signs
			wheels[w_id].slipRatio = 0;
		}
	}

	private void applyDrag(PhysicsSpace space, float tpf) {
		// rolling resistance (https://en.wikipedia.org/wiki/Rolling_resistance)
		Vector3f w_pos = rbc.getPhysicsLocation();
		Quaternion w_angle = rbc.getPhysicsRotation();
		Vector3f w_velocity = rbc.getLinearVelocity();

		Vector3f velocity = w_angle.inverse().mult(w_velocity);
		doForEachWheel((w_id) -> {
			if (!wheels[w_id].inContact) {
				wheels[w_id].rollingResistance = 0;
				return;
			}

			// apply rolling resistance in the negative direction
			Vector3f wheel_force = new Vector3f(0, 0, FastMath.sign(velocity.z) * -carData.rollingResistance(w_id, wheels[w_id].susForce));
			wheels[w_id].rollingResistance = wheel_force.z * tpf;
			rbc.applyImpulse(w_angle.mult(wheel_force).mult(tpf), wheels[w_id].curBasePosWorld.subtract(w_pos));
		});

		// quadratic drag (air resistance)
		dragDir = carData.quadraticDrag(w_velocity);
		float dragDown = -0.5f * carData.areo_downforce * 1.225f * (w_velocity.z * w_velocity.z); // formula for downforce from wikipedia
		rbc.applyCentralForce(dragDir.add(0, dragDown, 0)); // apply downforce after
	}

	/**Apply wheel for in the correct world space and back to the object you are touching */
	private void applyWheelForce(Vector3f force, RayWheel wheel) {
		rbc.applyImpulse(force, wheel.curBasePosWorld.subtract(rbc.getPhysicsLocation()));

		// only apply if its something that we can 'work' with
		if (wheel.collisionObject != null) {
			wheel.collisionObject.applyImpulse(force.negate(), wheel.curBasePosWorld.subtract(wheel.collisionObject.getPhysicsLocation()));
		}
	}

	/////////////////
	// control methods
	protected final void updateControlInputs(float steering, float braking, boolean handbrake) {
		this.steeringCur = steering;
		this.wheels[0].steering = steering;
		this.wheels[1].steering = steering;
		this.brakingCur = braking;
		this.handbrakeCur = handbrake;
	}

	protected final void setWheelTorque(int w_id, float torque) {
		wheelTorque[w_id] = torque;
	}

	public final float getWheelTorque(int w_id) {
		if (w_id < 0 || w_id > 4)
			return 0;
		return wheelTorque[w_id] + wheels[w_id].rollingResistance;
	}

	/////////////////
	// helper functions
	protected final void doForEachWheel(Consumer<Integer> func) {
		if (func == null)
			throw new NullPointerException("func is null");

		for (int i = 0; i < wheels.length; i++)
			func.accept(i);
	}

	private Vector3f vecLocalToWorld(Vector3f in) {
		Vector3f out = new Vector3f();
		return rbc.getPhysicsRotation().mult(out.set(in), out).add(rbc.getPhysicsLocation());
	}

	private Vector3f getVelocityInWorldPoint(PhysicsRigidBody control, Vector3f worldPos) {
		Vector3f relPos = worldPos.subtract(control.getPhysicsLocation());
		// http://www.letworyinteractive.com/blendercode/dd/d16/btRigidBody_8h_source.html#l00366
		Vector3f vel = control.getLinearVelocity();
		Vector3f ang = control.getAngularVelocity();
		return vel.add(ang.cross(relPos));
	}

	private boolean isFrontWheel(int w_id) {
		return w_id < 2;
	}
}

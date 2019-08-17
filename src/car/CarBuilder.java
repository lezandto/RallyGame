package car;

import java.util.Collection;
import java.util.HashMap;
import java.util.function.BiFunction;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.HullCollisionShape;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import car.CarModelData.CarPart;
import car.ai.CarAI;
import car.ai.DriveAtAI;
import car.data.Car;
import car.ray.CarDataConst;
import car.ray.RayCarControl;
import effects.LoadModelWrapper;
import game.App;
import helper.H;
import helper.Log;

public class CarBuilder extends AbstractAppState {

	private final HashMap<Integer, RayCarControl> cars;
	private final Node rootNode;
	private App app;

	public CarBuilder(App app) {
		this.app = app;
		cars = new HashMap<>();
		rootNode = new Node("Car Builder Root");
	}
	
	@Override
	public void initialize(AppStateManager stateManager, Application app) {
		super.initialize(stateManager, app);
		Log.p("CarBuilder init");
		if (this.app != app) {
			Log.e("!!!!!!! Car builder found a different application to run against.");
			System.exit(-6754);
		}

		this.app.getRootNode().attachChild(rootNode);
	}
	
	public void setEnabled(boolean state) {
		super.setEnabled(state);
		for (Integer i : cars.keySet()) {
			cars.get(i).setEnabled(state);
			cars.get(i).enableSound(state);
		}
	}
	
	public RayCarControl addCar(int id, Car car, Vector3f start, Matrix3f rot, boolean aPlayer, BiFunction<RayCarControl, RayCarControl, CarAI> ai) {
		if (this.app == null) {
			try {
				throw new Exception("App hasn't been initialised");
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		if (cars.containsKey(id)) {
			try {
				throw new Exception("A car already has that id: " + id);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		
		Vector3f grav = new Vector3f();
		this.app.getPhysicsSpace().getGravity(grav);
		CarDataConst carData = car.get(grav);
		
		AssetManager am = this.app.getAssetManager();
		
		Node carNode = new Node(id+"");
		Node carModel = LoadModelWrapper.create(am, carData.carModel, ColorRGBA.Magenta);
		
		//fetch the collision shape (if there is one in car model file)
		Spatial collisionShape = H.removeNamedSpatial(carModel, CarPart.Collision.getPartName());
		CollisionShape colShape = null;
		try {
			Mesh collisionMesh = ((Geometry)collisionShape).getMesh();
			colShape = new HullCollisionShape(collisionMesh);
		} catch (Exception e) {
			Log.e("!! car type " + carData.carModel + " is missing a collision shape.");
			e.printStackTrace();
			return null; //to make it clear this failed, and you need to fix it
		}

		//init car
		RayCarControl carControl = new RayCarControl(this.app, colShape, carData, carNode);
		
		carNode.attachChild(carModel);

		rootNode.attachChild(carNode);
		carControl.setPhysicsLocation(start);
		carControl.setPhysicsRotation(rot);

		if (aPlayer) { //players get the keyboard
			carControl.attachControls(app.getInputManager());
		} else {
			CarAI _ai;
			if (ai != null)
				_ai = ai.apply(carControl, get(0));
			else
				_ai = new DriveAtAI(carControl, get(0).getPhysicsObject());
			carControl.attachAI(_ai, true);
		}
		
		if (aPlayer) { //players get sound
			carControl.giveSound(new AudioNode(am, "assets/sound/engine.wav", AudioData.DataType.Buffer));
		}
		
		cars.put(id, carControl);
		
		return carControl;
	}

	public void setCarData(int id, CarDataConst carData) {
		RayCarControl car = this.get(0);
		if (car == null)
			return;
		
		car.setCarData(carData);
	}
	
	public void removeCar(int id) {
		if (!cars.containsKey(id)) {
			try {
				throw new Exception("A car doesn't have that id: " + id);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		rootNode.detachChildNamed(id+"");
		RayCarControl car = cars.get(id);
		car.cleanup(this.app);
		cars.remove(id);
	}
	
	public void removeCar(RayCarControl mpv) {
		for (int key: cars.keySet()) {
			RayCarControl car = cars.get(key);
			if (car == mpv) {
				rootNode.detachChildNamed(key+"");
				car.cleanup(this.app);
				cars.remove(key);
				return;
			}
		}
		
		try {
			throw new Exception("That car is not in my records, *shrug*.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void update(float tpf) {
		if (!isEnabled())
			return;
		
		for (RayCarControl rcc: cars.values()) {
			if (rcc.isEnabled())
				rcc.update(tpf);
		}
	}

	public RayCarControl get(int a) {
		if (cars.containsKey(a))
			return cars.get(a);
		return null;
	}
	public Collection<? extends RayCarControl> getAll() {
		return cars.values();
	}
	public int getCount() {
		return cars.size();
	}

	@Override
	public void cleanup() {
		super.cleanup();
		for (int key : cars.keySet()) {
			RayCarControl car = cars.get(key);
			car.cleanup(this.app);
		}
		this.app.getRootNode().detachChild(rootNode);
		Log.p("carbuilder cleanup");
		
		this.app = null;
	}
}

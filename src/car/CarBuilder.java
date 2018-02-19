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
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.material.Material;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import car.ai.CarAI;
import car.ai.DriveAtAI;
import game.App;
import game.Main;
import helper.H;

public class CarBuilder extends AbstractAppState {

	HashMap<Integer, MyVC> cars;
	Node rootNode;

	public CarBuilder() {
		cars = new HashMap<>();
		rootNode = new Node("Car Builder Root");
		
		App.rally.getRootNode().attachChild(rootNode);
	}
	
	public void initialize(AppStateManager stateManager, Application app) {
		super.initialize(stateManager, app);
		H.p("carbuilder init");
	}
	
	public void setEnabled(boolean state) {
		super.setEnabled(state);
		for (Integer i : cars.keySet()) {
			cars.get(i).enableSound(state);
			
			for (MyWheelNode w: cars.get(i).wheel) {
				w.setEnabled(state);
			}
		}
	}
	
	//TODO this should be giving the ai
	public MyVC addCar(int id, CarData car, Vector3f start, Matrix3f rot, boolean aPlayer, BiFunction<MyPhysicsVehicle, MyPhysicsVehicle, CarAI> ai) {
		if (cars.containsKey(id)) {
			try {
				throw new Exception("A car already has that Id");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		//clone CarData so if its edited, only this one changes not the global one
		car = car.cloneWithSerialization();
		
		Main r = App.rally;
		AssetManager am = r.getAssetManager();
		
		Spatial carmodel = am.loadModel(car.carModel);
		if (carmodel instanceof Geometry) {

		} else {
			carmodel = (Node) am.loadModel(car.carModel);

			//TODO car reflection
			for (Geometry g: H.getGeomList((Node)carmodel)) {  
				Material m = g.getMaterial();
				if (!m.getMaterialDef().getName().equals("Unshaded")) { //this material type not correct for these settings
					m.setBoolean("UseMaterialColors", true);
					m.setVector3("FresnelParams", new Vector3f(0.05f, 0.18f, 0.11f));
				}
				g.setMaterial(m);
			}
		}

		//its possible to shift the center of gravity offset (TODO add to CarData)
		//Convex collision shape or hull might be faster here)
		CompoundCollisionShape compoundShape = new CompoundCollisionShape();
		compoundShape.addChildShape(CollisionShapeFactory.createDynamicMeshShape(carmodel), new Vector3f(0,0,0));
		
		Node carNode = new Node(id+"");
		MyVC player = new MyVC(compoundShape, car, carNode);
		
		carNode.addControl(player);
		carNode.attachChild(carmodel);

		if (aPlayer) { //player gets a shadow
			carNode.setShadowMode(ShadowMode.CastAndReceive);
		} else {
			carNode.setShadowMode(ShadowMode.Receive);
		}

		rootNode.attachChild(carNode);
		player.setPhysicsLocation(start);
		player.setPhysicsRotation(rot);

		if (aPlayer) { //players get the keyboard
			player.attachControls();
		} else {
			CarAI _ai;
			if (ai != null)
				_ai = ai.apply(player, get(0));
			else
				_ai = new DriveAtAI(player, get(0));
			player.attachAI(_ai);
		}
		
		if (aPlayer) {
			player.giveSound(new AudioNode(am, "assets/sound/engine.wav", AudioData.DataType.Buffer));
		}
		
		cars.put(id, player);

		App.rally.getPhysicsSpace().addTickListener(player);
		App.rally.getPhysicsSpace().add(player);
		return player;
	}

	public void removePlayer(int id) {
		if (!cars.containsKey(id)) {
			try {
				throw new Exception("A car doesn't have that Id");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		rootNode.detachChildNamed(id+"");
		MyVC car = cars.get(id);
		
		App.rally.getPhysicsSpace().removeTickListener(car);
		App.rally.getPhysicsSpace().remove(car);
		car.cleanup();
		cars.remove(id);
	}
	public void removePlayer(MyPhysicsVehicle mpv) {
		for (int key: cars.keySet()) {
			MyPhysicsVehicle car = cars.get(key);
			if (car == mpv) {
				rootNode.detachChildNamed(key+"");
				App.rally.getPhysicsSpace().remove(car);
				car.cleanup();
				cars.remove(key);
				return;
			}
		}
		
		try {
			throw new Exception("That car is not in my records");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void update(float tpf) {
		if (!isEnabled())
			return;
		
		if (!cars.isEmpty()) {
			for (Integer i : cars.keySet()) {
				cars.get(i).setEnabled(isEnabled());
				
				for (MyWheelNode w: cars.get(i).wheel)
					w.update(tpf); //TODO this kind of reach through feels like a hack
			}
		}
	}

	public MyPhysicsVehicle get(int a) {
		if (cars.containsKey(a))
			return cars.get(a);
		return null;
	}
	public Collection<? extends MyPhysicsVehicle> getAll() {
		return cars.values();
	}
	public int getCount() {
		return cars.size();
	}

	public void cleanup() {
		for (int key : cars.keySet()) {
			MyVC car = cars.get(key);
			App.rally.getPhysicsSpace().remove(car);
			car.cleanup();
		}
		App.rally.getRootNode().detachChild(rootNode);
		H.p("carbuilder cleanup");
	}
}

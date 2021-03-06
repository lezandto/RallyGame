package rallygame.game;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.bullet.BulletAppState;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import com.simsilica.lemur.Button;
import com.simsilica.lemur.Command;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Label;

import rallygame.car.CarBuilder;
import rallygame.car.data.Car;
import rallygame.car.ray.RayCarControl;
import rallygame.car.ui.CarStatsUI;
import rallygame.car.ui.PowerCurveGraph;
import rallygame.helper.Log;
import rallygame.service.Screen;
import rallygame.service.Screen.HorizontalPos;
import rallygame.service.Screen.VerticalPos;
import rallygame.world.StaticWorld;
import rallygame.world.StaticWorldBuilder;
import rallygame.world.IWorld;

public class ChooseCar extends BaseAppState {

	private final IChooseStuff choose;

	private IWorld world;
	private StaticWorld worldType;

	private CarBuilder cb;
	private Car car;
	private Container infoWindow;
	
	private static final float RESET_IMPULSE = 1/60f;
	private float posReset;
	
	private BasicCamera camera;
	
	private PowerCurveGraph graph;

	public ChooseCar(IChooseStuff choose) {
		this.choose = choose;
		worldType = StaticWorld.garage2;
		car = Car.values()[0];
	}

	@SuppressWarnings("unchecked")
	@Override
	public void initialize(Application app) {
		getState(BulletAppState.class).setEnabled(true);
		
		//init world
		world = new StaticWorldBuilder(worldType);
		getStateManager().attach(world);
        
        //init player
		cb = getState(CarBuilder.class);
		cb.addCar(car, worldType.start, new Quaternion(), true);

		//init camera
		camera = new BasicCamera("Camera", app.getCamera(), new Vector3f(0,3,7), new Vector3f(0,1.2f, 0));
		getStateManager().attach(camera);
		
		//init guis
        Screen screen = new Screen(app.getContext().getSettings());

		//info window first so the event listeners can delete it
        infoWindow = new CarStatsUI(app.getAssetManager(), this.cb.get(0).getCarData());
        screen.topLeftMe(infoWindow);
		((SimpleApplication) app).getGuiNode().attachChild(infoWindow);

		Container myWindow = new Container();
		((SimpleApplication)app).getGuiNode().attachChild(myWindow);
		myWindow.setLocalTranslation(300, 300, 0);
		myWindow.addChild(new Label("Choose Car"), 0, 0);
		int i = 0;
        for (Car c: Car.values()) {
        	Button carB = myWindow.addChild(new Button(c.name()), 1, i);
        	carB.addClickCommands(new Command<Button>() {
                @Override
                public void execute( Button source ) {
                    car = c;

                    cb.removeCar(cb.get(0));
    				cb.addCar(car, worldType.start, worldType.rot, true);
                    
                    Screen screen = new Screen(app.getContext().getSettings());

					((SimpleApplication) app).getGuiNode().detachChild(infoWindow);
					infoWindow = new CarStatsUI(app.getAssetManager(), cb.get(0).getCarData());
                    screen.topLeftMe(infoWindow);
					((SimpleApplication) app).getGuiNode().attachChild(infoWindow);

    				graph.updateMyPhysicsVehicle(cb.get(0).getCarData());
                }
            });
        	i++;
        }
        
        Button select = myWindow.addChild(new Button("Choose"));
        select.addClickCommands(new Command<Button>() {
            @Override
            public void execute( Button source ) {
            	chooseCar();
            	((App)app).getGuiNode().detachChild(myWindow);
            	((App)app).getGuiNode().detachChild(infoWindow);
            }
        });
        
        Vector3f size = new Vector3f(400,400,0);
        graph = new PowerCurveGraph(app.getAssetManager(), this.cb.get(0).getCarData(), size);
		graph.setLocalTranslation(screen.get(HorizontalPos.Right, VerticalPos.Bottom, size));
		((SimpleApplication)app).getGuiNode().attachChild(graph);
	}


	public void update(float tpf) {
		super.update(tpf);

		// keep the car still enough, and also prevent high frame rates from breaking it
		posReset += tpf;
		if (posReset > RESET_IMPULSE) {
			posReset = 0;

			RayCarControl car = cb.get(0);
            Vector3f pos = car.location;
            car.setPhysicsProperties(new Vector3f(0, pos.y, 0), null, (Quaternion) null, null);
		}
	}

	@Override
	public void cleanup(Application app) {
		getStateManager().detach(world);
		world = null;
		
		getStateManager().detach(camera);
		camera = null;
		
		graph.removeFromParent();
		graph = null;

		cb.removeAll();
		cb = null;
	}

	@Override
	protected void onEnable() {
		this.cb.setEnabled(true);
	}

	@Override
	protected void onDisable() {
		this.cb.setEnabled(false);
	}


	/////////////////////////////
	//UI stuff
	public void chooseCar() {
		if (car == null) { Log.p("no return value for ChooseCar()"); };
		choose.chooseCar(car);
	}
}

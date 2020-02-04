package service.checkpoint;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.GhostControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

import car.ray.RayCarControl;
import effects.LoadModelWrapper;
import helper.Duo;
import service.GhostObjectCollisionListener;

public class CheckpointProgress extends BaseAppState implements GhostObjectCollisionListener.IListener {

    private final Vector3f[] checkpointPositions;
    private final RayCarControl player;
    private final GhostObjectCollisionListener checkpointCollisionListener;

    private Checkpoint firstCheckpoint;
    private final Checkpoint[] checkpoints;
    private final Node rootNode;
    private final Map<RayCarControl, RacerState> racers;
    private final Map<Integer, Instant> timeAtCheckpoints;

    private Spatial baseSpat;
    private float checkpointScale;
    private ColorRGBA checkpointColour;
    private boolean attachModels;

    public CheckpointProgress(Vector3f[] checkpoints, Collection<RayCarControl> cars, RayCarControl player) {
        this.checkpointPositions = checkpoints;
        this.checkpoints = new Checkpoint[checkpoints.length];
        
        this.checkpointScale = 2;
        this.checkpointColour = new ColorRGBA(0, 1, 0, 0.4f);
        this.attachModels = true;

        this.rootNode = new Node("progress root node");

        this.racers = new HashMap<>();
        for (RayCarControl car : cars) {
            this.racers.put(car, new RacerState(car));
        }
        this.timeAtCheckpoints = new HashMap<>();

        this.checkpointCollisionListener = new GhostObjectCollisionListener(this);

        this.player = player;
    }

    public void setBoxCheckpointSize(float size) {
        if (this.isInitialized())
            throw new IllegalStateException("This must be called before initialization.");
        this.checkpointScale = size;
    }

    public void setBoxCheckpointColour(ColorRGBA colour) {
        if (this.isInitialized())
            throw new IllegalStateException("This must be called before initialization.");
        this.checkpointColour = colour;
    }

    public void attachVisualModel(boolean attach) {
        if (this.isInitialized())
            throw new IllegalStateException("This must be called before initialization.");
        this.attachModels = attach;
    }

    public void setCheckpointModel(Spatial spat) {
        if (this.isInitialized())
            throw new IllegalStateException("This must be called before initialization.");
        this.baseSpat = spat;
        //TODO figure out checkpoint rotating
    }

    @Override
    protected void initialize(Application app) {
        ((SimpleApplication) app).getRootNode().attachChild(rootNode);

        PhysicsSpace physicsSpace = getState(BulletAppState.class).getPhysicsSpace();

        // generate the checkpoint objects
        if (baseSpat == null) {
            Vector3f checkpointSize = Vector3f.UNIT_XYZ.mult(checkpointScale);
            baseSpat = new Geometry("checkpoint", new Box(checkpointSize.negate(), checkpointSize));
            baseSpat = LoadModelWrapper.create(app.getAssetManager(), baseSpat, checkpointColour);
        }
        CollisionShape colShape = CollisionShapeFactory.createBoxShape(baseSpat);

        for (int i = 0; i < checkpointPositions.length; i++) {
            GhostControl ghost = new GhostControl(colShape);

            Spatial box = baseSpat.clone();
            box.setLocalTranslation(checkpointPositions[i]);
            box.addControl(ghost);
            if (attachModels)
                rootNode.attachChild(box);
            physicsSpace.add(ghost);

            this.checkpoints[i] = new Checkpoint(i, checkpointPositions[i], ghost);
        }
        this.firstCheckpoint = this.checkpoints[0];

        //set progress values
        for (RacerState racer : this.racers.values()) {
            racer.lastCheckpoint = this.firstCheckpoint;
            racer.nextCheckpoint = this.firstCheckpoint;
        }

        physicsSpace.addCollisionListener(checkpointCollisionListener);
    }

    public RayCarControl isThereAWinner(int laps, int checkpoints) {
        List<RacerState> racers = getRaceState();
        Collections.sort(racers);

        RacerState racer = racers.get(0);
        if (racer.lap >= laps && racer.lastCheckpoint != null && racer.lastCheckpoint.num >= checkpoints)
            return racer.car;

        return null;
    }

    @Override
    protected void onDisable() {
    }

    @Override
    protected void onEnable() {
    }

    public RacerState getPlayerRacerState() {
        return this.racers.get(player);
    }

    protected List<RacerState> getRaceState() {
        return new ArrayList<>(this.racers.values());
    }

    @Override
    public void update(float tpf) {
        // this is intentionally blank
    }

    @Override
    public void cleanup(Application app) {
        ((SimpleApplication) app).getRootNode().detachChild(rootNode);

        PhysicsSpace physicsSpace = app.getStateManager().getState(BulletAppState.class).getPhysicsSpace();
        for (Checkpoint checkpoint : checkpoints) {
            physicsSpace.remove(checkpoint.ghost);
        }
        physicsSpace.removeCollisionListener(checkpointCollisionListener);
    }

    public Vector3f getNextCheckpoint(RayCarControl car) {
        Checkpoint check = racers.get(car).nextCheckpoint;
        if (check == null)
            return null;
        return check.position;
    }

    public Vector3f getLastCheckpoint(RayCarControl car) {
        Checkpoint check = racers.get(car).lastCheckpoint;
        if (check == null)
            return null;
        return check.position;
    }

    @Override
    public void ghostCollision(GhostControl ghost, RigidBodyControl obj) {
        Checkpoint checkpoint = getIfCheckpoint(ghost);
        RacerState racer = getIfCar(obj);
        if (checkpoint == null || racer == null)
            return;

        if (racer.nextCheckpoint == checkpoint) {
            // update checkpoints
            Duo<Integer, Integer> nextCheckpoint = calcNextCheckpoint(racer, checkpoints.length);
            racer.lastCheckpoint = racer.nextCheckpoint;
            racer.nextCheckpoint = checkpoints[nextCheckpoint.second];
            racer.lap = nextCheckpoint.first;

            // update last time
            int fakeCheckpointHash = racer.lap * 10000 + checkpoint.num;
            if (!timeAtCheckpoints.containsKey(fakeCheckpointHash)) {
                timeAtCheckpoints.put(fakeCheckpointHash, Instant.now());
                racer.duration = Duration.ZERO;
            } else {
                racer.duration = Duration.between(timeAtCheckpoints.get(fakeCheckpointHash), Instant.now());
            }
        }
    }

    private RacerState getIfCar(RigidBodyControl pObject) {
        for (Entry<RayCarControl, RacerState> racer : racers.entrySet())
            if (pObject == racer.getKey().getPhysicsObject())
                return racer.getValue();
        return null;
    }

    private Checkpoint getIfCheckpoint(GhostControl ghost) {
        for (Checkpoint checkpoint : checkpoints)
            if (checkpoint.ghost == ghost)
                return checkpoint;
        return null;
    }

    /** Lap,checkpoint */
    public static Duo<Integer, Integer> calcNextCheckpoint(RacerState racer, int checkpointCount) {
        int nextNum = racer.nextCheckpoint.num + 1;
        int lap = racer.lap;
        if (nextNum >= checkpointCount) {
            nextNum = 0;
            lap++;
        }
        return new Duo<>(lap, nextNum);
    }
}

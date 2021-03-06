package rallygame.car.ui;

import java.util.LinkedList;
import java.util.List;

import com.jme3.asset.AssetManager;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.DefaultRangedValueModel;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.ProgressBar;
import com.simsilica.lemur.RollupPanel;
import com.simsilica.lemur.component.QuadBackgroundComponent;

import rallygame.car.data.CarDataConst;
import rallygame.car.ray.GripHelper;
import rallygame.helper.Colours;
import rallygame.helper.H;

public class CarStatsUI extends Container {

    private final CarStats stats;

    public CarStatsUI(AssetManager am, CarDataConst carData) {

        stats = new CarStats(carData);
        addChild(new Label(carData.name));

        NormalisedStats nStats = stats.getNormalisedStats();
        addChild(new Label("Acceleration " + H.roundDecimal(nStats.getAccel(), 3)));
        addChild(createBar(nStats.getAccel()));
        addChild(new Label("Top Speed " + H.roundDecimal(nStats.getTopSpeed(), 3)));
        addChild(createBar(nStats.getTopSpeed()));
        addChild(new Label("Handling " + H.roundDecimal(nStats.getHandling(), 3)));
        addChild(createBar(nStats.getHandling()));
        addChild(new Label("Braking " + H.roundDecimal(nStats.getBraking(), 3)));
        addChild(createBar(nStats.getBraking()));

        Container realStats = new Container();
        for (Stat stat: stats.getStats()) {
            realStats.addChild(new Label(stat.label+": " + stat.value));
        }
        addChild(new RollupPanel("Real stats", realStats, null)).setOpen(false);
    }

    private ProgressBar createBar(float value) {
        ProgressBar pb = new ProgressBar();
        pb.setProgressPercent(value);
        pb.setModel(new DefaultRangedValueModel(0, 1, value));
        pb.getValueIndicator().setBackground(new QuadBackgroundComponent(Colours.getOnRGBScale(1 - value)));
        return pb;
    }
}

class CarStats {

    private final List<Stat> stats;
    private final NormalisedStats normalisedStats;

    public CarStats(CarDataConst data) {
        stats = new LinkedList<>();

        rallygame.helper.Duo<Float, Float> power = data.getMaxPower();
        stats.add(new Stat("Power", power.first + "kW? @"+power.second+ "rpm"));
        stats.add(new Stat("Mass", data.mass));
        stats.add(new Stat("Drive", data.driveFront ? data.driveRear ? "AWD" : "Front" : data.driveRear ? "Rear" : "Unknown"));
        stats.add(new Stat("Max Rpm", data.e_redline));
        stats.add(new Stat("Braking", data.brakeMaxTorque/data.mass));
        stats.add(new Stat("Drag: ", data.areo_drag + "(" + data.areo_lineardrag + ")"));
        stats.add(new Stat("Nitro", data.nitro_on ? data.nitro_force : 0));
        stats.add(new Stat("Downforce", data.areo_downforce));
        float bestLatForce = GripHelper.calcMaxLoad(data.wheelData[0].pjk_lat);
        stats.add(new Stat("Handling", data.mass * 10 / bestLatForce));

        normalisedStats = new NormalisedStats(data);
    }
    
    public List<Stat> getStats() {
        return stats;
    }

    public NormalisedStats getNormalisedStats() {
        return normalisedStats;
    }
}

class Stat {
    public final String label;
    public final String value;

    public Stat(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public Stat(String label, float value) {
        this.label = label;
        this.value = Float.toString(value);
    }
}

class NormalisedStats {

    private float accel;
    private float topSpeed;
    private float handling;
    private float braking;
    
    //idea for top speed: calc max speed for gearing and compare drag
    public NormalisedStats(CarDataConst data) {
        accel = skewLog(data.getMaxPower().first / data.mass, -1, 0.75f);
        topSpeed = skewLog(data.getMaxPower().first / Math.abs(data.quadraticDrag(new Vector3f(27, 0, 0)).x), -2, 10);
        handling = skewLog(data.mass / GripHelper.calcMaxLoad(data.wheelData[0].pjk_lat), -1, 1);
        braking = skewLog(Math.min(data.brakeMaxTorque, GripHelper.calcMaxLoad(data.wheelData[0].pjk_long)) / data.mass, 0, 10);
    }

    // attempting to scale all values onto 0-1 so that we can display them like any game
    private static float skewLog(float value, float preMin, float preMax) {
        return skewLog(value, preMin, preMax, 0, 1);
    }

    private static float skewLog(float value, float preMin, float preMax, float min, float max) {
        float mx = FastMath.log(value - preMin) / FastMath.log(preMax - preMin);
        return mx * (max - min) + min;
    }

    public float getAccel() {
        return accel;
    }
    public float getBraking() {
        return braking;
    }
    public float getHandling() {
        return handling;
    }
    public float getTopSpeed() {
        return topSpeed;
    }

    @Override
    public String toString() {
        return "A: " + accel + " S:" + topSpeed + " H:" + handling + " B:" + braking;
    }
}

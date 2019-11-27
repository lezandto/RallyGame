package duel;

import com.jme3.asset.AssetManager;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Label;

import car.CarStatsUI;
import car.data.Car;

public class DuelCarStatsUI extends Container {

    public DuelCarStatsUI(AssetManager am, Car car1, Car car2) {
        addChild(new CarStatsUI(am, car1), 0);
        addChild(new Label("Vs"), 1);
        addChild(new CarStatsUI(am, car2), 2);
    }
}

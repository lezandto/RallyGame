package drive.race;

import java.util.Collections;
import java.util.List;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Label;

import helper.Screen;

public class DriveRaceUI extends BaseAppState {

    private final DriveRaceProgress progress;

    private Container main;
    private RacerStateTableView progressTable;
    private Screen screen;

    private Container basicPanel;
    private Label basicLabel;

    public DriveRaceUI(DriveRaceProgress progress) {
        this.progress = progress;
    }
    
    @Override
    protected void initialize(Application app) {
        screen = new Screen(app.getContext().getSettings());

        basicPanel = new Container();
        basicLabel = new Label("Race state?");
        basicPanel.attachChild(basicLabel);
        basicPanel.setLocalTranslation(screen.topLeft().add(0, -25, 0));
        ((SimpleApplication) app).getGuiNode().attachChild(basicPanel);

        this.main = new Container();
        main.setLocalTranslation(screen.topLeft().add(0, -100, 0));
        ((SimpleApplication) app).getGuiNode().attachChild(main);

        progressTable = new RacerStateTableView(progress.getRaceState());
        this.main.addChild(progressTable);
    }

    @Override
    protected void cleanup(Application app) {
        ((SimpleApplication) app).getGuiNode().detachChild(basicPanel);
        ((SimpleApplication) app).getGuiNode().detachChild(main);
    }

    @Override
    protected void onEnable() {}
    @Override
    protected void onDisable() {}

    public void setBasicText(String text) {
        if (basicLabel != null)
            basicLabel.setText(text);
    }
    
    @Override
    public void update(float tpf) {
        super.update(tpf);

        List<RacerState> racers = progress.getRaceState();

        Collections.sort(racers);

        RacerState st = progress.getPlayerRacerState();
        progressTable.update(racers, st);

        screen.topRightMe(main);
    }
}

class RacerStateTableView extends Container {

    private final static RacerState HEADER = new RacerState("Player");

    private final int rowLength;
    
    public RacerStateTableView(List<RacerState> initialState) {
        int count = 0;
        String[] values = convertToValueRows(HEADER, false);
        this.addChild(new Label(""), count, 0);
        for (int i = 0; i < values.length; i++) {
            this.addChild(new Label(values[i]), count, i+1);
        }
        count++;
        rowLength = values.length;

        for (RacerState state: initialState) {
            values = convertToValueRows(state, false);
            
            this.addChild(new Label(count+""), count, 0);
            for (int i = 0; i < values.length; i++) {
                this.addChild(new Label(values[i]), count, i+1);
            }
            count++;
        }
    }

    public void update(List<RacerState> states, RacerState playerState) {
        int count = 1;
        for (RacerState racer: states) {
            String[] values = convertToValueRows(racer, racer == playerState); 
            for (int i = 0; i < values.length; i++) {
                //this is kind of a hack to prevent recreating labels
                ((Label)this.getChild((rowLength+1)*count + i+1)).setText(values[i]);
            }
            count++;
        }
    }

    private String[] convertToValueRows(RacerState state, boolean isPlayer) {
        if (state == HEADER) {
            return new String[] {
                    state.name,
                    "lap",
                    ""
                };
        }

        return new String[] {
                state.name,
                state.lap + "",
                isPlayer ? "-" : ""
            };
    }
}
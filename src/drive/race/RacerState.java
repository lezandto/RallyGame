package drive.race;

import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;

import drive.race.RacerState;

class RacerState implements Comparable<RacerState> {
    public final String name;
    public int lap;
    public Checkpoint nextCheckpoint;
    public float distanceToNextCheckpoint;
    public Geometry arrow;

    public RacerState(String name) {
        this.name = name;
    }

    public void calcCheckpointDistance(Vector3f pos) {
        this.distanceToNextCheckpoint = pos.subtract(nextCheckpoint.position).length();
    }

    @Override
    public int compareTo(RacerState o) {
        if (this.lap != o.lap)
            return o.lap - this.lap;
        if (this.nextCheckpoint.num != o.nextCheckpoint.num)
            return o.nextCheckpoint.num - this.nextCheckpoint.num;

        // note this is backwards because closer is better
        return (int) ((this.distanceToNextCheckpoint - o.distanceToNextCheckpoint) * 1000);
    }

    public float calcLapProgress(int checkpointCount) {
        return nextCheckpoint.num/((float)checkpointCount);
    }
}
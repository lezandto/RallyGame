package rallygame.world;

import com.jme3.math.Transform;
import com.jme3.math.Vector3f;

public interface ICheckpointWorld extends IWorld {
    //For starting positions
    Transform start(int i);
    //path along the route
    Vector3f[] checkpoints();
}

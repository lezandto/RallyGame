package rallygame.world.lsystem;

import java.util.function.BiFunction;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;

import rallygame.effects.LoadModelWrapper;
import rallygame.helper.Geo;

public class Roads {

	private static Quaternion rot90 = new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y);
	
	//TODO use actual curves
	private AssetManager am;
	public Roads(AssetManager am) {
		this.am = am;
		//TODO use the input function
	}

	public CurveQueueObj generateFirst(TerrainQuad terrain) {
		Curve curve = new BeizerCurve(new Vector3f[] {
				new Vector3f(0, terrain.getHeight(new Vector2f(0,0)), 0),
				new Vector3f(0, terrain.getHeight(new Vector2f(0,10)), 10),
				new Vector3f(0, terrain.getHeight(new Vector2f(0,40)), 40),
				new Vector3f(0, terrain.getHeight(new Vector2f(0,50)), 50)
			}, CurveFunction());
		return new CurveQueueObj(FastMath.nextRandomFloat()*0.2f, curve, "+");
	}
	
	public static BiFunction<Vector3f, Vector3f, BSegment> CurveFunction() { 
		return (Vector3f off, Vector3f tang) -> {
	
			Vector3f angleOff = new Vector3f(0, -1, 0);
			Vector3f normal = rot90.mult(tang.normalize());
			return new BSegment(new Vector3f[] { 
					off, //center of the road, not used for collision just position
					off.add(normal.mult(6f)).add(angleOff),
					off.add(normal.mult(5.5f)),
					off.add(normal.mult(-5.5f)),
					off.add(normal.mult(-6f)).add(angleOff),
			});
		};
	}
	
	public void placePiece(CurveQueueObj cqo, Node rootNode, PhysicsSpace phys, boolean helpPoints) {
		BSegment[] nodes = cqo.curve.calcPoints();
		
		Vector3f[] curveNodes = cqo.curve.getNodes();
		
		if (cqo.rule != null && cqo.rule.equals("+")) {
			Vector3f a = curveNodes[0];
			Vector3f dir = curveNodes[3].subtract(a).normalize();
			drawMeAnArrow(am, rootNode, a.add(0,2,0), dir);
			dir.y = 0;
			dir.normalizeLocal();
			Vector3f left = rot90.mult(dir);
			
			//somehow create an intersection starting at 'a' in the direction 'dir' 
			//for example:
			//lt---rt
			// |   |
			//lb-a-rb
			
			Vector3f lb = a.add(left.mult(10));
			Vector3f lt = lb.add(dir.mult(20));
			
			Vector3f rb = a.add(left.mult(-10));
			Vector3f rt = rb.add(dir.mult(20));
			
			drawMeAQuad(am, rootNode, phys, new Vector3f[]{lb,rb,lt,rt}, ColorRGBA.Cyan);
			return;
		}
		
		Vector3f a = curveNodes[0];
		Vector3f dir = curveNodes[curveNodes.length-1].subtract(a);
		dir.y = 0; //no height please
		dir.normalizeLocal();
		drawMeAnArrow(am, rootNode, a.add(0,2,0), dir);
		
		for (int i = 1; i < nodes.length; i++) {
			for (int j = 2; j < nodes[i].v.length; j++) { //avoid the first one
				Vector3f[] vs = new Vector3f[] {
					nodes[i-1].v[j-1],
					nodes[i-1].v[j],
					nodes[i].v[j-1],
					nodes[i].v[j],
				};
				
				drawMeAQuad(am, rootNode, phys, vs, null);
			}
		}
		
		if (helpPoints) {
			Vector3f[] nds = cqo.curve.getNodes();
			for (Vector3f v: nds)
				drawMeAVerySmallBox(am, rootNode, v, ColorRGBA.LightGray);
//			drawMeALine(nds[0], nds[1], ColorRGBA.Blue);
//			drawMeALine(nds[2], nds[3], ColorRGBA.Blue);
		}
	}
	
	public CurveQueueObj[] genNextNodes(CurveQueueObj cqo, TerrainQuad terrain) {
		//TODO thinking some kind of 'frame % N' thing like the gridwars enemy spawning
		
		Vector3f[] curveNodes = cqo.curve.getNodes();
		
		if (cqo.rule.equals("A")) {
			Vector3f a = curveNodes[3];
			Vector3f dir = a.subtract(curveNodes[0]);
			dir.y = 0; //no height please
			dir.normalizeLocal();
			Vector3f b = a.add(dir.mult(50));
			b.y = terrain.getHeight(new Vector2f(b.x,b.z));
			Curve curve = new BeizerCurve(new Vector3f[] { a, a.add(dir.mult(10)), b.subtract(dir.mult(10)), b }, CurveFunction());
			
			int val = FastMath.nextRandomInt(0, 1);
			String rule = null;
			if (val == 0)
				rule = "+"; //one in 3
			else
				rule = "A";
			
			return new CurveQueueObj[] { 
					new CurveQueueObj(cqo.time + FastMath.nextRandomFloat()*0.2f + cqo.time, curve, rule)
				};
		} else if (cqo.rule.equals("+")) {
			Vector3f a = curveNodes[0];
			Vector3f dir = curveNodes[3].subtract(a);
			dir.y = 0; //no height please
			dir.normalizeLocal();
			Vector3f left = rot90.mult(dir);
			
			//     t2
			//     |
			//   +-t-+
			//l2-l   r-r2
			//   +-a-+
			Vector3f l = a.add(left.mult(10)).add(dir.mult(10));
			Vector3f l2 = l.add(left.mult(50+20));
			l2.y = terrain.getHeight(new Vector2f(l2.x,l2.z));
			Curve curveL = new BeizerCurve(new Vector3f[] { l, l.add(left.mult(10)), l2.subtract(left.mult(10)), l2 }, CurveFunction());
			
			Vector3f r = a.add(left.mult(-10)).add(dir.mult(10));
			Vector3f r2 = r.add(left.mult(-50-10));
			r2.y = terrain.getHeight(new Vector2f(r2.x,r2.z));
			Curve curveR = new BeizerCurve(new Vector3f[] { r, r.add(left.mult(-10)), r2.subtract(left.mult(-10)), r2 }, CurveFunction());
			
			Vector3f t = a.add(dir.mult(20));
			Vector3f t2 = t.add(dir.mult(50+20));
			t2.y = terrain.getHeight(new Vector2f(t2.x, t2.z));
			Curve curveT = new BeizerCurve(new Vector3f[] { t, t.add(dir.mult(10)), t2.subtract(dir.mult(10)), t2 }, CurveFunction());
			
			return new CurveQueueObj[] { 
					new CurveQueueObj(cqo.time + FastMath.nextRandomFloat()*0.2f + cqo.time, curveL, "A"),
					new CurveQueueObj(cqo.time + FastMath.nextRandomFloat()*0.2f + cqo.time, curveR, "A"),
					new CurveQueueObj(cqo.time + FastMath.nextRandomFloat()*0.2f + cqo.time, curveT, "A")
				};
		}
		
		return new CurveQueueObj[0];
	}
	
	private void drawMeAnArrow(AssetManager am, Node rootNode, Vector3f start, Vector3f dir) {  
		Geometry geometry = Geo.makeShapeArrow(am, ColorRGBA.Green, dir, start);
		rootNode.attachChild(geometry);
	}
	
	private void drawMeAVerySmallBox(AssetManager am, Node rootNode, Vector3f pos, ColorRGBA colour) {
		Geometry geometry =	Geo.makeShapeBox(am, colour, pos, 0.1f);
		rootNode.attachChild(geometry);
	}
	
	private void drawMeAQuad(AssetManager am, Node rootNode, PhysicsSpace phys, Vector3f[] v, ColorRGBA colour) {
		Mesh mesh = Geo.createQuad(v);
		Geometry geo = new Geometry("Quad", mesh);
		
		Spatial s = LoadModelWrapper.create(am, geo, colour != null ? colour : ColorRGBA.White);
		
		CollisionShape col = CollisionShapeFactory.createMeshShape(s);
		RigidBodyControl c = new RigidBodyControl(col, 0);
		
		rootNode.attachChild(s);
		phys.add(c);
	}
}

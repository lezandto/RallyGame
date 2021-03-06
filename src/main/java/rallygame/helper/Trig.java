package rallygame.helper;

import com.jme3.math.FastMath;
import com.jme3.math.LineSegment;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

public class Trig {
    
    public static float dotXZ(Vector3f a, Vector3f b) {
        return a.x * b.x + a.z * b.z;
    }

    /**Get distance from an inf line. If you want a segment then try distFromSegment */
    public static float distFromLineXZ(Vector3f start, Vector3f end, Vector3f point) {
        return distFromLine(H.v3tov2fXZ(start), H.v3tov2fXZ(end), H.v3tov2fXZ(point));
    }
    /** Get distance from an inf line. If you want a segment then try distFromSegment */
    public static float distFromLine(Vector2f start, Vector2f end, Vector2f point) {
        float x0 = point.x;
        float y0 = point.y;
        float x1 = start.x;
        float y1 = start.y;
        float x2 = end.x;
        float y2 = end.y;
        return (Math.abs((y2 - y1) * x0 - (x2 - x1) * y0 + x2 * y1 - y2 * x1))
                / FastMath.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
    }

    /** Get distance from Ray with start, direction and inf length */
    public static float distFromRay(Vector2f start, Vector2f dir, Vector2f point) {
        return distFromRay(H.v2tov3fXZ(start), H.v2tov3fXZ(dir), H.v2tov3fXZ(point));
    }
    /** Get distance from Ray with start, direction and inf length */
    public static float distFromRay(Vector3f start, Vector3f dir, Vector3f point) {
        Ray r = new Ray(start, dir.normalize());
        return FastMath.sqrt(r.distanceSquared(point));
    }

    /**Get distance from a line segment. If you want an inf line then try distFromLine */
    public static float distFromSegment(Vector2f start, Vector2f end, Vector2f point) {
        return distFromSegment(H.v2tov3fXZ(start), H.v2tov3fXZ(end), H.v2tov3fXZ(point));
    }
    /**Get distance from a line segment. If you want an inf line then try distFromLine */
    public static float distFromSegment(Vector3f start, Vector3f end, Vector3f point) {
        return new LineSegment(start, end).distance(point);
    }

    /** Returns extents of V3f in xz directions. [xmin, zmin, xmax, zmax] */
    public static float[] boundingBoxXZ(Vector3f... p) {
        float xmin = Float.POSITIVE_INFINITY;
        float xmax = Float.NEGATIVE_INFINITY;
        float zmin = Float.POSITIVE_INFINITY;
        float zmax = Float.NEGATIVE_INFINITY;
        for (Vector3f v : p) {
            xmin = Math.min(xmin, v.x);
            xmax = Math.max(xmax, v.x);
            zmin = Math.min(zmin, v.z);
            zmax = Math.max(zmax, v.z);
        }
        return new float[] { xmin, zmin, xmax, zmax };
    }

    public static Vector3f closestTo(Vector3f pos, Vector3f[] list) {
        Vector3f cur = null;
        float curDist = Float.MAX_VALUE;
        for (int i = 0; i < list.length; i++) {
            float dist = pos.distance(list[i]);
            if (dist < curDist) {
                cur = list[i];
                curDist = dist;
            }
        }
        return cur;
    }

    public static Vector3f[] rectFromLineXZ(Vector3f start, Vector3f end, float thickness) {
        // https://stackoverflow.com/a/1937202, with comment fix by Ryan Clarke

        Vector3f[] list = new Vector3f[4];
        float dx = end.x - start.x; // delta x
        float dy = end.z - start.z; // delta z
        float linelength = FastMath.sqrt(dx * dx + dy * dy);
        dx /= linelength;
        dy /= linelength;
        if (linelength == 0)
            return null;

        // Ok, (dx, dy) is now a unit vector pointing in the direction of the line
        // A perpendicular vector is given by (-dy, dx)
        float px = 0.5f * thickness * (-dy); // perpendicular vector with lenght thickness * 0.5
        float py = 0.5f * thickness * dx;
        list[0] = new Vector3f(start.x + px, start.y, start.z + py);
        list[1] = new Vector3f(end.x + px, end.y, end.z + py);
        list[2] = new Vector3f(end.x - px, end.y, end.z - py);
        list[3] = new Vector3f(start.x - px, start.y, start.z - py);
        return list;
    }

    /**
     * Vector3f the y is the height, and must be defined for a b c d. p is the
     * point, which we want the height for. Assumptions: - All abcd are coplanar - p
     * is inside abcd
     * 
     * @return the height of point p
     */
    public static float heightInQuad(Vector2f p, Vector3f a, Vector3f b, Vector3f c, Vector3f d) {
        float result = heightInTri(a, b, c, p);
        if (Float.isNaN(result))
            result = heightInTri(b, c, d, p);
        return result;
    }

    public static float heightInTri(Vector3f a, Vector3f b, Vector3f c, Vector3f p) {
        return heightInTri(a, b, c, H.v3tov2fXZ(p));
    }

    public static float heightInTri(Vector3f a, Vector3f b, Vector3f c, Vector2f p) {
        Vector2f _a = H.v3tov2fXZ(a);
        Vector2f _b = H.v3tov2fXZ(b);
        Vector2f _c = H.v3tov2fXZ(c);

        //these aren't in the triangle for some reason
        if (p.equals(_a)) return a.y;
        if (p.equals(_b)) return b.y;
        if (p.equals(_c)) return c.y;

        if (FastMath.pointInsideTriangle(_a, _b, _c, p) == 0)
            return Float.NaN;

        Vector2f q = intersectionOf2LinesGiven2PointsEach(_a, p, _b, _c);
        float slopeBC = (b.y - c.y) / (_b.subtract(_c).length()); // length is never negative
        float distCToq = q.subtract(_c).length();

        Vector3f q3 = new Vector3f(q.x, c.y + slopeBC * distCToq, q.y);
        float slopeAQ = (a.y - q3.y) / (q.subtract(_a).length());
        float distAToq = p.subtract(_a).length();

        float pheight = a.y - slopeAQ * distAToq;
        return pheight;
    }

    /**
     * https://en.wikipedia.org/wiki/Line%E2%80%93line_intersection#Given_two_points_on_each_line
     * Gets the point where 2 lines interept, when the 2 lines are given as points.
     * (p1 and p2) and (p3 and p4)
     * 
     * @return the point
     */
    public static Vector2f intersectionOf2LinesGiven2PointsEach(Vector2f p1, Vector2f p2, Vector2f p3, Vector2f p4) {

        float top1 = (p1.x * p2.y - p1.y * p2.x);
        float top2 = (p3.x * p4.y - p3.y * p4.x);
        float base = ((p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x));

        float px = (top1 * (p3.x - p4.x) - (p1.x - p2.x) * top2) / base;
        float py = (top1 * (p3.y - p4.y) - (p1.y - p2.y) * top2) / base;

        return new Vector2f(px, py);
    }
    

    /** Uses an equation of the form: ax + by + cz + d = 0 and returns z */
    public static float getZFromPlaneEqandXY(float[] eq, float x, float y) {
        return -(eq[0] * x + eq[1] * y + eq[3]) / eq[2];
    }

    /** Uses the 3 points and returns the equation in the form: ax + by + cz + d = 0 
     * WARNING: this seems to have some issues, use jme3 Plane class instead
    */
    public static float[] calcPlaneEquationFromPoints(Vector3f a, Vector3f b, Vector3f c) {
        Vector3f BA = b.subtract(a);
        Vector3f CA = c.subtract(a);

        float A = (BA.y * CA.z) - (CA.y * BA.z);
        float B = (BA.z * CA.x) - (CA.z * BA.x);
        float C = (BA.x * CA.y) - (CA.x * BA.y);
        float D = -(A * a.x + B * a.y + C * a.z);

        return new float[] { A, B, C, D };
    }

    /** Gets the average of the given points */
    public static Vector2f pointAverage(Vector2f ...p) {
        Vector2f total = new Vector2f();
        for (Vector2f cur: p) {
            total.addLocal(cur);
        }
        return total.divide(p.length);
    }

    /** Finds the point closest to a point, given a center and size. (if inside circle, then the point) */
    public static Vector2f pointOnCircleClosestTo(Vector2f center, float circleSize, Vector2f point) {
        Vector2f v = point.subtract(center);
        if (v.length() < circleSize)
            return point; // already in the circle
        
        return center.add(v.divide(v.length()/circleSize));
    }

    /** Finds the point closest to many external points, given a center and size */
    public static Vector2f pointOnCircleClosestTo(Vector2f center, float circleSize, Vector2f... points) {
        Vector2f avg = Trig.pointAverage(points);
        return pointOnCircleClosestTo(center, circleSize, avg);
    }
}

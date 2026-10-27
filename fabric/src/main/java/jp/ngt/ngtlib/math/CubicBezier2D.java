package jp.ngt.ngtlib.math;
/** KaizPatchX's translation-invariant cubic evaluation. */
public final class CubicBezier2D {
    record Vector(double x,double y) {
        double length() {return Math.hypot(x,y);}
        Vector add(Vector v) {return new Vector(x+v.x,y+v.y);}
        Vector sub(Vector v) {return new Vector(x-v.x,y-v.y);}
        Vector mul(double s) {return new Vector(x*s,y*s);}
    }
    private final double startX,startY,endX,endY;
    private final Vector first,middle,last,third;
    final double controlPolygonLength,tangentScale;
    public CubicBezier2D(double sx,double sy,double ax,double ay,double bx,double by,double ex,double ey) {
        startX=sx;startY=sy;endX=ex;endY=ey;
        first=new Vector(ax-sx,ay-sy);middle=new Vector(bx-ax,by-ay);last=new Vector(ex-bx,ey-by);
        controlPolygonLength=first.length()+middle.length()+last.length();tangentScale=3*Math.max(first.length(),Math.max(middle.length(),last.length()));
        third=last.sub(middle.mul(2)).add(first).mul(6);
    }
    public double[] pointAt(double parameter) {
        double t=Math.max(0,Math.min(1,parameter));if(t==0)return new double[]{startX,startY};if(t==1)return new double[]{endX,endY};
        Vector v=offsetAt(t);return new double[]{startX+v.x,startY+v.y};
    }
    Vector offsetAt(double t) {return first.mul(3).add(middle.sub(first).mul(3).add(third.mul(t/6)).mul(t)).mul(t);}
    Vector tangentAt(double t) {double u=1-t;return first.mul(u*u).add(middle.mul(2*t*u)).add(last.mul(t*t)).mul(3);}
    Vector meanTangent(double from,double to) {double width=to-from;return tangentAt((from+to)*0.5).add(third.mul(width*width/24));}
}

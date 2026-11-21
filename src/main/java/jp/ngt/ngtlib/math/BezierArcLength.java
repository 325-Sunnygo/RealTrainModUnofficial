package jp.ngt.ngtlib.math;
public final class BezierArcLength {
    private final double[] distances;
    private final double length;
    public BezierArcLength(CubicBezier2D curve,int samplesPerUnit) {
        int segments=Math.max(16,(int)Math.ceil(curve.controlPolygonLength*samplesPerUnit));distances=new double[segments+1];var previous=curve.offsetAt(0);
        for(int i=1;i<=segments;i++) {var point=curve.offsetAt((double)i/segments);distances[i]=distances[i-1]+point.sub(previous).length();previous=point;}
        length=distances[segments];
    }
    public double getLength() {return length;}
    public double parameterAt(double ratio) {
        if(ratio<=0)return 0;if(ratio>=1)return 1;if(length==0)return ratio;double distance=ratio*length;if(distance>=length)return 1;
        int lower=0,upper=distances.length-1;while(upper-lower>1) {int middle=(lower+upper)>>>1;if(distances[middle]<=distance)lower=middle;else upper=middle;}
        double weight=(distance-distances[lower])/(distances[upper]-distances[lower]);return (lower+weight)/(distances.length-1);
    }
}

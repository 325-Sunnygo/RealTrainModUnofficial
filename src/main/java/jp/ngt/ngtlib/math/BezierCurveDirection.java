package jp.ngt.ngtlib.math;
public final class BezierCurveDirection {
    private BezierCurveDirection() {}
    public static double slopeAt(CubicBezier2D curve,double parameter) {
        double t=Math.max(0,Math.min(1,parameter)),tolerance=curve.tangentScale*1E-12;if(curve.tangentScale==0)return 0;
        var tangent=curve.tangentAt(t);if(tangent.length()>tolerance)return Math.atan2(tangent.y(),tangent.x());
        double before=Math.max(t-1E-4,0),after=Math.min(t+1E-4,1);var centered=curve.meanTangent(before,after);
        if(centered.length()>tolerance)return Math.atan2(centered.y(),centered.x());
        var one=t<1?curve.meanTangent(t,after):curve.meanTangent(before,t);return Math.atan2(one.y(),one.x());
    }
}

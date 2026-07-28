package jp.ngt.ngtlib.math;

public interface ILine {
    double[] getPoint(int split, int index);

    int getNearlestPoint(int split, double x, double z);

    double getSlope(int split, int index);

    double getLength();
}

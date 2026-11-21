package com.portofino.realtrainmodunofficial.client.model;

/** Selects primitives while preserving vertex normals under Iris world rendering. */
public final class LegacyIrisGeometry {
    private LegacyIrisGeometry() {}
    public static boolean useTriangles(boolean shaderPack, boolean smoothNormals) {
        return !shaderPack || smoothNormals;
    }
    /** Native storage uses four vertices, eight floats each, with XYZ normal at offsets 3..5. */
    public static boolean hasVaryingNormals(float[] data) {
        for (int q=0; q+31<data.length; q+=32) {
            for (int v=1; v<4; v++) {
                for (int axis=0; axis<3; axis++) {
                    if (Float.floatToIntBits(data[q+3+axis]) != Float.floatToIntBits(data[q+v*8+3+axis])) return true;
                }
            }
        }
        return false;
    }
}

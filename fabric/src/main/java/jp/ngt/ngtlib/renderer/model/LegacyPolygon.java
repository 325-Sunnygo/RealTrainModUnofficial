package jp.ngt.ngtlib.renderer.model;

/** MQO triangulation and logical-face normals used by the original RTM triangle loader. */
public final class LegacyPolygon {
    private LegacyPolygon() {}

    public static int[] triangleIndices(int vertexCount) {
        if (vertexCount < 3) return new int[0];
        int[] indices = new int[(vertexCount - 2) * 3];
        for (int i = 0; i < indices.length; i++) {
            int index = i % 3 == 0 ? 0 : i / 3 + i % 3;
            indices[i] = (vertexCount - index) % vertexCount;
        }
        return indices;
    }

    /** Each source polygon contributes once, irrespective of triangle area or subdivision. */
    public static float[] faceNormal(float[] triangles) {
        double x = 0, y = 0, z = 0;
        for (int i = 0; i + 8 < triangles.length; i += 9) {
            double ax = triangles[i + 3] - triangles[i], ay = triangles[i + 4] - triangles[i + 1], az = triangles[i + 5] - triangles[i + 2];
            double bx = triangles[i + 6] - triangles[i], by = triangles[i + 7] - triangles[i + 1], bz = triangles[i + 8] - triangles[i + 2];
            double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 0 && Double.isFinite(length)) {
                x += nx / length;
                y += ny / length;
                z += nz / length;
            }
        }
        double length = Math.sqrt(x * x + y * y + z * z);
        return length > 0 && Double.isFinite(length)
                ? new float[]{(float) (x / length), (float) (y / length), (float) (z / length)}
                : new float[]{0, 0, 0};
    }
}

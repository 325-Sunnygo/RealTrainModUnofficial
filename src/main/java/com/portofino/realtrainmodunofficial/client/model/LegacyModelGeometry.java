package com.portofino.realtrainmodunofficial.client.model;

import jp.ngt.ngtlib.renderer.model.*;
import org.joml.Vector3f;

/** CPU geometry expansion shared by script rendering and model verification. */
public final class LegacyModelGeometry {
    private static final float[] FLAT_EMPTY = new float[0];
    private LegacyModelGeometry() {}
    public static float[] flatten(GroupObject group, boolean smoothing) {
        if (group == null || group.faces.isEmpty()) {
            return FLAT_EMPTY;
        }
        int quadVerts = 0;
        for (Face face : group.faces) {
            int n = face.vertices.length;
            if (n >= 3) {
                quadVerts += (group.drawMode == 4 && n % 3 == 0 ? n / 3 : n == 4 ? 1 : n - 2) * 4;
            }
        }
        float[] flat = new float[quadVerts * 8];
        int w = 0;
        for (Face face : group.faces) {
            int n = face.vertices.length;
            if (n < 3) {
                continue;
            }
            TextureCoordinate[] coordinates=face.getTextureCoordinates();
            boolean triangles = group.drawMode == 4 && n % 3 == 0;
            for (int t = 0; t < (triangles ? n / 3 : n == 4 ? 1 : n - 2); t++) {
                int[] idx = triangles ? new int[]{t * 3, t * 3 + 1, t * 3 + 2, t * 3 + 2}
                        : n == 4 ? new int[]{0, 1, 2, 3} : new int[]{0, t + 1, t + 2, t + 2};
                Vector3f normal = face.faceNormal != null
                        ? new Vector3f(face.faceNormal.getX(), face.faceNormal.getY(), face.faceNormal.getZ())
                        : faceNormal(face, idx);
                // ★頂点法線があればそちらを使う (本家 NGTRenderHelper.addFace の smoothing=true 側)。
                // 無い場合だけ面法線 = フラット陰影。
                Vertex[] vertexNormals = smoothing ? face.vertexNormals : null;
                for (int k = 0; k < 4; k++) {
                    Vertex vert = face.vertices[idx[k]];
                    Vector3f vn = normal;
                    if (vertexNormals != null && idx[k] < vertexNormals.length && vertexNormals[idx[k]] != null) {
                        Vertex n0 = vertexNormals[idx[k]];
                        vn = new Vector3f(n0.getX(), n0.getY(), n0.getZ());
                        if (vn.lengthSquared() < 1.0e-8F) {
                            vn = normal;
                        }
                    }
                    float u = 0.0F, vv = 0.0F;
                    if (coordinates != null && idx[k] < coordinates.length && coordinates[idx[k]]!=null) {
                        u = coordinates[idx[k]].getU();
                        vv = coordinates[idx[k]].getV();
                    }
                    flat[w++] = vert.x;
                    flat[w++] = vert.y;
                    flat[w++] = vert.z;
                    flat[w++] = u;
                    flat[w++] = vv;
                    flat[w++] = vn.x;
                    flat[w++] = vn.y;
                    flat[w++] = vn.z;
                }
            }
        }
        return flat;
    }
    /** Exact cache validation against live script-owned geometry; no hash collisions or allocations. */
    public static boolean matches(GroupObject group,boolean smoothing,float[] flat) {
        if(flat==null) return false;
        if(group==null) return flat.length==0;
        int w=0;
        for(Face face:group.faces) {
            int n=face.vertices.length;if(n<3) continue;
            boolean triangles=group.drawMode==4 && n%3==0;
            int count=triangles?n/3:n==4?1:n-2;
            TextureCoordinate[] coords=face.getTextureCoordinates();
            for(int t=0;t<count;t++) for(int k=0;k<4;k++) {
                int index=triangles?t*3+Math.min(k,2):n==4?k:k==0?0:t+Math.min(k,2);
                Vertex vertex=face.vertices[index],normal=face.faceNormal;
                if(smoothing && face.vertexNormals!=null && index<face.vertexNormals.length && face.vertexNormals[index]!=null) {
                    Vertex candidate=face.vertexNormals[index];
                    if(candidate.x*candidate.x+candidate.y*candidate.y+candidate.z*candidate.z>=1.0e-8F) normal=candidate;
                }
                // Faces without precomputed normals use the existing fallback builder.
                if(normal==null || w+8>flat.length) return false;
                float u=coords!=null && index<coords.length && coords[index]!=null?coords[index].getU():0;
                float v=coords!=null && index<coords.length && coords[index]!=null?coords[index].getV():0;
                if(!same(flat[w],vertex.x)||!same(flat[w+1],vertex.y)||!same(flat[w+2],vertex.z)
                    ||!same(flat[w+3],u)||!same(flat[w+4],v)||!same(flat[w+5],normal.x)
                    ||!same(flat[w+6],normal.y)||!same(flat[w+7],normal.z)) return false;
                w+=8;
            }
        }
        return w==flat.length;
    }
    private static boolean same(float a,float b) {return Float.floatToIntBits(a)==Float.floatToIntBits(b);}

    private static Vector3f faceNormal(Face face, int[] idx) {
        Vertex v0 = face.vertices[idx[0]];
        Vertex v1 = face.vertices[idx[1]];
        Vertex v2 = face.vertices[idx[2]];
        float ax = v1.x - v0.x, ay = v1.y - v0.y, az = v1.z - v0.z;
        float bx = v2.x - v0.x, by = v2.y - v0.y, bz = v2.z - v0.z;
        Vector3f n = new Vector3f(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx);
        // ★大きさで捨てない (細かい文字の三角形が真上を向く)。
        com.portofino.realtrainmodunofficial.client.model.FaceNormals.normalize(n);
        return n;
    }
}

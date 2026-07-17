package com.myname.legacyloader.bridge.client.model;

import com.myname.legacyloader.bridge.client.renderer.LegacyTesrContext;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wavefront (.obj) パーサ + 描画。Forge の WavefrontObject 相当。
 * v / vt / vn / f (三角形・多角形・負インデックス) と g/o によるグループを扱う。
 * {@code renderAll()} は現在の {@link LegacyTesrContext} へ三角形を流す。
 */
public class LegacyWavefrontObject implements LegacyModelCustom {

    private static final class Face {
        int[] v;   // 頂点インデックス (0-based)
        int[] t;   // texcoord インデックス (0-based, -1=なし)
        int[] n;   // normal インデックス (0-based, -1=なし)
    }

    private final List<float[]> vertices = new ArrayList<>();
    private final List<float[]> texCoords = new ArrayList<>();
    private final List<float[]> normals = new ArrayList<>();
    private final List<Face> allFaces = new ArrayList<>();
    private final Map<String, List<Face>> groups = new LinkedHashMap<>();

    public LegacyWavefrontObject(InputStream in) {
        parse(in);
    }

    private void parse(InputStream in) {
        String currentGroup = "default";
        List<Face> groupFaces = groups.computeIfAbsent(currentGroup, k -> new ArrayList<>());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                if (line.startsWith("v ")) {
                    vertices.add(parseFloats(line, 3));
                } else if (line.startsWith("vt ")) {
                    float[] uv = parseFloats(line, 2);
                    // OBJ の V は下が 0。MC のアトラス/テクスチャは上が 0 なので反転。
                    uv[1] = 1.0F - uv[1];
                    texCoords.add(uv);
                } else if (line.startsWith("vn ")) {
                    normals.add(parseFloats(line, 3));
                } else if (line.startsWith("f ")) {
                    Face face = parseFace(line);
                    if (face != null) {
                        allFaces.add(face);
                        groupFaces.add(face);
                    }
                } else if (line.startsWith("g ") || line.startsWith("o ")) {
                    currentGroup = line.substring(2).trim();
                    if (currentGroup.isEmpty()) {
                        currentGroup = "default";
                    }
                    groupFaces = groups.computeIfAbsent(currentGroup, k -> new ArrayList<>());
                }
            }
        } catch (Exception e) {
            com.myname.legacyloader.LegacyLoaderMod.LOGGER.warn("LegacyLoader: OBJ parse error", e);
        }
    }

    private static float[] parseFloats(String line, int count) {
        String[] parts = line.split("\\s+");
        float[] out = new float[count];
        for (int i = 0; i < count && i + 1 < parts.length; i++) {
            try {
                out[i] = Float.parseFloat(parts[i + 1]);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private Face parseFace(String line) {
        String[] parts = line.split("\\s+");
        int n = parts.length - 1;
        if (n < 3) {
            return null;
        }
        Face face = new Face();
        face.v = new int[n];
        face.t = new int[n];
        face.n = new int[n];
        for (int i = 0; i < n; i++) {
            String[] refs = parts[i + 1].split("/", -1);
            face.v[i] = resolveIndex(refs.length > 0 ? refs[0] : "", vertices.size());
            face.t[i] = refs.length > 1 ? resolveIndex(refs[1], texCoords.size()) : -1;
            face.n[i] = refs.length > 2 ? resolveIndex(refs[2], normals.size()) : -1;
        }
        return face;
    }

    /** OBJ インデックス (1-based, 負可) → 0-based。 */
    private static int resolveIndex(String s, int size) {
        if (s == null || s.isEmpty()) {
            return -1;
        }
        try {
            int idx = Integer.parseInt(s.trim());
            if (idx < 0) {
                return size + idx; // -1 = 末尾
            }
            return idx - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public String getType() {
        return "obj";
    }

    @Override
    public void renderAll() {
        renderFaces(allFaces);
    }

    @Override
    public void renderOnly(String... groupNames) {
        for (String name : groupNames) {
            List<Face> faces = groups.get(name);
            if (faces != null) {
                renderFaces(faces);
            }
        }
    }

    @Override
    public void renderPart(String partName) {
        List<Face> faces = groups.get(partName);
        if (faces != null) {
            renderFaces(faces);
        }
    }

    private void renderFaces(List<Face> faces) {
        LegacyTesrContext ctx = LegacyTesrContext.current();
        if (ctx == null) {
            return;
        }
        for (Face face : faces) {
            int count = face.v.length;
            if (count == 4) {
                // 四角形はそのまま 1 枚のクアッドで
                emitQuad(ctx, face);
            } else {
                // 三角形・多角形は扇状に分割 (emitTriangle 側で縮退クアッド化)
                for (int i = 1; i + 1 < count; i++) {
                    emit(ctx, face, 0, i, i + 1);
                }
            }
        }
    }

    private void emitQuad(LegacyTesrContext ctx, Face face) {
        double[] p0 = toDouble(vertex(face.v[0]));
        double[] p1 = toDouble(vertex(face.v[1]));
        double[] p2 = toDouble(vertex(face.v[2]));
        double[] p3 = toDouble(vertex(face.v[3]));
        float[] uv0 = texCoord(face.t[0]);
        float[] uv1 = texCoord(face.t[1]);
        float[] uv2 = texCoord(face.t[2]);
        float[] uv3 = texCoord(face.t[3]);
        float[] normal = face.n[0] >= 0 ? normal(face.n[0]) : computeNormal(p0, p1, p2);
        ctx.emitQuad(p0, p1, p2, p3, uv0, uv1, uv2, uv3, normal);
    }

    private void emit(LegacyTesrContext ctx, Face face, int a, int b, int c) {
        double[] p0 = toDouble(vertex(face.v[a]));
        double[] p1 = toDouble(vertex(face.v[b]));
        double[] p2 = toDouble(vertex(face.v[c]));
        float[] uv0 = texCoord(face.t[a]);
        float[] uv1 = texCoord(face.t[b]);
        float[] uv2 = texCoord(face.t[c]);
        float[] normal = face.n[a] >= 0 ? normal(face.n[a]) : computeNormal(p0, p1, p2);
        ctx.emitTriangle(p0, p1, p2, uv0, uv1, uv2, normal);
    }

    private float[] vertex(int i) {
        return i >= 0 && i < vertices.size() ? vertices.get(i) : new float[3];
    }

    private float[] texCoord(int i) {
        return i >= 0 && i < texCoords.size() ? texCoords.get(i) : new float[]{0, 0};
    }

    private float[] normal(int i) {
        return i >= 0 && i < normals.size() ? normals.get(i) : new float[]{0, 1, 0};
    }

    private static double[] toDouble(float[] f) {
        return new double[]{f[0], f[1], f[2]};
    }

    private static float[] computeNormal(double[] p0, double[] p1, double[] p2) {
        double ux = p1[0] - p0[0], uy = p1[1] - p0[1], uz = p1[2] - p0[2];
        double vx = p2[0] - p0[0], vy = p2[1] - p0[1], vz = p2[2] - p0[2];
        return new float[]{
                (float) (uy * vz - uz * vy),
                (float) (uz * vx - ux * vz),
                (float) (ux * vy - uy * vx)
        };
    }
}

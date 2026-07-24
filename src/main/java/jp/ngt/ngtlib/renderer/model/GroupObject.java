package jp.ngt.ngtlib.renderer.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本家 jp.ngt.ngtlib.renderer.model.GroupObject のスクリプト互換移植。
 */
public class GroupObject {
    public final String name;
    public final List<Face> faces = new ArrayList<>();
    /** 描画モード (GL_TRIANGLES=4 / GL_QUADS=7)。 */
    public byte drawMode = 4;
    /** スムージング角 (度)。0 ならスムージングなし。 */
    public float smoothingAngle;

    public GroupObject(String name) {
        this.name = name == null ? "" : name;
    }

    public GroupObject(String name, int drawMode) {
        this(name);
        this.drawMode = (byte) drawMode;
    }

    public String getName() {
        return this.name;
    }

    /**
     * 本家 calcVertexNormals: グループ内の面から「頂点→共有面」を作り、
     * 各面にスムージング済みの頂点法線を計算させる。
     */
    public void calcVertexNormals(VecAccuracy accuracy) {
        Map<Vertex, List<Face>> faceMap = new HashMap<>(this.faces.size() * 4);
        for (Face face : this.faces) {
            if (face.faceNormal == null) {
                face.calculateFaceNormal(accuracy);
            }
            for (Vertex vertex : face.vertices) {
                if (vertex == null) {
                    continue;
                }
                List<Face> list = faceMap.computeIfAbsent(vertex, k -> new ArrayList<>());
                if (!list.contains(face)) {
                    list.add(face);
                }
            }
        }
        float angleCos = (float) Math.cos(Math.toRadians(this.smoothingAngle));
        for (Face face : this.faces) {
            face.calcVertexNormals(faceMap, angleCos, accuracy);
        }
    }

    /** 本家 render(smoothing): NGTTessellator へ積んで即描画する。 */
    public void render(boolean smoothing) {
        if (this.faces.isEmpty()) {
            return;
        }
        jp.ngt.ngtlib.renderer.NGTTessellator tessellator = jp.ngt.ngtlib.renderer.NGTTessellator.instance;
        tessellator.startDrawing(this.drawMode);
        this.render(tessellator, smoothing);
        tessellator.draw();
    }

    /** 本家 render(tessellator, smoothing): 指定 tessellator へ頂点を積むだけ。 */
    public void render(Object tessellator, boolean smoothing) {
        for (Face face : this.faces) {
            face.addFaceForRender(tessellator, smoothing);
        }
    }

    public GroupObject copy(String newName) {
        GroupObject go = new GroupObject(newName, this.drawMode);
        go.smoothingAngle = this.smoothingAngle;
        for (Face face : this.faces) {
            go.faces.add(face.copy());
        }
        return go;
    }
}

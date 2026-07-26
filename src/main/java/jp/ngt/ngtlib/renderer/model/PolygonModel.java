package jp.ngt.ngtlib.renderer.model;

import jp.ngt.ngtlib.renderer.GLRecorder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 本家 jp.ngt.ngtlib.renderer.model.PolygonModel のスクリプト互換移植。
 * renderPart は GLRecorder に記録され、再生側が現在テクスチャで面を emit する。
 */
public class PolygonModel {
    public final List<GroupObject> groupObjects = new ArrayList<>();
    /** 全頂点。本家はパース中に貯める。 */
    public final List<Vertex> vertices = new ArrayList<>();
    /** 座標精度。RTMU は float 固定だがスクリプトが読むので持つ。 */
    public VecAccuracy accuracy = VecAccuracy.MEDIUM;
    /** 外接箱 {minX,minY,minZ,maxX,maxY,maxZ}。 */
    public float[] sizeBox = new float[6];
    /** 描画モード (GL_TRIANGLES=4 / GL_QUADS=7)。 */
    public int drawMode = 4;
    /** 読み込み元ファイル名。 */
    public String fileName = "";
    /** パース中のグループ。 */
    public GroupObject currentGroupObject;
    /** マテリアル名 → Material。 */
    public final java.util.Map<String, Material> materials = new java.util.LinkedHashMap<>();

    public GroupObject getGroupObject(String name) {
        for (GroupObject group : this.groupObjects) {
            if (group.name.equalsIgnoreCase(name)) {
                return group;
            }
        }
        return null;
    }

    public List<GroupObject> getGroupObjects(String... names) {
        if (names == null || names.length == 0) {
            return this.groupObjects;
        }
        List<GroupObject> out = new ArrayList<>();
        for (String name : names) {
            GroupObject g = this.getGroupObject(name);
            if (g != null) {
                out.add(g);
            }
        }
        return out;
    }

    /** 本家 getGroupObjects(): 全グループ。 */
    public List<GroupObject> getGroupObjects() {
        return this.groupObjects;
    }

    public java.util.Map<String, Material> getMaterials() {
        return this.materials;
    }

    public int getDrawMode() {
        return this.drawMode;
    }

    public String getFileName() {
        return this.fileName;
    }

    /** 本家 getSize(): 外接箱をそのまま返す。 */
    public float[] getSize() {
        return this.sizeBox;
    }

    /** 本家 calcSizeBox: 頂点 1 つ分だけ外接箱を広げる。 */
    public void calcSizeBox(Vertex vtx) {
        if (vtx == null) {
            return;
        }
        if (vtx.getX() < this.sizeBox[0]) {
            this.sizeBox[0] = vtx.getX();
        } else if (vtx.getX() > this.sizeBox[3]) {
            this.sizeBox[3] = vtx.getX();
        }
        if (vtx.getY() < this.sizeBox[1]) {
            this.sizeBox[1] = vtx.getY();
        } else if (vtx.getY() > this.sizeBox[4]) {
            this.sizeBox[4] = vtx.getY();
        }
        if (vtx.getZ() < this.sizeBox[2]) {
            this.sizeBox[2] = vtx.getZ();
        } else if (vtx.getZ() > this.sizeBox[5]) {
            this.sizeBox[5] = vtx.getZ();
        }
    }

    /** 本家 getFloat: "-NAN" 等が混ざる MQO 対策で、読めなければ 0。 */
    public float getFloat(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException | NullPointerException e) {
            return 0.0F;
        }
    }

    /** 本家 split(target, char): 区切り文字 1 文字での分割。 */
    public String[] split(String target, char regex) {
        return this.split(target, String.valueOf(regex));
    }

    /** 本家 split(target, String): 正規表現メタを含まない素朴な分割。 */
    public String[] split(String target, String regex) {
        if (target == null || target.isEmpty()) {
            return new String[0];
        }
        List<String> out = new ArrayList<>();
        int from = 0;
        int hit;
        while ((hit = target.indexOf(regex, from)) >= 0) {
            out.add(target.substring(from, hit));
            from = hit + regex.length();
        }
        out.add(target.substring(from));
        return out.toArray(new String[0]);
    }

    /** 本家 tessellateAll: 全グループの頂点を tessellator へ積む (draw はしない)。 */
    public void tessellateAll(Object tessellator, boolean smoothing) {
        for (GroupObject group : this.groupObjects) {
            group.render(tessellator, smoothing);
        }
    }

    /** 本家 renderOnly: 指定グループだけ描画。 */
    public void renderOnly(boolean smoothing, String... groupNames) {
        if (groupNames == null) {
            return;
        }
        for (String name : groupNames) {
            this.renderPart(smoothing, name);
        }
    }

    /** 本家 deepCopy: グループ/面/頂点まで複製した独立モデルを返す。 */
    public PolygonModel deepCopy() {
        PolygonModel copy = new PolygonModel();
        copy.accuracy = this.accuracy;
        copy.drawMode = this.drawMode;
        copy.fileName = this.fileName;
        System.arraycopy(this.sizeBox, 0, copy.sizeBox, 0, this.sizeBox.length);
        copy.materials.putAll(this.materials);
        for (GroupObject group : this.groupObjects) {
            copy.groupObjects.add(group.copy(group.name));
        }
        return copy;
    }

    /**
     * 本家: renderPart(smoothing, objName) — 指定グループを即時描画。
     */
    public void renderPart(boolean smoothing, String objName) {
        GLRecorder rec = GLRecorder.active();
        if (rec != null && objName != null) {
            rec.drawModelGroup(this, objName.toLowerCase(Locale.ROOT), smoothing);
        }
    }

    public void renderPart(String objName) {
        this.renderPart(false, objName);
    }

    /**
     * 全グループ描画。
     */
    public void renderAll() {
        this.renderAll(false);
    }

    /** 本家 renderAll(smoothing)。smoothing=true なら再生側が頂点法線で描く。 */
    public void renderAll(boolean smoothing) {
        GLRecorder rec = GLRecorder.active();
        if (rec != null) {
            for (GroupObject group : this.groupObjects) {
                rec.drawModelGroup(this, group.name.toLowerCase(Locale.ROOT), smoothing);
            }
        }
    }
}

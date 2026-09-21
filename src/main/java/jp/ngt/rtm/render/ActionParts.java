package jp.ngt.rtm.render;

import jp.ngt.ngtlib.renderer.GLRecorder;
import jp.ngt.ngtlib.renderer.model.Face;
import jp.ngt.ngtlib.renderer.model.GroupObject;
import jp.ngt.ngtlib.renderer.model.VecAccuracy;
import jp.ngt.ngtlib.renderer.model.Vertex;

/**
 * 本家 jp.ngt.rtm.render.ActionParts の移植。
 * クリック/ドラッグで操作できるパーツ (運転台のマスコン・ブレーキ弁・スイッチ類)。
 *
 * <p>本家は LIGHT パスで「カーソルが当たっているパーツ」に輪郭線を描く
 * (通常は白、右クリック中は橙)。本家は {@code glCullFace(GL_FRONT)} で前面を落として
 * リムを作るが、RTMU の RenderType は前面カリングを表現できないため、
 * <b>三角形の巻き順を逆にして</b>通常の裏面カリングで前面を落とす (見た目は同じリム)。
 * 当たり判定 (PICK パス) は {@code client.ActionPartsPicker} が三角形レイキャストで代替する。
 */
public class ActionParts extends Parts {
    private static final float OUTLINE_THICKNESS = 0.005F;
    /** 本家 RenderPass.PICK の id (定数参照の循環を避けるため数値で持つ)。 */
    private static final int PASS_PICK = RenderPass.PICK.id;
    private static final int PASS_LIGHT = RenderPass.LIGHT.id;
    private static final int GL_TRIANGLES = 4;

    public final ActionType behavior;

    /** 本家 outlineModel: 法線方向へ膨らませた複製グループ (モデル単位でキャッシュ)。 */
    private GroupObject[] outlineModels;
    private Object outlineModelsSource;

    public ActionParts(ActionType behavior, String... objNames) {
        super(objNames);
        this.behavior = behavior;
    }

    @Override
    public boolean isActionParts() {
        return true;
    }

    @Override
    public void init(PartsRenderer renderer) {
        super.init(renderer);
        ModelObject mo = renderer.getModelObject();
        if (mo != null && mo.model != null) {
            getOutlineModels(mo.model);
        }
    }

    /**
     * 本家 setupOutlineModel: 頂点を法線方向へ 0.005 膨らませた輪郭用グループを作る。
     * 列車経路と車経路の両方から使えるよう、モデルを引数に取りキャッシュする。
     */
    public GroupObject[] getOutlineModels(jp.ngt.ngtlib.renderer.model.PolygonModel polygonModel) {
        if (polygonModel == null) {
            return new GroupObject[0];
        }
        if (this.outlineModels != null && this.outlineModelsSource == polygonModel) {
            return this.outlineModels;
        }
        this.outlineModelsSource = polygonModel;
        GroupObject[] objs = this.getObjects(polygonModel);
        java.util.List<GroupObject> out = new java.util.ArrayList<>();
        for (int i = 0; i < objs.length; i++) {
            GroupObject src = objs[i];
            if (src == null) {
                continue;
            }
            GroupObject go = src.copy("outline_" + i);
            go.smoothingAngle = 180.0F;
            go.calcVertexNormals(VecAccuracy.MEDIUM);
            for (Face face : go.faces) {
                for (int k = 0; k < face.vertices.length; k++) {
                    Vertex vNormal = face.vertexNormals[k].copy(VecAccuracy.MEDIUM);
                    face.vertices[k] = face.vertices[k].add(vNormal.expand(OUTLINE_THICKNESS));
                }
                face.calculateFaceNormal(VecAccuracy.MEDIUM);
            }
            out.add(go);
        }
        this.outlineModels = out.toArray(new GroupObject[0]);
        return this.outlineModels;
    }

    @Override
    public void render(PartsRenderer renderer) {
        if (renderer.currentPass == PASS_PICK) {
            // RTMU に PICK パスは無い (ActionPartsPicker が代替)。通常描画のみ行う。
            super.render(renderer);
            return;
        }
        super.render(renderer);
        Parts hit = renderer.hittedParts.get(renderer.hittedEntity);
        if (renderer.currentPass == PASS_LIGHT && this.equals(hit)) {
            renderOutline(getOutlineModels(renderer.getPolygonModel()),
                    isRightButtonDown() ? 0xFF8800 : 0xFFFFFF);
        }
    }

    /** 本家の色定数 (右クリック中 = 橙)。車経路からも使う。 */
    public static int outlineColor(boolean rightButtonDown) {
        return rightButtonDown ? 0xFF8800 : 0xFFFFFF;
    }

    /** クライアントでの右ボタン押下判定 (車経路の輪郭色用)。 */
    public static boolean isRightButtonDownClient() {
        return isRightButtonDown();
    }

    private static boolean isRightButtonDown() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc.getWindow() != null
            && org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),
                   org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    /** 本家 renderOutline: 色付きの輪郭を記録する (加算ではなく不透明色)。 */
    private void renderOutline(GroupObject[] outline, int color) {
        GLRecorder rec = GLRecorder.active();
        if (rec == null || outline == null || outline.length == 0) {
            return;
        }
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        // 本家はテクスチャ無効で描く。既定テクスチャ (白) に戻して単色にする。
        rec.bindTexture(null);
        rec.brightness(0xF000F0);
        for (GroupObject go : outline) {
            float[] arr = buildOutlineTriangles(go, r, g, b);
            if (arr.length > 0) {
                // cullFront = true: 描画側が RenderSystem.cullFace(FRONT) を設定して即時 flush する
                // (= 本家 glCullFace(GL_FRONT))。
                rec.drawTess(new GLRecorder.TessDraw(GL_TRIANGLES, arr, false, true));
            }
        }
        rec.brightness(-1);
    }

    /**
     * 輪郭三角形 (stride 9, 元の巻き順) を作る。
     * 前面カリングは描画側 (RenderSystem.cullFace(FRONT)) が行う。
     */
    public static float[] buildOutlineTriangles(GroupObject group, float r, float g, float b) {
        if (group == null || group.faces == null) {
            return new float[0];
        }
        java.util.List<Float> verts = new java.util.ArrayList<>();
        for (Face face : group.faces) {
            Vertex[] vs = face.vertices;
            if (vs == null || vs.length < 3) {
                continue;
            }
            for (int i = 1; i + 1 < vs.length; i++) {
                // 元の巻き順。前面カリングは描画側で行う。
                addOutlineVertex(verts, vs[0], r, g, b);
                addOutlineVertex(verts, vs[i], r, g, b);
                addOutlineVertex(verts, vs[i + 1], r, g, b);
            }
        }
        float[] arr = new float[verts.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = verts.get(i);
        }
        return arr;
    }

    private static void addOutlineVertex(java.util.List<Float> out, Vertex v, float r, float g, float b) {
        out.add(v.x);
        out.add(v.y);
        out.add(v.z);
        out.add(0.0F);
        out.add(0.0F);
        out.add(r);
        out.add(g);
        out.add(b);
        out.add(1.0F);
    }

    @Override
    public boolean ignoreMatId(PartsRenderer renderer) {
        return renderer.currentPass == PASS_PICK;
    }
}

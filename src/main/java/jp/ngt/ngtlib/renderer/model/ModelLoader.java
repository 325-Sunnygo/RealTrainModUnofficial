package jp.ngt.ngtlib.renderer.model;

import jp.ngt.ngtlib.io.NGTFileLoader;
import jp.ngt.ngtlib.io.NGTLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本家 jp.ngt.ngtlib.renderer.model.ModelLoader のスクリプト互換移植。
 * パック内 .mqo/.mqoz を PolygonModel (グループ/面/頂点グラフ) として読む。
 * 頂点スケールは既存 MqoModelLoader と同じ 0.01 (座標系一致が必須)。
 */
public final class ModelLoader {
    private static final Map<String, PolygonModel> CACHE = new ConcurrentHashMap<>();

    private ModelLoader() {
    }

    public static PolygonModel loadModel(Object resource, VecAccuracy accuracy) {
        return loadModel(resource, accuracy, null);
    }

    /**
     * スクリプト形: ModelLoader.loadModel(resource, VecAccuracy.LOW, [])
     */
    public static PolygonModel loadModel(Object resource, VecAccuracy accuracy, Object options) {
        String path = pathOf(resource);
        if (path == null) {
            return new PolygonModel();
        }
        return CACHE.computeIfAbsent(path.toLowerCase(Locale.ROOT), key -> {
            byte[] bytes = NGTFileLoader.findAsset(path);
            if (bytes == null) {
                //models/ 接頭辞のゆれに対応
                bytes = NGTFileLoader.findAsset("models/" + path);
            }
            if (bytes == null) {
                NGTLog.debug("[ModelLoader] model not found: " + path);
                return new PolygonModel();
            }
            try {
                return parse(bytes, path);
            } catch (Exception e) {
                NGTLog.debug("[ModelLoader] failed to parse " + path + ": " + e);
                return new PolygonModel();
            }
        });
    }

    /**
     * MQO テキストから直接 (VehicleScriptRenderers が車体モデルグラフ生成に使用)。
     */
    public static PolygonModel parse(byte[] bytes, String name) throws IOException {
        String text = MqoReader.extractText(bytes, name);
        PolygonModel model = new PolygonModel();
        if (text == null) {
            return model;
        }
        //★書式の解釈は MqoReader だけが持つ ([[MqoReader]])。ここは「スクリプトが見る
        //グラフ」への組み立てに徹する: 座標は 0.01 倍、面の頂点は逆順格納。
        MqoReader.read(text, new MqoReader.Handler() {
            private GroupObject current;
            private final List<float[]> verts = new ArrayList<>();

            @Override
            public void objectStart(String objectName) {
                this.current = new GroupObject(objectName);
                model.groupObjects.add(this.current);
                this.verts.clear();
            }

            @Override
            public void facet(float angle) {
                if (this.current != null) {
                    this.current.smoothingAngle = angle;
                }
            }

            @Override
            public void verticesStart() {
                this.verts.clear();
            }

            @Override
            public void vertex(float x, float y, float z) {
                this.verts.add(new float[]{x * 0.01F, y * 0.01F, z * 0.01F});
            }

            @Override
            public void face(int count, int materialId, int[] vertexIndices, float[] uvs) {
                if (this.current != null) {
                    addFace(this.current, this.verts, count, materialId, vertexIndices, uvs);
                }
            }
        });
        //本家 PolygonModel のコンストラクタは init() の直後に calcVertexNormals() を回す。
        //★これが無いと face.vertexNormals が null のままで、スクリプト描画経路
        //(drawModelGroup) が面法線だけで描く = 常にフラット陰影になる。
        //文字入りモデル (ナンバープレート等) で「変な所が影になる」の正体がこれ。
        for (GroupObject group : model.groupObjects) {
            group.calcVertexNormals(VecAccuracy.MEDIUM);
        }
        return model;
    }

    /**
     * 本家 MqoModel.parseFaceQuads は addVertex(3 - i, ...) で頂点/UV を<b>逆順</b>格納する。
     * 法線 (= CustomAnimator の左右判定 / CustomMonitor の向き) がこれに依存するため必ず一致させる。
     */
    private static void addFace(GroupObject group, List<float[]> verts,
                                int count, int materialId, int[] vertexIndices, float[] uvs) {
        Vertex[] faceVerts = new Vertex[count];
        float[] revUvs = uvs != null ? new float[count * 2] : null;
        try {
            for (int i = 0; i < count; i++) {
                int rev = count - 1 - i;
                float[] v = verts.get(vertexIndices[i]);
                faceVerts[rev] = new Vertex(v[0], v[1], v[2]);
                if (revUvs != null) {
                    revUvs[rev * 2] = uvs[i * 2];
                    revUvs[rev * 2 + 1] = uvs[i * 2 + 1];
                }
            }
        } catch (Exception e) {
            return;
        }
        Face face = new Face(faceVerts, revUvs, materialId);
        //本家はロード時に法線計算済み — スクリプト (CustomAnimator 等) は
        //face.faceNormal が非 null である前提で toVec() を呼ぶ
        face.calculateFaceNormal(VecAccuracy.LOW);
        group.faces.add(face);
    }



    private static String pathOf(Object resource) {
        if (resource instanceof jp.ngt.mccompat.ResourceLocation compat) {
            return compat.func_110623_a();
        }
        if (resource instanceof net.minecraft.resources.ResourceLocation rl) {
            return rl.getPath();
        }
        if (resource instanceof String s) {
            return s;
        }
        return null;
    }
}

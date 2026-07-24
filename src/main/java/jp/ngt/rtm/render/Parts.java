package jp.ngt.rtm.render;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 本家 jp.ngt.rtm.render.Parts の移植。
 * スクリプト用法:
 *   pf = renderer.registerParts(new Parts("PF_01"));            //単一
 *   ba = renderer.registerParts(new Parts("Ba1","Ba2",...));    //複数 (可変長)
 *   pf.render(renderer); ba.containsName(objName);
 */
public class Parts {
    private final String[] names;
    private final Set<String> nameSet;
    //正規化済み (小文字) 名前 Set。GLRecorder 再生側の IdentityHashMap キャッシュに
    //毎フレーム同一インスタンスでヒットさせるため、生成時に確定して使い回す。
    private final Set<String> normalizedNames;
    private jp.ngt.ngtlib.renderer.model.GroupObject[] objs;
    /** 本家 ActionParts.id: registerParts で 1 始まりで振られるピッキング ID。 */
    public int id;

    /** 操作パーツ (ActionParts) か。本家は instanceof で判定する。 */
    public boolean isActionParts() {
        return false;
    }

    public Parts(String... names) {
        this.names = names != null ? names : new String[0];
        this.nameSet = new LinkedHashSet<>(Arrays.asList(this.names));
        Set<String> normalized = new LinkedHashSet<>();
        for (String name : this.names) {
            if (name != null) {
                normalized.add(name.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        this.normalizedNames = java.util.Collections.unmodifiableSet(normalized);
    }

    public String[] getNames() {
        return this.names;
    }

    /**
     * 本家 objNames フィールド互換 (スクリプトが直接参照する)。
     */
    public String[] getObjNames() {
        return this.names;
    }

    public void init(PartsRenderer renderer) {
    }

    /**
     * 本家 getObjects(IModelNGT) 互換 — PolygonModel からグループ取得 (CustomAnimator 等)。
     */
    public jp.ngt.ngtlib.renderer.model.GroupObject[] getObjects(Object model) {
        if (this.objs == null) {
            if (model instanceof jp.ngt.ngtlib.renderer.model.PolygonModel pm) {
                java.util.List<jp.ngt.ngtlib.renderer.model.GroupObject> found = new java.util.ArrayList<>();
                for (String name : this.names) {
                    for (jp.ngt.ngtlib.renderer.model.GroupObject obj : pm.groupObjects) {
                        if (name.equals(obj.name)) {
                            found.add(obj);
                            break;
                        }
                    }
                }
                this.objs = found.toArray(new jp.ngt.ngtlib.renderer.model.GroupObject[0]);
            } else {
                this.objs = new jp.ngt.ngtlib.renderer.model.GroupObject[0];
            }
        }
        return this.objs;
    }

    public boolean containsName(String objName) {
        return objName != null && this.nameSet.contains(objName);
    }

    public void render(PartsRenderer renderer) {
        //名前ごとの記録ではなく正規化済み Set を 1 コマンドで記録する
        //(再生側キャッシュに同一インスタンスでヒットさせる + コマンド数削減)
        renderer.recordRenderPartsSet(this.normalizedNames);
    }

    public void render(Object renderer) {
        if (renderer instanceof PartsRenderer pr) {
            this.render(pr);
        }
    }

    /** 本家ignoreMatId: マテリアルIDを無視して描くか。既定false。 */
    public boolean ignoreMatId(PartsRenderer renderer) {
        return false;
    }

}

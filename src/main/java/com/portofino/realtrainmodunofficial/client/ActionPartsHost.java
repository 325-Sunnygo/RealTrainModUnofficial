package com.portofino.realtrainmodunofficial.client;

import jp.ngt.ngtlib.renderer.model.PolygonModel;
import jp.ngt.rtm.render.Parts;

import javax.script.ScriptEngine;
import java.util.List;

/** ActionParts を持つ描画スクリプトのレンダラ共通面。 */
public interface ActionPartsHost {

    /** registerParts で登録されたパーツ (ActionParts を含む)。 */
    List<Parts> getTargetsList();

    /** グループ頂点を持つポリゴンモデル (無ければ null)。 */
    PolygonModel getPolygonModel();

    /** スクリプトエンジン (onRightClick/onRightDrag の呼び出しに使う)。 */
    ScriptEngine getScript();

    /** 本家 PartsRenderer.hittedParts / hittedEntity を更新する。 */
    default void setHoveredParts(Object entity, Parts part) {
    }
}

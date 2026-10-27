package com.portofino.realtrainmodunofficial.client;

import jp.ngt.ngtlib.renderer.model.PolygonModel;
import jp.ngt.rtm.render.Parts;

import javax.script.ScriptEngine;
import java.util.List;

/**
 * ActionParts を持つ描画スクリプトのレンダラ共通面。
 *
 * <p>RTMU には 2 つの経路がある:
 * <ul>
 *   <li>列車: {@code jp.ngt.rtm.render.PartsRenderer} (GLRecorder 経路)</li>
 *   <li>車/編成: {@code TrainScriptSystem.ScriptModelRenderer} (OpList 経路)</li>
 * </ul>
 * どちらも {@link ActionPartsPicker} から同じように扱えるようにする。
 */
public interface ActionPartsHost {

    /** registerParts で登録されたパーツ (ActionParts を含む)。 */
    List<Parts> getTargetsList();

    /** グループ頂点を持つポリゴンモデル (無ければ null)。 */
    PolygonModel getPolygonModel();

    /** スクリプトエンジン (onRightClick/onRightDrag の呼び出しに使う)。 */
    ScriptEngine getScript();

    /**
     * 本家 PartsRenderer.hittedParts / hittedEntity を更新する。
     * スクリプトが「今カーソルが当たっているパーツ」を参照できるようにする。
     */
    default void setHoveredParts(Object entity, Parts part) {
    }
}

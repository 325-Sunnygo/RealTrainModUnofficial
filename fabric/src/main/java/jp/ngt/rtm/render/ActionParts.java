package jp.ngt.rtm.render;

/**
 * 本家 jp.ngt.rtm.render.ActionParts: クリック/ドラッグで操作できるパーツ
 * (運転台のマスコン・ブレーキ弁・スイッチ類)。
 * 本家はカーソルが当たったパーツに輪郭線を描くが、RTMU は GLRecorder 経由の
 * 記録再生でピッキングパス (RenderPass.PICK) を持たないため、輪郭表示は行わず
 * 通常パーツとして描く。id / behavior はスクリプトが読むので保持する。
 */
public class ActionParts extends Parts {
    public final ActionType behavior;

    public ActionParts(ActionType behavior, String... objNames) {
        super(objNames);
        this.behavior = behavior;
    }

    @Override
    public boolean isActionParts() {
        return true;
    }
}

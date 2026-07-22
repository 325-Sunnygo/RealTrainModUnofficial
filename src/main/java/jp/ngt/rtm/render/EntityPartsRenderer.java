package jp.ngt.rtm.render;

import net.minecraft.world.entity.Entity;

/**
 * 本家 jp.ngt.rtm.render.EntityPartsRenderer の移植。
 */
public abstract class EntityPartsRenderer extends PartsRenderer {

    //スクリプトが getTick を読んだ = 描画結果が時間に依存する (TIMS モニター等) 合図。
    //VehicleScriptRenderers はこれを見て、その車両を「静止でも毎 tick 再記録」に切り替える。
    //描画は単一スレッドなので素の static で足りる (clear→render→was の順で使う)。
    private static boolean timeAccessed;

    public EntityPartsRenderer(String... par1) {
        super(par1);
    }

    public int getTick(Object entity) {
        timeAccessed = true;
        return entity instanceof Entity e ? e.tickCount : 0;
    }

    /** 1 回ぶんの描画前に呼んで検知フラグを倒す。 */
    public static void clearTimeAccessed() {
        timeAccessed = false;
    }

    /** 直前の描画中に getTick が読まれた (=時間依存) か。 */
    public static boolean wasTimeAccessed() {
        return timeAccessed;
    }
}

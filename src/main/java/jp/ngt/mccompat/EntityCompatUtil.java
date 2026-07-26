package jp.ngt.mccompat;

import net.minecraft.world.entity.Entity;

/**
 * スクリプトから渡される「エンティティらしきもの」(実 Entity / PlayerCompat /
 * CarEntity 等) を実 Entity に解決する共通ヘルパー。
 */
public final class EntityCompatUtil {
    private EntityCompatUtil() {
    }

    public static Entity unwrapEntity(Object obj) {
        if (obj instanceof Entity e) {
            return e;
        }
        if (obj instanceof PlayerCompat p) {
            return p.player;
        }
        return null;
    }

    /**
     * func_110124_au = Entity.getUniqueID。
     * <p>レシーバがバニラの Entity でシムを挟めないため、スクリプト変換がここへ回す。
     * NGTO Builder 2 がプレイヤーごとの編集状態のキーに使う。
     */
    public static java.util.UUID func_110124_au(Object entity) {
        return entity instanceof net.minecraft.world.entity.Entity e ? e.getUUID() : null;
    }

    /** func_71053_j = EntityPlayer.closeScreen (開いている画面を閉じる)。 */
    public static void func_71053_j(Object player) {
        if (player instanceof net.minecraft.world.entity.player.Player p) {
            p.closeContainer();
        }
    }
}

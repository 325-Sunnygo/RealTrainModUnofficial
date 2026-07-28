package jp.ngt.mcte.editor.filter;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

/**
 * RTMU のレールをコピー/貼り付けできるようにする補正 (neo mcte)。
 * RTMU のレールは絶対座標を NBT に持つので、素のブロックコピーでは貼り付け先で壊れる。
 */
public final class RailPaste {

    private RailPaste() {
    }

    /** 読み込ませる前に NBT の座標をずらす。レール以外の BE は何も変えない。 */
    public static void shift(CompoundTag tag, int dx, int dy, int dz) {
        if (tag == null || (dx == 0 && dy == 0 && dz == 0)) {
            return;
        }
        // 当たり判定/道床: 所属コアの座標
        if (tag.contains("spX")) {
            tag.putInt("spX", tag.getInt("spX") + dx);
            tag.putInt("spY", tag.getInt("spY") + dy);
            tag.putInt("spZ", tag.getInt("spZ") + dz);
        }
        // コア: 始点/終点
        shiftRailPosition(tag, "StartRP", dx, dy, dz);
        shiftRailPosition(tag, "EndRP", dx, dy, dz);
        // 分岐は副レールを持つ
        if (tag.contains("SubRails", Tag.TAG_LIST)) {
            ListTag list = tag.getList("SubRails", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag sub = list.getCompound(i);
                shiftRailPosition(sub, "StartRP", dx, dy, dz);
                shiftRailPosition(sub, "EndRP", dx, dy, dz);
            }
        }
    }

    private static void shiftRailPosition(CompoundTag parent, String key, int dx, int dy, int dz) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag rp = parent.getCompound(key);
        int[] pos = rp.getIntArray("BlockPos");
        if (pos.length == 3) {
            rp.putIntArray("BlockPos", new int[]{pos[0] + dx, pos[1] + dy, pos[2] + dz});
        }
    }

    /**
     * 貼り付け後の仕上げ。コアならレール形状を作り直す。
     * これを呼ばないと、NBT は正しくても見た目と当たり判定が元のままになる。
     */
    public static void finish(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be instanceof jp.ngt.rtm.rail.TileEntityLargeRailCore core) {
            try {
                core.createRailMap();
                core.setChanged();
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            } catch (Exception e) {
                jp.ngt.ngtlib.io.NGTLog.debug("[RailPaste] レール形状の再構築に失敗: " + e);
            }
        }
    }
}

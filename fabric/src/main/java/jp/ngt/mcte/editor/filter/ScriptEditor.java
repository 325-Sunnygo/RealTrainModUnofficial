package jp.ngt.mcte.editor.filter;

import jp.ngt.mcte.editor.EditorSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 独自フィルタのスクリプトへ渡す作業机 (neo mcte)。
 * 触るためのもの。
 */
public class ScriptEditor {

    private final ServerLevel level;
    private final UndoHistory.Snapshot snapshot = new UndoHistory.Snapshot();
    private final EditorSelection selection;
    private int changed;

    /** 選択範囲 (両端を含む)。スクリプトから直接読む。 */
    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;

    public ScriptEditor(ServerLevel level, EditorSelection selection) {
        this.level = level;
        this.selection = selection;
        AABB b = selection.getSelectionBox();
        this.minX = (int) Math.floor(b.minX);
        this.minY = (int) Math.floor(b.minY);
        this.minZ = (int) Math.floor(b.minZ);
        this.maxX = (int) Math.ceil(b.maxX) - 1;
        this.maxY = (int) Math.ceil(b.maxY) - 1;
        this.maxZ = (int) Math.ceil(b.maxZ) - 1;
    }

    /** 変更した数。 */
    public int getChanged() {
        return changed;
    }

    /** その位置のブロック名 (minecraft:stone 等)。 */
    public String getBlock(int x, int y, int z) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock()).toString();
    }

    public boolean isAir(int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).isAir();
    }

    /**
     * 置く。範囲外・不正なブロック名・上限超過は何もせず false。
     * Undo の記録はここで行う。
     */
    public boolean setBlock(int x, int y, int z, String id) {
        if (changed >= EditorOps.MAX_BLOCKS) {
            return false;
        }
        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
            // ★選択範囲の外は触らせない。スクリプトの書き間違いでワールドを壊さないため。
            return false;
        }
        BlockState to = parse(id);
        if (to == null) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState cur = level.getBlockState(pos);
        if (cur.equals(to)) {
            return false;
        }
        snapshot.record(level, pos, cur);
        level.setBlock(pos, to, 3);
        changed++;
        return true;
    }

    /** 範囲を丸ごと埋める。 */
    public int fill(String id) {
        int n = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (setBlock(x, y, z, id)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** 実行後に呼ぶ。変更があれば Undo へ積む。 */
    void commit() {
        if (changed > 0) {
            UndoHistory.push(selection, snapshot);
        }
    }

    private static BlockState parse(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id.trim()));
            if (b == Blocks.AIR && !id.trim().endsWith("air")) {
                return null;
            }
            return b.defaultBlockState();
        } catch (Exception e) {
            return null;
        }
    }
}

package jp.ngt.mcte.editor.filter;

import jp.ngt.mcte.editor.EditorSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.function.BiPredicate;

/**
 * フィルタから使う共通処理 (neo mcte)。
 *
 * <p>本家 {@code Editor.setBlock} / {@code record} 相当。
 * <b>Undo 用の記録はここで一括して行う</b>ので、各フィルタは記録を意識しなくてよい。
 */
public final class EditorOps {

    /** 1 回の編集で触れるブロック数の上限 (サーバを止めないため)。 */
    public static final int MAX_BLOCKS = 512 * 1024;

    private EditorOps() {
    }

    /** 選択範囲を整数ボックスとして走査する。 */
    public interface PosConsumer {
        void accept(BlockPos pos);
    }

    public static void forEach(EditorSelection editor, PosConsumer consumer) {
        AABB b = editor.getSelectionBox();
        int x0 = (int) Math.floor(b.minX), x1 = (int) Math.ceil(b.maxX) - 1;
        int y0 = (int) Math.floor(b.minY), y1 = (int) Math.ceil(b.maxY) - 1;
        int z0 = (int) Math.floor(b.minZ), z1 = (int) Math.ceil(b.maxZ) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    consumer.accept(p.set(x, y, z));
                }
            }
        }
    }

    /**
     * 置き換え。Undo 記録込み。
     *
     * @param filter 置き換えてよいか (現在の状態を見て判定)。null なら常に置き換える
     * @return 実際に変えた数
     */
    public static int replace(ServerLevel level, EditorSelection editor, BlockState to,
                              BiPredicate<BlockPos, BlockState> filter) {
        UndoHistory.Snapshot snapshot = new UndoHistory.Snapshot();
        int[] count = {0};
        forEach(editor, pos -> {
            if (count[0] >= MAX_BLOCKS) {
                return;
            }
            BlockState cur = level.getBlockState(pos);
            if (filter != null && !filter.test(pos, cur)) {
                return;
            }
            if (cur.equals(to)) {
                return;
            }
            snapshot.record(level, pos.immutable(), cur);
            level.setBlock(pos.immutable(), to, 3);
            count[0]++;
        });
        if (count[0] > 0) {
            UndoHistory.push(editor, snapshot);
        }
        return count[0];
    }
}

package jp.ngt.mcte.editor.filter;

import jp.ngt.mcte.editor.EditorSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 元に戻す (neo mcte)。本家 MCTE {@code WorldSnapshot} + {@code Editor.history} の移植。
 *
 * <p>本家はエディタ 1 個につきスタックを持っていた。ここはエディタのエンティティが
 * 消えても履歴が残るよう<b>エディタの UUID をキー</b>にしてサーバ側で持つ。
 *
 * <p>フィルタが誤爆したときに戻せないと使い物にならないので、
 * 「まず記録、それから変更」を {@link EditorOps} 側で徹底している。
 */
public final class UndoHistory {

    /** エディタ 1 個あたりの履歴数。 */
    private static final int MAX_HISTORY = 16;

    private static final Map<UUID, Deque<Snapshot>> HISTORY = new ConcurrentHashMap<>();
    /** やり直し用。元に戻した内容をここへ積む。新しい編集をしたら捨てる。 */
    private static final Map<UUID, Deque<Snapshot>> REDO = new ConcurrentHashMap<>();

    private UndoHistory() {
    }

    /** 変更前の状態。 */
    public static final class Snapshot {
        private final List<BlockPos> positions = new ArrayList<>();
        private final List<BlockState> states = new ArrayList<>();
        private final List<CompoundTag> tags = new ArrayList<>();

        public void record(ServerLevel level, BlockPos pos, BlockState state) {
            positions.add(pos);
            states.add(state);
            var be = level.getBlockEntity(pos);
            tags.add(be == null ? null : be.saveWithoutMetadata(level.registryAccess()));
        }

        public int size() {
            return positions.size();
        }

        /** いま現在のワールドの状態を、同じ位置ぶんだけ控える (やり直し用)。 */
        Snapshot captureCurrent(ServerLevel level) {
            Snapshot now = new Snapshot();
            for (BlockPos p : positions) {
                now.record(level, p, level.getBlockState(p));
            }
            return now;
        }

        /** 戻す。戻した数を返す。 */
        public int restore(ServerLevel level) {
            //後ろから戻す (同じ位置を複数回触っていた場合に最初の状態へ帰る)
            for (int i = positions.size() - 1; i >= 0; i--) {
                BlockPos p = positions.get(i);
                level.setBlock(p, states.get(i), 3);
                CompoundTag tag = tags.get(i);
                if (tag != null && level.getBlockEntity(p) != null) {
                    try {
                        level.getBlockEntity(p).loadWithComponents(tag, level.registryAccess());
                        level.getBlockEntity(p).setChanged();
                    } catch (Exception ignored) {
                        //読めなくてもブロック自体は戻っているので続ける
                    }
                }
            }
            return positions.size();
        }
    }

    public static void push(EditorSelection editor, Snapshot snapshot) {
        if (editor != null) {
            push(editor.getUUID(), snapshot);
        }
    }

    /** エディタ以外 (ペインター等) からも履歴を積めるようにする。 */
    public static void push(java.util.UUID key, Snapshot snapshot) {
        if (key == null || snapshot.size() == 0) {
            return;
        }
        //新しい編集をしたら、やり直しの先は無くなる (一般的な編集履歴の作法)
        REDO.remove(key);
        Deque<Snapshot> stack = HISTORY.computeIfAbsent(key, k -> new ArrayDeque<>());
        stack.push(snapshot);
        while (stack.size() > MAX_HISTORY) {
            stack.removeLast();
        }
    }

    /** 直前の編集を戻す。戻したブロック数。無ければ 0。 */
    public static int undo(ServerLevel level, EditorSelection editor) {
        return editor == null ? 0 : undo(level, editor.getUUID());
    }

    public static int undo(ServerLevel level, java.util.UUID key) {
        Deque<Snapshot> stack = HISTORY.get(key);
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Snapshot s = stack.pop();
        //戻す前の姿を控えておけば、そのまま「やり直し」になる
        Snapshot forRedo = s.captureCurrent(level);
        int n = s.restore(level);
        Deque<Snapshot> redo = REDO.computeIfAbsent(key, k -> new ArrayDeque<>());
        redo.push(forRedo);
        while (redo.size() > MAX_HISTORY) {
            redo.removeLast();
        }
        return n;
    }

    /** やり直す。やり直した数。無ければ 0。 */
    public static int redo(ServerLevel level, EditorSelection editor) {
        return editor == null ? 0 : redo(level, editor.getUUID());
    }

    public static int redo(ServerLevel level, java.util.UUID key) {
        Deque<Snapshot> stack = REDO.get(key);
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Snapshot s = stack.pop();
        //やり直す前の姿を控えて、元に戻す側へ積み直す
        Snapshot back = s.captureCurrent(level);
        int n = s.restore(level);
        Deque<Snapshot> undo = HISTORY.computeIfAbsent(key, k -> new ArrayDeque<>());
        undo.push(back);
        while (undo.size() > MAX_HISTORY) {
            undo.removeLast();
        }
        return n;
    }

    public static int depth(EditorSelection editor) {
        Deque<Snapshot> stack = editor == null ? null : HISTORY.get(editor.getUUID());
        return stack == null ? 0 : stack.size();
    }

    /** エディタが消えたら履歴も捨てる (溜め込まない)。 */
    public static void forget(EditorSelection editor) {
        if (editor != null) {
            HISTORY.remove(editor.getUUID());
            REDO.remove(editor.getUUID());
        }
    }
}

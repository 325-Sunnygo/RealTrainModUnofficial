package jp.ngt.mcte.editor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤー 1 人ぶんの選択範囲 (neo mcte)。
 * ★以前は選択範囲をエンティティ (EditorEntity) として世界に置いていたが、
 * 1.21 では
 */
public class EditorSelection {

    public static final int SLOT_FILL = 0;
    public static final int SLOT_REPLACE = 1;

    private static final Map<UUID, EditorSelection> BY_PLAYER = new ConcurrentHashMap<>();

    /** そのプレイヤーの選択範囲 (無ければ作る)。 */
    public static EditorSelection of(Player player) {
        return BY_PLAYER.computeIfAbsent(player.getUUID(), EditorSelection::new);
    }

    /** そのプレイヤーの選択範囲 (無ければ null)。 */
    public static EditorSelection peek(Player player) {
        return BY_PLAYER.get(player.getUUID());
    }

    public static void clear(Player player) {
        BY_PLAYER.remove(player.getUUID());
    }

    private final UUID owner;
    private BlockPos start = BlockPos.ZERO;
    private BlockPos end = BlockPos.ZERO;
    private boolean hasEnd;
    private final ItemStack[] items = {ItemStack.EMPTY, ItemStack.EMPTY};

    private EditorSelection(UUID owner) {
        this.owner = owner;
    }

    /** Undo 履歴やクリップボードのキー。 */
    public UUID getUUID() {
        return owner;
    }

    public BlockPos getStart() {
        return start;
    }

    public BlockPos getEnd() {
        return end;
    }

    public boolean hasEnd() {
        return hasEnd;
    }

    public void setStart(BlockPos pos) {
        this.start = pos;
        this.hasEnd = false;
    }

    /** 始点だけ動かす (終点と確定状態は保つ)。 */
    public void setStartKeepEnd(BlockPos pos) {
        this.start = pos;
    }

    public void setEnd(BlockPos pos) {
        this.end = pos;
        this.hasEnd = true;
    }

    /** 選択範囲 (両端を含む)。未確定なら始点だけの 1 ブロック。 */
    public AABB getSelectionBox() {
        BlockPos a = start;
        BlockPos b = hasEnd ? end : start;
        return new AABB(
            Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
            Math.max(a.getX(), b.getX()) + 1.0D,
            Math.max(a.getY(), b.getY()) + 1.0D,
            Math.max(a.getZ(), b.getZ()) + 1.0D);
    }

    public long getVolume() {
        AABB box = getSelectionBox();
        return (long) (box.getXsize() * box.getYsize() * box.getZsize());
    }

    // ---- 埋める / 置換先のスロット (本家 ContainerEditor の 2 個) ----

    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.length ? items[slot] : ItemStack.EMPTY;
    }

    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < items.length) {
            items[slot] = stack;
        }
    }

    /** スロットに入っているブロック。無ければ null。 */
    public BlockState slotBlock(int slot) {
        ItemStack stack = getItem(slot);
        return stack.getItem() instanceof BlockItem bi ? bi.getBlock().defaultBlockState() : null;
    }
}

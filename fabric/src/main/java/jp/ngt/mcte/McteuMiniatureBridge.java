package jp.ngt.mcte;

import jp.ngt.ngtlib.block.BlockSet;
import jp.ngt.ngtlib.block.NGTObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCTEUnofficial (com.mattya.mcteunofficial) のミニチュアを NGTO Builder から使えるようにする
 * ソフト連携ブリッジ。
 * 無害に no-op で、RTMU 自前ミニチュアにフォールバックする)。
 */
public final class McteuMiniatureBridge {
    /** MCTEU のミニチュアアイテムの登録 ID。 */
    private static final String MINIATURE_ITEM_ID = "mcteunofficial:miniature";

    private McteuMiniatureBridge() {
    }

    // --- リフレクションハンドル (MCTEU 導入時のみ解決。ensureInit で 1 度だけ試行) ---
    private static boolean initialized;
    private static boolean available;
    private static Method mGetMiniatureId;   // MiniatureItem.getMiniatureId(ItemStack) -> String
    private static Method mLoadItemData;      // PlacedMiniatureManager.loadItemData(String) -> MiniatureData
    private static Field fBlocks;             // MiniatureData.blocks : List<BlockEntry>
    private static Field feX, feY, feZ;       // BlockEntry.x / y / z : int
    private static Method mToBlockState;      // BlockEntry.toBlockState() -> BlockState

    // 変換済み NGTObject NBT を miniatureId でキャッシュ (毎tick の変換を避ける)。
    // MiniatureData のインスタンスが差し替わった (編集/再同期) ときは作り直す = 陳腐化しない。
    private static final Map<String, Object[]> CACHE = new ConcurrentHashMap<>();

    /** この ItemStack が MCTEU のミニチュアアイテムか (登録 ID で判定、MCTEU 未導入でも安全)。 */
    public static boolean isMiniature(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && MINIATURE_ITEM_ID.equals(id.toString());
    }

    /**
     * MCTEU ミニチュアの中身を、NGTO Builder が読める NGTObject 形式の CompoundTag
     * ({ObjId,SizeX,SizeY,SizeZ,OrigX/Y/Z,BlocksData:[...]}) に変換して返す。
     * MCTEU 未導入 / データ未解決 (マルチで未同期等) / 変換失敗なら null
     * (呼び出し側は通常の NBT にフォールバックする)。
     */
    public static CompoundTag getBlocksDataTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        net.minecraft.resources.ResourceLocation rid = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String ridStr = rid == null ? "null" : rid.toString();
        boolean mini = MINIATURE_ITEM_ID.equals(ridStr);
        boolean avail = ensureInit();
        if (!mini || !avail) {
            return null;
        }
        try {
            String id = (String) mGetMiniatureId.invoke(null, stack);
            Object data = (id == null || id.isEmpty()) ? null : mLoadItemData.invoke(null, id);
            if (id == null || id.isEmpty() || data == null) {
                return null;
            }
            Object[] cached = CACHE.get(id);
            if (cached != null && cached[0] == data) {
                return (CompoundTag) cached[1];
            }
            CompoundTag tag = convert(data);
            if (tag != null) {
                CACHE.put(id, new Object[]{data, tag});
            }
            return tag;
        } catch (Throwable t) {
            return null;
        }
    }

    /** MCTEU の MiniatureData を RTMU の NGTObject NBT へ変換する。 */
    private static CompoundTag convert(Object miniatureData) throws Exception {
        List<?> blocks = (List<?>) fBlocks.get(miniatureData);
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        // 座標を 0 基準へ正規化 (MCTEU の原点規約に依存しないよう min を引き、サイズは実測)。
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Object e : blocks) {
            int x = feX.getInt(e), y = feY.getInt(e), z = feZ.getInt(e);
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int d = maxZ - minZ + 1;
        List<BlockSet> sets = new ArrayList<>(blocks.size());
        for (Object e : blocks) {
            BlockState state = (BlockState) mToBlockState.invoke(e);
            if (state == null) {
                continue;
            }
            sets.add(new BlockSet(feX.getInt(e) - minX, feY.getInt(e) - minY, feZ.getInt(e) - minZ, state, null));
        }
        if (sets.isEmpty()) {
            return null;
        }
        NGTObject obj = NGTObject.createNGTO(sets, w, h, d, 0, 0, 0);
        return obj.writeToNBT();
    }

    /** MCTEU のクラス/メンバをリフレクションで解決する (1 度だけ)。未導入なら available=false。 */
    private static synchronized boolean ensureInit() {
        if (initialized) {
            return available;
        }
        initialized = true;
        try {
            Class<?> itemCls = Class.forName("com.mattya.mcteunofficial.MiniatureItem");
            mGetMiniatureId = itemCls.getMethod("getMiniatureId", ItemStack.class);

            Class<?> mgrCls = Class.forName("com.mattya.mcteunofficial.PlacedMiniatureManager");
            mLoadItemData = mgrCls.getDeclaredMethod("loadItemData", String.class);
            mLoadItemData.setAccessible(true);

            Class<?> dataCls = Class.forName("com.mattya.mcteunofficial.MiniatureData");
            fBlocks = dataCls.getField("blocks");

            Class<?> entryCls = Class.forName("com.mattya.mcteunofficial.MiniatureData$BlockEntry");
            feX = entryCls.getField("x");
            feY = entryCls.getField("y");
            feZ = entryCls.getField("z");
            mToBlockState = entryCls.getMethod("toBlockState");

            available = true;
        } catch (Throwable t) {
            // MCTEU 未導入 or API 変更。NGTO Builder は RTMU 自前ミニチュアで動作する。
            available = false;
        }
        return available;
    }
}

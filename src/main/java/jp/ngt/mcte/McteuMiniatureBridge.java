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
 * MCTEUnofficial ({@code com.mattya.mcteunofficial}) のミニチュアを NGTO Builder から使えるようにする
 * <b>ソフト連携</b>ブリッジ。MCTEU には<b>一切コンパイル依存しない</b> (全てリフレクション、未導入なら
 * 無害に no-op で、RTMU 自前ミニチュアにフォールバックする)。
 *
 * <p>NGTO Builder のスクリプトは、手持ちアイテムの NBT に {@code "BlocksData"} (= 原作 MCTE / RTMU 自前
 * ミニチュアの {@link NGTObject} 形式) があるかで判定し、{@code ItemMiniature.getNGTObject()} で中身を取り出す。
 * MCTEU のミニチュアは全く別形式 ({@code MiniatureData}: BlockState ベースのブロックリストを {@code miniatureId}
 * で参照) なので、ここで MCTEU の {@code MiniatureData} を RTMU の {@link NGTObject} NBT ({@code "BlocksData"} を
 * 含む) へ変換して橋渡しする。{@link jp.ngt.mccompat.ItemStackCompat#func_77978_p()} から呼ばれる。
 *
 * <p>データ解決は {@code PlacedMiniatureManager.loadItemData(id)} を使う。これは MCTEU 内部のメモリキャッシュ
 * (サーバー保存 / マルチのクライアント同期の両方が入る) → {@code items/&lt;id&gt;.json} の順で解決するため、
 * サーバー (設置) とクライアント (プレビュー) の両方で機能する。
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
    // 各値は Object[]{MiniatureData, CompoundTag}。
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
     * MCTEU ミニチュアの中身を、NGTO Builder が読める {@link NGTObject} 形式の CompoundTag
     * ({@code {ObjId,SizeX,SizeY,SizeZ,OrigX/Y/Z,BlocksData:[...]}}) に変換して返す。
     * MCTEU 未導入 / データ未解決 (マルチで未同期等) / 変換失敗なら {@code null}
     * (呼び出し側は通常の NBT にフォールバックする)。
     */
    public static CompoundTag getBlocksDataTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        //--- 診断ログ (初見のアイテムID/ミニチュアIDを 1 度だけ出す。原因特定後に外す) ---
        net.minecraft.resources.ResourceLocation rid = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String ridStr = rid == null ? "null" : rid.toString();
        boolean mini = MINIATURE_ITEM_ID.equals(ridStr);
        boolean avail = ensureInit();
        if (DIAG_SEEN.add("item:" + ridStr)) {
            diag("held item id=" + ridStr + " isMiniature=" + mini + " mcteuAvailable=" + avail);
        }
        if (!mini || !avail) {
            return null;
        }
        try {
            String id = (String) mGetMiniatureId.invoke(null, stack);
            Object data = (id == null || id.isEmpty()) ? null : mLoadItemData.invoke(null, id);
            if (DIAG_SEEN.add("mini:" + id)) {
                int bc = -1;
                if (data != null) {
                    Object blocks = fBlocks.get(data);
                    bc = (blocks instanceof java.util.List<?> l) ? l.size() : -1;
                }
                diag("miniatureId='" + id + "' loadItemData=" + (data == null ? "NULL" : "ok") + " blocks=" + bc);
            }
            if (id == null || id.isEmpty() || data == null) {
                return null;
            }
            Object[] cached = CACHE.get(id);
            if (cached != null && cached[0] == data) {
                return (CompoundTag) cached[1];
            }
            CompoundTag tag = convert(data);
            if (DIAG_SEEN.add("conv:" + id)) {
                diag("converted id='" + id + "' -> " + (tag == null ? "NULL" : "BlocksData size=" + tag.getList("BlocksData", 10).size()));
            }
            if (tag != null) {
                CACHE.put(id, new Object[]{data, tag});
            }
            return tag;
        } catch (Throwable t) {
            diag("exception for id: " + t);
            return null;
        }
    }

    private static final java.util.Set<String> DIAG_SEEN = ConcurrentHashMap.newKeySet();

    private static void diag(String msg) {
    }

    /** MCTEU の {@code MiniatureData} を RTMU の NGTObject NBT へ変換する。 */
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
            diag("ensureInit OK — MCTEUnofficial detected, miniature bridge enabled");
        } catch (Throwable t) {
            // MCTEU 未導入 or API 変更。NGTO Builder は RTMU 自前ミニチュアで動作する。
            available = false;
            diag("ensureInit FAILED (MCTEU 未導入 or API 変更): " + t);
        }
        return available;
    }
}

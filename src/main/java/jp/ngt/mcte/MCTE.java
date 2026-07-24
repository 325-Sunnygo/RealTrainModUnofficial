package jp.ngt.mcte;

import net.minecraft.world.item.Item;

/**
 * MCTE (Minecraft Train Editor) のスクリプト互換。
 * NGTO Builder が MCTE.itemMiniature を参照する (無視リスト用)。
 */
@SuppressWarnings("unused")
public final class MCTE {
    private MCTE() {
    }

    public static final String MODID = "MCTE";
    public static final String VERSION = "1.7.10.16 KaizPatchX/remaster-1.21.1";

    //エディタ GUI の ID (本家の固定値)
    public static short guiIdEditor = 1700;
    public static short guiIdGenerator = 1701;
    public static short guiIdPainter = 1702;
    public static short guiIdItemMiniature = 1703;

    public static final byte KEY_EditMode = 0;
    public static final byte KEY_Clear = 6;
    public static final byte KEY_EditMenu = 7;
    public static final byte KEY_Undo = 8;

    /** 編集権限のアクション名。 */
    public static final String USE_EDITOR = "useEditor";
    /** Undo 保持数。 */
    public static int numberOfUndo;
    /** ミニチュア回転の刻み角。 */
    public static int rotationInterval = 15;

    public static Item itemMiniature;
    /** 本家 miniature: MCTE.miniature も同じアイテムを指す。 */
    public static Item miniature;

    static {
        try {
            itemMiniature = miniature =
                    com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.MINIATURE_ITEM.get();
        } catch (Throwable t) {
            jp.ngt.ngtlib.io.NGTLog.debug("[MCTE] item statics not ready: " + t);
        }
    }
}

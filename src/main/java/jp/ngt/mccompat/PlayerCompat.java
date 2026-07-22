package jp.ngt.mccompat;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 1.7.10 スクリプト互換のプレイヤーラッパー。
 * SRB3/NGTO Builder 等のパックスクリプトは entity.field_70153_n (rider) や
 * MCWrapperClient.getPlayer() で得たプレイヤーの SRG フィールド
 * (field_71071_by = inventory 等) を直接読むため、実 Player を包んで公開する。
 * 同一プレイヤーには常に同一インスタンスを返す (スクリプトの === 比較用)。
 * フィールド値は {@link #refresh()} で更新される (CarEntity の tick / MCWrapperClient から呼ぶ)。
 */
public final class PlayerCompat {
    private static final Map<Player, PlayerCompat> CACHE = new WeakHashMap<>();

    public final Player player;

    // === SRG フィールド (refresh() で更新) ===
    public double field_70165_t;//posX
    public double field_70163_u;//posY
    public double field_70161_v;//posZ
    public float field_70177_z;//yaw
    public float field_70125_A;//pitch
    public final InventoryCompat field_71071_by = new InventoryCompat();
    public WorldCompat field_70170_p;

    private PlayerCompat(Player player) {
        this.player = player;
        this.refresh();
    }

    /** 空プレイヤー (player=null)。field_71439_g を絶対 null にしないための null-object。 */
    private PlayerCompat() {
        this.player = null;
        //refresh() は呼ばない。field_71071_by は空のまま = func_70448_g() が null を返す。
    }

    /**
     * mc.player が null の瞬間 (ワールド遷移・特定の描画パス等) 用の共有インスタンス。
     * スクリプトが {@code getMinecraft().field_71439_g.field_71071_by.func_70448_g()} を
     * ノーガードで呼んでも (hi03 ATS 地上子 / Baru's Roof 等)、null 手ぶら扱いで落ちない。
     */
    public static final PlayerCompat EMPTY = new PlayerCompat();

    public static synchronized PlayerCompat of(Player player) {
        if (player == null) {
            return null;
        }
        PlayerCompat c = CACHE.get(player);
        if (c == null) {
            c = new PlayerCompat(player);
            CACHE.put(player, c);
        }
        return c;
    }

    /**
     * ラッパー/実体どちらからでも実 Player を取り出す。
     */
    public static Player unwrap(Object obj) {
        if (obj instanceof PlayerCompat c) {
            return c.player;
        }
        if (obj instanceof Player p) {
            return p;
        }
        return null;
    }

    public void refresh() {
        if (this.player == null) {
            return; //EMPTY (空プレイヤー) は更新しない
        }
        this.field_70165_t = player.getX();
        this.field_70163_u = player.getY();
        this.field_70161_v = player.getZ();
        this.field_70177_z = player.getYRot();
        this.field_70125_A = player.getXRot();
        if (this.field_70170_p == null || this.field_70170_p.getLevel() != player.level()) {
            this.field_70170_p = new WorldCompat(player.level());
        }
        this.field_71071_by.refresh(player);
    }

    // === SRG メソッド (スクリプトが直接呼ぶ) ===

    /** func_70078_a = mountEntity (1.7.10)。null で降車。 */
    public void func_70078_a(Object target) {
        if (target == null) {
            player.stopRiding();
        } else {
            net.minecraft.world.entity.Entity e = EntityCompatUtil.unwrapEntity(target);
            if (e != null) {
                player.startRiding(e, true);
            }
        }
    }

    /** func_184210_p = dismountRidingEntity (1.12) */
    public void func_184210_p() {
        player.stopRiding();
    }

    /** func_145782_y = getEntityId */
    public int func_145782_y() {
        return player.getId();
    }

    /** func_70005_c_ = getCommandSenderName */
    public String func_70005_c_() {
        return player.getName().getString();
    }

    @Override
    public String toString() {
        return "PlayerCompat[" + player.getName().getString() + "]";
    }

    /**
     * 1.7.10 InventoryPlayer 互換。field_70462_a は ItemStack ラッパーの配列
     * (スクリプトが inventory.field_70462_a[index] と添字アクセスする)。
     */
    public static final class InventoryCompat {
        /** currentItem (選択スロット) */
        public int field_70461_c;
        /** mainInventory (36 スロット) */
        public final ItemStackCompat[] field_70462_a = new ItemStackCompat[36];

        void refresh(Player player) {
            this.field_70461_c = player.getInventory().selected;
            for (int i = 0; i < 36; i++) {
                net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
                this.field_70462_a[i] = stack.isEmpty() ? null : new ItemStackCompat(stack);
            }
        }

        /**
         * func_70448_g = getCurrentItem (今持っているアイテム)。手ぶらなら null。
         *
         * <p>Baru's Roof の照明スクリプトは「設置物アイテムを持っている間だけ
         * 目印を描く」ために使う。無いと毎フレーム TypeError で落ちていた。
         */
        public ItemStackCompat func_70448_g() {
            int i = this.field_70461_c;
            return i >= 0 && i < this.field_70462_a.length ? this.field_70462_a[i] : null;
        }

        public ItemStackCompat getCurrentItem() {
            return this.func_70448_g();
        }

        /** func_70301_a = getStackInSlot(i) */
        public ItemStackCompat func_70301_a(int i) {
            return i >= 0 && i < this.field_70462_a.length ? this.field_70462_a[i] : null;
        }
    }
}

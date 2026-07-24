package jp.ngt.ngtlib.util;

import net.neoforged.fml.util.thread.EffectiveSide;

/**
 * 本家 NGTLib jp.ngt.ngtlib.util.NGTUtil の段階的移植。
 * Phase 0 時点では ObjectPool 等が必要とする最小限のみ。
 * Phase 3 (スクリプト API) で本家のメソッド群 (getSide, getMinecraft 系等) を拡充する。
 */
public final class NGTUtil {

    private NGTUtil() {
    }

    /**
     * 実効サイドがサーバーか (本家: FMLCommonHandler の effective side 判定)
     */
    public static boolean isServer() {
        return EffectiveSide.get().isServer();
    }

    public static boolean isClient() {
        return EffectiveSide.get().isClient();
    }

    /**
     * 本家: システム時間を使用
     */
    public static long getUniqueId() {
        return System.currentTimeMillis();
    }

    /**
     * 本家: クライアントプレイヤー (Client Only, スクリプト用)
     * <p>
     * ★ このクラスから {@code net.minecraft.client.*} を<b>直接参照してはいけない</b>。
     * <p>
     * 以前ここは {@code Minecraft.getInstance().player} を直接返していた。フィールドの型は
     * {@code LocalPlayer} で戻り値は {@code Player} なので、JVM の<b>検証器が代入互換性を
     * 確かめるために LocalPlayer を読み込む</b>。専用サーバーではクライアントクラスが存在
     * しないため、{@code NGTUtil} は<b>クラスのロード時点で</b> NoClassDefFoundError になり、
     * このクラスのどのメソッドも呼べなくなる (連結処理の {@code NGTUtil.reverse} が巻き添えで
     * 落ち、編成が壊れて列車が消えていた)。
     * <p>
     * メソッド解決は遅延なので「呼ばなければ安全」ではない。<b>戻り値やフィールドの型として
     * 現れるだけで検証時にロードされる</b>。そのためクライアント専用の処理は
     * {@link NGTUtilClient} 側に置き、こちらからは dist を見てリフレクションで呼ぶ。
     */
    public static net.minecraft.world.entity.player.Player getClientPlayer() {
        Object player = invokeClientOnly("getClientPlayer");
        return player instanceof net.minecraft.world.entity.player.Player p ? p : null;
    }

    /**
     * 本家: クライアントワールド (Client Only, スクリプト用)
     */
    public static Object getClientWorld() {
        return invokeClientOnly("getClientWorld");
    }

    /**
     * クライアント専用処理を {@link NGTUtilClient} に投げる。専用サーバーでは何もしない。
     * <p>
     * リフレクションなのは、このクラスの<b>バイトコードにクライアント型を一切登場させない</b>
     * ため。クラス名を文字列で持てば、専用サーバーでは NGTUtilClient がロードされない。
     */
    private static Object invokeClientOnly(String methodName) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return null;
        }
        try {
            Class<?> clientUtil = Class.forName("jp.ngt.ngtlib.util.NGTUtilClient");
            return clientUtil.getMethod(methodName).invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 本家 NGTUtil.setValueToField : リフレクションでフィールドへ書込
     * (NGTO Builder が使用。SRG/現行名どちらでも探す)
     */
    public static void setValueToField(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null) {
            return;
        }
        trySetField(target.getClass(), target, value, fieldName);
    }

    /**
     * 本家 NGTUtil.setValueToField の 4 引数版 (NGTO Builder の Wire ツールが使用):
     * {@code setValueToField(TileEntityConnectorBase.class, tileEntity, value, "modelName")}。
     * 第1引数はフィールド探索の起点クラス。そこに無ければ target の実クラス階層も探す。
     * どちらにも無ければ (RTMU では碍子=InstalledObjectBlockEntity で modelName フィールドが無い等)
     * 無害に no-op する — ワイヤー設置自体は継続する。
     */
    public static void setValueToField(Class<?> startClass, Object target, Object value, String fieldName) {
        if (target == null || fieldName == null) {
            return;
        }
        //NGTO Builder の Wire ツール: setModelName が TileEntityConnectorBase.modelName へ reflection で
        //書き込むが、RTMU の碍子は InstalledObjectBlockEntity (definitionId 管理) なのでブリッジする。
        //これが無いと Enter で置いた碍子が「選んだモデル」にならない。
        if ("modelName".equals(fieldName) && value instanceof String name
                && target instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity be) {
            be.applyScriptModelName(name);
            return;
        }
        if (trySetField(startClass, target, value, fieldName)) {
            return;
        }
        trySetField(target.getClass(), target, value, fieldName);
    }

    private static boolean trySetField(Class<?> cls, Object target, Object value, String fieldName) {
        while (cls != null) {
            try {
                java.lang.reflect.Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return true;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /**
     * 本家 NGTUtil.getValueFromField 相当
     */
    public static Object getValueFromField(Object target, String fieldName) {
        if (target == null || fieldName == null) {
            return null;
        }
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                java.lang.reflect.Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 本家 NGTUtil.reverse : 配列を逆順に
     */
    /**
     * 本家 NGTUtil.getMethod: リフレクションでメソッドを呼び、戻り値を返す。
     *
     * <p>パックはこれで <b>private なメソッド</b> (Formation.getControlCar 等) を叩く。
     * 未実装だったため、西武 2000 系のような運転台スクリプトが
     * {@code TypeError: NGTUtil.getMethod is not a function} で落ち、スクリプトごと無効化されて
     * <b>運転台のオブジェクトが全部おかしくなっていた</b>。
     *
     * <p>引数の並びは NGTLib のバージョンで違う:
     * <pre>
     *   (clazz, instance, names[], types[], args...)        1.7.10 / KaizPatchX
     *   (clazz, instance, ???, names[], types[], args...)   RTM 2.x
     * </pre>
     * どちらでも通るよう、2 番目を対象インスタンスとし、残りからメソッド名を拾う。
     */
    public static Object getMethod(Object... params) {
        if (params == null || params.length < 2 || params[1] == null) {
            return null;
        }
        Object instance = params[1];
        java.util.List<String> names = new java.util.ArrayList<>();
        for (int i = 2; i < params.length; i++) {
            collectStrings(params[i], names);
        }
        for (String name : names) {
            java.lang.reflect.Method method = findMethod(instance.getClass(), name);
            if (method != null) {
                try {
                    method.setAccessible(true);
                    return method.invoke(instance);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 本家 NGTUtil.getField: リフレクションでフィールドを読む。 */
    public static Object getField(Object... params) {
        if (params == null || params.length < 2 || params[1] == null) {
            return null;
        }
        Object instance = params[1];
        java.util.List<String> names = new java.util.ArrayList<>();
        for (int i = 2; i < params.length; i++) {
            collectStrings(params[i], names);
        }
        for (String name : names) {
            for (Class<?> c = instance.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field field = c.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(instance);
                } catch (ReflectiveOperationException ignored) {
                    //次の親クラスへ
                }
            }
        }
        return null;
    }

    /** 引数が 文字列 / 文字列配列 / JS 配列 のどれでもメソッド名を拾えるようにする。 */
    private static void collectStrings(Object obj, java.util.List<String> out) {
        if (obj instanceof String str) {
            out.add(str);
        } else if (obj instanceof Object[] array) {
            for (Object o : array) {
                collectStrings(o, out);
            }
        } else if (obj instanceof java.util.Collection<?> collection) {
            for (Object o : collection) {
                collectStrings(o, out);
            }
        } else if (obj instanceof java.util.Map<?, ?> map) {
            //Nashorn の JS 配列は Map ("0" -> 値) として渡ってくる
            for (Object o : map.values()) {
                collectStrings(o, out);
            }
        }
    }

    /** 引数なしのメソッドを、private も含めて親クラスまで探す。 */
    private static java.lang.reflect.Method findMethod(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                //次の親クラスへ
            }
        }
        return null;
    }

    public static <T> void reverse(T[] array) {
        for (int i = 0, j = array.length - 1; i < j; ++i, --j) {
            T temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }

    /**
     * 本家 NGTUtil.addArray : 配列の全要素を List に追加
     */
    public static <T> void addArray(java.util.List<T> list, T[] array) {
        java.util.Collections.addAll(list, array);
    }

    /** 本家getLightValue: 空light(昼夜補正済み)とblock lightの大きい方。 */
    public static int getLightValue(Object world, int x, int y, int z) {
        net.minecraft.world.level.Level level = jp.ngt.ngtlib.block.BlockUtil.toLevel(world);
        if (level == null) {
            return 15;
        }
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        int sky = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) - level.getSkyDarken();
        int block = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
        return Math.max(sky, block);
    }

    /** 本家contains: 配列に要素が含まれるか。 */
    public static <E> boolean contains(E[] array, E obj) {
        if (array == null) {
            return false;
        }
        for (E e : array) {
            if (java.util.Objects.equals(e, obj)) {
                return true;
            }
        }
        return false;
    }

    /** 本家getChunkLoadDistance: 描画距離(チャンク)×16。 */
    public static int getChunkLoadDistance() {
        try {
            return net.minecraft.client.Minecraft.getInstance().options.renderDistance().get() * 16;
        } catch (Throwable t) {
            return 128;
        }
    }

    public static double getChunkLoadDistanceSq() {
        int i = getChunkLoadDistance();
        return (double) i * (double) i;
    }

    /** 本家getServer。 */
    public static net.minecraft.server.MinecraftServer getServer() {
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    /** 本家isSMP: マルチプレイか。 */
    public static boolean isSMP() {
        net.minecraft.server.MinecraftServer server = getServer();
        return server == null || !server.isSingleplayer();
    }

    /** 本家openedLANWorld。 */
    public static boolean openedLANWorld() {
        net.minecraft.server.MinecraftServer server = getServer();
        return server != null && server.isSingleplayer() && server.isPublished();
    }

    /** 本家isEquippedItem: プレイヤーが指定アイテムを手に持っているか。 */
    public static boolean isEquippedItem(Object player, Object item) {
        if (player instanceof net.minecraft.world.entity.player.Player p) {
            net.minecraft.world.item.ItemStack stack = p.getMainHandItem();
            return !stack.isEmpty() && stack.getItem() == item;
        }
        return false;
    }

    public static int byteArrayToInteger(byte[] par1) {
        return java.nio.ByteBuffer.wrap(par1).asIntBuffer().get();
    }

    public static byte[] integerToByteArray(int par1) {
        return new byte[]{(byte) (par1 >>> 24), (byte) (par1 >>> 16), (byte) (par1 >>> 8), (byte) par1};
    }

    /** 本家getNewRenderType: 1.21では未使用。 */
    public static int getNewRenderType() {
        return -1;
    }

    /** 本家sendPacketToServer: RTMUでは各機能が個別に同期するため何もしない。 */
    public static void sendPacketToServer(Object packet) {
    }

    /** 本家sendPacketToClient: 同上。 */
    public static void sendPacketToClient(Object packet, Object player) {
    }

    /**
     * 旧 RTMU の JS スタブが持っていた補助 API。
     * 実クラスへ一本化するにあたり、同名で使うスクリプトが落ちないよう本体側にも用意する。
     */
    public static long getCurrentTime() {
        return System.currentTimeMillis();
    }

    /** 現在のワールド (クライアント側)。サーバーでは null。 */
    public static Object getCurrentWorld() {
        return getClientWorld();
    }

    /** 現在のプレイヤー (クライアント側)。サーバーでは null。 */
    public static Object getCurrentPlayer() {
        return getClientPlayer();
    }

    public static String getMCVersion() {
        return "1.21.1";
    }

    /** 本家には無いが旧スタブが持っていた。言語判定は行わないので常に false。 */
    public static boolean isLanguage(String lang) {
        return false;
    }

}

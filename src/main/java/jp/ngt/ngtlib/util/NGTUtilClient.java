package jp.ngt.ngtlib.util;

import jp.ngt.ngtlib.renderer.GLRecorder;
import net.minecraft.client.Minecraft;

/**
 * 本家 jp.ngt.ngtlib.util.NGTUtilClient のスクリプト互換ファサード。
 * bindTexture は GLRecorder に BIND_TEXTURE として記録され、
 * 以降の描画 (parts.render / tessellator / model.renderPart) にテクスチャ差し替えとして効く。
 */
@SuppressWarnings("unused")
public final class NGTUtilClient {
    private NGTUtilClient() {
    }

    /**
     * スクリプト互換の Minecraft ラッパーを返す (field_71462_r/field_71439_g 等の
     * SRG フィールドを SRB3/NGTO Builder が直接読むため)。最新値へ refresh してから返す。
     */
    public static jp.ngt.mccompat.Minecraft getMinecraft() {
        jp.ngt.mccompat.Minecraft.refresh();
        return jp.ngt.mccompat.Minecraft.func_71410_x();
    }

    public static Minecraft getRealMinecraft() {
        return Minecraft.getInstance();
    }

    /**
     * クライアントプレイヤー。NGTUtil#getClientPlayer からリフレクションで呼ばれる。
     * NGTUtil 側に置くと、戻り値の型と Minecraft.player (LocalPlayer) の
     * 代入互換性を JVM の検証器が確かめるために LocalPlayer を読み込み、専用サーバーで
     * NGTUtil ごとロードできなくなる。クライアント専用のこちらに置くこと。
     */
    public static net.minecraft.world.entity.player.Player getClientPlayer() {
        return Minecraft.getInstance().player;
    }

    /** クライアントワールド。NGTUtil#getClientWorld からリフレクションで呼ばれる。 */
    public static Object getClientWorld() {
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        return level != null ? new jp.ngt.mccompat.WorldCompat(level) : null;
    }

    /**
     * スクリプトが渡すテクスチャ指定を記録する。null でデフォルトテクスチャへ復帰。
     * 解決できない型でも黙って捨ててはいけない。
     */
    public static void bindTexture(Object texture) {
        GLRecorder r = GLRecorder.active();
        if (r == null) {
            return;
        }
        if (texture == null) {
            r.bindTexture(null);
            return;
        }
        net.minecraft.resources.ResourceLocation rl = toResourceLocation(texture);
        if (rl == null) {
            jp.ngt.ngtlib.io.NGTLog.debug("[RTM] bindTexture: unresolved texture object "
                    + texture.getClass().getName() + " (" + texture + ")");
        }
        // 元パスも記録する。再生側は「モデルの素テクスチャへ戻す bind」を検出して
        // 上書きを解除する (残すと以降のパーツが全部そのテクスチャで描かれる)。
        r.bindTexture(rl, rawPathOf(texture));
    }

    /** スクリプトが渡したテクスチャの元パス (正規化済み小文字)。取れなければ null。 */
    private static String rawPathOf(Object texture) {
        String path = null;
        if (texture instanceof jp.ngt.mccompat.ResourceLocation compat) {
            path = compat.func_110623_a();
        } else if (texture instanceof net.minecraft.resources.ResourceLocation rl) {
            path = rl.getPath();
        } else if (texture instanceof CharSequence cs) {
            path = cs.toString();
        } else {
            path = invokeString(texture, "func_110623_a");
        }
        if (path == null) {
            return null;
        }
        return path.replace('\\', '/').replaceFirst("^/+", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** スクリプトが渡す各種テクスチャ表現を実 ResourceLocation へ寄せる。解決不能なら null。 */
    private static net.minecraft.resources.ResourceLocation toResourceLocation(Object texture) {
        if (texture instanceof net.minecraft.resources.ResourceLocation rl) {
            return rl;
        }
        if (texture instanceof jp.ngt.mccompat.ResourceLocation compat) {
            return resolve(compat);
        }
        if (texture instanceof CharSequence cs) {
            return fromPath(null, cs.toString());
        }
        // ScriptTexture 等、SRG アクセサ (func_110624_b=namespace / func_110623_a=path) を
        // 持つラッパー。ModelPack 由来のテクスチャはこの形で渡ってくることがある。
        String path = invokeString(texture, "func_110623_a");
        if (path != null) {
            return fromPath(invokeString(texture, "func_110624_b"), path);
        }
        return null;
    }

    private static String invokeString(Object target, String method) {
        try {
            Object v = target.getClass().getMethod(method).invoke(target);
            return v instanceof String s && !s.isEmpty() ? s : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * パック内アセットとして解決を試み、駄目なら実 RL を組む。
     * RTM パックのテクスチャ名は大文字を含むことが多く、1.21 の ResourceLocation は
     * 小文字しか許さないため、実 RL の生成は失敗しうる。例外で描画ごと落とさないこと。
     */
    private static net.minecraft.resources.ResourceLocation fromPath(String namespace, String path) {
        net.minecraft.resources.ResourceLocation packTex =
                jp.ngt.ngtlib.io.NGTFileLoader.resolvePackTexture(path);
        if (packTex != null) {
            return packTex;
        }
        String ns = namespace;
        String p = path;
        if (ns == null && p.contains(":")) {
            String[] sa = p.split(":", 2);
            ns = sa[0];
            p = sa[1];
        }
        try {
            return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    ns == null || ns.isEmpty() ? "minecraft" : ns, p);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * スクリプトが作る RL はパック内アセットを指すことが多い。
     * パックアセットとして解決済みならその動的テクスチャ、そうでなければ実 RL をそのまま返す。
     */
    private static net.minecraft.resources.ResourceLocation resolve(jp.ngt.mccompat.ResourceLocation compat) {
        net.minecraft.resources.ResourceLocation packTex =
                jp.ngt.ngtlib.io.NGTFileLoader.resolvePackTexture(compat.func_110623_a());
        return packTex != null ? packTex : compat.toReal();
    }

    public static boolean usingShader() {
        // Iris/Oculus のシェーダーパック使用中か (スクリプトが発光の描き方を切り替える)
        return com.portofino.realtrainmodunofficial.client.ShaderCompat.isShaderPackInUse();
    }

    /** 本家getLightValue。 */
    public static int getLightValue(Object world, int x, int y, int z) {
        return jp.ngt.ngtlib.util.NGTUtil.getLightValue(world, x, y, z);
    }

    /** 本家playSound: クライアントローカルで音を鳴らす。 */
    public static void playSound(Object soundName, float volume, float pitch) {
        try {
            String name = String.valueOf(soundName);
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(name);
            if (rl == null) {
                return;
            }
            net.minecraft.sounds.SoundEvent ev = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(rl);
            net.minecraft.client.Minecraft mc = getRealMinecraft();
            if (mc.player != null) {
                mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), ev,
                    net.minecraft.sounds.SoundSource.MASTER, volume, pitch, false);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 本家checkGLError: no-op。 */
    public static void checkGLError(String msg) {
    }

}

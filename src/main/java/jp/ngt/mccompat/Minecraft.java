package jp.ngt.mccompat;

/**
 * パックスクリプト互換: 1.12 SRG の Minecraft.func_71410_x().func_110434_K() 系。
 * (RTMCore.VERSION に "1.7.10" を含むため通常は旧パスが使われるが、保険として提供)
 */
public final class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    private static final TextureManagerCompat TEXTURE_MANAGER = new TextureManagerCompat();
    private static final LanguageManagerCompat LANGUAGE_MANAGER = new LanguageManagerCompat();

    /** currentScreen (SRG)。refresh() で更新。null なら GUI 非表示。 */
    public Object field_71462_r;
    /** thePlayer (SRG)。refresh() で更新 (PlayerCompat ラッパー)。 */
    public PlayerCompat field_71439_g;
    /** theWorld (SRG)。refresh() で更新。 */
    public WorldCompat field_71441_e;
    /**
     * entityRenderer (SRG)。500 系等の ForceLighting が
     * {@code func_78483_a(0.0)} (disableLightmap) / {@code func_78463_b(0.0)} (enableLightmap) を呼ぶ。
     * disable は no-op (直後の setLightmapMaxBrightness が全灯にする)、enable は環境光へ復帰。
     */
    public final EntityRendererCompat field_71460_t = new EntityRendererCompat();

    public static final class EntityRendererCompat {
        /** 1.7.10 disableLightmap */
        public void func_78483_a(double partialTicks) {
        }

        /** 1.7.10 enableLightmap → 環境光へ復帰 (brightness 負値 = 再生側が packedLight に戻す) */
        public void func_78463_b(double partialTicks) {
            jp.ngt.ngtlib.renderer.GLRecorder r = jp.ngt.ngtlib.renderer.GLRecorder.active();
            if (r != null) {
                r.brightness(-1);
            }
        }

        /** 1.12 disableLightmap */
        public void func_175072_h() {
        }

        /** 1.12 enableLightmap */
        public void func_180436_i() {
            func_78463_b(0.0D);
        }
    }

    private Minecraft() {
    }

    /**
     * getMinecraft
     */
    public static Minecraft func_71410_x() {
        return INSTANCE;
    }

    public static Minecraft getInstanceCompat() {
        return INSTANCE;
    }

    /**
     * クライアントの毎フレーム/毎tick 呼び出しで現在値を反映する
     * (CarRenderer / クライアント tick イベントから)。
     */
    public static void refresh() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            INSTANCE.field_71462_r = mc.screen;
            //field_71439_g (thePlayer) は<b>絶対に null にしない</b>。mc.player が null の瞬間でも
            //空プレイヤー (PlayerCompat.EMPTY) を入れる。パックの描画スクリプトは
            //getMinecraft().field_71439_g.field_71071_by.func_70448_g() をノーガードで呼ぶため、
            //null だと TypeError で毎フレーム落ちてスクリプトごと無効化されていた (hi03 ATS 地上子等)。
            if (mc.player != null) {
                INSTANCE.field_71439_g = PlayerCompat.of(mc.player);
                INSTANCE.field_71439_g.refresh();
            } else {
                INSTANCE.field_71439_g = PlayerCompat.EMPTY;
            }
            if (mc.level != null && (INSTANCE.field_71441_e == null || INSTANCE.field_71441_e.getLevel() != mc.level)) {
                INSTANCE.field_71441_e = new WorldCompat(mc.level);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * func_135016_M = getLanguageManager
     */
    public LanguageManagerCompat func_135016_M() {
        return LANGUAGE_MANAGER;
    }

    public static final class LanguageManagerCompat {
        /** func_135041_c = getCurrentLanguage */
        public LanguageCompat func_135041_c() {
            return new LanguageCompat();
        }
    }

    public static final class LanguageCompat {
        /** func_135034_a = getLanguageCode */
        public String func_135034_a() {
            try {
                return net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
            } catch (Throwable t) {
                return "en_us";
            }
        }
    }

    /**
     * getTextureManager
     */
    public TextureManagerCompat func_110434_K() {
        return TEXTURE_MANAGER;
    }

    public static final class TextureManagerCompat {
        /**
         * register (name, DynamicTexture) → ResourceLocation
         */
        public Object func_110578_a(String name, Object dynamicTexture) {
            if (dynamicTexture instanceof DynamicTexture dyn && dyn.image != null) {
                int handle = TextureUtil.func_110996_a();
                TextureUtil.func_110987_a(handle, dyn.image);
                return TextureUtil.getTexture(handle);
            }
            return null;
        }

        /**
         * bindTexture
         */
        public void func_110577_a(Object rl) {
            jp.ngt.ngtlib.util.NGTUtilClient.bindTexture(rl);
        }
    }
}

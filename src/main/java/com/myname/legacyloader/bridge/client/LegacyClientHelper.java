package com.myname.legacyloader.bridge.client;

import com.myname.legacyloader.bridge.client.audio.LegacySoundHandler;
import com.myname.legacyloader.bridge.client.resources.LegacyIResourceManager;
import com.myname.legacyloader.bridge.client.resources.LegacyResource;
import com.myname.legacyloader.bridge.client.settings.LegacyGameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import java.io.File;
import java.io.IOException;

public class LegacyClientHelper {
    public static byte[] field_71444_a = new byte[10_485_760];
    private static LegacyGameSettings legacyGameSettings;

    public static Minecraft getMinecraft() {
        return Minecraft.getInstance();
    }

    public static Minecraft func_71410_x() {
        return getMinecraft();
    }

    // field_71412_D -> GameRenderer
    public static GameRenderer getEntityRenderer(Minecraft mc) {
        return mc.gameRenderer;
    }

    // 笘・ｿｽ蜉: field_71409_v -> mcDataDir
    public static File getMcDataDir(Minecraft mc) {
        return mc.gameDirectory; // 1.20.1縺ｧ縺ｯ gameDirectory
    }
    public static LegacyGameSettings getGameSettings(Minecraft mc) {
        if (legacyGameSettings == null || legacyGameSettings.mc != mc) {
            legacyGameSettings = new LegacyGameSettings(mc, mc.gameDirectory);
        }
        return legacyGameSettings;
    }

    public static LegacySoundHandler getSoundHandler(Minecraft mc) {
        return new LegacySoundHandler(mc);
    }

    public static LegacyIResourceManager getResourceManager(Minecraft mc) {
        return new ClientResourceManagerBridge(mc);
    }

    /** 1.7.10 Minecraft.func_135016_M() (getLanguageManager) の代役。 */
    public static com.myname.legacyloader.bridge.client.resources.LegacyLanguageManager getLanguageManager(Minecraft mc) {
        return new com.myname.legacyloader.bridge.client.resources.LegacyLanguageManager();
    }

    /**
     * 1.7.10 Minecraft.func_147108_a(GuiScreen) (displayGuiScreen) の代役。
     * 引数を Object にすることでスタブ GUI 型 (GuiConfirmOpenLink 等) を渡してもバイトコード
     * 検証を通す (これを invokevirtual のまま残すと、スタブ GUI がモダン Screen と非互換で
     * VerifyError → そのブロッククラスがロード不能になり、登録が丸ごと失敗していた)。
     * 実際に現行 Screen なら表示、そうでなければ何もしない。
     */
    public static void displayGuiScreen(Minecraft mc, Object screen) {
        try {
            if (mc != null && screen instanceof net.minecraft.client.gui.screens.Screen s) {
                mc.setScreen(s);
            }
            // スタブ GUI (Web リンク確認等) は現行 Screen ではないので無視する。
        } catch (Throwable ignored) {
        }
    }

    public static LegacyIResourceManager func_110442_L(Minecraft mc) {
        return getResourceManager(mc);
    }

    public static ClientLevel getClientLevel(Minecraft mc) {
        return mc.level;
    }

    public static LocalPlayer getClientPlayer(Minecraft mc) {
        return mc.player;
    }

    public static ParticleEngine getParticleEngine(Minecraft mc) {
        return mc.particleEngine;
    }

    public static void spawnParticle(ClientLevel level, String name, double x, double y, double z,
                                     double xSpeed, double ySpeed, double zSpeed) {
        if (level == null) return;
        level.addParticle(toParticle(name), x, y, z, xSpeed, ySpeed, zSpeed);
    }

    private static ParticleOptions toParticle(String name) {
        if (name == null) return ParticleTypes.POOF;
        return switch (name) {
            case "flame" -> ParticleTypes.FLAME;
            case "smoke", "largesmoke" -> ParticleTypes.SMOKE;
            case "cloud" -> ParticleTypes.CLOUD;
            case "crit" -> ParticleTypes.CRIT;
            case "heart" -> ParticleTypes.HEART;
            case "bubble" -> ParticleTypes.BUBBLE;
            case "splash" -> ParticleTypes.SPLASH;
            default -> ParticleTypes.POOF;
        };
    }

    private record ClientResourceManagerBridge(Minecraft minecraft) implements LegacyIResourceManager {
        @Override
        public LegacyResource getResource(ResourceLocation location) throws IOException {
            return func_110536_a(location);
        }

        @Override
        public LegacyResource func_110536_a(ResourceLocation location) throws IOException {
            Resource resource = minecraft.getResourceManager().getResource(location)
                    .orElseThrow(() -> new IOException("Missing resource: " + location));
            return new LegacyResource.Wrapped(resource, location);
        }
    }
}

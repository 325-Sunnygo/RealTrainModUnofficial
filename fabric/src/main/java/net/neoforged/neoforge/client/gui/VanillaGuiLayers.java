package net.neoforged.neoforge.client.gui;

import net.minecraft.resources.ResourceLocation;

/** シム: RenderGuiLayerEvent の getName 比較に使う識別子だけ提供。 */
public final class VanillaGuiLayers {
    public static final ResourceLocation CHAT = ResourceLocation.withDefaultNamespace("chat");
    public static final ResourceLocation EXPERIENCE_BAR = ResourceLocation.withDefaultNamespace("experience_bar");
    public static final ResourceLocation EXPERIENCE_LEVEL = ResourceLocation.withDefaultNamespace("experience_level");
    public static final ResourceLocation HOTBAR = ResourceLocation.withDefaultNamespace("hotbar");
    public static final ResourceLocation CROSSHAIR = ResourceLocation.withDefaultNamespace("crosshair");

    private VanillaGuiLayers() {
    }
}

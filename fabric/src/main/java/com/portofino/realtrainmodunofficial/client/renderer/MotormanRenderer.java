package com.portofino.realtrainmodunofficial.client.renderer;

import jp.ngt.rtm.entity.npc.EntityMotorman;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Calendar;

/**
 * 本家 jp.ngt.rtm.entity.npc.RenderNPC の運転士ぶんの移植。
 * バニラのプレイヤーモデル + 同梱の運転士スキン (assets/rtm/textures/motorman.png)。
 *
 * <p>本家の季節スキンも移植: 1/1〜1/3 は獅子舞、12/24〜12/26 はサンタ。
 * (起動時に一度だけ判定 — 軽量)
 */
@OnlyIn(Dist.CLIENT)
public class MotormanRenderer extends MobRenderer<EntityMotorman, PlayerModel<EntityMotorman>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("rtm", "textures/motorman.png");
    private static final ResourceLocation TEX_SANTA =
            ResourceLocation.fromNamespaceAndPath("rtm", "textures/motorman_santa.png");
    private static final ResourceLocation TEX_SHISHI =
            ResourceLocation.fromNamespaceAndPath("rtm", "textures/motorman_shishi.png");

    private static final ResourceLocation ACTIVE_TEXTURE = pickSeasonalTexture();

    public MotormanRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    private static ResourceLocation pickSeasonalTexture() {
        Calendar calendar = Calendar.getInstance();
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        if (month == 1 && day >= 1 && day <= 3) {
            return TEX_SHISHI; //本家: 正月は獅子舞
        } else if (month == 12 && day >= 24 && day <= 26) {
            return TEX_SANTA; //本家: クリスマスはサンタ
        }
        return TEXTURE;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityMotorman entity) {
        //GUI で選択したスキン (entityData で同期)。"" は既定 (季節スキン)。
        String skin = entity.getSkin();
        if (skin.isEmpty()) {
            return ACTIVE_TEXTURE;
        }
        return switch (skin) {
            case "default" -> TEXTURE;
            case "santa" -> TEX_SANTA;
            case "shishi" -> TEX_SHISHI;
            default -> MotormanSkinLoader.getOrLoad(skin); //npc_skins/ のカスタムスキン
        };
    }
}

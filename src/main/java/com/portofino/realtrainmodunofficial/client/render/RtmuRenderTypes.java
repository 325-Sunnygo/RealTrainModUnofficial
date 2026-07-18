package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * RTMU 専用の {@link RenderType}。
 * <p>
 * {@code entityTranslucent} は深度を書き込む (COLOR_DEPTH_WRITE) ため、車両のガラス越しに
 * 後方のレール等が「先にガラスが深度を書く → 後で描くレールが深度テストで消える」現象を起こす。
 * ここではガラス専用に <b>深度を書き込まない (COLOR_WRITE)</b> 半透明 RenderType を用意する。
 * ガラスは他の不透明物 (地形/レール) を全て描いた後 (遅延バッファ) に描くので、深度を書かなくても
 * 正しく重なり、かつ後方を塞がない。両面表示 (NO_CULL) で車内側からもガラス色が見える。
 * <p>
 * シャード ({@code RENDERTYPE_ENTITY_TRANSLUCENT_SHADER} 等) は {@code RenderType} の
 * protected static メンバなので、このクラスを {@code RenderType} のサブクラスにして参照する。
 */
public final class RtmuRenderTypes extends RenderType {

    private RtmuRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                            boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    //sortOnUpload=false: 本家 1.7.10 の即時描画と同じ「提出順」で描く。
    //MC のバッファは半透明クアッドを毎フレーム視点距離でソートし直すため、同一平面の
    //重なり (屋根裏と内装天井等) の勝敗がフレームごとに入れ替わり「内装チカチカ」になる。
    //提出順 (= バッチ順 = モデル作者の順) なら毎フレーム同一で安定する。
    private static final Function<ResourceLocation, RenderType> GLASS_NO_DEPTH = Util.memoize(tex ->
        create("rtmu_glass_nodepth",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(tex, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setCullState(NO_CULL)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(true)));

    /** 深度を書き込まない半透明・両面・提出順 (ソート無し) のガラス用 RenderType。 */
    public static RenderType glassNoDepth(ResourceLocation texture) {
        return GLASS_NO_DEPTH.apply(texture);
    }

    //glassNoDepth の片面カリング版。本家 doCulling=true のモデルは半透明も片面描画するため
    //(RenderVehicleBase は glDisable(GL_CULL_FACE) を doCulling でしか外さない)、それに合わせる用。
    //深度書き込み無し・提出順は据え置きで、カリングだけ有効にする。
    private static final Function<ResourceLocation, RenderType> GLASS_NO_DEPTH_CULL = Util.memoize(tex ->
        create("rtmu_glass_nodepth_cull",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(tex, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setCullState(CULL)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(true)));

    /** {@link #glassNoDepth} の片面カリング版 (本家 doCulling=true 用)。 */
    public static RenderType glassNoDepthCull(ResourceLocation texture) {
        return GLASS_NO_DEPTH_CULL.apply(texture);
    }

    //entityTranslucent 相当 (深度書き込みあり・両面) だが提出順で描く。台車・座席など
    //「AlphaBlend だが実質不透明」なパーツの pass1 用。ソートに載らないのでチカチカしない。
    private static final Function<ResourceLocation, RenderType> TRANSLUCENT_NO_SORT = Util.memoize(tex ->
        create("rtmu_translucent_nosort",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(tex, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setCullState(NO_CULL)
                .createCompositeState(true)));

    /** 半透明・両面・深度書き込みあり・提出順 (ソート無し) の RenderType。 */
    public static RenderType translucentNoSort(ResourceLocation texture) {
        return TRANSLUCENT_NO_SORT.apply(texture);
    }
}

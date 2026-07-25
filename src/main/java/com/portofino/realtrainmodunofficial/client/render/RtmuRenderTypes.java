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
 * 用途は<b>「提出順で描く」ことだけ</b>。深度は本家どおり書き込む。
 * <p>
 * ★以前はガラスを深度非書き込み (COLOR_WRITE) にしていた。これは「ガラスを遅延バッファで
 * 全ての不透明物より後に描く」独自処理とセットの設計だったが、その遅延処理は本家に無く、
 * 車体越しにレールが透けるので撤去した。遅延が無いまま深度を書かないと、後から描かれる
 * レールがガラスや車体を突き抜けて見える。本家 pass1 は depthMask を既定 (ON) のままなので、
 * それに合わせる。
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
                .createCompositeState(true)));

    /** 半透明・両面・提出順 (ソート無し)。深度は本家どおり書き込む。 */
    public static RenderType glassNoDepth(ResourceLocation texture) {
        return GLASS_NO_DEPTH.apply(texture);
    }

    //上の片面カリング版。本家 doCulling=true のモデルは半透明も片面描画するため
    //(RenderVehicleBase は glDisable(GL_CULL_FACE) を doCulling でしか外さない)、それに合わせる用。
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
                .createCompositeState(true)));

    /** {@link #glassNoDepth} の片面カリング版 (本家 doCulling=true 用)。 */
    public static RenderType glassNoDepthCull(ResourceLocation texture) {
        return GLASS_NO_DEPTH_CULL.apply(texture);
    }

    //本家 RenderVehicleBase.renderBodyLight の i>0 (前照灯/尾灯) 相当。
    //  GLHelper.disableLighting();        → ライティング無し = 発光シェーダ
    //  GL11.glEnable(GL_BLEND);
    //  GL11.glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA);
    //  GLHelper.setLightmapMaxBrightness();
    //★本家は glDepthMask(false) を<b>呼んでいない</b>。GL ではブレンドと深度書き込みは独立なので、
    //  前照灯/尾灯パスは深度を書く。バニラの entityTranslucentEmissive は COLOR_WRITE
    //  (深度書き込み無し) なので、そのまま使うと基本テクスチャが透明で実体が
    //  ***_light1/2.png 側にある面 (0系の鼻) がライト点灯中だけ深度を書かず、
    //  後から描くレールに突き抜かれる。書き込みマスクだけ既定へ戻したものを使う。
    private static final Function<ResourceLocation, RenderType> EMISSIVE_DEPTH_WRITE = Util.memoize(tex ->
        create("rtmu_emissive_depth",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(new TextureStateShard(tex, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setOverlayState(OVERLAY)
                .createCompositeState(true)));

    /** 発光 (ライティング無し) + ブレンド + <b>深度書き込みあり</b>。本家の前照灯/尾灯パス相当。 */
    public static RenderType emissiveDepthWrite(ResourceLocation texture) {
        return EMISSIVE_DEPTH_WRITE.apply(texture);
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

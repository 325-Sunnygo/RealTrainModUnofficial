package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/** RTMU 専用の RenderType。 用途は「提出順で描く」ことだけ。深度は本家どおり書き込む。 */
public final class RtmuRenderTypes extends RenderType {


    // ★以前の経緯: 同一深度の「後勝ち」を深度バイアスで作ると、傾き比例のため遠方・浅い角度で 内装が…

    private RtmuRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                            boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    // sortOnUpload=false: 本家 1.7.10 の即時描画と同じ「提出順」で描く。
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

    // 上の片面カリング版。本家 doCulling=true のモデルは半透明も片面描画するため (Re…
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

    /** #glassNoDepth の片面カリング版 (本家 doCulling=true 用)。 */
    public static RenderType glassNoDepthCull(ResourceLocation texture) {
        return GLASS_NO_DEPTH_CULL.apply(texture);
    }

    // 本家 RenderVehicleBase.renderBodyLight の i>0 (前照灯/尾…
    private static final Function<ResourceLocation, RenderType> EMISSIVE_DEPTH_WRITE = Util.memoize(tex ->
        create("rtmu_emissive_depth",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(new TextureStateShard(tex, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setOverlayState(OVERLAY)
                .createCompositeState(true)));

    /** 発光 (ライティング無し) + ブレンド + 深度書き込みあり。本家の前照灯/尾灯パス相当。 */
    public static RenderType emissiveDepthWrite(ResourceLocation texture) {
        return EMISSIVE_DEPTH_WRITE.apply(texture);
    }

    // entityTranslucent 相当 (深度書き込みあり・両面) だが提出順で描く。
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

    // 前照灯/尾灯パス (i>0) 用。 ポリゴンオフセットを足したもの。
    private static final java.util.Map<String, Function<ResourceLocation, RenderType>> EMISSIVE_BLEND_BY_PASS =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** 前照灯/尾灯パス (i>0) 用。本家 renderBodyLight と同じ「発光 + ブレンド… */
    private static Function<ResourceLocation, RenderType> emissiveBlendFactory(int legacyPass, boolean cull) {
        return EMISSIVE_BLEND_BY_PASS.computeIfAbsent(legacyPass + "|" + cull, k -> Util.memoize(tex ->
            create("rtmu_emissive_blend_" + legacyPass + (cull ? "_cull" : "_nocull"),
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
                CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(tex, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setOverlayState(OVERLAY)
                    .setCullState(cull ? CULL : NO_CULL)
                    // ★深度は書かない (色だけ書く)。 前照灯/尾灯パスは車体と完全に同じ面を _light1/_li…
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .createCompositeState(true))));
    }

    // 室内灯パス (RenderPass.LIGHT = i==0) 用。
    private static final Function<ResourceLocation, RenderType> EMISSIVE_CUTOUT_LAYERED = Util.memoize(tex ->
        create("rtmu_emissive_cutout_layered",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                .setTextureState(new TextureStateShard(tex, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setCullState(CULL)
                .createCompositeState(true)));

    private static final Function<ResourceLocation, RenderType> EMISSIVE_CUTOUT_NO_CULL_LAYERED = Util.memoize(tex ->
        create("rtmu_emissive_cutout_nocull_layered",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                .setTextureState(new TextureStateShard(tex, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setCullState(NO_CULL)
                .createCompositeState(true)));

    /** 室内灯パス (i==0) 用。本家と同じ不透明描画に、深度だけのオフセットを足したもの。 */
    public static RenderType emissiveCutoutLayered(ResourceLocation texture, boolean cull) {
        return cull ? EMISSIVE_CUTOUT_LAYERED.apply(texture) : EMISSIVE_CUTOUT_NO_CULL_LAYERED.apply(texture);
    }

    /** 前照灯/尾灯パス (i>0) 用。発光 + ブレンド + 深度書き込み。 */
    public static RenderType emissiveBlendLayered(ResourceLocation texture, int legacyPass, boolean cull) {
        return emissiveBlendFactory(legacyPass, cull).apply(texture);
    }

    /** 半透明・両面・深度書き込みあり・提出順 (ソート無し) の RenderType。 */
    public static RenderType translucentNoSort(ResourceLocation texture) {
        return TRANSLUCENT_NO_SORT.apply(texture);
    }

    /** 不透明エンティティを三角形で描く型。シェーダーパック使用時だけ使う。 */
    private static final Function<ResourceLocation, RenderType> ENTITY_CUTOUT_TRIANGLES = Util.memoize(tex ->
        create("rtmu_entity_cutout_tri",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 1536, true, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                .setTextureState(new TextureStateShard(tex, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true)));

    /** #ENTITY_CUTOUT_TRIANGLES の両面版。 */
    private static final Function<ResourceLocation, RenderType> ENTITY_CUTOUT_NO_CULL_TRIANGLES = Util.memoize(tex ->
        create("rtmu_entity_cutout_nocull_tri",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 1536, true, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                .setTextureState(new TextureStateShard(tex, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setCullState(NO_CULL)
                .createCompositeState(true)));

    /** #ENTITY_CUTOUT_TRIANGLES 参照。シェーダー使用時の不透明描画用。 */
    public static RenderType entityCutoutTriangles(ResourceLocation texture) {
        return ENTITY_CUTOUT_TRIANGLES.apply(texture);
    }

    /** 上の両面版 (本家 doCulling=false 用)。 */
    public static RenderType entityCutoutNoCullTriangles(ResourceLocation texture) {
        return ENTITY_CUTOUT_NO_CULL_TRIANGLES.apply(texture);
    }

    /** 四角形 1 枚を三角形 2 枚に割るときの頂点順 (0,1,2 / 0,2,3)。 */
    public static final int[] TRI_ORDER = {0, 1, 2, 0, 2, 3};

    /** ガラスを深度を書かずに描く型。シェーダーパック使用時だけ使う。 */
    private static final Function<ResourceLocation, RenderType> GLASS_NO_DEPTH_WRITE = Util.memoize(tex ->
        create("rtmu_glass_no_depth_write",
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

    private static final Function<ResourceLocation, RenderType> GLASS_NO_DEPTH_WRITE_CULL = Util.memoize(tex ->
        create("rtmu_glass_no_depth_write_cull",
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

    /** #GLASS_NO_DEPTH_WRITE 参照。シェーダー使用時のガラス用 (両面)。 */
    public static RenderType glassNoDepthWrite(ResourceLocation texture) {
        return GLASS_NO_DEPTH_WRITE.apply(texture);
    }

    /** 上の片面版 (本家 doCulling=true 用)。 */
    public static RenderType glassNoDepthWriteCull(ResourceLocation texture) {
        return GLASS_NO_DEPTH_WRITE_CULL.apply(texture);
    }
    private static final java.util.Map<Boolean, Function<ResourceLocation, RenderType>> LEGACY_ALPHA =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Forge vehicle pass 1: source alpha blending and d… */
    public static RenderType legacyAlphaBlend(ResourceLocation texture, boolean cull) {
        return LEGACY_ALPHA.computeIfAbsent(cull, enabled -> Util.memoize(tex ->
                create("rtmu_legacy_alpha_" + enabled, DefaultVertexFormat.NEW_ENTITY,
                        VertexFormat.Mode.QUADS, 1536, true, false,
                        CompositeState.builder()
                                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                                .setTextureState(new TextureStateShard(tex, false, false))
                                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                                .setWriteMaskState(COLOR_WRITE)
                                .setLightmapState(LIGHTMAP)
                                .setOverlayState(OVERLAY)
                                .setCullState(enabled ? CULL : NO_CULL)
                                .createCompositeState(true)))).apply(texture);
    }

}

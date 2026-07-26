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


    /**
     * 発光パス用の<b>ごく小さい</b>ポリゴンオフセット。
     *
     * <p>pass0 と発光パスは同じ頂点・同じ行列で描いているが、<b>シェーダーが別</b>
     * (pass0 = ENTITY_CUTOUT / 前照灯・尾灯 = ENTITY_TRANSLUCENT_EMISSIVE) なので、
     * 同じ式でも最適化の違いで深度が僅かにずれ、画素ごとに勝敗が揺れる。
     * これは傾き比例の差なので、units だけのオフセットでは消えない (実測)。
     *
     * <p>ただし傾き係数を大きくすると、遠方・浅い角度で内装が車体を突き抜けて
     * 破線に見える (実測: factor -1 で発生)。ずれ自体は ULP 規模なので、
     * 桁で言えば十分すぎる小さい値にする。
     */
    private static final LayeringStateShard LIGHT_PASS_OFFSET = new LayeringStateShard(
        "rtmu_light_pass_offset",
        () -> {
            com.mojang.blaze3d.systems.RenderSystem.polygonOffset(-0.05F, -1.0F);
            com.mojang.blaze3d.systems.RenderSystem.enablePolygonOffset();
        },
        () -> {
            com.mojang.blaze3d.systems.RenderSystem.polygonOffset(0.0F, 0.0F);
            com.mojang.blaze3d.systems.RenderSystem.disablePolygonOffset();
        });

    //★以前の経緯:
    //同一深度の「後勝ち」を深度バイアスで作ると、傾き比例のため<b>遠方・浅い角度</b>で
    //内装が車体を突き抜けて破線に見える (実測)。二重描画そのものを止める方向へ変更した
    //(MqoModelLoader.setLightCoveredGroups)。

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
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
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

    //前照灯/尾灯パス (i>0) 用。本家 renderBodyLight と同じ「発光 + ブレンド + 深度書き込み」に
    //<b>ポリゴンオフセット</b>を足したもの。
    //
    //本家は即時描画なので NORMAL → LIGHT_FRONT → LIGHT_BACK が必ずこの順に GPU へ行き、
    //同じ深度でも後から描いた方が残る。1.21 はブレンド描画が RenderType 単位でまとめられるため、
    //同じ頂点・同じ深度に複数のライトパスが重なると勝敗が定まらず、ポリゴンが重なったように見える。
    //形は動かさず深度値だけずらして、確実に基本パスより手前にする。
    private static final java.util.Map<String, Function<ResourceLocation, RenderType>> EMISSIVE_BLEND_BY_PASS =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 前照灯/尾灯パス (i>0) 用。本家 renderBodyLight と同じ「発光 + ブレンド + 深度書き込み」。
     *
     * <p>★ポリゴンオフセットは掛けない。{@code glPolygonOffset(factor, units)} の実効量は
     * {@code factor × そのポリゴンのウィンドウ空間の深度勾配} を含み、<b>視線角度で毎フレーム変わる</b>。
     * 車内は床/天井/側壁が視線に対して浅い角度になるため勾配が大きく、カメラの微小な回転で
     * 引き出し量が振れて内装がちらつく。本家にオフセットは存在しない。
     *
     * <p>★{@code sortOnUpload} は false。true だと flush のたびにカメラ距離で
     * クアッドが並べ替わり、深度書き込み ON のこの型では勝敗が毎フレーム入れ替わる。
     *
     * <p>★カリングは<b>車両の doCulling を尊重</b>する。以前は NO_CULL 固定だったため、
     * 片面指定のパックでも外板の裏面が内装と同じバッファに入り、重なる面数が倍になっていた。
     */
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
                    //★深度は<b>書かない</b> (色だけ書く)。
                    //前照灯/尾灯パスは車体と<b>完全に同じ面</b>を _light1/_light2 で
                    //α0.8 ブレンドして重ねるだけの<b>上塗り</b>で、深度は pass0 が既に書いている。
                    //State_Light=2 (前照灯・尾灯) では i==1 と i==2 が<b>両方</b>走るため、
                    //同じ面に深度を書くレイヤーが 3 枚 (pass0 + 2 枚) 重なり、
                    //1.21 のバッチ描画では勝敗が定まらずちらつく
                    //(実測: State_Light=2 のときだけ内装がちらつく)。
                    //深度書き込みを止めれば重ね順は提出順で決まり、揺れなくなる。
                    //本家は即時描画で順序が保証されるので depthMask を触る必要が無かっただけで、
                    //見た目 (色の重なり) は本家と同じになる。
                    //★ポリゴンオフセットで勝たせる手は使わない。傾き比例なので遠方・浅い角度で
                    //内装が車体を突き抜けて破線に見える (実測で確認済み)。
                    .setWriteMaskState(COLOR_WRITE)
                .setLayeringState(LIGHT_PASS_OFFSET)
                    .createCompositeState(true))));
    }

    //室内灯パス (RenderPass.LIGHT = i==0) 用。
    //本家はここで GL 状態を一切変えない (通常ライティング・ブレンド無し・深度書き込みあり) が、
    //描くのは<b>本体と完全に同じ面</b>を _light0 テクスチャで置き換えたもの。
    //即時描画の本家は「後から描いた方が残る」ので問題にならないが、1.21 は RenderType 単位で
    //まとめて描くため同一深度の勝敗が定まらず<b>ちらつく</b>。
    //本家と同じく頂点は動かさず、深度値だけずらして確実に本体より手前にする。
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
                .setLayeringState(LIGHT_PASS_OFFSET)
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
                .setLayeringState(LIGHT_PASS_OFFSET)
                .createCompositeState(true)));

    /** 室内灯パス (i==0) 用。本家と同じ不透明描画に、深度だけのオフセットを足したもの。 */
    public static RenderType emissiveCutoutLayered(ResourceLocation texture, boolean cull) {
        return cull ? EMISSIVE_CUTOUT_LAYERED.apply(texture) : EMISSIVE_CUTOUT_NO_CULL_LAYERED.apply(texture);
    }

    /** 前照灯/尾灯パス (i>0) 用。発光 + ブレンド + 深度書き込み + ポリゴンオフセット。 */
    public static RenderType emissiveBlendLayered(ResourceLocation texture, int legacyPass, boolean cull) {
        return emissiveBlendFactory(legacyPass, cull).apply(texture);
    }

    /** 半透明・両面・深度書き込みあり・提出順 (ソート無し) の RenderType。 */
    public static RenderType translucentNoSort(ResourceLocation texture) {
        return TRANSLUCENT_NO_SORT.apply(texture);
    }

}

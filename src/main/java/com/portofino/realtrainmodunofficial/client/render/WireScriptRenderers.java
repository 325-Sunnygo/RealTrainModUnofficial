package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import jp.ngt.ngtlib.io.NGTFileLoader;
import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.ngtlib.renderer.GLRecorder;
import jp.ngt.ngtlib.renderer.model.Material;
import jp.ngt.ngtlib.renderer.model.ModelLoader;
import jp.ngt.ngtlib.renderer.model.PolygonModel;
import jp.ngt.ngtlib.renderer.model.TextureSet;
import jp.ngt.rtm.render.ModelObject;
import jp.ngt.rtm.render.WirePartsRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

import javax.script.ScriptEngine;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本家式の架線・架線柱スクリプト描画。
 *
 * <p>ModelWire_*.json の rendererPath (render_baru_pole_bx_f.js 等) を Nashorn で実行し、
 * renderClass ({@link WirePartsRenderer}) の renderWireStatic / renderWireDynamic を
 * 本家 RenderElectricalWiring と同じ座標系 (<b>接続元の取付点が原点</b>) で呼ぶ。
 *
 * <p>これが無かったため、架線柱パックはすべて自前の近似描画 (モデルを線に沿って等間隔で
 * 並べるだけ) に落ちていた。Baru's Pole のようにビームを Fix/Loop/Fix に分けて長さに
 * 合わせて敷き詰めるパックは、本家と違う見た目になっていた。
 *
 * <p>スクリプトが何も描かなかった場合 (未対応 API で落ちた等) は false を返し、
 * 呼び出し側の従来描画にフォールバックする。
 */
public final class WireScriptRenderers {

    private static final Map<String, Scripted> CACHE = new ConcurrentHashMap<>();
    private static final Scripted INVALID = new Scripted(null, null, null);

    private WireScriptRenderers() {
    }

    public static Scripted get(InstalledObjectDefinition def) {
        if (def == null || def.getScriptPath() == null || def.getScriptPath().isBlank()) {
            return null;
        }
        Scripted s = CACHE.computeIfAbsent(def.getId(), id -> create(def));
        return s == INVALID ? null : s;
    }

    private static Scripted create(InstalledObjectDefinition def) {
        try {
            byte[] bytes = NGTFileLoader.findAsset(def.getScriptPath());
            if (bytes == null) {
                RealTrainModUnofficial.LOGGER.warn("[wire] スクリプトが見つかりません: {} ({})",
                        def.getId(), def.getScriptPath());
                return INVALID;
            }
            //Shift_JIS のパックがあるため必ず PackTextDecoder を通す (生 UTF-8 だと構文ごと壊れる)
            String source = com.portofino.realtrainmodunofficial.util.PackTextDecoder.decodeText(bytes);
            ScriptEngine se = ScriptUtil.doScript(
                    "var GL11 = Java.type('jp.ngt.ngtlib.renderer.GL11Facade');\n"
                    + "var GL12 = GL11;\n"
                    + "var MathHelper = Java.type('jp.ngt.mccompat.MathHelper');\n"
                    //スクリプトは 1.12 の net.minecraft.util.math.BlockPos を import する。
                    //1.21 では core.BlockPos なので、あらかじめグローバルに束縛しておく。
                    + "var BlockPos = Java.type('net.minecraft.core.BlockPos');\n"
                    + com.portofino.realtrainmodunofficial.script.PackScriptSource.prepare(source));
            Object rcName = se.get("renderClass");
            if (rcName == null) {
                RealTrainModUnofficial.LOGGER.warn("[wire] renderClass がありません: {}", def.getId());
                return INVALID;
            }
            Class<?> rc = Class.forName(rcName.toString(), true, ScriptUtil.class.getClassLoader());
            Object instance;
            try {
                instance = rc.getConstructor(String[].class).newInstance(new Object[]{new String[0]});
            } catch (NoSuchMethodException e) {
                instance = rc.getDeclaredConstructor().newInstance();
            }
            if (!(instance instanceof WirePartsRenderer renderer)) {
                //架線以外の renderClass (BasicWire 等が別クラスを指す場合) は従来描画に任せる
                return INVALID;
            }
            renderer.setScript(se);
            se.put("renderer", renderer);

            //定義 (JSON) の値を本家 WireConfig 相当としてレンダラへ
            renderer.sectionLength = def.getSectionLength();
            renderer.deflectionCoefficient = def.getDeflectionCoefficient();
            renderer.smoothing = def.isSmoothing();

            List<TextureSet> sets = new ArrayList<>();
            if (def.getTextureOverrides() != null) {
                for (String path : def.getTextureOverrides().values()) {
                    int meta = path.indexOf("|ptmeta=");
                    String clean = meta >= 0 ? path.substring(0, meta) : path;
                    sets.add(new TextureSet(new Material(new jp.ngt.mccompat.ResourceLocation("minecraft", clean))));
                }
            }
            if (sets.isEmpty()) {
                sets.add(new TextureSet(new Material(null)));
            }
            ModelObject mo = new ModelObject(sets.toArray(new TextureSet[0]));
            byte[] modelBytes = NGTFileLoader.findAsset("models/" + def.getModelFile());
            if (modelBytes == null) {
                modelBytes = NGTFileLoader.findAsset(def.getModelFile());
            }
            mo.model = modelBytes != null ? ModelLoader.parse(modelBytes, def.getModelFile()) : new PolygonModel();

            renderer.init(null, mo);
            return new Scripted(renderer, mo, resolveDefaultTexture(def));
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to init wire script renderer for {}", def.getId(), t);
            return INVALID;
        }
    }

    /**
     * モデル定義の "default" テクスチャ (無ければ最初の 1 つ) をパックアセットとして解決する。
     * 架線スクリプトが bindTexture を呼ばないため、tessellator 描画の既定テクスチャに使う。
     */
    private static net.minecraft.resources.ResourceLocation resolveDefaultTexture(InstalledObjectDefinition def) {
        Map<String, String> overrides = def.getTextureOverrides();
        if (overrides == null || overrides.isEmpty()) {
            return null;
        }
        String path = overrides.get("default");
        if (path == null) {
            path = overrides.values().iterator().next();
        }
        int meta = path.indexOf("|ptmeta=");
        if (meta >= 0) {
            path = path.substring(0, meta);
        }
        return NGTFileLoader.resolvePackTexture(path);
    }

    private static final java.util.Set<String> WARNED_EMPTY = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static final class Scripted {
        private final WirePartsRenderer renderer;
        private final ModelObject modelObject;
        /**
         * モデル定義の "default" テクスチャ。<b>架線スクリプトは自分で bindTexture を呼ばない</b>
         * (本家は描画前に host 側がモデルの default テクスチャを bind してからスクリプトを呼ぶ)。
         * RenderSimpleCatenary.js は UV だけ指定して simpleCatenary.png を貼る前提なので、
         * これを tessellator 描画の既定テクスチャとして渡さないと白い板になる。
         */
        private final net.minecraft.resources.ResourceLocation defaultTexture;

        Scripted(WirePartsRenderer renderer, ModelObject modelObject,
                 net.minecraft.resources.ResourceLocation defaultTexture) {
            this.renderer = renderer;
            this.modelObject = modelObject;
            this.defaultTexture = defaultTexture;
        }

        /**
         * @param from 接続元の取付点 (ブロック隅からの相対座標)
         * @param to   接続先の取付点 (同上)
         * @return true = スクリプトが描いた (呼び出し元は従来描画をスキップする)
         */
        public boolean render(InstalledObjectBlockEntity be, net.minecraft.world.phys.Vec3 from,
                              net.minecraft.world.phys.Vec3 to, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay,
                              MqoModelLoader.MqoModel model) {
            //★model == null を弾かないこと。BasicWireBlack / SimpleCatenary の modelFile は
            //"Model_none.mqo" (モデル無し) で、架線の見た目は<b>全てスクリプトの tessellator</b>が
            //描く。ここで弾いていたため、これらの架線がスクリプトを実行されず、RTMU 独自の
            //ハードコード単線描画 (黒いたるみ線 1 本) に落ちていた。本家はトロリ線とハンガーを
            //含む板をテクスチャで描くので、見た目がまったく違っていた。
            if (this.renderer == null) {
                return false;
            }
            GLRecorder rec = new GLRecorder();
            GLRecorder.activate(rec);
            try {
                this.renderer.modelGroupNames = model != null ? model.getOriginalGroupNames() : java.util.Set.of();
                //本家 RenderElectricalWiring: 原点は<b>接続元の取付点</b>、vec は相手までの相対ベクトル
                Vec3 vec = new Vec3(to.x - from.x, to.y - from.y, to.z - from.z);
                rec.push();
                rec.translate((float) from.x, (float) from.y, (float) from.z);
                this.renderer.renderWire(be, null, vec, partialTick, 0);
                rec.pop();
            } catch (Throwable t) {
                RealTrainModUnofficial.LOGGER.warn("Wire script render failed", t);
            } finally {
                GLRecorder.deactivate();
                this.renderer.consumeScriptFailure();
            }
            //スクリプトが未対応 API で落ちて何も描けなかった場合は従来描画に戻す
            if (!rec.hasGeometry()) {
                if (!WARNED_EMPTY.contains(this.renderer.getClass().getName())) {
                    WARNED_EMPTY.add(this.renderer.getClass().getName());
                    RealTrainModUnofficial.LOGGER.warn("[wire] スクリプトが何も描きませんでした → 従来描画にフォールバック");
                }
                return false;
            }
            VehicleScriptRenderers.replay(rec, poseStack, buffer, packedLight, packedOverlay, model,
                    this.modelObject != null ? this.modelObject.model : null,
                    jp.ngt.rtm.render.RenderPass.NORMAL.id, null, this.defaultTexture);
            return true;
        }
    }
}

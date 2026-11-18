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

/** 本家式の架線・架線柱スクリプト描画。 ModelWire_*.json の rendererPat… */
public final class WireScriptRenderers {

    private static final Map<String, Scripted> CACHE = new ConcurrentHashMap<>();
    private static final Scripted INVALID = new Scripted(null, null, null);

    private WireScriptRenderers() {
    }

    /** 架線の描画器を返す。 ★スクリプトを持たない架線でも返す。 */
    public static Scripted get(InstalledObjectDefinition def) {
        if (def == null) {
            return null;
        }
        Scripted s = CACHE.computeIfAbsent(def.getId(), id -> create(def));
        return s == INVALID ? null : s;
    }

    private static Scripted create(InstalledObjectDefinition def) {
        try {
            String scriptPath = def.getScriptPath();
            boolean hasScript = scriptPath != null && !scriptPath.isBlank();
            WirePartsRenderer renderer;
            if (hasScript) {
                byte[] bytes = NGTFileLoader.findAsset(scriptPath);
                if (bytes == null) {
                    RealTrainModUnofficial.LOGGER.warn("[wire] スクリプトが見つかりません: {} ({})",
                            def.getId(), scriptPath);
                    return INVALID;
                }
                // Shift_JIS のパックがあるため必ず PackTextDecoder を通す (生 UTF-…
                String source = com.portofino.realtrainmodunofficial.util.PackTextDecoder.decodeText(bytes);
                ScriptEngine se = ScriptUtil.doScript(
                        "var GL11 = Java.type('jp.ngt.ngtlib.renderer.GL11Facade');\n"
                        + "var GL12 = GL11;\n"
                        + "var MathHelper = Java.type('jp.ngt.mccompat.MathHelper');\n"
                        // スクリプトは 1.12 の net.minecraft.util.math.BlockPos を…
                        + "var BlockPos = Java.type('" + net.minecraft.core.BlockPos.class.getName() + "');\n"
                        + com.portofino.realtrainmodunofficial.script.PackScriptSource.prepare(source, scriptPath));
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
                if (!(instance instanceof WirePartsRenderer wpr)) {
                    // 架線以外の renderClass (BasicWire 等が別クラスを指す場合) は描かない
                    return INVALID;
                }
                renderer = wpr;
                renderer.setScript(se);
                // どのパックのスクリプトが落ちたか追えるように名前を持たせる
                renderer.scriptName = def.getId() + " (" + scriptPath + ")";
                se.put("renderer", renderer);
            } else {
                renderer = new WirePartsRenderer(new String[0]);
                renderer.scriptName = def.getId() + " (スクリプト無し)";
            }

            // 定義 (JSON) の値を本家 WireConfig 相当としてレンダラへ
            renderer.sectionLength = def.getSectionLength();
            renderer.deflectionCoefficient = def.getDeflectionCoefficient();
            renderer.lengthCoefficient = def.getLengthCoefficient();
            renderer.smoothing = def.isSmoothing();

            ModelObject mo = new ModelObject(new TextureSet[0]);
            byte[] modelBytes = NGTFileLoader.findAsset("models/" + def.getModelFile());
            if (modelBytes == null) {
                modelBytes = NGTFileLoader.findAsset(def.getModelFile());
            }
            mo.model = modelBytes != null ? ModelLoader.parse(modelBytes, def.getModelFile()) : new PolygonModel();
            mo.setTextures(jp.ngt.ngtlib.renderer.model.LegacyTextureLayout.create(mo.model, def.getTextureOverrides()));

            // 本家 ModelObject: renderer.init(ModelSetBase, Model…
            jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> modelSet =
                    jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE.getScriptOnlyModelSet(
                            "wire-script:" + def.getId(), def.getDisplayName(), "ModelWire",
                            null, null, null, def.getButtonTexture());
            renderer.init(modelSet, mo);
            return new Scripted(renderer, mo, resolveDefaultTexture(def));
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to init wire script renderer for {}", def.getId(), t);
            return INVALID;
        }
    }

    /** モデル定義の "default" テクスチャ (無ければ最初の 1 つ) をパックアセットとして解… */
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

    public static final class Scripted {
        private final WirePartsRenderer renderer;
        private final ModelObject modelObject;
        /** モデル定義の "default" テクスチャ。 */
        private final net.minecraft.resources.ResourceLocation defaultTexture;

        Scripted(WirePartsRenderer renderer, ModelObject modelObject,
                 net.minecraft.resources.ResourceLocation defaultTexture) {
            this.renderer = renderer;
            this.modelObject = modelObject;
            this.defaultTexture = defaultTexture;
        }

        
        public boolean render(Object be, net.minecraft.world.phys.Vec3 from,
                              net.minecraft.world.phys.Vec3 to, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int packedLight, int packedOverlay,
                              MqoModelLoader.MqoModel model) {
            // ★model == null を弾かないこと。
            if (this.renderer == null) {
                return false;
            }
            this.renderer.modelGroupNames = model != null ? model.getOriginalGroupNames() : java.util.Set.of();
            // 本家 RenderElectricalWiring: 原点は接続元の取付点、vec は相手までの相…
            Vec3 vec = new Vec3(to.x - from.x, to.y - from.y, to.z - from.z);

            // 本家 RenderElectricalWiring.renderWire のパス構成をそのまま再現…
            boolean drawn = renderPass(jp.ngt.rtm.render.RenderPass.NORMAL, be, from, vec, partialTick,
                poseStack, buffer, packedLight, packedOverlay, model);
            if (this.modelObject != null && this.modelObject.light) {
                drawn |= renderPass(jp.ngt.rtm.render.RenderPass.LIGHT, be, from, vec, partialTick,
                    poseStack, buffer, packedLight, packedOverlay, model);
            }
            if (this.modelObject != null && this.modelObject.alphaBlend) {
                drawn |= renderPass(jp.ngt.rtm.render.RenderPass.TRANSPARENT, be, from, vec, partialTick,
                    poseStack, buffer, packedLight, packedOverlay, model);
            }

            return drawn;
        }

        /** 1 パス分を記録して再生する。本家の pass ごとの renderWire 呼び出しに対応。 */
        private boolean renderPass(jp.ngt.rtm.render.RenderPass pass, Object be,
                                   net.minecraft.world.phys.Vec3 from, Vec3 vec, float partialTick,
                                   PoseStack poseStack, MultiBufferSource buffer,
                                   int packedLight, int packedOverlay, MqoModelLoader.MqoModel model) {
            GLRecorder rec = new GLRecorder();
            GLRecorder.activate(rec);
            try {
                rec.push();
                rec.translate((float) from.x, (float) from.y, (float) from.z);
                this.renderer.renderWire(be, null, vec, partialTick, pass.id);
                rec.pop();
            } catch (Throwable t) {
                RealTrainModUnofficial.LOGGER.warn("Wire script render failed", t);
            } finally {
                GLRecorder.deactivate();
                this.renderer.consumeScriptFailure();
            }
            if (!rec.hasGeometry()) {
                return false;
            }
            // 本家は LIGHT パスでライティングを切って満照度にする
            int light = pass == jp.ngt.rtm.render.RenderPass.LIGHT
                ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : packedLight;
            VehicleScriptRenderers.replay(rec, poseStack, buffer, light, packedOverlay, model,
                    this.modelObject != null ? this.modelObject.model : null,
                    pass.id, null, this.defaultTexture);
            return true;
        }
    }
}

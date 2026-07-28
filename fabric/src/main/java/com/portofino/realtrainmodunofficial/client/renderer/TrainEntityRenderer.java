package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.ClientRenderProfiler;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.entity.TrainSeatEntity;
import com.portofino.realtrainmodunofficial.script.TrainScriptSystem;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public class TrainEntityRenderer extends EntityRenderer<TrainEntity> {
    private static ResourceLocation glowTexture;

    // 初回スポーン時に一度だけログを吐く為のセット (スパム防止)。
    private static final java.util.Set<String> LOGGED_VEHICLES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static final java.util.Set<String> LOGGED_BOGIE_VEHICLES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private static ResourceLocation getGlowTexture() {
        if (glowTexture == null) {
            glowTexture = buildGlowTexture();
        }
        return glowTexture;
    }

    private static ResourceLocation buildGlowTexture() {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
            RealTrainModUnofficial.MODID, "dynamic/effect/train_light_glow");
        NativeImage img = new NativeImage(64, 64, false);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                float dx = (x - 31.5f) / 32.0f;
                float dy = (y - 31.5f) / 32.0f;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                // Smooth power falloff: bright center → transparent edge
                float alpha = r >= 1.0f ? 0.0f : (float) Math.pow(1.0f - r, 1.5);
                int a = Math.min(255, (int)(alpha * 255));
                // NativeImage stores ABGR (A at bit24, B at bit16, G at bit8, R at bit0)
                img.setPixelRGBA(x, y, (a << 24) | 0x00FFFFFF);
            }
        }
        Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(img));
        return loc;
    }

    public TrainEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    private static int resolveTrainPackedLight(TrainEntity entity, int fallbackPackedLight) {
        if (entity == null || entity.level() == null) {
            return fallbackPackedLight;
        }
        try {
            BlockPos bodyPos = BlockPos.containing(entity.getX(), entity.getY() + 1.5D, entity.getZ());
            return LevelRenderer.getLightColor(entity.level(), bodyPos);
        } catch (Throwable ignored) {
            return fallbackPackedLight;
        }
    }

    /**
     * 本家 RenderVehicleBase.preRenderBody の移植。
     * useInteriorLighting = cfg.interiorLights != null && getLightValue(vehicle) < 7
     * if (lightState > 0 && useInteriorLighting) GLHelper.setLightmapMaxBrightness;
     */
    public static int applyInteriorLighting(net.minecraft.world.level.Level level, double x, double y, double z,
                                            boolean hasInteriorLights, int interiorLightState, int packedLight) {
        if (level == null || !hasInteriorLights || interiorLightState <= 0) {
            return packedLight;
        }
        if (ambientLightValue(level, x, y, z) >= 7) {
            return packedLight;
        }
        return net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
    }

    /** 本家 getLightValue: 空 (時刻で減衰) とブロックの明るいほう。 */
    private static int ambientLightValue(net.minecraft.world.level.Level level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y + 0.5D, z);
        int skyBrightness = Mth.clamp(15 - level.getSkyDarken(), 0, 15);
        int sky = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) * skyBrightness / 15;
        int block = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
        return Math.max(sky, block);
    }

    @Override
    public ResourceLocation getTextureLocation(TrainEntity entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public boolean shouldRender(TrainEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        // 軽量化: 車両描画距離が有効なら遠方車両を丸ごと省略 (既定 0 = 無制限)。
        if (com.portofino.realtrainmodunofficial.RtmuSettings.beyondVehicleRenderDistance(
                entity.getX(), entity.getY(), entity.getZ(), camX, camY, camZ)) {
            return false;
        }
        // Use a square box (halfLength on all horizontal axes) so the train stays
        // visible regardless of rotation. A Z-only offset disappears when the train
        // faces east/west and the camera is slightly off-center.
        double halfLength = Math.max(3.0D, entity.getTrainDistance() + 3.0D);
        AABB renderBounds = new AABB(
            entity.getX() - halfLength, entity.getY() - 1.5D, entity.getZ() - halfLength,
            entity.getX() + halfLength, entity.getY() + 5.0D, entity.getZ() + halfLength
        );
        return frustum.isVisible(renderBounds);
    }

    @Override
    public void render(TrainEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        long profilerStart = ClientRenderProfiler.begin();
        VehicleDefinition def = VehicleRegistry.getById(entity.getVehicleId());
        if (def == null) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.error("Vehicle definition not found for ID: {}", entity.getVehicleId());
            ClientRenderProfiler.endTrain(profilerStart);
            return;
        }

        com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.MqoModel model = MqoModelLoader.loadModelForVehicle(def);
        if (model == null) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn("Model is null for vehicle {}", entity.getVehicleId());
            ClientRenderProfiler.endTrain(profilerStart);
            return;
        }

        if (model.getScriptEngine() != null && entity.getScriptEngine() != model.getScriptEngine()) {
            entity.setScriptEngine(model.getScriptEngine());
        }
        if (entity.getSoundScriptEngine() == null && def.hasSoundScript()) {
            entity.setSoundScriptEngine(MqoModelLoader.loadSoundScriptForVehicle(def));
        }

        boolean failed = false;
        poseStack.pushPose();
        try {
            float renderYaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
            poseStack.mulPose(Axis.YP.rotationDegrees(renderYaw));

            // Pitch: 坂で車体が水平のまま浮かないように適用。
            // GL11.glRotated で body に独自に pitch を加える車両があり、二重回転で異常傾斜の原因になる。
            float renderPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
            renderPitch = Mth.clamp(renderPitch, -45.0F, 45.0F);
            // 坂で車体を傾ける。スクリプトの有無に関わらず適用 (RTM 標準スクリプトは
            // pitch を自前で扱わないため、こちらで適用しても二重傾斜にはならない)。
            if (Math.abs(renderPitch) > 0.001F) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-renderPitch));
            }

            // Banking/cant: same formula as TrainBogieEntityRenderer.
            float yawDelta = Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
            float horizSpeed = (float) entity.getDeltaMovement().horizontalDistance();
            float bankAngle = Mth.clamp(-yawDelta * horizSpeed * 5.0F, -10.0F, 10.0F);
            if (Math.abs(bankAngle) > 0.01F) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(bankAngle));
            }

            // Apply model offset and scale (TrainEntity Y がすでに body center に合わせて
            // RTM_VEHICLE_Y_OFFSET 分上げてあるので、ここでは +0.2 のような追加リフトをしない)
            poseStack.translate(def.getModelOffset().x, def.getModelOffset().y, def.getModelOffset().z);
            // ★ボクセルモデル (.ngto/.ngtz) はここで縮尺を掛けない。本家は
            // NGTOParts.render の中だけで glScalef(scale) するので、スクリプトが台車/ドアを
            // 置く glTranslatef はブロック単位のまま。ここで掛けると全部が中央に寄る。
            if (!model.isVoxelModel()) {
                poseStack.scale(def.getModelScale(), def.getModelScale(), def.getModelScale());
            }

            Minecraft mc = Minecraft.getInstance();
            boolean ridingThisTrain = false;
            if (mc.player != null) {
                if (mc.player.getVehicle() instanceof TrainEntity riddenTrain) {
                    ridingThisTrain = riddenTrain.getFormationHead() == entity.getFormationHead();
                } else if (mc.player.getVehicle() instanceof TrainSeatEntity seat && seat.getTrain() != null) {
                    ridingThisTrain = seat.getTrain().getFormationHead() == entity.getFormationHead();
                }
            }
            double cameraDistanceSq = mc.gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(entity.getX(), entity.getY() + 1.5D, entity.getZ());
            boolean compatibilityHeavy = def.isDoCulling() || shouldUseCompatibilityRendering(def, model);
            double nearThreshold = compatibilityHeavy ? 34.0D : 48.0D;
            double aggressiveThreshold = compatibilityHeavy ? 40.0D : 56.0D;
            double rollsignThreshold = compatibilityHeavy ? 42.0D : 64.0D;
            double lightThreshold = compatibilityHeavy ? 64.0D : 96.0D;
            boolean nearTrain = cameraDistanceSq < nearThreshold * nearThreshold;
            // ★本家は内装を距離で間引かない。
            // 室内の面そのものが見えているからで、内装を消すと光も消える。
            boolean distanceCulling =
                com.portofino.realtrainmodunofficial.RtmuSettings.vehicleRenderDistance > 0;
            boolean renderInterior = ridingThisTrain || !distanceCulling || nearTrain;
            boolean aggressiveDistanceCulling = distanceCulling && !ridingThisTrain
                && cameraDistanceSq > aggressiveThreshold * aggressiveThreshold;
            boolean renderRollsigns = ridingThisTrain || cameraDistanceSq < rollsignThreshold * rollsignThreshold;
            boolean renderLights = ridingThisTrain || cameraDistanceSq < lightThreshold * lightThreshold;
            int trainPackedLight = resolveTrainPackedLight(entity, packedLight);

            boolean modelScriptRunning = model.hasRenderScript();
            // def.hasScript covers cases where the JS engine failed to load (e.g. unsupported JS runtime)
            // but the pack was designed with a renderer script (e.g. SL packs with rod animation).
            // In that case, wheel/truck groups belong in the main model and must NOT be filtered out.
            boolean modelHasScript = modelScriptRunning || def.hasScript();
            // ★ボクセルモデル (.ngto/.ngtz) + スクリプト稼働中は、ベイク側を一切描かない。
            // 本家 NGTZModel はパーツを自分のグリッド中央へ寄せて描き、位置決めはスクリプトの
            // glTranslatef が行う。
            boolean voxelDrawnByScript = model.isVoxelModel() && modelScriptRunning;
            MqoModelLoader.GroupPredicate groupFilter = voxelDrawnByScript
                ? groupName -> false
                : groupName -> shouldRenderTrainGroup(groupName, renderInterior, aggressiveDistanceCulling, compatibilityHeavy, def, modelHasScript, modelScriptRunning);
            // 本家 BasicVehiclePartsRenderer と同じく、動かす部品は JSON の objects で決まる。
            // 名前に "door" を含むか等の推測はしない (本家に無いし、扉/panel 等の命名で外れる)。
            java.util.Map<String, java.util.List<PartsStep>> leftChains = partsChains(def.getLeftDoors());
            java.util.Map<String, java.util.List<PartsStep>> rightChains = partsChains(def.getRightDoors());
            MqoModelLoader.GroupTransform doorTransform = new MqoModelLoader.GroupTransform() {
                @Override public void apply(PoseStack stack, String groupName) {
                    applyRunningGearTransform(stack, entity, def, model, groupName, renderYaw, partialTicks);
                    if (leftChains.isEmpty() && rightChains.isEmpty()) {
                        applyDoorTransform(stack, def.getLeftDoors(), groupName, entity.doorMoveL, true);
                        applyDoorTransform(stack, def.getRightDoors(), groupName, entity.doorMoveR, false);
                        return;
                    }
                    applyPartsTransform(stack, leftChains, groupName, entity.doorMoveL);
                    applyPartsTransform(stack, rightChains, groupName, entity.doorMoveR);
                }
                @Override public boolean mayModify(String groupName) {
                    // 動かない batch は pushPose 不要 ⇒ Pose (Matrix4f+Matrix3f) 確保を回避。
                    if (groupName == null || groupName.isEmpty()) return false;
                    if (isRunningGearGroup(groupName)) return true;
                    if (leftChains.isEmpty() && rightChains.isEmpty()) {
                        // ドア定義の無いパックは名前推定フォールバックに掛かる可能性がある
                        return groupName.length() >= 4 && containsDoorWord(groupName);
                    }
                    return hasPartsTransform(leftChains, groupName) || hasPartsTransform(rightChains, groupName);
                }
            };
            // Direct GL 経路の前に他エンティティのバッチを flush し、深度バッファ整合性を保つ。
            if (buffer instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource bs) {
                bs.endBatch();
            }
            // 初回スポーン時に1回だけ詳細ログを吐く（スパム防止）。
            if (LOGGED_VEHICLES.add(entity.getVehicleId())) {
                java.util.Set<String> allGroups = model.getAllNormalizedGroupNames();
            }
            // 列車の実座標から取り直した lightmap を使用し、室内灯OFFの外装が夜に白く浮かないようにする。
            // 発光 pass は MqoModelLoader/TrainScriptSystem 側で室内灯ONの内装だけに制限する。
            int bodyLight = applyInteriorLighting(entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                !def.getInteriorLights().isEmpty(), entity.isInteriorLightOn() ? 1 : 0, trainPackedLight);
            com.portofino.realtrainmodunofficial.client.render.InteriorLighting.begin(
                def.getInteriorLights(), false, entity.level().getGameTime() * 50L);
            try {
                MqoModelLoader.renderModel(model, poseStack, buffer, bodyLight, groupFilter, doorTransform, entity);
            } finally {
                com.portofino.realtrainmodunofficial.client.render.InteriorLighting.end();
            }
            // 台車は車体と同じ変換内で描画し、各台車ごとにレール高へ補正する。
            try {
                renderBogiesInline(entity, def, model, poseStack, buffer, trainPackedLight, partialTicks);
            } catch (Throwable t) {
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER
                    .debug("Inline bogie render failed for {}: {}", entity.getVehicleId(), t.toString());
            }
            // 台車の当たり判定は TrainBogieEntity、見た目は車体レンダー内で描画する。
            if (renderRollsigns && !modelHasScript) {
                renderConfiguredRollsigns(entity, def, poseStack, buffer, trainPackedLight);
            }
            if (renderLights) {
                renderConfiguredLights(entity, def, model, poseStack, buffer, renderYaw, ridingThisTrain);
            }

        } catch (Throwable e) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.error("Failed to render model", e);
            failed = true;
        } finally {
            try { poseStack.popPose(); } catch (Throwable ignored) {}
        }
        ClientRenderProfiler.endTrain(profilerStart);
    }

    private static void applyRunningGearTransform(PoseStack poseStack, TrainEntity entity, VehicleDefinition def,
                                                  MqoModelLoader.MqoModel model, String groupName,
                                                  float renderYaw, float partialTicks) {
        if (poseStack == null || entity == null || def == null || model == null || !isRunningGearGroup(groupName)) {
            return;
        }
        if (def.getBogies().isEmpty()) {
            return;
        }
        net.minecraft.world.phys.Vec3 center = model.getGroupCenter(groupName);
        if (center == null) {
            return;
        }
        int bestIndex = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < def.getBogies().size(); i++) {
            net.minecraft.world.phys.Vec3 bogiePos = def.getBogies().get(i).position();
            double dz = center.z - bogiePos.z;
            double dx = center.x - bogiePos.x;
            double distance = dz * dz + dx * dx * 0.25D;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        if (bestIndex < 0) {
            return;
        }
        VehicleDefinition.BogieDefinition bogie = def.getBogies().get(bestIndex);
        net.minecraft.world.phys.Vec3 corrected = entity.getBogieRenderOffset(bestIndex, bogie, renderYaw, partialTicks);
        net.minecraft.world.phys.Vec3 delta = corrected.subtract(bogie.position());
        if (delta.lengthSqr() > 1.0E-8D) {
            poseStack.translate(delta.x, delta.y, delta.z);
        }
    }

    private static boolean isRunningGearGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return false;
        }
        String lower = groupName.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("bogie")
            || lower.contains("wheel")
            || lower.contains("truck")
            || lower.contains("daisya")
            || lower.contains("daisha")
            || lower.contains("sharin")
            || lower.contains("車輪")
            || lower.contains("台車");
    }

    /** 本家 EntityVehicleBase.MAX_DOOR_MOVE。開度は doorMove / これ。 */
    public static final float MAX_DOOR_MOVE = 60.0F;

    /** 本家 VehicleParts の変換 1 段 (pos を原点にした transform 列)。 */
    public record PartsStep(float[] pos, java.util.List<float[]> transforms) {
    }

    private static final java.util.Map<java.util.List<VehicleDefinition.DoorAnimationDefinition>,
            java.util.Map<String, java.util.List<PartsStep>>> PARTS_CHAIN_CACHE =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    /**
     * 本家 BasicVehiclePartsRenderer.getParts 相当。
     * 親子の入れ子を「グループ名 → 根から順に適用する変換列」へ平坦化する。
     */
    public static java.util.Map<String, java.util.List<PartsStep>> partsChains(
            java.util.List<VehicleDefinition.DoorAnimationDefinition> parts) {
        if (parts == null || parts.isEmpty()) {
            return java.util.Map.of();
        }
        return PARTS_CHAIN_CACHE.computeIfAbsent(parts, key -> {
            java.util.Map<String, java.util.List<PartsStep>> out = new java.util.HashMap<>();
            collectPartsChains(key, java.util.List.of(), out);
            return out;
        });
    }

    private static void collectPartsChains(java.util.List<VehicleDefinition.DoorAnimationDefinition> parts,
                                           java.util.List<PartsStep> parent,
                                           java.util.Map<String, java.util.List<PartsStep>> out) {
        for (VehicleDefinition.DoorAnimationDefinition part : parts) {
            java.util.List<PartsStep> chain = new java.util.ArrayList<>(parent);
            net.minecraft.world.phys.Vec3 p = part.closedPosition();
            chain.add(new PartsStep(new float[]{(float) p.x, (float) p.y, (float) p.z}, part.transforms()));
            java.util.List<PartsStep> frozen = java.util.List.copyOf(chain);
            for (String name : part.objects()) {
                // 大小どちらの綴りでも引けるようにしておく (完全一致が当たれば lowercase を作らずに済む)
                out.put(name, frozen);
                out.putIfAbsent(name.toLowerCase(java.util.Locale.ROOT), frozen);
            }
            if (!part.childParts().isEmpty()) {
                collectPartsChains(part.childParts(), frozen, out);
            }
        }
    }

    /** このグループに動く部品の定義があるか (pushPose を省くための判定)。 */
    public static boolean hasPartsTransform(java.util.Map<String, java.util.List<PartsStep>> chains, String groupName) {
        if (chains.isEmpty() || groupName == null) {
            return false;
        }
        return chains.containsKey(groupName)
                || chains.containsKey(groupName.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * 本家 BasicVehiclePartsRenderer.renderParts の忠実移植。
     * translate(pos) → transform を並び順に全て適用 → translate(-pos)
     * transform の要素数 3 = 平行移動 {x,y,z} × move
     * 4 = 回転 {angle × move, vecX, vecY, vecZ}
     */
    public static void applyPartsTransform(PoseStack poseStack,
                                           java.util.Map<String, java.util.List<PartsStep>> chains,
                                           String groupName, float progressTicks) {
        if (chains.isEmpty() || groupName == null) {
            return;
        }
        java.util.List<PartsStep> chain = chains.get(groupName);
        if (chain == null) {
            chain = chains.get(groupName.toLowerCase(java.util.Locale.ROOT));
        }
        if (chain == null) {
            return;
        }
        float move = sigmoid(Mth.clamp(progressTicks / MAX_DOOR_MOVE, 0.0F, 1.0F));
        for (PartsStep step : chain) {
            float[] pos = step.pos();
            poseStack.translate(pos[0], pos[1], pos[2]);
            for (float[] t : step.transforms()) {
                if (t.length == 3) {
                    poseStack.translate(t[0] * move, t[1] * move, t[2] * move);
                } else if (t.length == 4) {
                    float angle = t[0] * move;
                    if (angle != 0.0F && (t[1] != 0.0F || t[2] != 0.0F || t[3] != 0.0F)) {
                        poseStack.mulPose(new org.joml.Quaternionf()
                                .rotationAxis(angle * Mth.DEG_TO_RAD, t[1], t[2], t[3]));
                    }
                }
            }
            poseStack.translate(-pos[0], -pos[1], -pos[2]);
        }
    }

    /** グループ名が "door" を含むか (ドア定義の無いパック向けフォールバックの絞り込み)。 */
    static boolean containsDoorWord(String groupName) {
        for (int i = 0, n = groupName.length() - 4; i <= n; i++) {
            char c0 = groupName.charAt(i);
            if (c0 != 'd' && c0 != 'D') continue;
            char c1 = groupName.charAt(i + 1);
            char c2 = groupName.charAt(i + 2);
            char c3 = groupName.charAt(i + 3);
            if ((c1 == 'o' || c1 == 'O') && (c2 == 'o' || c2 == 'O') && (c3 == 'r' || c3 == 'R')) {
                return true;
            }
        }
        return false;
    }

    /** 本家 PartsRenderer.sigmoid (開度 0..1 のイージング)。 */
    private static float sigmoid(float x) {
        if (x == 1.0F || x == 0.0F) {
            return x;
        }
        float f0 = (x - 0.5F) * 5.0F;
        float f1 = (float) ((double) f0 / Math.sqrt(1.0D + (double) f0 * (double) f0));
        return (f1 + 1.0F) * 0.5F;
    }

    public static void applyDoorTransform(PoseStack poseStack, java.util.List<VehicleDefinition.DoorAnimationDefinition> doors,
                                           String groupName, float progressTicks, boolean leftSide) {
        if (groupName == null) {
            return;
        }
        if (doors == null || doors.isEmpty()) {
            // JSON にドア定義が無いパック向けの名前推定フォールバック (本家には無い RTMU の補助)
            applyLegacyDoorFallback(poseStack, groupName,
                    smoothstep(Mth.clamp(progressTicks / MAX_DOOR_MOVE, 0.0F, 1.0F)), leftSide);
            return;
        }
        applyPartsTransform(poseStack, partsChains(doors), groupName, progressTicks);
    }

    private static void applyLegacyDoorFallback(PoseStack poseStack, String groupName, float progress, boolean leftSide) {
        String normalized = groupName == null ? "" : groupName.toLowerCase(java.util.Locale.ROOT);
        if (!normalized.contains("door")) {
            return;
        }
        boolean isDoorLeaf = normalized.matches(".*(?:^|_)[0-9]+[lr](?:_|$).*")
            || normalized.matches(".*(?:^|_)[lr](?:_|$).*");
        if (!isDoorLeaf) {
            return;
        }
        double slide = 0.72D * progress;
        boolean opensTowardPositiveZ = normalized.matches(".*[0-9]+l(?:_|$).*")
            || normalized.contains("_l_")
            || normalized.endsWith("_l");
        // Barus Keikyu 系はドア名に train-side 情報を持たないため、まずは対象側の開閉で
        // 全ドア葉を確実に動かし、葉ごとの L/R だけでスライド方向を決める。
        poseStack.translate(0.0D, 0.0D, opensTowardPositiveZ ? slide : -slide);
    }


    private static float smoothstep(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    private static void renderConfiguredRollsigns(TrainEntity entity, VehicleDefinition def, PoseStack poseStack,
                                                  MultiBufferSource buffer, int packedLight) {
        renderConfiguredRollsigns(entity.getDestinationIndex(), entity.getRollsignAnimation(), def, poseStack, buffer, packedLight);
    }

    /**
     * 車両 JSON の rollsigns (頂点座標 + UV で定義された方向幕パネル) を描く。
     * 本家 RenderVehicleBase.renderRollsign と同じで、描画スクリプトの有無に関係なく
     * エンジン側が描く。
     */
    static void renderConfiguredRollsigns(int rawDestinationIndex, float animation, VehicleDefinition def, PoseStack poseStack,
                                          MultiBufferSource buffer, int packedLight) {
        if (def == null) {
            return;
        }
        renderSignPanels(rawDestinationIndex, animation, def.getPackName(), def.getRollsignTexture(),
                def.getRollsignNames(), def.getRollsigns(), poseStack, buffer, packedLight);
    }

    /**
     * RTMU 追加: 種別幕 (方向幕と同じ仕組みで別テクスチャ・別インデックス State_Type)。
     * 種別幕は本家に無く幕回し用の連続値も持たないので、doAnimation でも index を静的表示する
     * (animation に index をそのまま渡す = f1 が整数のままスナップ)。
     */
    static void renderConfiguredTypeSigns(int rawTypeIndex, VehicleDefinition def, PoseStack poseStack,
                                          MultiBufferSource buffer, int packedLight) {
        if (def == null) {
            return;
        }
        renderSignPanels(rawTypeIndex, (float) rawTypeIndex, def.getPackName(), def.getTypeSignTexture(),
                def.getTypeSignNames(), def.getTypeSigns(), poseStack, buffer, packedLight);
    }

    /**
     * uv+pos で定義された幕パネル群を、名前数で縦分割したテクスチャの index 番目の帯で描く。
     * 方向幕 (rollsigns) と種別幕 (typeSigns) の共通処理。
     */
    private static void renderSignPanels(int rawIndex, float animation, String packName, String texturePath,
                                         List<String> names, List<VehicleDefinition.RollsignDefinition> panels,
                                         PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (panels == null || panels.isEmpty()) {
            return;
        }
        if (texturePath == null || texturePath.isBlank()) {
            return;
        }
        ResourceLocation texture = MqoModelLoader.resolvePackTexture(packName, texturePath);
        int count = Math.max(1, names == null || names.isEmpty() ? 1 : names.size());
        int discreteIndex = Math.floorMod(rawIndex, count);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        for (VehicleDefinition.RollsignDefinition rollsign : panels) {
            float[] uv = rollsign.uv();
            if (uv == null || uv.length < 4) {
                continue;
            }
            float uMin = uv[0];
            float uMax = uv[1];
            float baseVMin = uv[2];
            float baseVMax = uv[3];
            // ★本家 RenderVehicleBase.renderRollsign と同じ:
            // f1 = doAnimation ? getRollsignAnimation (連続値で滑らかに幕回し) : 行先 index (段でスナップ)。
            float f1 = rollsign.doAnimation() ? animation : (float) discreteIndex;
            float segment = (baseVMax - baseVMin) / (float) count;
            float vMin = baseVMin + segment * f1;
            float vMax = baseVMin + segment * (f1 + 1.0F);
            // ★本家は disableLighting が false のときに GL のライティングを切って
            // ライトマップを最大にする = 幕が自己発光する。名前と逆なので注意
            // (RTMU は条件が反転していて、光るべき幕が暗いままだった)。
            int signLight = rollsign.disableLighting() ? packedLight : 0x00F000F0;

            for (float[][] quad : rollsign.pos()) {
                if (quad == null || quad.length < 4) {
                    continue;
                }
                emitRollsignQuad(mat, normalMatrix, consumer, signLight,
                    toPoint(quad[3]), toPoint(quad[2]), toPoint(quad[1]), toPoint(quad[0]),
                    uMin, vMin, uMin, vMax, uMax, vMax, uMax, vMin);
            }
        }
    }

    private static Vector3f toPoint(float[] point) {
        return new Vector3f(point[0], point[1], point[2]);
    }

    private static void emitRollsignQuad(Matrix4f mat, Matrix3f normalMatrix, VertexConsumer consumer, int packedLight,
                                         Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
                                         float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
        Vector3f edge1 = new Vector3f(p1).sub(p0);
        Vector3f edge2 = new Vector3f(p2).sub(p0);
        Vector3f normal = edge1.cross(edge2);
        if (normal.lengthSquared() <= 1.0E-8F) {
            return;
        }
        normal.normalize();

        Vector3f offset = new Vector3f(normal).mul(0.0015F);
        float nx = normalMatrix.m00() * normal.x + normalMatrix.m10() * normal.y + normalMatrix.m20() * normal.z;
        float ny = normalMatrix.m01() * normal.x + normalMatrix.m11() * normal.y + normalMatrix.m21() * normal.z;
        float nz = normalMatrix.m02() * normal.x + normalMatrix.m12() * normal.y + normalMatrix.m22() * normal.z;

        putRollsignVertex(consumer, mat, p0, offset, u0, v0, packedLight, nx, ny, nz);
        putRollsignVertex(consumer, mat, p1, offset, u1, v1, packedLight, nx, ny, nz);
        putRollsignVertex(consumer, mat, p2, offset, u2, v2, packedLight, nx, ny, nz);
        putRollsignVertex(consumer, mat, p3, offset, u3, v3, packedLight, nx, ny, nz);
    }

    private static void putRollsignVertex(VertexConsumer consumer, Matrix4f mat, Vector3f point, Vector3f offset,
                                          float u, float v, int packedLight, float nx, float ny, float nz) {
        consumer.addVertex(mat, point.x + offset.x, point.y + offset.y, point.z + offset.z)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(nx, ny, nz);
    }

    /**
     * 連結曲げ用の角度バリアント (末尾 "-NN" で NN>=10、(mx)鏡像サフィックスは先に剥がす) か。
     * 例: body-90, body-180(mx), bogie1-90, type_1-90。NN<10 (body-1 等のセクション分割) は対象外。
     */
    private static boolean isAngleBendVariant(String normalized) {
        String s = normalized.endsWith("(mx)") ? normalized.substring(0, normalized.length() - 4) : normalized;
        int dash = s.lastIndexOf('-');
        if (dash <= 0 || dash == s.length() - 1) return false;
        for (int i = dash + 1; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        try {
            return Integer.parseInt(s.substring(dash + 1)) >= 10;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean shouldRenderTrainGroup(String groupName, boolean renderInterior,
                                                  boolean aggressiveDistanceCulling, boolean compatibilityHeavy,
                                                  VehicleDefinition def, boolean hasScript, boolean scriptActuallyRunning) {
        if (groupName == null || groupName.isBlank()) {
            return true;
        }
        String normalized = groupName.toLowerCase(java.util.Locale.ROOT);
        // 連結曲げ用の角度バリアントメッシュ (body-90 / body-80(mx) / bogie1-90 等、末尾"-NN"でNN>=10)
        // は RTM が曲げ角に応じ1つだけ描く代替メッシュ。
        // 描くと翼のように散乱する。
        if (isAngleBendVariant(normalized)) {
            return false;
        }
        // ★台車/車輪グループは隠さない。
        // 出し分けない (台車は EntityBogie 側で別途描かれるだけ)。
        if (normalized.contains("shadow")) {
            return false;
        }
        // _ms / _kage - RTMパックの地面投影シャドウメッシュ (例: body_a_ms, 影ms)
        if (normalized.endsWith("_ms") || normalized.endsWith("_kage")
                || normalized.contains("_ms_") || normalized.contains("_kage_")) {
            return false;
        }
        if (normalized.endsWith("_guide") || normalized.endsWith("[obj]")
                || normalized.endsWith("_atari") || normalized.endsWith(" atari")) {
            return false;
        }
        if (!renderInterior) {
            if (normalized.contains("seat")
                || normalized.contains("chair")
                || normalized.contains("interior")
                || normalized.contains("inside")
                || normalized.contains("floor")
                || normalized.contains("ceiling")
                || normalized.contains("handrail")
                || normalized.contains("strap")
                || normalized.contains("shelf")
                || normalized.contains("cab")
                || normalized.contains("desk")
                || normalized.contains("instrument")
                || normalized.contains("panel")) {
                return false;
            }
        }
        // 台車/車輪グループは常に描画する。
        // 多発するため、確実に見えるようベイクド側からも描画する。
        if (def.hasScript() && !scriptActuallyRunning
                && def.getHeadLights().isEmpty() && def.getTailLights().isEmpty()) {
            // "light" (exact): SL front headlight ( etc.)
            // "lightf" / "lightb" / "lightr" / "lightl": EMU-style per-direction headlights
            boolean isLightGroup = normalized.equals("light") || normalized.equals("lightf") || normalized.equals("lightb")
                    || normalized.equals("lightr") || normalized.equals("lightl")
                    || normalized.startsWith("light_f") || normalized.startsWith("light_b")
                    || normalized.startsWith("lightf_") || normalized.startsWith("lightb_")
                    || normalized.endsWith("_light") || normalized.endsWith("-light");
            if (isLightGroup) {
                // Keep the group hidden only when there is no light mode context (def has no way
                // to know mode here). We allow it to pass and rely on isEmissiveGroup / light-mode
                // gating inside renderParts to control visibility per-frame.
                // Only suppress the "shadow"-level legacy groups that truly break when unscripted:
            }
        }
        if (aggressiveDistanceCulling) {
            if (normalized.contains("wiper")
                || normalized.contains("coupler")
                || normalized.contains("connector")
                || normalized.contains("hoses")
                || normalized.contains("step")
                || normalized.contains("pantograph")
                || normalized.contains("under")
                || normalized.contains("detail")) {
                return false;
            }
        }
        if (compatibilityHeavy) {
            if (aggressiveDistanceCulling && (normalized.contains("cooler")
                || normalized.contains("fan")
                || normalized.contains("antenna"))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 台車冗長描画: TrainBogieEntity が機能しない場合の保険として、本体描画と同じ
     * poseStack 状態で各 bogie 位置に bogie モデルを直接描く。poseStack は呼び出し時点で
     * 既に yaw / banking / model offset / model scale が適用済み。
     */
    private static void renderBogiesInline(TrainEntity entity, VehicleDefinition def,
                                           MqoModelLoader.MqoModel bodyModel,
                                           PoseStack poseStack, MultiBufferSource buffer,
                                           int packedLight, float partialTicks) {
        if (def == null || def.getBogies().isEmpty()) {
            return;
        }
        // 蒸気機関車など、車体MQOが車輪・台車を自前で持ちスクリプトで描く車両は、
        // RTMの読み込めない ModelBogie.class が汎用台車に置換されて二重描画/散乱する。
        // 自前走り装置がある場合は .class 置換台車を描かない (本物のMQO台車を持つEMUは対象外)。
        boolean selfDrawsRunningGear = bodyModel != null
            && (bodyModel.hasOwnWheelGroups() || bodyModel.hasOwnBogieGroups());
        float baseYaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        for (int i = 0; i < def.getBogies().size(); i++) {
            VehicleDefinition.BogieDefinition bogieDef = def.getBogies().get(i);
            if (shouldSkipInlineBogie(selfDrawsRunningGear, bogieDef)) {
                continue;
            }
            try {
                BogieRenderer.renderBogie(poseStack, i, bogieDef, def, entity, buffer, packedLight, baseYaw, partialTicks);
            } catch (Throwable ignored) {
                // 1台の台車失敗で他を巻き込まない
            }
        }
    }

    /**
     * 本家 RenderBogie は台車モデルを無条件に描く。
     * 描かないのは「モデルセットがダミー」= パック側が台車を無効にしている時だけなので、
     * ここも air/none 等のダミー指定だけを見る。
     */
    private static boolean shouldSkipInlineBogie(boolean selfDrawsRunningGear, VehicleDefinition.BogieDefinition bogieDef) {
        if (bogieDef == null || bogieDef.modelFile() == null || bogieDef.modelFile().isBlank()) {
            return true;
        }
        return BogieRenderer.isDummyBogieModel(bogieDef.modelFile());
    }

    private static boolean shouldUseCompatibilityRendering(VehicleDefinition def, MqoModelLoader.MqoModel model) {
        if (def == null || model == null) {
            return false;
        }
        int translucentBatches = model.getTranslucentBatchCount();
        int totalVertices = model.getTotalVertexCount();
        int batchCount = model.getBatchCount();
        boolean hasLegacyScript = def.getScriptPath() != null && !def.getScriptPath().isBlank();
        boolean hasManyOverlayFeatures = !def.getRollsigns().isEmpty()
            || !def.getHeadLights().isEmpty()
            || !def.getTailLights().isEmpty()
            || !def.getInteriorLights().isEmpty();
        return totalVertices >= 18_000
            || batchCount >= 160
            || translucentBatches >= 28
            || (hasLegacyScript && translucentBatches >= 12)
            || (hasManyOverlayFeatures && totalVertices >= 12_000 && batchCount >= 96);
    }

    private static void renderConfiguredLights(TrainEntity entity, VehicleDefinition def,
                                               com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.MqoModel model,
                                               PoseStack poseStack, MultiBufferSource buffer, float renderYaw,
                                               boolean ridingThisTrain) {
        // 「臨場感ライト」(放射状グローのビルボード)はユーザー要望で無効化。
        // 実際のランプ部品(モデルの発光テクスチャ)はスクリプト/モデル側で描画されるので残る。
        if (true) return;
        if (def == null) return;
        int mode = entity.getLightMode();
        boolean interiorOn = entity.isInteriorLightOn();
        if (mode <= 0 && !interiorOn) return;

        boolean singleTrainActive = def.isSingleTrain() && !entity.isConnected();
        boolean renderHeadLights = mode == 1 || mode == 3;
        boolean renderTailLights = mode == 2 || mode == 3;
        if (singleTrainActive && mode == 1) renderTailLights = true;

        // カメラの right/up ベクトルを列車ローカル座標系に変換してビルボードを実現。
        // 列車トランスフォームは Y軸回転 + オフセット + スケール。
        Vector3f billRight = new Vector3f(1, 0, 0);
        Vector3f billUp    = new Vector3f(0, 1, 0);
        Quaternionf invYaw = Axis.YP.rotationDegrees(renderYaw).conjugate();
        Minecraft.getInstance().gameRenderer.getMainCamera().rotation().transform(billRight);
        Minecraft.getInstance().gameRenderer.getMainCamera().rotation().transform(billUp);
        invYaw.transform(billRight);
        invYaw.transform(billUp);
        billRight.normalize();
        billUp.normalize();

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(getGlowTexture()));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        if (renderHeadLights) {
            for (VehicleDefinition.LightDefinition light : def.getHeadLights()) {
                renderLightGlow(consumer, mat, normalMatrix, light, true, billRight, billUp);
            }
        }
        if (renderTailLights) {
            for (VehicleDefinition.LightDefinition light : def.getTailLights()) {
                renderLightGlow(consumer, mat, normalMatrix, light, false, billRight, billUp);
            }
        }
        // 室内灯のビルボードグローは乗車中（=室内視点）のみ描画する。
        // 外から見るとビルボードが半透明窓や車体越しに滲んで外装まで光って見えるため。
        if (interiorOn && ridingThisTrain) {
            for (VehicleDefinition.LightDefinition light : def.getInteriorLights()) {
                renderLightGlow(consumer, mat, normalMatrix, light, true, billRight, billUp);
            }
        }
        if (def.getHeadLights().isEmpty() && def.getTailLights().isEmpty()
                && (model == null || !model.hasRenderScript())) {
            renderLegacyFallbackLights(entity, consumer, mat, normalMatrix, mode, billRight, billUp);
        }
    }

    private static void renderLegacyFallbackLights(TrainEntity entity, VertexConsumer consumer, Matrix4f mat,
                                                   Matrix3f normalMatrix, int mode,
                                                   Vector3f billRight, Vector3f billUp) {
        float halfLength = Math.max(3.5F, entity.getTrainDistance() - 0.45F);
        float lampY = 1.52F;
        float lampX = 0.58F;
        if (mode == 1 || mode == 3) {
            renderLightGlow(consumer, mat, normalMatrix,
                new VehicleDefinition.LightDefinition((byte) 0, 0xFFF6F0C8,
                    new net.minecraft.world.phys.Vec3(-lampX, lampY, halfLength), 0.6F, false),
                true, billRight, billUp);
            renderLightGlow(consumer, mat, normalMatrix,
                new VehicleDefinition.LightDefinition((byte) 0, 0xFFF6F0C8,
                    new net.minecraft.world.phys.Vec3(lampX, lampY, halfLength), 0.6F, false),
                true, billRight, billUp);
        }
        if (mode == 2 || mode == 3) {
            renderLightGlow(consumer, mat, normalMatrix,
                new VehicleDefinition.LightDefinition((byte) 0, 0xFFFF4040,
                    new net.minecraft.world.phys.Vec3(-lampX, lampY, -halfLength), 0.45F, true),
                false, billRight, billUp);
            renderLightGlow(consumer, mat, normalMatrix,
                new VehicleDefinition.LightDefinition((byte) 0, 0xFFFF4040,
                    new net.minecraft.world.phys.Vec3(lampX, lampY, -halfLength), 0.45F, true),
                false, billRight, billUp);
        }
    }

    // RTM 臨場感ライト: 3層の同心ビルボードクアッドで放射状グロー効果を実現。
    private static void renderLightGlow(VertexConsumer consumer, Matrix4f mat, Matrix3f normalMatrix,
                                        VehicleDefinition.LightDefinition light, boolean frontFacing,
                                        Vector3f billRight, Vector3f billUp) {
        if (light == null || light.position() == null) return;

        int argb = light.color() == 0 ? 0xFFFFFFFF : light.color();
        int baseAlpha = (argb >>> 24) & 0xFF;
        if (baseAlpha == 0) baseAlpha = 230;
        int red   = (argb >>> 16) & 0xFF;
        int green = (argb >>>  8) & 0xFF;
        int blue  =  argb         & 0xFF;

        float cx = (float) light.position().x;
        float cy = (float) light.position().y;
        float cz = (float) light.position().z;
        float baseSize = Math.max(light.radius() * 0.4F, 0.10F);

        float nx = normalMatrix.m20(), ny = normalMatrix.m21(), nz = normalMatrix.m22();

        // 内側 (明るく小さい), 中間, 外側ハロー の3層
        putBillboardQuad(consumer, mat, cx, cy, cz, baseSize * 0.45F,
            red, green, blue, baseAlpha, billRight, billUp, nx, ny, nz);
        putBillboardQuad(consumer, mat, cx, cy, cz, baseSize,
            red, green, blue, (int)(baseAlpha * 0.55F), billRight, billUp, nx, ny, nz);
        putBillboardQuad(consumer, mat, cx, cy, cz, baseSize * 2.0F,
            red, green, blue, (int)(baseAlpha * 0.22F), billRight, billUp, nx, ny, nz);
    }

    private static void putBillboardQuad(VertexConsumer consumer, Matrix4f mat,
                                         float cx, float cy, float cz, float size,
                                         int red, int green, int blue, int alpha,
                                         Vector3f right, Vector3f up,
                                         float nx, float ny, float nz) {
        float rx = right.x * size, ry = right.y * size, rz = right.z * size;
        float ux = up.x * size,    uy = up.y * size,    uz = up.z * size;
        putLightVertex(consumer, mat, cx - rx + ux, cy - ry + uy, cz - rz + uz, 0f, 0f, red, green, blue, alpha, nx, ny, nz);
        putLightVertex(consumer, mat, cx + rx + ux, cy + ry + uy, cz + rz + uz, 1f, 0f, red, green, blue, alpha, nx, ny, nz);
        putLightVertex(consumer, mat, cx + rx - ux, cy + ry - uy, cz + rz - uz, 1f, 1f, red, green, blue, alpha, nx, ny, nz);
        putLightVertex(consumer, mat, cx - rx - ux, cy - ry - uy, cz - rz - uz, 0f, 1f, red, green, blue, alpha, nx, ny, nz);
    }

    private static void putLightVertex(VertexConsumer consumer, Matrix4f mat, float x, float y, float z,
                                       float u, float v, int red, int green, int blue, int alpha,
                                       float nx, float ny, float nz) {
        consumer.addVertex(mat, x, y, z)
            .setColor(red, green, blue, alpha)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(0x00F000F0)
            .setNormal(nx, ny, nz);
    }
}

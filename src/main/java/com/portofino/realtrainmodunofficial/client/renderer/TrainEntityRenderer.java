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

public class TrainEntityRenderer extends EntityRenderer<TrainEntity> {
    private static ResourceLocation glowTexture;

    // 初回スポーン時に一度だけログを吐く為のセット (スパム防止)。
    private static final java.util.Set<String> LOGGED_VEHICLES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static final java.util.Set<String> LOGGED_BOGIE_VEHICLES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static final java.util.Set<String> ROLLSIGN_DIAG_LOGGED =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static final java.util.Map<java.util.UUID, String> LIGHT_DIAG_SEEN =
        new java.util.concurrent.ConcurrentHashMap<>();

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

    @Override
    public ResourceLocation getTextureLocation(TrainEntity entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public boolean shouldRender(TrainEntity entity, Frustum frustum, double camX, double camY, double camZ) {
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

            // Pitch: 坂で車体が水平のまま浮かないように適用。ただし RTM 系のレンダラスクリプトが
            // GL11.glRotated で body に独自に pitch を加える車両があり、二重回転で異常傾斜の原因になる。
            // → スクリプトを持つ車両ではここでは適用せず、スクリプト側に任せる。
            // → スクリプト無し or スクリプト読込失敗の車両のみここで適用。
            // さらに値域を ±45° に clamp して、ボギー1個がブロック上に乗っただけで45度傾く現象を防ぐ。
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
            poseStack.scale(def.getModelScale(), def.getModelScale(), def.getModelScale());

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
            boolean renderInterior = ridingThisTrain || nearTrain;
            boolean aggressiveDistanceCulling = !ridingThisTrain && cameraDistanceSq > aggressiveThreshold * aggressiveThreshold;
            boolean renderRollsigns = ridingThisTrain || cameraDistanceSq < rollsignThreshold * rollsignThreshold;
            boolean renderLights = ridingThisTrain || cameraDistanceSq < lightThreshold * lightThreshold;
            int trainPackedLight = resolveTrainPackedLight(entity, packedLight);

            boolean modelScriptRunning = model.hasRenderScript();
            // def.hasScript() covers cases where the JS engine failed to load (e.g. unsupported JS runtime)
            // but the pack was designed with a renderer script (e.g. SL packs with rod animation).
            // In that case, wheel/truck groups belong in the main model and must NOT be filtered out.
            boolean modelHasScript = modelScriptRunning || def.hasScript();
            MqoModelLoader.GroupPredicate groupFilter =
                groupName -> shouldRenderTrainGroup(groupName, renderInterior, aggressiveDistanceCulling, compatibilityHeavy, def, modelHasScript, modelScriptRunning);
            MqoModelLoader.GroupTransform doorTransform = new MqoModelLoader.GroupTransform() {
                @Override public void apply(PoseStack stack, String groupName) {
                    applyRunningGearTransform(stack, entity, def, model, groupName, renderYaw, partialTicks);
                    applyDoorTransform(stack, def.getLeftDoors(), groupName, entity.doorMoveL, true);
                    applyDoorTransform(stack, def.getRightDoors(), groupName, entity.doorMoveR, false);
                }
                @Override public boolean mayModify(String groupName) {
                    // door 系グループ以外は pushPose 不要 ⇒ Pose (Matrix4f+Matrix3f) 確保を回避。
                    // SL のような扉なし車両では全 batch でスキップされる。
                    if (groupName == null || groupName.length() < 4) return false;
                    if (isRunningGearGroup(groupName)) return true;
                    // i + 3 が最後の有効インデックスになるよう n = length - 4。
                    // 旧コードは n = length - 3 で i = n のとき charAt(i+3) = charAt(length) で IOOB。
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
            };
            // Direct GL 経路の前に他エンティティのバッチを flush し、深度バッファ整合性を保つ。
            if (buffer instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource bs) {
                bs.endBatch();
            }
            // 初回スポーン時に1回だけ詳細ログを吐く（スパム防止）。
            if (LOGGED_VEHICLES.add(entity.getVehicleId())) {
                java.util.Set<String> allGroups = model.getAllNormalizedGroupNames();
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
                    "[Render] vehicle={} script={} scriptRunning={} bogies={} groupsCount={}",
                    entity.getVehicleId(),
                    def.hasScript(),
                    modelScriptRunning,
                    def.getBogies().size(),
                    allGroups.size()
                );
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
                    "[Render] all groups: {}", allGroups
                );
            }
            // [診断] ライト点灯状態の調査。スクリプトは st0=getVehicleState(0)(進行方向) で
            // 前後の点灯/消灯グループを切り替える。常に消灯なら st0 が常に同じか確認する。
            // 状態(st0/reverser/lightMode)が変化した時だけ1行出す。
            {
                float st0 = entity.getVehicleState(0);
                String diagKey = st0 + "|" + entity.getReverser() + "|" + entity.getLightMode();
                String prev = LIGHT_DIAG_SEEN.put(entity.getUUID(), diagKey);
                if (!diagKey.equals(prev)) {
                    com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
                        "[LightDiag] vehicle={} st0(dir)={} reverser={} lightMode={}",
                        entity.getVehicleId(), st0, entity.getReverser(), entity.getLightMode());
                }
            }
            // 列車の実座標から取り直した lightmap を使用し、室内灯OFFの外装が夜に白く浮かないようにする。
            // 発光 pass は MqoModelLoader/TrainScriptSystem 側で室内灯ONの内装だけに制限する。
            MqoModelLoader.renderModel(model, poseStack, buffer, trainPackedLight, groupFilter, doorTransform, entity);
            // 台車は車体と同じ変換内で描画し、各台車ごとにレール高へ補正する。
            try {
                renderBogiesInline(entity, def, model, poseStack, buffer, trainPackedLight, partialTicks);
            } catch (Throwable t) {
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER
                    .debug("Inline bogie render failed for {}: {}", entity.getVehicleId(), t.toString());
            }
            // 台車の当たり判定は TrainBogieEntity、見た目は車体レンダー内で描画する。
            // 本家RTM(RenderVehicleBase)は方向幕(rollsignTexture+rollsigns)を「スクリプト有無に
            // 関係なく常にJava側で描画」する。RTMUは以前 !modelHasScript でスクリプト車両を
            // 除外しており、JSONに方向幕を持つスクリプト車(大半のRTMパック)で方向幕が一切
            // 出ていなかった。JSONに rollsigns が無い車両は renderConfiguredRollsigns 冒頭の
            // 早期returnでスキップされるので、常に呼んでも二重描画にはならない。
            if (renderRollsigns) {
                renderConfiguredRollsigns(entity, def, poseStack, buffer, trainPackedLight);
            }
            if (renderLights) {
                renderConfiguredLights(entity, def, model, poseStack, buffer, renderYaw, ridingThisTrain);
            }
            // パンタグラフ(pantograph_front/back)を関節アニメ付きで描画。本家RTM
            // (BasicVehiclePartsRenderer)準拠でスクリプト有無に関係なく常にJava描画する。
            // パンタ群は shouldRenderTrainGroup で静止描画から除外済みなので二重にならない。
            renderConfiguredPantographs(entity, def, model, poseStack, buffer, trainPackedLight);

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

    private static void applyDoorTransform(PoseStack poseStack, java.util.List<VehicleDefinition.DoorAnimationDefinition> doors,
                                           String groupName, float progressTicks, boolean leftSide) {
        if (groupName == null) {
            return;
        }
        // 早期除外: groupName が "door"/"Door"/"DOOR" を含まないなら何もしない。
        // 大半の batch (body, wheel, rod 等) がここで弾かれるため lowercase/regex を省ける。
        // 列車 1 台あたり ~100 batch × 2 (L/R) = 200 回呼ばれる hot path。
        if (groupName.indexOf('d') < 0 && groupName.indexOf('D') < 0) return;
        boolean mayBeDoor = false;
        for (int i = 0, n = groupName.length() - 4; i <= n; i++) {
            char c0 = groupName.charAt(i);
            if (c0 != 'd' && c0 != 'D') continue;
            char c1 = groupName.charAt(i + 1);
            char c2 = groupName.charAt(i + 2);
            char c3 = groupName.charAt(i + 3);
            if ((c1 == 'o' || c1 == 'O') && (c2 == 'o' || c2 == 'O') && (c3 == 'r' || c3 == 'R')) {
                mayBeDoor = true;
                break;
            }
        }
        if (!mayBeDoor) return;
        float progress = smoothstep(Mth.clamp(progressTicks / 60.0F, 0.0F, 1.0F));
        if (doors == null || doors.isEmpty()) {
            applyLegacyDoorFallback(poseStack, groupName, progress, leftSide);
            return;
        }
        for (VehicleDefinition.DoorAnimationDefinition door : doors) {
            if (!matchesDoorGroup(door.objects(), groupName)) {
                continue;
            }
            poseStack.translate(
                door.openTranslation().x * progress,
                door.openTranslation().y * progress,
                door.openTranslation().z * progress
            );
            return;
        }
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

    private static boolean matchesDoorGroup(java.util.List<String> objects, String groupName) {
        if (objects == null || objects.isEmpty() || groupName == null || groupName.isBlank()) {
            return false;
        }
        for (String objectName : objects) {
            if (objectName != null && objectName.equalsIgnoreCase(groupName)) {
                return true;
            }
        }
        return false;
    }

    private static float smoothstep(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    /**
     * 本家RTM(BasicVehiclePartsRenderer)準拠のパンタグラフ関節描画。
     * move = pantograph_F/40 (前) / pantograph_B/40 (後)。0=上昇(モデル姿勢)、1=下降(折畳)。
     * 各パーツは pos へ移動 → transform×move を適用 → -pos → グループ描画 → 子へ継承。
     */
    private static void renderConfiguredPantographs(TrainEntity entity, VehicleDefinition def,
                                                    MqoModelLoader.MqoModel model, PoseStack poseStack,
                                                    MultiBufferSource buffer, int packedLight) {
        if (def == null || model == null) {
            return;
        }
        java.util.List<VehicleDefinition.PantographPart> front = def.getPantographFront();
        java.util.List<VehicleDefinition.PantographPart> back = def.getPantographBack();
        if ((front == null || front.isEmpty()) && (back == null || back.isEmpty())) {
            return;
        }
        float moveFront = Mth.clamp(entity.pantograph_F / 40.0F, 0.0F, 1.0F);
        float moveBack = Mth.clamp(entity.pantograph_B / 40.0F, 0.0F, 1.0F);
        if (front != null) {
            for (VehicleDefinition.PantographPart part : front) {
                renderPantographPart(part, moveFront, model, poseStack, buffer, packedLight);
            }
        }
        if (back != null) {
            for (VehicleDefinition.PantographPart part : back) {
                renderPantographPart(part, moveBack, model, poseStack, buffer, packedLight);
            }
        }
    }

    private static void renderPantographPart(VehicleDefinition.PantographPart part, float move,
                                             MqoModelLoader.MqoModel model, PoseStack poseStack,
                                             MultiBufferSource buffer, int packedLight) {
        if (part == null) {
            return;
        }
        float[] pos = part.pos() != null && part.pos().length >= 3 ? part.pos() : new float[]{0, 0, 0};
        poseStack.pushPose();
        try {
            poseStack.translate(pos[0], pos[1], pos[2]);
            if (part.transforms() != null) {
                for (float[] fa : part.transforms()) {
                    if (fa == null) continue;
                    if (fa.length == 4) {
                        // [角度, ax, ay, az] を move 倍して回転
                        float angle = fa[0] * move;
                        if (Math.abs(fa[1]) > 1.0E-6F || Math.abs(fa[2]) > 1.0E-6F || Math.abs(fa[3]) > 1.0E-6F) {
                            poseStack.mulPose(new org.joml.Quaternionf().rotationAxis(
                                (float) Math.toRadians(angle), fa[1], fa[2], fa[3]));
                        }
                    } else if (fa.length == 3) {
                        poseStack.translate(fa[0] * move, fa[1] * move, fa[2] * move);
                    }
                }
            }
            poseStack.translate(-pos[0], -pos[1], -pos[2]);
            java.util.List<String> objects = part.objects();
            if (objects != null && !objects.isEmpty()) {
                java.util.Set<String> normalized = new java.util.HashSet<>();
                for (String o : objects) {
                    if (o != null && !o.isBlank()) normalized.add(o.trim().toLowerCase(java.util.Locale.ROOT));
                }
                MqoModelLoader.GroupPredicate filter = g ->
                    g != null && normalized.contains(g.toLowerCase(java.util.Locale.ROOT));
                MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, false, filter, null);
                if (model.hasTranslucentBatches()) {
                    MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, true, filter, null);
                }
            }
            if (part.childParts() != null) {
                for (VehicleDefinition.PantographPart child : part.childParts()) {
                    renderPantographPart(child, move, model, poseStack, buffer, packedLight);
                }
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderConfiguredRollsigns(TrainEntity entity, VehicleDefinition def, PoseStack poseStack,
                                                  MultiBufferSource buffer, int packedLight) {
        if (def == null || def.getRollsigns().isEmpty()) {
            return;
        }
        String texturePath = def.getRollsignTexture();
        if (texturePath == null || texturePath.isBlank()) {
            return;
        }
        ResourceLocation texture = MqoModelLoader.resolvePackTexture(def.getPackName(), texturePath);
        int count = Math.max(1, def.getRollsignNames().isEmpty() ? 1 : def.getRollsignNames().size());
        int destinationIndex = Math.floorMod(entity.getDestinationIndex(), count);
        float segmentV0 = destinationIndex / (float) count;
        float segmentV1 = (destinationIndex + 1.0F) / (float) count;
        // [診断] 純正方向幕が暗い/出ない調査用。pack+texture 単位で1回だけ出す。
        String rsDiagKey = def.getPackName() + "|" + texturePath;
        if (ROLLSIGN_DIAG_LOGGED.add(rsDiagKey)) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
                "[RollsignDiag] pack={} texPath={} resolved={} rollsigns={} names={} destIdx={} V0={} V1={}",
                def.getPackName(), texturePath, texture,
                def.getRollsigns().size(), def.getRollsignNames().size(),
                destinationIndex, segmentV0, segmentV1);
        }
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        for (VehicleDefinition.RollsignDefinition rollsign : def.getRollsigns()) {
            float[] uv = rollsign.uv();
            if (uv == null || uv.length < 4) {
                continue;
            }
            float uMin = uv[0];
            float uMax = uv[1];
            float baseVMin = uv[2];
            float baseVMax = uv[3];
            float vMin = Mth.lerp(segmentV0, baseVMin, baseVMax);
            float vMax = Mth.lerp(segmentV1, baseVMin, baseVMax);
            // 本家RTM(RenderVehicleBase#renderRollsign)準拠: disableLighting==false(既定)の方向幕は
            // バックライト点灯表示としてフルブライト描画する。RTMU は判定が逆で、既定の方向幕が
            // 周囲光(packedLight)依存になり暗所/夜/室内で真っ黒になっていた。本家と同じ向きに修正。
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
        // パンタグラフ(pantograph_front/back)のパーツは renderConfiguredPantographs が関節アニメ付きで
        // 描画する。ここ(通常/baked描画)で描くと静止姿勢のパンタが二重に出る(=動かない/上がりっぱなし)。
        if (def != null && def.getPantographGroupNames().contains(normalized)) {
            return false;
        }
        // 連結曲げ用の角度バリアントメッシュ (body-90 / body-80(mx) / bogie1-90 等、末尾"-NN"でNN>=10)
        // は RTM が曲げ角に応じ1つだけ描く代替メッシュ。移植版には曲げ処理が無いため、原点姿勢で
        // 描くと翼のように散乱する。スクリプトが描画失敗したフレームでは shouldRenderBakedGroup の
        // 除外が効かず、このフィルタだけで baked が全グループを描くため、ここでも必ず除外する。
        // (TTP蒸気機関車は全て曲げ変種を持つため、スクリプト不調時に車体パネルが散乱していた)
        if (isAngleBendVariant(normalized)) {
            return false;
        }
        // 台車/車輪グループ: 別台車モデルファイルがある場合のみ body MQO から非表示にする。
        // 別ファイルがある場合は TrainBogieEntityRenderer が正しい位置に描画するため、
        // body MQO で同じグループを描くと z-fighting / チカチカが発生する。
        // 別ファイルがない場合は body MQO 側のグループが唯一の台車描画手段なので表示する。
        boolean isBogieOrWheel = normalized.contains("bogie")
            || normalized.contains("wheel")
            || normalized.contains("daisya")
            || normalized.contains("daisha")
            || normalized.contains("sharin");
        if (isBogieOrWheel) {
            boolean hasSeparateBogieModel = def != null && def.getBogies().stream()
                .anyMatch(b -> b.modelFile() != null && !b.modelFile().isBlank());
            if (hasSeparateBogieModel && !scriptActuallyRunning) return false;
        }
        // RTMパックには非表示にすべきヘルパーグループが含まれている:
        //   shadow    - 地面に張り付いたシャドウポリゴン(レールを隠す)
        //   *_guide   - モデルエディタ用ガイド(roll_guide, seat_guide_L/R など)
        //   *_atari   - 当たり判定用ガイドポリゴン
        //   *[obj]    - Metasequoiaの親コンテナ(フェイスなしだが念のため)
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
        // 台車/車輪グループは常に描画する。スクリプトが描いてもバグで消えるケースが
        // 多発するため、確実に見えるようベイクド側からも描画する。別bogieファイルと
        // 多少重なってもよしとする(消えるより遥かに良い)。
        // 旧: !hasScript && !def.getBogies().isEmpty() のときだけ非表示にしていた
        // Script-controlled headlight/taillight overlay geometry uses emissive textures
        // that need script management for correct color/alpha. Only suppress them when
        // the light mode is OFF — always show when the train's lights are switched on,
        // even without a running script, so something visible appears as a fallback.
        // (The script path handles animated color; the baked path gives a static glow.)
        if (def.hasScript() && !scriptActuallyRunning
                && def.getHeadLights().isEmpty() && def.getTailLights().isEmpty()) {
            // "light" (exact): SL front headlight (D51 etc.)
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
                // none currently match this category, so allow all through.
                // Intentionally not returning false here so baked render shows the geometry.
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
        boolean selfDrawsRunningGear = bodyModel != null && bodyModel.hasOwnWheelGroups();
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

    private static boolean shouldSkipInlineBogie(boolean selfDrawsRunningGear, VehicleDefinition.BogieDefinition bogieDef) {
        if (bogieDef == null || bogieDef.modelFile() == null || bogieDef.modelFile().isBlank()) {
            return true;
        }
        if (BogieRenderer.isDummyBogieModel(bogieDef.modelFile())) {
            return true;
        }
        // .class 台車(本家組込 ModelBogie 等)は、車体モデル/スクリプトが自前で台車(bogieF 等)を
        // 描画・回転させる前提のもの。RTMU 標準台車(ft1)へ差し替えて BogieRenderer で描くと、
        // 純正台車の回転が反映されず見た目が崩れるため、ここでは描かずスクリプト/車体側に任せる。
        // (台車は車体固定位置で接線方向へ回転する＝本家RTMと同一挙動。)
        return bogieDef.modelFile().toLowerCase(java.util.Locale.ROOT).endsWith(".class");
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
        // 列車トランスフォームは Y軸回転 + オフセット + スケール。方向ベクトルには
        // 回転のみが効くため、カメラ回転に列車 yaw の逆クォータニオンを掛けて変換する。
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

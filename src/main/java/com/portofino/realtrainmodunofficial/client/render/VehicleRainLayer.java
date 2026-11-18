package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.VehicleVisualSettings;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import jp.ngt.rtm.RTMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/** 車両表面の雨粒。KaizPatchX/RTM には無い RTMU の追加演出。 */
@net.neoforged.fml.common.EventBusSubscriber(modid = RealTrainModUnofficial.MODID,
        value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class VehicleRainLayer {
    private static ResourceLocation texture;
    private static final Map<net.minecraft.world.level.Level, List<Occluder>> VEHICLE_OCCLUDERS =
            new WeakHashMap<>();

    /** 車体の向き付き遮蔽ボリューム。AABB だと斜めの列車で周囲の雨まで消えるため OBB で判定する。 */
    private record Occluder(double cx, double cz, double cos, double sin,
                            double halfWidth, double halfLength, double minY, double maxY) {
        boolean contains(Vec3 p) {
            if (p.y < this.minY || p.y > this.maxY) {
                return false;
            }
            double dx = p.x - this.cx;
            double dz = p.z - this.cz;
            double lx = dx * this.cos + dz * this.sin;
            double lz = -dx * this.sin + dz * this.cos;
            return Math.abs(lx) <= this.halfWidth && Math.abs(lz) <= this.halfLength;
        }

        boolean intersectsColumn(int x, int z) {
            double dx = x + 0.5D - this.cx;
            double dz = z + 0.5D - this.cz;
            double lx = dx * this.cos + dz * this.sin;
            double lz = -dx * this.sin + dz * this.cos;
            // 1x1 列をローカル空間へ変換したときの半径。
            double r = 0.5D * (Math.abs(this.cos) + Math.abs(this.sin));
            return Math.abs(lx) <= this.halfWidth + r && Math.abs(lz) <= this.halfLength + r;
        }
    }

    private VehicleRainLayer() {
    }

    /** クライアント tick: 雨を遮る車両の AABB を更新する。 */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientLevel level = minecraft.level;
        if (level == null) {
            VEHICLE_OCCLUDERS.clear();
            return;
        }
        List<Occluder> occluders = new ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : level.entitiesForRendering()) {
            if (entity instanceof TrainEntity train) {
                double yaw = Math.toRadians(train.getYRot());
                occluders.add(new Occluder(train.getX(), train.getZ(),
                        Math.cos(yaw), Math.sin(yaw),
                        train.getRainHalfWidth(), train.getRainHalfLength(),
                        train.getY() - 0.5D, train.getY() + train.getRainHeight()));
            } else if (entity instanceof com.portofino.realtrainmodunofficial.entity.CarEntity
                    || entity instanceof jp.ngt.rtm.entity.train.EntityTrainBase) {
                var box = entity.getBoundingBox().inflate(0.15D);
                occluders.add(new Occluder((box.minX + box.maxX) * 0.5D,
                        (box.minZ + box.maxZ) * 0.5D, 1.0D, 0.0D,
                        (box.maxX - box.minX) * 0.5D, (box.maxZ - box.minZ) * 0.5D,
                        box.minY, box.maxY));
            }
        }
        VEHICLE_OCCLUDERS.put(level, List.copyOf(occluders));
    }

    /** その XZ 列に車両が居るか (バニラ雨をその列だけ止める)。 */
    public static boolean blocksRain(net.minecraft.world.level.Level level, int x, int z) {
        // 表面の雨粒表示 (rainEnabled) とは独立。
        List<Occluder> occluders;
        synchronized (VEHICLE_OCCLUDERS) {
            occluders = VEHICLE_OCCLUDERS.get(level);
        }
        if (occluders == null || occluders.isEmpty()) {
            return false;
        }
        for (Occluder occluder : occluders) {
            if (occluder.intersectsColumn(x, z)) {
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation texture() {
        if (texture == null) {
            NativeImage image = new NativeImage(16, 16, false);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    float dx = (x - 7.5F) / 7.5F;
                    float dy = (y - 7.5F) / 7.5F;
                    float r = (float) Math.sqrt(dx * dx + dy * dy);
                    float a = r >= 1.0F ? 0.0F : (float) Math.pow(1.0F - r, 1.6);
                    // 水滴のハイライト
                    float hi = (x >= 5 && x <= 8 && y >= 4 && y <= 7) ? 0.35F : 0.0F;
                    int alpha = Mth.clamp((int) ((a + hi) * 255.0F), 0, 255);
                    int red = Mth.clamp((int) (170 + hi * 80), 0, 255);
                    int green = Mth.clamp((int) (205 + hi * 40), 0, 255);
                    int blue = 255;
                    image.setPixelRGBA(x, y, (alpha << 24) | (blue << 16) | (green << 8) | red);
                }
            }
            texture = ResourceLocation.fromNamespaceAndPath(
                    RealTrainModUnofficial.MODID, "dynamic/effect/vehicle_rain_drop");
            Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(image));
        }
        return texture;
    }

    /** カメラが車両の内側にあるか。 */
    public static boolean cameraInsideVehicle(net.minecraft.world.level.Level level, Vec3 pos) {
        if (level == null || pos == null) {
            return false;
        }
        List<Occluder> occluders;
        synchronized (VEHICLE_OCCLUDERS) {
            occluders = VEHICLE_OCCLUDERS.get(level);
        }
        if (occluders == null) {
            return false;
        }
        for (Occluder occluder : occluders) {
            if (occluder.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /** 車体ローカル空間 (ヨー/ピッチ/バンク適用後、モデルの offset/scale を掛ける前)… */
    public static void render(TrainEntity entity,
                              com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition definition,
                              PoseStack poseStack, MultiBufferSource buffer,
                              int packedLight, float partialTick, boolean voxelModel) {
        if (!com.portofino.realtrainmodunofficial.client.VehicleVisualSettings.rainEnabled || entity == null || entity.level() == null) {
            return;
        }
        float rain = entity.level().getRainLevel(partialTick);
        if (rain <= VehicleVisualSettings.rainMinLevel || !entity.level().isRaining()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        if (VehicleVisualSettings.rainHideInsideVehicle
                && cameraInsideVehicle(entity.level(), cameraPos)) {
            return;
        }
        double cameraDistanceSq = cameraPos
                .distanceToSqr(entity.getX(), entity.getY(), entity.getZ());
        double maxDistance = com.portofino.realtrainmodunofficial.client.VehicleVisualSettings.rainDistance;
        if (maxDistance <= 0 || cameraDistanceSq > maxDistance * maxDistance) {
            return;
        }

        float scale = definition == null || voxelModel ? 1.0F
                : Math.max(0.01F, definition.getModelScale());
        Vec3 offset = definition == null ? Vec3.ZERO : definition.getModelOffset();
        float halfWidth = Math.max(0.7F, entity.getBbWidth() * 0.5F);
        float height = Math.max(1.0F, entity.getBbHeight());
        float halfLength = Math.max(1.5F, entity.getTrainDistance());
        int count = Math.round(VehicleVisualSettings.rainDensity
                * Math.min(2.5F, halfLength / 8.0F) * Mth.clamp(rain, 0.0F, 1.0F));
        if (count <= 0) {
            return;
        }
        count = Math.min(count, com.portofino.realtrainmodunofficial.client.VehicleVisualSettings.rainMaxDrops);

        long seed = entity.getUUID().getMostSignificantBits()
                ^ entity.getUUID().getLeastSignificantBits()
                ^ entity.getId();
        Random random = new Random(seed);
        float time = (entity.tickCount + partialTick) * 0.05F * com.portofino.realtrainmodunofficial.client.VehicleVisualSettings.rainSpeed;
        float size = com.portofino.realtrainmodunofficial.client.VehicleVisualSettings.rainSize / scale;
        float opacity = com.portofino.realtrainmodunofficial.client.VehicleVisualSettings.rainOpacity * Mth.clamp(rain, 0.0F, 1.0F);
        float sheen = VehicleVisualSettings.rainHighlight
                + VehicleVisualSettings.rainReflection * 0.25F;
        float red = Mth.clamp(VehicleVisualSettings.rainColorR + sheen, 0.0F, 1.0F);
        float green = Mth.clamp(VehicleVisualSettings.rainColorG + sheen, 0.0F, 1.0F);
        float blue = Mth.clamp(VehicleVisualSettings.rainColorB + sheen, 0.0F, 1.0F);
        float roofShare = Math.max(0.0F, VehicleVisualSettings.rainRoofRatio);
        float sideShare = Math.max(0.0F, VehicleVisualSettings.rainSideRatio);
        float roofChance = roofShare + sideShare <= 0.0F ? 0.33F : roofShare / (roofShare + sideShare);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(texture()));
        int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;

        for (int i = 0; i < count; i++) {
            boolean roof = random.nextFloat() < roofChance;
            int side = roof ? 0 : (random.nextBoolean() ? 1 : 2); // 0=屋根, 1=左, 2=右
            float phase = random.nextFloat();
            float z = (random.nextFloat() * 2.0F - 1.0F) * halfLength;
            float wind = VehicleVisualSettings.rainWind
                    * (float) Math.sin((time + phase) * 6.2831855F)
                    * (1.0F + VehicleVisualSettings.rainTurbulence);
            z += wind * 0.35F;
            float dropSize = size * (0.55F + random.nextFloat() * 0.9F);
            if (side == 0) {
                // 屋根: 表面に付いた水滴
                if (!VehicleVisualSettings.rainSplash) {
                    continue;
                }
                float x = (random.nextFloat() * 2.0F - 1.0F) * halfWidth;
                float pulse = 0.8F + 0.2F * Mth.sin((time + phase) * 6.2831855F);
                drawQuad(vc, matrix,
                        (x - (float) offset.x) / scale,
                        (height + 0.012F - (float) offset.y) / scale,
                        (z - (float) offset.z) / scale,
                        dropSize * pulse, dropSize * pulse, true, 0.0F, 0.0F, 1.0F,
                        light, opacity, red, green, blue);
            } else {
                float x = side == 1 ? -halfWidth - 0.012F : halfWidth + 0.012F;
                // 側面: 上から下へ流れる粒 + 尾
                float fall = (time + phase) % 1.0F;
                float y = height - fall * height;
                float nx = side == 1 ? -1.0F : 1.0F;
                float mx = (x - (float) offset.x) / scale;
                float my = (y - (float) offset.y) / scale;
                float mz = (z - (float) offset.z) / scale;
                drawQuad(vc, matrix, mx, my, mz,
                        dropSize * 0.7F, dropSize, false, nx, 0.0F, 0.0F,
                        light, opacity, red * 0.92F, green * 0.97F, blue);
                // 尾 (上方向へ薄く伸びる)
                float streak = VehicleVisualSettings.rainStreak;
                drawQuad(vc, matrix, mx, my + dropSize * streak, mz,
                        dropSize * 0.22F, dropSize * streak, false, nx, 0.0F, 0.0F,
                        light, opacity * 0.35F, red * 0.82F, green * 0.92F, blue);
            }
        }
    }

    private static void drawQuad(VertexConsumer vc, Matrix4f matrix,
                                 float x, float y, float z, float halfW, float halfH,
                                 boolean horizontal, float nx, float ny, float nz,
                                 int light, float alpha, float r, float g, float b) {
        if (horizontal) {
            // XZ 平面 (屋根)
            vertex(vc, matrix, x - halfW, y, z - halfH, 0.0F, 1.0F, nx, ny, nz, light, alpha, r, g, b);
            vertex(vc, matrix, x - halfW, y, z + halfH, 0.0F, 0.0F, nx, ny, nz, light, alpha, r, g, b);
            vertex(vc, matrix, x + halfW, y, z + halfH, 1.0F, 0.0F, nx, ny, nz, light, alpha, r, g, b);
            vertex(vc, matrix, x + halfW, y, z - halfH, 1.0F, 1.0F, nx, ny, nz, light, alpha, r, g, b);
        } else {
            // YZ 平面 (側面)
            vertex(vc, matrix, x, y - halfH, z - halfW, 0.0F, 1.0F, nx, ny, nz, light, alpha, r, g, b);
            vertex(vc, matrix, x, y - halfH, z + halfW, 0.0F, 0.0F, nx, ny, nz, light, alpha, r, g, b);
            vertex(vc, matrix, x, y + halfH, z + halfW, 1.0F, 0.0F, nx, ny, nz, light, alpha, r, g, b);
            vertex(vc, matrix, x, y + halfH, z - halfW, 1.0F, 1.0F, nx, ny, nz, light, alpha, r, g, b);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f matrix,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int light,
                               float alpha, float r, float g, float b) {
        com.portofino.realtrainmodunofficial.client.render.VertexWriter.addVertex(vc, matrix, x, y, z)
                .setColor(r, g, b, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }
}

package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import jp.ngt.ngtlib.io.NGTFileLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 本家 KaizPatchX の customIconTexture — アイテムの絵をモデル定義の画像に差し替える。
 * 本家 RenderItemWithModel の移植。
 */
public class CustomIconItemRenderer extends BlockEntityWithoutLevelRenderer {

    public CustomIconItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
            Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ResourceLocation texture = resolve(iconPathOf(stack));
        if (texture == null) {
            return;
        }
        // 持ち物欄は影を付けたくないのでフルブライト。手元/地面は周囲の明るさに従う。
        int light = context == ItemDisplayContext.GUI ? 0x00F000F0 : packedLight;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentCull(texture));
        Matrix4f m = poseStack.last().pose();
        var normal = poseStack.last();

        // バニラの item/generated と同じ 0..1 の枠に収める。z は板の中心。
        final float z = 0.5F;
        // 表 (+Z 向き)
        quad(vc, m, normal, z, 0.0F, 0.0F, 1.0F, 1.0F, light, packedOverlay, 1.0F);
        // 裏 (-Z 向き)。平面 1 枚だと真横や裏から見えなくなる
        quad(vc, m, normal, z, 1.0F, 0.0F, 0.0F, 1.0F, light, packedOverlay, -1.0F);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose, float z,
                             float x0, float v0, float x1, float v1, int light, int overlay,
                             float nz) {
        // UV は左上原点。x0>x1 のときは裏面なので巻き順が反転する
        float u0 = nz > 0 ? 0.0F : 1.0F;
        float u1 = nz > 0 ? 1.0F : 0.0F;
        vc.addVertex(m, x0, 0.0F, z).setColor(0xFFFFFFFF).setUv(u0, 1.0F)
          .setOverlay(overlay == 0 ? OverlayTexture.NO_OVERLAY : overlay)
          .setLight(light).setNormal(pose, 0.0F, 0.0F, nz);
        vc.addVertex(m, x1, 0.0F, z).setColor(0xFFFFFFFF).setUv(u1, 1.0F)
          .setOverlay(overlay == 0 ? OverlayTexture.NO_OVERLAY : overlay)
          .setLight(light).setNormal(pose, 0.0F, 0.0F, nz);
        vc.addVertex(m, x1, 1.0F, z).setColor(0xFFFFFFFF).setUv(u1, 0.0F)
          .setOverlay(overlay == 0 ? OverlayTexture.NO_OVERLAY : overlay)
          .setLight(light).setNormal(pose, 0.0F, 0.0F, nz);
        vc.addVertex(m, x0, 1.0F, z).setColor(0xFFFFFFFF).setUv(u0, 0.0F)
          .setOverlay(overlay == 0 ? OverlayTexture.NO_OVERLAY : overlay)
          .setLight(light).setNormal(pose, 0.0F, 0.0F, nz);
    }

    /** その ItemStack が指すモデル定義の customIconTexture。無ければ空。 */
    public static String iconPathOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String id = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge
            .getSelectedModelId(stack);
        if (id == null || id.isEmpty()) {
            return "";
        }
        InstalledObjectDefinition def = InstalledObjectRegistry.getById(id);
        if (def != null && !def.getCustomIconTexture().isEmpty()) {
            return def.getCustomIconTexture();
        }
        // 自動車・列車も同じ仕組み (1 つのアイテムで中身のモデルを選ぶ形は同じ)
        com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition vehicle =
            com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getById(id);
        if (vehicle != null && !vehicle.getCustomIconTexture().isEmpty()) {
            return vehicle.getCustomIconTexture();
        }
        return "";
    }

    // ------------------------------------------------------------------
    // テクスチャの読み込み (パス → 登録済み ResourceLocation)
    // ------------------------------------------------------------------

    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final ResourceLocation NONE =
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "dynamic/icon/none");

    /**
     * "rtm:textures/items/itemLinePole_1.png" のような指定を実テクスチャへ。
     * 本家は名前空間つきなら ResourceLocation をそのまま使うが、RTMU はパックの中身を
     * バニラのリソースパックには載せていないので、assets/<名前空間>/<パス> として
     * NGTFileLoader で探す。名前空間なしならそのままのパスで探す。
     */
    private static synchronized ResourceLocation resolve(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        ResourceLocation cached = CACHE.get(path);
        if (cached != null) {
            return cached == NONE ? null : cached;
        }
        byte[] bytes = null;
        int colon = path.indexOf(':');
        if (colon > 0) {
            String namespace = path.substring(0, colon);
            String rest = path.substring(colon + 1);
            bytes = NGTFileLoader.findAsset("assets/" + namespace + "/" + rest);
            if (bytes == null) {
                bytes = NGTFileLoader.findAsset(rest);
            }
        } else {
            bytes = NGTFileLoader.findAsset(path);
        }
        if (bytes == null) {
            RealTrainModUnofficial.LOGGER.warn(
                "[RTMU] customIconTexture が見つかりません: {}", path);
            CACHE.put(path, NONE);
            return null;
        }
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                RealTrainModUnofficial.MODID,
                "dynamic/icon/" + sanitize(path));
            DynamicTexture tex = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            // アイコンはピクセル等倍で見せたい (拡大でにじむと本家と違う見え方になる)
            tex.setFilter(false, false);
            CACHE.put(path, loc);
            return loc;
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn(
                "[RTMU] customIconTexture の読み込みに失敗: {}", path, e);
            CACHE.put(path, NONE);
            return null;
        }
    }

    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    /** リソースリロードで作り直す。 */
    public static synchronized void clear() {
        CACHE.clear();
    }
}

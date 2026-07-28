package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.portofino.realtrainmodunofficial.blockentity.MiniatureBlockEntity;
import jp.ngt.mcte.item.ItemMiniature;
import jp.ngt.ngtlib.block.BlockSet;
import jp.ngt.ngtlib.block.NGTObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import com.mojang.math.Axis;

/**
 * 設置済みミニチュアの描画 (neo mcte)。本家 MCTE {@code RenderMiniature} 相当。
 *
 * <p>中身の {@link BlockSet} をバニラのブロック描画でそのまま描き、
 * 行列側で縮尺・回転・オフセットを掛ける。ブロックごとに専用のモデルを持たないので、
 * どんなブロックでも (MOD ブロックでも) そのまま模型になる。
 *
 * <p>★負荷対策: ミニチュアは中身が数千ブロックになり得るので、
 * <ul>
 *   <li>視距離で打ち切る (遠くの模型は描かない)</li>
 *   <li>1 フレームあたりの描画ブロック数に上限を設ける</li>
 * </ul>
 * を入れてある。上限に当たった場合は手前から順に描く。
 */
public class MiniatureBlockEntityRenderer implements BlockEntityRenderer<MiniatureBlockEntity> {

    /** これより遠い模型は描かない (ブロック)。 */
    private static final double MAX_DISTANCE = 64.0D;
    /** 1 個の模型で 1 フレームに描くブロック数の上限。 */
    private static final int MAX_BLOCKS_PER_FRAME = 4096;

    private final BlockEntityRendererProvider.Context context;

    public MiniatureBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    @Override
    public void render(MiniatureBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        NGTObject obj = be.getNGTObject();
        if (obj == null || obj.blockList.isEmpty()) {
            return;
        }
        float scale = be.getScale();
        if (scale <= 0.0F) {
            return;
        }

        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        //明るさは設定値があればそれを最優先 (本家 MBState.LightValue 相当)。
        int light = be.getLightValue() > 0 ? LightTexture.FULL_BRIGHT : packedLight;

        float[] off = be.getOffset();

        poseStack.pushPose();
        //ブロック中心へ
        poseStack.translate(0.5D, 0.0D, 0.5D);
        //設置時のプレイヤー向き
        poseStack.mulPose(Axis.YP.rotationDegrees(-be.getRotation()));
        //設定オフセット
        poseStack.translate(off[0], off[1], off[2]);
        poseStack.scale(scale, scale, scale);
        //模型の中心を合わせる (X/Z は中央、Y は接地)
        poseStack.translate(-obj.xSize * 0.5D, 0.0D, -obj.zSize * 0.5D);

        int drawn = 0;
        for (BlockSet set : obj.blockList) {
            if (set == null || set.state == null || set.state.isAir()) {
                continue;
            }
            if (++drawn > MAX_BLOCKS_PER_FRAME) {
                break;
            }
            poseStack.pushPose();
            poseStack.translate(set.x, set.y, set.z);
            try {
                dispatcher.renderSingleBlock(set.state, poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
            } catch (Throwable ignored) {
                //描けないブロック (専用レンダラ前提の MOD ブロック等) は飛ばす。
                //1 個の失敗で模型ごと消さない。
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    //★@Override を付けないこと: これは NeoForge が足したメソッドで、バニラには無い
    public AABB getRenderBoundingBox(MiniatureBlockEntity be) {
        return be.getRenderBoundingBox();
    }

    @Override
    public boolean shouldRenderOffScreen(MiniatureBlockEntity be) {
        //縮尺次第でブロック境界からはみ出すので、画面外判定に頼らない
        return true;
    }

    @Override
    public int getViewDistance() {
        return (int) MAX_DISTANCE;
    }
}

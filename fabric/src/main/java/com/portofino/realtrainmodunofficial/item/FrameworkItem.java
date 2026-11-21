package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 鉄骨。本家 {@code RTMBlock.framework} (BlockLinePole + TileEntityPole) の移植。
 *
 * <p>本家の実体は「ornamentType=Pole の既定モデル IronFrame01 を持つ設置物ブロック」。
 * RenderConnectablePole.js が隣の鉄骨/架線柱と接続する。
 * 架線柱と同じグリッド揃え (setRotation 無し) で置く。
 * 本家 isLadder=true (よじ登れる) は共有ブロックの都合で未対応。
 */
public class FrameworkItem extends Item {

    public FrameworkItem() {
        super(new Properties());
    }

    private static InstalledObjectDefinition frameDefinition() {
        InstalledObjectDefinition def = InstalledObjectRegistry.getByBareName(
            "IronFrame01", InstalledObjectCategory.OVERHEAD_LINE_POLE);
        if (def != null) {
            return def;
        }
        //保険: 同カテゴリの Frame を名前で探す
        for (InstalledObjectDefinition candidate
                : InstalledObjectRegistry.getByCategory(InstalledObjectCategory.OVERHEAD_LINE_POLE)) {
            if (candidate.getId().toLowerCase(java.util.Locale.ROOT).contains("ironframe")) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        InstalledObjectDefinition definition = frameDefinition();
        if (definition == null) {
            if (!level.isClientSide()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "鉄骨のモデル (IronFrame01) が見つかりません"), true);
            }
            return InteractionResult.FAIL;
        }
        BlockPos placePos = context.getClickedPos().relative(context.getClickedFace());
        BlockState state = level.getBlockState(placePos);
        if (!state.canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            level.setBlock(placePos,
                RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3);
            if (level.getBlockEntity(placePos) instanceof InstalledObjectBlockEntity blockEntity) {
                //本家 BlockLinePole: グリッド揃え・回転なし
                blockEntity.setDefinition(definition.getId(),
                    InstalledObjectCategory.OVERHEAD_LINE_POLE, 0.0F);
                blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                level.sendBlockUpdated(placePos, blockEntity.getBlockState(),
                    blockEntity.getBlockState(), 3);
            }
            if (!player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}

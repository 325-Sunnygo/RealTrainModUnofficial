package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class WireItem extends Item implements ModelSelectableItem {
    /**
     * 本家 KaizPatchX の customIconTexture 用の描画器。
     * 実際に使われるのは、選択中のモデルが customIconTexture を持つときだけ
     * (com.portofino.realtrainmodunofficial.client.renderer.CustomIconItemModel が判定する)。
     */
    @Override
    public void initializeClient(java.util.function.Consumer<
            net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private com.portofino.realtrainmodunofficial.client.renderer.CustomIconItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new com.portofino.realtrainmodunofficial.client.renderer.CustomIconItemRenderer();
                }
                return renderer;
            }
        });
    }

    public WireItem() {
        super(new Properties());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            ClientHooks.openInstalledObjectSelectScreen(player, player.getItemInHand(hand), InstalledObjectCategory.WIRE);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        String selectedId = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedModelId(stack);
        InstalledObjectDefinition definition = InstalledObjectRegistry.getById(selectedId);
        if (definition == null || definition.getCategory() != InstalledObjectCategory.WIRE) {
            if (level.isClientSide) {
                ClientHooks.openInstalledObjectSelectScreen(player, stack, InstalledObjectCategory.WIRE);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos clickedPos = context.getClickedPos();
        boolean connective = false;
        if (level.getBlockEntity(clickedPos) instanceof InstalledObjectBlockEntity clicked) {
            InstalledObjectCategory cat = clicked.getCategory();
            connective = cat == InstalledObjectCategory.INSULATOR
                || cat == InstalledObjectCategory.OVERHEAD_LINE_POLE
                || cat == InstalledObjectCategory.CONNECTOR_INPUT
                || cat == InstalledObjectCategory.CONNECTOR_OUTPUT;
        } else if (level.getBlockEntity(clickedPos) instanceof jp.ngt.rtm.electric.TileEntitySignalConverter) {
            // 信号変換器もワイヤー接続可能 (本家 IBlockConnective)
            connective = true;
        }
        if (!connective) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("ワイヤーは碍子・コネクタ・信号変換器にのみ接続できます"), true);
            }
            return InteractionResult.FAIL;
        }

        CompoundTag startTag = stack.get(RealTrainModUnofficialComponents.WIRE_PLACEMENT_START.get());
        if (startTag == null || !startTag.contains("X")) {
            if (!level.isClientSide) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("X", clickedPos.getX());
                tag.putInt("Y", clickedPos.getY());
                tag.putInt("Z", clickedPos.getZ());
                stack.set(RealTrainModUnofficialComponents.WIRE_PLACEMENT_START.get(), tag);
                player.displayClientMessage(Component.literal("始点の碍子を記録しました"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos startPos = new BlockPos(startTag.getInt("X"), startTag.getInt("Y"), startTag.getInt("Z"));
        if (startPos.equals(clickedPos)) {
            if (!level.isClientSide) {
                stack.remove(RealTrainModUnofficialComponents.WIRE_PLACEMENT_START.get());
                player.displayClientMessage(Component.literal("ワイヤー設置を解除しました"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // 本家 ItemWire: 2 回目にクリックした碍子へ接続を張る (そちらが root)。
        // 中間ブロックは置かない。ワイヤーモデルは接続 (Connection) が持ち、
        // 碍子はそのまま残る (NGTO Builder2 のビーム設置と同じ形)。
        if (level.getBlockEntity(clickedPos) instanceof InstalledObjectBlockEntity targetBe) {
            if (!level.isClientSide) {
                targetBe.setConnectionTo(startPos.getX(), startPos.getY(), startPos.getZ(),
                    jp.ngt.rtm.electric.Connection.ConnectionType.WIRE, definition.getId());
                stack.remove(RealTrainModUnofficialComponents.WIRE_PLACEMENT_START.get());
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                player.displayClientMessage(Component.literal("ワイヤーを設置しました"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // 設置物以外 (信号変換器など) は従来の中間ブロック式のまま
        BlockPos mid = new BlockPos((startPos.getX() + clickedPos.getX()) >> 1, (startPos.getY() + clickedPos.getY()) >> 1, (startPos.getZ() + clickedPos.getZ()) >> 1);
        BlockState state = level.getBlockState(mid);
        if (!state.canBeReplaced()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("ワイヤー中央にブロックがあるため設置できません"), true);
            }
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            level.setBlock(mid, RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3);
            if (level.getBlockEntity(mid) instanceof InstalledObjectBlockEntity blockEntity) {
                blockEntity.setDefinition(definition.getId(), InstalledObjectCategory.WIRE, player.getYRot());
                blockEntity.setWireEndpoints(startPos, clickedPos);
                level.sendBlockUpdated(mid, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            }
            stack.remove(RealTrainModUnofficialComponents.WIRE_PLACEMENT_START.get());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.literal("ワイヤーを設置しました"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String selectedId = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedModelId(stack);
        if (selectedId != null && !selectedId.isBlank()) {
            InstalledObjectDefinition def = InstalledObjectRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            lines.add(Component.translatable("tooltip.realtrainmodunofficial.model.selected", name).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tooltip.realtrainmodunofficial.model.none").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public List<SelectableModelInfo> getSelectableModels() {
        return InstalledObjectRegistry.getByCategory(InstalledObjectCategory.WIRE).stream()
            .map(def -> new SelectableModelInfo(def.getId(), def.getDisplayName(), def.getPackName(), def.getButtonTexture()))
            .toList();
    }

    // --- NGTO Builder の Wire ツール互換 (本家 ItemWithModel の API 名で選択モデルを返す) ---
    // 注意: スクリプトは実 ItemStack ではなく jp.ngt.mccompat.ItemStackCompat (ラッパー) を渡すため、
    // 引数は Object で受けて unwrap する (ItemStack 型で受けると ClassCastException になる)。

    /** 手持ちワイヤーの選択モデル ID。スクリプトが setConnectionTo の modelName に使う。 */
    public String getModelName(Object stackLike) {
        ItemStack stack = jp.ngt.mccompat.ItemStackCompat.unwrap(stackLike);
        if (stack == null) {
            return "";
        }
        String id = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedModelId(stack);
        return id == null ? "" : id;
    }

    /** 選択モデルの状態。スクリプトは .type.subType を読み、setConnectionTo にも渡す。 */
    public WireModelState getModelState(Object stackLike) {
        return new WireModelState(getModelName(stackLike));
    }

    /** 選択モデルのサブタイプ (リレー判定に使用。通常ワイヤーは "Relay" 以外)。 */
    public String getSubType(Object stackLike) {
        return getModelName(stackLike);
    }

    /** 本家 ResourceState の .type.subType を Nashorn から読めるようにする最小互換。 */
    public static final class WireModelState {
        public final String modelId;
        public final WireModelType type;
        public WireModelState(String modelId) {
            this.modelId = modelId == null ? "" : modelId;
            this.type = new WireModelType(this.modelId);
        }
    }

    public static final class WireModelType {
        public final String subType;
        public WireModelType(String subType) {
            this.subType = subType == null ? "" : subType;
        }
    }
}

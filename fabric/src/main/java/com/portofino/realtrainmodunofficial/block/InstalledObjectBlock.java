package com.portofino.realtrainmodunofficial.block;

import com.mojang.serialization.MapCodec;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import com.portofino.realtrainmodunofficial.signal.SignalNetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class InstalledObjectBlock extends BaseEntityBlock {
    public static final MapCodec<InstalledObjectBlock> CODEC = simpleCodec(InstalledObjectBlock::new);
    private static final VoxelShape RTM_SELECTION_SHAPE = box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape EMPTY_SHAPE = Shapes.empty();

    public InstalledObjectBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public InstalledObjectBlock() {
        this(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(0.4F, 2.0F).noOcclusion());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * 中ボタン(ピックブロック): 設置済みの照明/信号/碍子/架線柱などを、そのモデルが選択済みの
     * 設置アイテムとしてコピーする。レール以外のモデルも中ボタンでコピーできるようにする要望対応。
     */
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be) {
            net.minecraft.world.item.Item item =
                RealTrainModUnofficialItems.getInstalledObjectItem(be.getCategory());
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                String defId = be.getDefinitionId();
                if (defId != null && !defId.isBlank()) {
                    // 選択モデルをこの設置物の定義に合わせる (置くと同じ物になる)。
                    com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.setSelectedModelData(stack, defId, "");
                }
                return stack;
            }
        }
        return super.getCloneItemStack(level, pos, state);
    }

    // ★3 引数の getLightEmission は NeoForge が足したもので、バニラは呼ばない。
    // Fabric では光源計算そのものを差し替えている (BlockLightEngineMixin)。


    /**
     * 置いた物ごとの明るさ (0-15)。
     * NeoForge は getLightEmission(state, level, pos) から、
     * Fabric は光源計算の mixin (BlockLightEngineMixin) から呼ぶ。
     */
    public static int dynamicLightEmission(InstalledObjectBlockEntity be) {
        if (be == null) {
            return 0;
        }
        if (be.getCategory() == InstalledObjectCategory.LIGHT && be.isPowered()) {
            return 15;
        }
        if (be.getCategory() == InstalledObjectCategory.SIGNBOARD) {
            return be.getSignboardLightEmission();
        }
        // 本家 BlockFluorescent.getLightValue: 蛍光灯は常時 15
        // (壊れた蛍光灯は 0/4/8/12 で明滅)。レッドストーン不要。
        if (be.getCategory() == InstalledObjectCategory.FLUORESCENT) {
            return be.getFluorescentLightValue();
        }
        return 0;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity) {
            if (blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
                return EMPTY_SHAPE;
            }
            return RTM_SELECTION_SHAPE;
        }
        return RTM_SELECTION_SHAPE;
    }

    /**
     * 設置物は本家と同じく 1 ブロックぶんの当たり判定を持つ。
     * 本家 RTM の設置物は BlockContainerCustom を継承しており、
     * 既定の当たり判定が (0,0,0)-(1,1,1) のまま使われている。
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity) {
            if (blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
                return EMPTY_SHAPE;
            }
            if (blockEntity.getCategory() == InstalledObjectCategory.TICKET_GATE
                && blockEntity.isTicketGateOpen()) {
                return EMPTY_SHAPE;
            }
        }
        return RTM_SELECTION_SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, RealTrainModUnofficialBlockEntities.INSTALLED_OBJECT.get(), InstalledObjectBlockEntity::tick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(RealTrainModUnofficialItems.IC_CARD_ITEM.get())
            && level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be
            && be.getCategory() == InstalledObjectCategory.TICKET_GATE) {
            if (!level.isClientSide) {
                be.activateTicketGate();
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // 切符での改札通過 (本家 BlockTurnstile.onEntityCollidedWithBlock 相当)。
        if (stack.getItem() instanceof com.portofino.realtrainmodunofficial.item.TicketItem ticket
            && level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be
            && be.getCategory() == InstalledObjectCategory.TICKET_GATE) {
            if (!level.isClientSide) {
                ItemStack remainder = ticket.consume(stack);
                // 本家: 使い切ったら消える。残りがあれば「入場済み」印を付けて返す。
                player.setItemInHand(hand, remainder);
                be.activateTicketGate();
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // 本家 BlockPoint.onBlockActivated: バールで右クリックすると move の符号が反転し、
        // 転轍機の本体が線路の反対側に移る。
        if (stack.getItem() instanceof com.portofino.realtrainmodunofficial.item.CrowbarItem
            && level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity point
            && point.getCategory() == InstalledObjectCategory.POINT) {
            if (!level.isClientSide) {
                point.setPointMove(-point.getPointMove());
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // 本家 BlockMachineBase.clickMachine: バールで右クリック → 微調整 GUI (GuiChangeOffset)。
        // レンチのシフト右クリックでも開けるようにする (ユーザー要望)。
        boolean crowbar = stack.getItem() instanceof com.portofino.realtrainmodunofficial.item.CrowbarItem;
        boolean wrenchSneak = player.isShiftKeyDown()
            && stack.getItem() instanceof com.portofino.realtrainmodunofficial.item.RtmWrenchItem;
        if ((crowbar || wrenchSneak) && level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be) {
            if (level.isClientSide) {
                com.portofino.realtrainmodunofficial.ClientHooks.openChangeOffsetScreen(be);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be && be.isSpeaker()) {
            if (level.isClientSide) {
                com.portofino.realtrainmodunofficial.ClientHooks.openSpeakerScreen(pos);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        // 本家 BlockSignBoard.onBlockActivated: 素手で右クリック → 看板エディタ (GuiSignboard)。
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be
            && be.getCategory() == InstalledObjectCategory.SIGNBOARD) {
            if (level.isClientSide) {
                com.portofino.realtrainmodunofficial.ClientHooks.openSignboardScreen(pos);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        // 列車検知器: 素手で右クリック → 出力先の座標と動作(置く/消す)の設定 GUI。
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be
            && be.getCategory() == InstalledObjectCategory.TRAIN_DETECTOR) {
            if (level.isClientSide) {
                com.portofino.realtrainmodunofficial.ClientHooks.openDetectorConfigScreen(pos);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        // 本家 BlockPoint.onBlockActivated: 素手で右クリック → 転てつを切り替える。
        // レッドストーン出力が反転するので、隣接する分岐器がそのまま動く。
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be
            && be.getCategory() == InstalledObjectCategory.POINT) {
            if (!level.isClientSide) {
                be.setPointActivated(!be.isPointActivated());
                // 本家: 自分と真下の両方に更新を通知する (真下のブロック越しに信号を伝えるため)。
                level.updateNeighborsAt(pos, this);
                level.updateNeighborsAt(pos.below(), this);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.LEVER_CLICK,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.3F, be.isPointActivated() ? 0.6F : 0.5F);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        // 本家 BlockTicketVendor.onBlockActivated: 素手で右クリック → 券売機 GUI (切符/回数券)。
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be
            && be.getCategory() == InstalledObjectCategory.TICKET_VENDOR) {
            if (level.isClientSide) {
                com.portofino.realtrainmodunofficial.ClientHooks.openTicketVendorScreen(pos);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        // 本家 BlockRailroadSign.onBlockActivated: 素手で右クリック → 標識のテクスチャ選択。
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be
            && be.getCategory() == InstalledObjectCategory.RAILROAD_SIGN) {
            if (level.isClientSide) {
                com.portofino.realtrainmodunofficial.ClientHooks.openRailroadSignScreen(pos);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide) {
            updatePoweredState(level, pos);
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    /** 本家 electric: 出力コネクタは配線網の信号レベルをレッドストーン出力する */
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter getter, BlockPos pos,
                            net.minecraft.core.Direction direction) {
        if (getter.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be) {
            if (be.getCategory() == InstalledObjectCategory.CONNECTOR_OUTPUT) {
                return net.minecraft.util.Mth.clamp(be.getElectricity(), 0, 15);
            }
            // 本家 BlockPoint.getWeakPower: 転轍機は切り替わっている間 15 を出す。
            // これで分岐器 (レール) やレッドストーン回路を直接動かせる。
            if (be.getCategory() == InstalledObjectCategory.POINT) {
                return be.isPointActivated() ? 15 : 0;
            }
        }
        return 0;
    }

    /**
     * 本家 BlockPoint.getStrongPower も弱電力と同じ値を返す。
     * これが無いと、転轍機を載せたブロック越しにレッドストーンを引けない。
     */
    @Override
    protected int getDirectSignal(BlockState state, net.minecraft.world.level.BlockGetter getter, BlockPos pos,
                                  net.minecraft.core.Direction direction) {
        if (getter.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be
                && be.getCategory() == InstalledObjectCategory.POINT) {
            return be.isPointActivated() ? 15 : 0;
        }
        return super.getDirectSignal(state, getter, pos, direction);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            updatePoweredState(level, pos);
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            removeSignalLink(level, pos);
            removeAttachedWires(level, pos);
            stopSpeakerSoundOnRemove(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /** スピーカーブロック破壊時、再生中の音を範囲内プレイヤーで停止させる(壊しても鳴り続ける対策)。 */
    private static void stopSpeakerSoundOnRemove(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double cx = pos.getX() + 0.5D, cy = pos.getY() + 0.5D, cz = pos.getZ() + 0.5D;
        var stop = new com.portofino.realtrainmodunofficial.network.SpeakerStopPayload(cx, cy, cz);
        for (net.minecraft.server.level.ServerPlayer p : serverLevel.players()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, stop);
        }
    }

    private static void removeSignalLink(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity) || !blockEntity.isSignal()) {
            return;
        }
        SignalNetworkSavedData.get(serverLevel).removeSignal(serverLevel, pos, blockEntity.getSignalChannel());
    }

    private static void removeAttachedWires(Level level, BlockPos pos) {
        int radius = 64;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    if (!(level.getBlockEntity(checkPos) instanceof InstalledObjectBlockEntity blockEntity)) {
                        continue;
                    }
                    BlockPos start = blockEntity.getWireStart();
                    BlockPos end = blockEntity.getWireEnd();
                    if (pos.equals(start) || pos.equals(end)) {
                        level.removeBlock(checkPos, false);
                    }
                }
            }
        }
    }

    private static void updatePoweredState(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity)) {
            return;
        }
        InstalledObjectCategory cat = blockEntity.getCategory();
        if (cat == InstalledObjectCategory.SPEAKER && !hasDefinitionRunningSound(blockEntity)) {
            updateSpeaker(level, pos, blockEntity);
            return;
        }
        // 照明: レッドストーン信号で点灯/消灯し、ブロック光源レベルを更新する。
        if (cat == InstalledObjectCategory.LIGHT) {
            boolean powered = level.hasNeighborSignal(pos);
            if (blockEntity.isPowered() != powered) {
                blockEntity.setPowered(powered);
                level.getLightEngine().checkBlock(pos);
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
            return;
        }
        if (cat != InstalledObjectCategory.CROSSING && !hasDefinitionRunningSound(blockEntity)) {
            return;
        }
        // hasNeighborSignal はワイヤ隣接などで拾えないことがあるため getBestNeighborSignal(>0) で判定。
        boolean powered = level.getBestNeighborSignal(pos) > 0;
        blockEntity.setPowered(powered);
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }

    private static boolean hasDefinitionRunningSound(InstalledObjectBlockEntity blockEntity) {
        var definition = InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
        String sound = definition == null ? "" : definition.getRunningSound();
        return sound != null && !sound.isBlank();
    }

    private static void updateSpeaker(Level level, BlockPos pos, InstalledObjectBlockEntity blockEntity) {
        // レッドストーン信号強度(1-15)を音源ID(本家踏襲)として使い、立ち上がり(OFF→ON)で鳴らす。
        int signal = level.getBestNeighborSignal(pos);
        boolean wasPowered = blockEntity.isPowered();
        boolean nowPowered = signal > 0;
        blockEntity.setPowered(nowPowered);
        if (level instanceof ServerLevel serverLevel) {
            double cx = pos.getX() + 0.5D;
            double cy = pos.getY() + 0.5D;
            double cz = pos.getZ() + 0.5D;
            if (nowPowered && !wasPowered) {
                // 立ち上がり: 再生 (本家同様 RS 強度 = 音スロット。
                // → 未登録はグローバル設定へフォールバック。
                blockEntity.playSpeakerSound(signal);
            } else if (!nowPowered && wasPowered) {
                // 立ち下がり(レバーOFF): 再生中の音を止める。範囲外プレイヤーにも送って取りこぼしを防ぐ。
                var stop = new com.portofino.realtrainmodunofficial.network.SpeakerStopPayload(cx, cy, cz);
                for (net.minecraft.server.level.ServerPlayer p : serverLevel.players()) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, stop);
                }
            }
        }
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }
}

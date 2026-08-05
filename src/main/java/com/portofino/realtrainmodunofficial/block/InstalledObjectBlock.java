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
import net.minecraft.world.entity.Entity;
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

    // 照明カテゴリかつレッドストーンで点灯中のときブロック光源レベル15を返す。
    // 看板は本家 BlockSignBoard.getLightValue 準拠 (設定の lightValue による)。
    @Override
    public int getLightEmission(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be) {
            int emission = dynamicLightEmission(be);
            if (emission > 0) {
                return emission;
            }
        }
        return super.getLightEmission(state, level, pos);
    }

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


    // ===== 本家の当たり判定 =====
    // 本家は設置物ごとに別のブロッククラスで、それぞれ setBlockBounds を持っている。
    // RTMU は 1 つのブロックに設置物を集約しているので、種類で振り分けて同じ形にする。
    // 値の出典は KaizPatchX の各ブロック (jp.ngt.rtm.block / jp.ngt.rtm.electric)。

    /** 標識 (BlockRailroadSign): 7/16〜9/16 の細い柱。高さ 1.5。 */
    private static final VoxelShape SIGN_SHAPE =
        Shapes.box(7.0D / 16.0D, 0.0D, 7.0D / 16.0D, 9.0D / 16.0D, 1.5D, 9.0D / 16.0D);
    /** 標識: 上に何かある場合は下へ 0.5 伸ばして高さ 1.0 まで (本家 setBlockBoundsBasedOnState)。 */
    private static final VoxelShape SIGN_SHAPE_UNDER =
        Shapes.box(7.0D / 16.0D, -0.5D, 7.0D / 16.0D, 9.0D / 16.0D, 1.0D, 9.0D / 16.0D);
    /** 転てつ機 (BlockPoint): 高さ 5/16 の板。 */
    private static final VoxelShape POINT_SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.3125D, 1.0D);
    /** 遮断機 (BlockCrossingGate): 2/16〜14/16、高さ 3。 */
    private static final VoxelShape CROSSING_SHAPE =
        Shapes.box(0.125D, 0.0D, 0.125D, 0.875D, 3.0D, 0.875D);
    /** 碍子・コネクタ (BlockConnector): 中心の 0.25〜0.75 の小箱。 */
    /**
     * 足場の当たり判定。本家 {@code BlockScaffold.addCollisionBoxToList} と同じで、
     * <b>床 1/16 + 隣が足場でない側の手すり (高さ 1.5)</b> を組む。
     * これが無いと足場の上に立てず、すり抜けてしまう。
     */
    private static final VoxelShape SCAFFOLD_FLOOR = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.0625D, 1.0D);
    private static final VoxelShape SCAFFOLD_RAIL_XP = Shapes.box(0.9375D, 0.0D, 0.0D, 1.0D, 1.5D, 1.0D);
    private static final VoxelShape SCAFFOLD_RAIL_XN = Shapes.box(0.0D, 0.0D, 0.0D, 0.0625D, 1.5D, 1.0D);
    private static final VoxelShape SCAFFOLD_RAIL_ZP = Shapes.box(0.0D, 0.0D, 0.9375D, 1.0D, 1.5D, 1.0D);
    private static final VoxelShape SCAFFOLD_RAIL_ZN = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.5D, 0.0625D);
    /** 階段の壁 (高さ 2.0)。本家 BlockScaffoldStairs.addCollisionBoxToList。 */
    private static final VoxelShape STAIR_WALL_XN = Shapes.box(0.0D, 0.0D, 0.0D, 0.0625D, 2.0D, 1.0D);
    private static final VoxelShape STAIR_WALL_XP = Shapes.box(0.9375D, 0.0D, 0.0D, 1.0D, 2.0D, 1.0D);
    private static final VoxelShape STAIR_WALL_ZN = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 2.0D, 0.0625D);
    private static final VoxelShape STAIR_WALL_ZP = Shapes.box(0.0D, 0.0D, 0.9375D, 1.0D, 2.0D, 1.0D);

    private static final VoxelShape CONNECTOR_SHAPE =
        Shapes.box(0.25D, 0.25D, 0.25D, 0.75D, 0.75D, 0.75D);
    /** 旗 (BlockFlag): 7/16〜9/16 の細い柱、高さ 1。 */
    private static final VoxelShape FLAG_SHAPE =
        Shapes.box(0.4375D, 0.0D, 0.4375D, 0.5625D, 1.0D, 0.5625D);
    /** 改札 (BlockTurnstile): 0.375〜0.625 の薄い壁、高さ 1.5 (向きで軸が変わる)。 */
    private static final VoxelShape TICKET_GATE_SHAPE_X =
        Shapes.box(0.375D, 0.0D, 0.0D, 0.625D, 1.5D, 1.0D);
    private static final VoxelShape TICKET_GATE_SHAPE_Z =
        Shapes.box(0.0D, 0.0D, 0.375D, 1.0D, 1.5D, 0.625D);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity) {
            if (blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
                return EMPTY_SHAPE;
            }
            return shiftToModel(outlineShape(blockEntity.getCategory(), level, pos), blockEntity);
        }
        return RTM_SELECTION_SHAPE;
    }

    /**
     * 信号の当たり判定をモデルと同じ場所へ動かす。
     *
     * <p>本家 {@code ItemSignal} は<b>クリックした柱ブロックそのものを信号に置き換える</b>ので、
     * 当たり判定は必ず見えている信号と同じ位置にある。RTMU は 1 つ手前のブロックに置いて
     * モデルだけ柱の中へ押し込んでいるため、そのままだと当たり判定だけが 1 ブロック
     * ずれた場所に残る (柱に挿したときだけズレる = 置き方で当たり判定が変わる)。
     * 描画オフセットぶん当たり判定も動かすことで、<b>どう置いても信号の当たり判定は常に同じ</b>になる。
     */
    private static VoxelShape shiftToModel(VoxelShape shape, InstalledObjectBlockEntity be) {
        if (be.getCategory() != InstalledObjectCategory.SIGNAL) {
            return shape;
        }
        net.minecraft.world.phys.Vec3 off = be.getRenderOffset();
        if (off == null || (off.x == 0.0D && off.y == 0.0D && off.z == 0.0D)) {
            return shape;
        }
        return shape.move(off.x, off.y, off.z);
    }

    /** 種類ごとの見た目どおりの形 (選択枠)。本家の setBlockBounds と同じ。 */
    private static VoxelShape outlineShape(InstalledObjectCategory category, BlockGetter level, BlockPos pos) {
        if (category == null) {
            return RTM_SELECTION_SHAPE;
        }
        return switch (category) {
            case RAILROAD_SIGN -> level.getBlockState(pos.above()).isAir() ? SIGN_SHAPE : SIGN_SHAPE_UNDER;
            case POINT -> POINT_SHAPE;
            case CROSSING -> CROSSING_SHAPE;
            case INSULATOR, CONNECTOR_INPUT, CONNECTOR_OUTPUT -> connectorShape(level, pos);
            case FLAG -> FLAG_SHAPE;
            case TICKET_GATE -> ticketGateShape(level, pos);
            //★本家 BlockScaffold/BlockScaffoldStairs の選択枠は<b>フルブロック</b>
            //  (setAABB(FULL_BLOCK_AABB) が既定)。床+手すりの複合形状は
            //  addCollisionBoxToList (=衝突) にしか使わない。選択枠へ流用すると
            //  枠が手すり形になり、狙い先も歩行感も本家とズレる。
            case SCAFFOLD, STAIR -> RTM_SELECTION_SHAPE;
            // 信号・架線柱・看板・券売機・スピーカー・照明などは本家も BlockContainer 既定の
            // 1 ブロックのまま (setBlockBounds を持っていない)。
            default -> RTM_SELECTION_SHAPE;
        };
    }

    /**
     * 本家 {@code BlockConnector.getBlockBounds}: 中心 0.25〜0.75 の小箱を
     * <b>取付面の側へ伸ばす</b> (meta%6 = クリック面)。
     */
    private static VoxelShape connectorShape(BlockGetter level, BlockPos pos) {
        int face = -1;
        if (level.getBlockEntity(pos) instanceof com.portofino.realtrainmodunofficial.blockentity
                .InstalledObjectBlockEntity be) {
            face = be.getMountFace();
        }
        double minX = 0.25D, minY = 0.25D, minZ = 0.25D, maxX = 0.75D, maxY = 0.75D, maxZ = 0.75D;
        switch (face) {
            case 0 -> maxY = 1.0D;   //本家 case0: 上へ (1.7.10 の面番号)
            case 1 -> minY = 0.0D;
            case 2 -> maxZ = 1.0D;
            case 3 -> minZ = 0.0D;
            case 4 -> maxX = 1.0D;
            case 5 -> minX = 0.0D;
            default -> {
            }
        }
        return Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** 本家 {@code BlockTurnstile.getSelectedBoundingBox}: 向きで薄い壁の軸が変わる。 */
    private static VoxelShape ticketGateShape(BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof com.portofino.realtrainmodunofficial.blockentity
                .InstalledObjectBlockEntity be) {
            int dir = net.minecraft.util.Mth.floor(be.getYaw() / 90.0F + 0.5F) & 3;
            //本家: meta 0/2 → Z 薄・X 貫通、それ以外 → X 薄・Z 貫通
            return (dir == 0 || dir == 2) ? TICKET_GATE_SHAPE_Z : TICKET_GATE_SHAPE_X;
        }
        return TICKET_GATE_SHAPE_Z;
    }

    /**
     * 本家 {@code BlockScaffold.addCollisionBoxToList} の移植。
     *
     * <p>★手すりは 4 方向に出るのではなく、<b>自分の向き (dir) に対して横になる 2 面</b>だけ。
     * 隣に足場/階段が続いていればその面は出さない。
     */
    private static VoxelShape scaffoldShape(BlockGetter level, BlockPos pos) {
        int dir = scaffoldDir(level, pos);
        boolean b0 = (dir == 0 || dir == 2);

        byte flag0 = connectionType(level, pos.east(), (byte) 1);
        byte flag1 = connectionType(level, pos.west(), (byte) 1);
        byte flag2 = connectionType(level, pos.south(), (byte) 0);
        byte flag3 = connectionType(level, pos.north(), (byte) 0);

        boolean crossZ = (flag2 == 1 || flag3 == 1 || flag2 == 3 || flag3 == 3);
        boolean crossX = (flag0 == 2 || flag1 == 2 || flag0 == 3 || flag1 == 3);

        VoxelShape shape = SCAFFOLD_FLOOR;
        if (!inRange(flag0) && (b0 || crossZ))  shape = Shapes.or(shape, SCAFFOLD_RAIL_XP);
        if (!inRange(flag1) && (b0 || crossZ))  shape = Shapes.or(shape, SCAFFOLD_RAIL_XN);
        if (!inRange(flag2) && (!b0 || crossX)) shape = Shapes.or(shape, SCAFFOLD_RAIL_ZP);
        if (!inRange(flag3) && (!b0 || crossX)) shape = Shapes.or(shape, SCAFFOLD_RAIL_ZN);
        return shape;
    }

    /**
     * 本家 {@code BlockScaffoldStairs.addCollisionBoxToList}: 4 段の階段 + 両側の壁 (高さ 2.0)。
     * 壁は隣に<b>同じ向きの階段</b>が続いていれば出さない。
     */
    private static VoxelShape stairShape(BlockGetter level, BlockPos pos) {
        int dir = jp.ngt.rtm.block.BlockScaffold.dirAt(level, pos);
        VoxelShape shape = Shapes.empty();
        if (dir == 0 || dir == 2) {
            if (jp.ngt.rtm.block.BlockScaffoldStairs.getConnectionType(level, pos.getX() - 1, pos.getY(), pos.getZ(), dir) != 3) {
                shape = Shapes.or(shape, STAIR_WALL_XN);
            }
            if (jp.ngt.rtm.block.BlockScaffoldStairs.getConnectionType(level, pos.getX() + 1, pos.getY(), pos.getZ(), dir) != 3) {
                shape = Shapes.or(shape, STAIR_WALL_XP);
            }
            for (int i = 0; i < 4; ++i) {
                double f0 = i * 0.25D;
                double f1 = (dir == 2) ? f0 : 0.75D - f0;
                shape = Shapes.or(shape, Shapes.box(0.0D, f0, f1, 1.0D, f0 + 0.25D, f1 + 0.25D));
            }
        } else {
            if (jp.ngt.rtm.block.BlockScaffoldStairs.getConnectionType(level, pos.getX(), pos.getY(), pos.getZ() - 1, dir) != 3) {
                shape = Shapes.or(shape, STAIR_WALL_ZN);
            }
            if (jp.ngt.rtm.block.BlockScaffoldStairs.getConnectionType(level, pos.getX(), pos.getY(), pos.getZ() + 1, dir) != 3) {
                shape = Shapes.or(shape, STAIR_WALL_ZP);
            }
            for (int i = 0; i < 4; ++i) {
                double f0 = i * 0.25D;
                double f1 = (dir == 1) ? f0 : 0.75D - f0;
                shape = Shapes.or(shape, Shapes.box(f1, f0, 0.0D, f1 + 0.25D, f0 + 0.25D, 1.0D));
            }
        }
        return shape;
    }

    private static boolean inRange(byte flag) {
        return flag >= 1 && flag <= 3;
    }

    /** ★判定は {@link jp.ngt.rtm.block.BlockScaffold} に一本化する (描画スクリプトと同じ物を使う)。 */
    private static byte connectionType(BlockGetter level, BlockPos pos, byte dir) {
        return jp.ngt.rtm.block.BlockScaffold.getConnectionType(level, pos.getX(), pos.getY(), pos.getZ(), dir);
    }

    private static int scaffoldDir(BlockGetter level, BlockPos pos) {
        return jp.ngt.rtm.block.BlockScaffold.dirAt(level, pos);
    }

    /**
     * 本家 {@code BlockScaffold.modifyAcceleration}: エスカレーター (conveyorSpeed 付きの足場) は
     * 乗っている物を押す。向きは {@code TileEntityScaffold.getMotionVec} と同じで
     * <b>(0,0,speed) を 180 - dir*90 度だけ Y 軸回転</b>。
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be)) {
            return;
        }
        InstalledObjectCategory category = be.getCategory();
        if (category != InstalledObjectCategory.SCAFFOLD && category != InstalledObjectCategory.STAIR) {
            return;
        }
        var definition = be.getDefinition();
        float speed = definition == null ? 0.0F : definition.getConveyorSpeed();
        if (speed == 0.0F) {
            return;
        }
        //本家 TileEntityScaffold.getVec = (0,0,s)。
        //★階段 (TileEntityScaffoldStairs) は<b>斜め上 (0, s·sin45, s·sin45)</b>。
        //  これが無いと上りエスカレーターに乗っても押し上げられず、段で止まる。
        double horizontal = speed;
        double vertical = 0.0D;
        if (category == InstalledObjectCategory.STAIR) {
            double d0 = Math.sin(Math.toRadians(45.0D));
            horizontal = speed * d0;
            vertical = speed * d0;
        }
        int dir = scaffoldDir(level, pos);
        float rad = (float) Math.toRadians(180.0F - (dir * 90.0F));
        double mx = net.minecraft.util.Mth.sin(rad) * horizontal;
        double mz = net.minecraft.util.Mth.cos(rad) * horizontal;
        entity.setDeltaMovement(entity.getDeltaMovement().add(mx, vertical, mz));
    }

    /**
     * ぶつかる判定。本家で {@code getCollisionBoundingBoxFromPool} が null を返す設置物は、
     * <b>すり抜けられるが壊せる</b> (選択枠だけ残る)。蛍光灯がこれ。
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity) {
            if (blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
                return EMPTY_SHAPE;
            }
            InstalledObjectCategory category = blockEntity.getCategory();
            if (category == InstalledObjectCategory.TICKET_GATE && blockEntity.isTicketGateOpen()) {
                return EMPTY_SHAPE;   //本家 BlockTurnstile: 開いていれば null
            }
            if (category == InstalledObjectCategory.FLUORESCENT) {
                return EMPTY_SHAPE;   //本家 BlockFluorescent: 当たり判定なし (壊せはする)
            }
            //★足場/階段の衝突は本家 addCollisionBoxToList の複合形状 (床 1/16 + 手すり 1.5 /
            //  4 段 + 壁 2.0)。選択枠 (getShape) はフルブロックのままにする。
            if (category == InstalledObjectCategory.SCAFFOLD) {
                return scaffoldShape(level, pos);
            }
            if (category == InstalledObjectCategory.STAIR) {
                return stairShape(level, pos);
            }
            return shiftToModel(outlineShape(category, level, pos), blockEntity);
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
            //★本家のコネクタはレッドストーンを出さない (RS との橋渡しは信号変換機)。
            //  出力コネクタは「取り付け先ブロックから信号出力」して配線へ流す側。
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

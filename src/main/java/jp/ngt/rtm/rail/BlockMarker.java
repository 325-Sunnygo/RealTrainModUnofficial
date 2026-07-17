package jp.ngt.rtm.rail;

import com.mojang.serialization.MapCodec;
import jp.ngt.ngtlib.io.NGTLog;
import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.rtm.RTMConfig;
import jp.ngt.rtm.rail.util.RailMaker;
import jp.ngt.rtm.rail.util.RailMapBasic;
import jp.ngt.rtm.rail.util.RailMapSwitch;
import jp.ngt.rtm.rail.util.RailMapTurntable;
import jp.ngt.rtm.rail.util.RailPosition;
import jp.ngt.rtm.rail.util.RailProperty;
import jp.ngt.rtm.rail.util.SwitchType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 本家 jp.ngt.rtm.rail.BlockMarker (KaizPatchX) の忠実移植。
 * metadata (0-7: 下位2bit=方角, bit2=斜め) は META ブロックステートで代替。
 * TODO(Phase 4): ItemRail (レール種選択) / GUI (GuiRailMarker) の接続。
 */
public class BlockMarker extends BaseEntityBlock {
    public static final MapCodec<BlockMarker> CODEC = simpleCodec(props -> new BlockMarker(0, props));

    /**
     * 本家 metadata 相当 (0-7)。ID は既存アセット (blockstates) と互換の "facing"。
     */
    public static final IntegerProperty META = IntegerProperty.create("facing", 0, 7);

    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.0625D, 1.0D);

    /**
     * 0:normal, 1:switch, 10:straight, 11: void
     */
    public final int markerType;

    public BlockMarker(int type, Properties props) {
        super(props
                .strength(0.5F)
                .sound(SoundType.GLASS)
                .lightLevel(state -> 15)
                .noOcclusion()
                .noCollission());
        this.markerType = type;
        this.registerDefaultState(this.stateDefinition.any().setValue(META, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(META);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityMarker(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof TileEntityMarker marker) {
                marker.tick();
            }
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        //本家 onBlockPlacedBy: プレイヤーの向きから 0-7 を算出
        LivingEntity placer = context.getPlayer();
        float yaw = placer != null ? placer.getYRot() : 0.0F;
        int playerFacing = Mth.floor(NGTMath.normalizeAngle(yaw + 180.0D) / 45.0D + 0.5D) & 7;
        playerFacing = playerFacing / 2 + (playerFacing % 2 == 0 ? 0 : 4);
        //Remaster 拡張: 斜め専用マーカーアイテムは斜め向きを強制
        if (context.getItemInHand().getItem() instanceof com.portofino.realtrainmodunofficial.item.MarkerItem mi
                && mi.isDiagonal() && playerFacing < 4) {
            playerFacing += 4;
        }
        return this.defaultBlockState().setValue(META, playerFacing);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        //設置直後に自動プレビュー (距離表示のため)。本家は右クリックで作るが、
        //旧 Remaster 同様「2本目を置いた瞬間に距離が出る」体感に合わせる。
        if (!world.isClientSide && placer instanceof Player player && (this.markerType == 0 || this.markerType == 1)) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof TileEntityMarker marker) {
                marker.tick();//rp 初期化
                this.onMarkerActivated(world, pos.getX(), pos.getY(), pos.getZ(), player, false);
            }
        }
    }

    /**
     * 本家 getMarkerDir(Block, meta) 相当。
     */
    public static byte getMarkerDir(BlockState state) {
        int meta = state.getValue(META);
        int i0 = meta & 3;
        int i1 = ((6 - i0) & 3) * 2;
        if (meta >= 4) {
            i1 = (i1 + 7) & 7;
        }
        return (byte) i1;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack item, BlockState state, Level world, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        BlockEntity tile = world.getBlockEntity(pos);
        if (!(tile instanceof TileEntityMarker marker)) {
            return ItemInteractionResult.SUCCESS;
        }

        if (!item.isEmpty()) {
            //本家: レンチ = makeRailMap + ItemWrench.onRightClickMarker (C/S 両方)
            if (item.getItem() instanceof com.portofino.realtrainmodunofficial.item.RtmWrenchItem wrench
                    && (this.markerType == 0 || this.markerType == 1)) {
                //シフト右クリック = カント設定 GUI (数値入力)
                if (player.isShiftKeyDown()) {
                    if (world.isClientSide) {
                        com.portofino.realtrainmodunofficial.ClientHooks.openMarkerCantScreen(marker);
                    }
                    return ItemInteractionResult.SUCCESS;
                }
                if (!world.isClientSide) {
                    this.makeRailMap(marker, pos.getX(), pos.getY(), pos.getZ(), player);
                }
                wrench.onRightClickMarker(item, world, player, marker);
                return ItemInteractionResult.SUCCESS;
            } else if (Block.byItem(item.getItem()) instanceof BlockMarker && (this.markerType == 0 || this.markerType == 1)) {
                if (!world.isClientSide) {
                    this.makeRailMap(marker, pos.getX(), pos.getY(), pos.getZ(), player);
                }
                //TODO(Phase 4): GuiRailMarker を開く
                return ItemInteractionResult.SUCCESS;
            }
        }

        if (!world.isClientSide) {
            if (this.onMarkerActivated(world, pos.getX(), pos.getY(), pos.getZ(), player, true)) {
                if (!player.getAbilities().instabuild) {
                    item.shrink(1);
                }
            }
        }

        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!world.isClientSide) {
            this.onMarkerActivated(world, pos.getX(), pos.getY(), pos.getZ(), player, true);
        }
        return InteractionResult.SUCCESS;
    }

    public void makeRailMap(TileEntityMarker marker, int x, int y, int z, Player player) {
        if (marker.startY < 0) {
            this.onMarkerActivated(marker.getLevel(), x, y, z, player, false);
        } else {
            this.onMarkerActivated(marker.getLevel(), marker.startX, marker.startY, marker.startZ, player, false);
        }
    }

    public boolean onMarkerActivated(Level world, int x, int y, int z, Player player, boolean makeRail) {
        RailProperty prop = this.hasRail(player, makeRail);
        if (prop != null) {
            int type = this.markerType;
            int dis1 = RTMConfig.railGeneratingDistance;
            int dis3 = dis1 * dis1;
            int hei1 = RTMConfig.railGeneratingHeight;
            boolean isCreative = player == null || player.getAbilities().instabuild;

            if (type == 0 || type == 10) {
                RailPosition rpS = this.getRailPosition(world, x, y, z);

                List<TileEntityMarker> markers = MarkerManager.getMarkers(world);
                if (!markers.isEmpty()) {
                    RailPosition rpE = markers.stream()
                            .filter(tile -> tile.getMarkerRP() != null && tile.getMarkerRP() != rpS)
                            .filter(tile -> tile.getBlockState().getBlock() == this)
                            .filter(tile -> tile.getDistanceFrom(x, tile.getBlockPos().getY(), z) < dis3)
                            .filter(tile -> Math.abs(tile.getBlockPos().getY() - y) < hei1)
                            .sorted(Comparator.comparingInt(o -> Math.abs(o.getBlockPos().getY() - y)))
                            .min(Comparator.comparingDouble(o -> o.getDistanceFrom(x, y, z)))
                            .map(TileEntityMarker::getMarkerRP)
                            .orElse(null);
                    if (rpS != null && rpE != null) {
                        if (type == 10) {
                            Vec3 eS = new Vec3(rpE.posX - rpS.posX, rpE.posY - rpS.posY, rpE.posZ - rpS.posZ);
                            Vec3 sE = new Vec3(rpS.posX - rpE.posX, rpS.posY - rpE.posY, rpS.posZ - rpE.posZ);
                            rpS.anchorYaw = eS.getYaw();
                            rpS.anchorPitch = eS.getPitch();
                            rpE.anchorYaw = sE.getYaw();
                            rpE.anchorPitch = sE.getPitch();
                        }
                        //RTMU: 敷設プレイヤーの設定で自動カント (カーブ) / 自動高さを適用
                        com.portofino.realtrainmodunofficial.RtmuAutoRail.applyTwo(world, player, rpS, rpE);
                        return createRail0(world, rpS, rpE, prop, makeRail, isCreative);
                    }
                }
            } else if (type == 1) {
                List<TileEntityMarker> markers = MarkerManager.getMarkers(world);
                List<RailPosition> list = markers.stream()
                        .filter(tile -> {
                            Block b = tile.getBlockState().getBlock();
                            return b == RTMRailBlocks.MARKER.get() || b == RTMRailBlocks.MARKER_SWITCH.get();
                        })
                        .filter(tile -> tile.getDistanceFrom(x, tile.getBlockPos().getY(), z) < dis3)
                        .filter(tile -> Math.abs(tile.getBlockPos().getY() - y) < hei1)
                        .sorted(Comparator.comparingInt(o -> Math.abs(o.getBlockPos().getY() - y)))
                        .map(TileEntityMarker::getMarkerRP)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList());

                if (list.size() == 2 && list.stream().allMatch(rp -> rp.switchType == 1)) {
                    return createTurntable(world, list.get(0), list.get(1), prop, makeRail, isCreative);
                }
                if (list.size() >= 3) {
                    //RTMU: 分岐レールへ自動高さを適用 (自動カントは 2 点レールのみ)
                    com.portofino.realtrainmodunofficial.RtmuAutoRail.applyList(player, list);
                    return createRail1(world, x, y, z, player, list, prop, makeRail, isCreative);
                }
            }
        }
        return false;
    }

    public static boolean createRail(Level world, int x, int y, int z, List<RailPosition> rps, RailProperty prop, boolean makeRail, boolean isCreative) {
        rps = rps.stream().sorted(Comparator.comparingInt(o -> o.blockY)).collect(Collectors.toList());
        if (rps.size() == 2) {
            if (rps.stream().allMatch(rp -> rp.switchType == 1)) {
                return createTurntable(world, rps.get(0), rps.get(1), prop, makeRail, isCreative);
            } else {
                return createRail0(world, rps.get(0), rps.get(1), prop, makeRail, isCreative);
            }
        } else if (rps.size() > 2) {
            return createRail1(world, x, y, z, null, rps, prop, makeRail, isCreative);
        }
        return false;
    }

    /**
     * 通常のレール<br>
     * y0 <= y1でなければならない
     */
    /**
     * そのブロックに<b>別のレールのコア</b>が既に居るか。
     * <p>
     * コアはレールの全情報 (RailPosition / RailProperty) を持っている唯一のブロックで、
     * ここを {@code setBlock} で潰すと<b>そのレールが丸ごと消える</b>。通常マーカーは
     * マーカーブロックの位置にコアを置くので衝突しないが、ペンマーカーは既存レールの端に
     * 吸着できるため、吸着先がそのレールのコアだと踏み抜く。
     */
    public static boolean hasRailCore(Level world, int x, int y, int z) {
        return world.getBlockEntity(new BlockPos(x, y, z)) instanceof TileEntityLargeRailCore;
    }

    /**
     * ソートせずに 2 点でレールを敷く (ペンマーカー用)。
     * <p>
     * {@link #createRail} は blockY で並べ替えるため「どちらの点がコアになるか」を呼び出し側で
     * 決められない。ペンは既存レールのコアを踏まない点をコアにしたいので、順序を保つ入口を出す。
     * 曲線は両端の anchorYaw から作られ、どちらを始点にしても同じ形になる。
     */
    public static boolean createRailKeepOrder(Level world, RailPosition start, RailPosition end,
                                              RailProperty prop, boolean makeRail, boolean isCreative) {
        return createRail0(world, start, end, prop, makeRail, isCreative);
    }

    /** ソートせずに分岐/クロスを敷く (ペンマーカー用)。コアは (x,y,z) に置かれる。 */
    public static boolean createSwitchAt(Level world, int x, int y, int z, List<RailPosition> list,
                                         RailProperty prop, boolean makeRail, boolean isCreative) {
        return createRail1(world, x, y, z, null, list, prop, makeRail, isCreative);
    }

    private static boolean createRail0(Level world, RailPosition start, RailPosition end, RailProperty prop, boolean makeRail, boolean isCreative) {
        RailMapBasic railMap = new RailMapBasic(start, end, RailMapBasic.fixRTMRailMapVersionCurrent);

        //コアの位置に別レールのコアが居るなら、置いた瞬間にそのレールが消える。敷設を中止する。
        if (hasRailCore(world, start.blockX, start.blockY, start.blockZ)) {
            return false;
        }

        if (makeRail && railMap.canPlaceRail(world, isCreative, prop)) {
            railMap.setRail(world, RTMRailBlocks.LARGE_RAIL_BASE.get(), start.blockX, start.blockY, start.blockZ, prop);

            BlockPos corePos = new BlockPos(start.blockX, start.blockY, start.blockZ);
            world.setBlock(corePos, RTMRailBlocks.LARGE_RAIL_NORMAL_CORE.get().defaultBlockState(), 2);
            TileEntityLargeRailCore tile = (TileEntityLargeRailCore) world.getBlockEntity(corePos);
            tile.setRailPositions(new RailPosition[]{start, end});
            tile.setProperty(prop);
            tile.setStartPoint(start.blockX, start.blockY, start.blockZ);
            tile.setFixRTMRailMapVersion(railMap.fixRTMRailMapVersion);

            tile.createRailMap();
            tile.setChanged();
            BlockState st = world.getBlockState(corePos);
            world.sendBlockUpdated(corePos, st, st, 3);

            BlockPos endPos = new BlockPos(end.blockX, end.blockY, end.blockZ);
            if (world.getBlockState(endPos).getBlock() instanceof BlockMarker) {
                world.setBlock(endPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }

            return true;
        } else {
            BlockEntity tile = world.getBlockEntity(new BlockPos(start.blockX, start.blockY, start.blockZ));
            if (tile instanceof TileEntityMarker) {
                List<int[]> list = new ArrayList<>();
                list.add(new int[]{start.blockX, start.blockY, start.blockZ});
                list.add(new int[]{end.blockX, end.blockY, end.blockZ});
                ((TileEntityMarker) tile).setMarkersPos(list);
            }
            return false;
        }
    }

    /**
     * 分岐レール
     */
    private static boolean createRail1(Level world, int x, int y, int z, Player player, List<RailPosition> list, RailProperty prop, boolean makeRail, boolean isCreative) {
        //コアの位置に別レールのコアが居るなら、置いた瞬間にそのレールが消える。敷設を中止する。
        if (hasRailCore(world, x, y, z)) {
            return false;
        }
        RailMaker railMaker = new RailMaker(world, list, RailMapBasic.fixRTMRailMapVersionCurrent);
        SwitchType st = railMaker.getSwitch();
        if (st == null) {
            if (player != null) {
                NGTLog.sendChatMessage(player, "message.rail.switch_type");
            }
            return false;
        }
        RailMapSwitch[] arrayOfRailMapSwitch = st.getAllRailMap();
        if (arrayOfRailMapSwitch == null) {
            return false;
        }
        boolean flag = false;
        for (RailMapSwitch railMapSwitch : arrayOfRailMapSwitch) {
            flag = !railMapSwitch.canPlaceRail(world, isCreative, prop);
        }
        if (!makeRail || flag) {
            BlockEntity tileEntity = world.getBlockEntity(new BlockPos(x, y, z));
            if (tileEntity instanceof TileEntityMarker) {
                List<int[]> posList = new ArrayList<>();
                for (RailPosition rp : list) {
                    posList.add(new int[]{rp.blockX, rp.blockY, rp.blockZ});
                }
                ((TileEntityMarker) tileEntity).setMarkersPos(posList);
            }
            return false;
        }
        for (RailMapSwitch railMapSwitch : arrayOfRailMapSwitch) {
            railMapSwitch.setRail(world, RTMRailBlocks.LARGE_RAIL_BASE.get(), x, y, z, prop);
        }
        for (RailPosition rp : list) {
            //既存レールのコアの上に分岐ベースを置くと、そのレールが消える。触らない。
            if (hasRailCore(world, rp.blockX, rp.blockY, rp.blockZ)) {
                continue;
            }
            BlockPos rpPos = new BlockPos(rp.blockX, rp.blockY, rp.blockZ);
            world.setBlock(rpPos, RTMRailBlocks.LARGE_RAIL_SWITCH_BASE.get().defaultBlockState(), 3);
            TileEntityLargeRailSwitchBase switchBase = (TileEntityLargeRailSwitchBase) world.getBlockEntity(rpPos);
            switchBase.setStartPoint(x, y, z);
        }
        BlockPos corePos = new BlockPos(x, y, z);
        world.setBlock(corePos, RTMRailBlocks.LARGE_RAIL_SWITCH_CORE.get().defaultBlockState(), 3);
        TileEntityLargeRailSwitchCore tile = (TileEntityLargeRailSwitchCore) world.getBlockEntity(corePos);
        tile.setRailPositions(list.toArray(new RailPosition[0]));
        tile.setProperty(prop);
        tile.setStartPoint(x, y, z);
        tile.setFixRTMRailMapVersion(railMaker.fixRTMRailMapVersion);
        tile.createRailMap();
        tile.setChanged();
        BlockState state = world.getBlockState(corePos);
        world.sendBlockUpdated(corePos, state, state, 3);
        return true;
    }

    private static boolean createTurntable(Level world, RailPosition start, RailPosition end, RailProperty prop, boolean makeRail, boolean isCreative) {
        int cx = 0;
        int cy = start.blockY;
        int cz = 0;
        int r = 0;

        if (start.blockX == end.blockX && (start.blockZ - end.blockZ) % 2 == 0) {
            cx = start.blockX;
            cz = (start.blockZ + end.blockZ) / 2;
            r = Math.abs(start.blockZ - end.blockZ) / 2;
        }

        if (start.blockZ == end.blockZ && (start.blockX - end.blockX) % 2 == 0) {
            cx = (start.blockX + end.blockX) / 2;
            cz = start.blockZ;
            r = Math.abs(start.blockX - end.blockX) / 2;
        }

        if (r == 0) {
            return false;
        }

        RailMapTurntable railMap = new RailMapTurntable(start, end, cx, cy, cz, r, RailMapBasic.fixRTMRailMapVersionCurrent);
        if (makeRail && railMap.canPlaceRail(world, isCreative, prop)) {
            railMap.setRail(world, RTMRailBlocks.LARGE_RAIL_BASE.get(), cx, cy, cz, prop);

            BlockPos corePos = new BlockPos(cx, cy, cz);
            world.setBlock(corePos, RTMRailBlocks.TURNTABLE_CORE.get().defaultBlockState(), 3);
            TileEntityTurnTableCore tile = (TileEntityTurnTableCore) world.getBlockEntity(corePos);
            tile.setRailPositions(new RailPosition[]{start, end});
            tile.setProperty(prop);
            tile.setStartPoint(cx, cy, cz);
            tile.setFixRTMRailMapVersion(railMap.fixRTMRailMapVersion);

            tile.createRailMap();
            tile.setChanged();
            BlockState state = world.getBlockState(corePos);
            world.sendBlockUpdated(corePos, state, state, 3);

            return true;
        }

        return false;
    }

    private RailPosition getRailPosition(Level world, int x, int y, int z) {
        BlockEntity tile = world.getBlockEntity(new BlockPos(x, y, z));
        if (tile instanceof TileEntityMarker) {
            return ((TileEntityMarker) tile).getMarkerRP();
        }
        return null;
    }

    /**
     * 最後にレールアイテムから使われた RailProperty。
     * 素手でマーカーを右クリックしてレールを敷く際に再利用する (ユーザー要望)。
     */
    private static RailProperty lastUsedProperty;

    public static void setLastUsedProperty(RailProperty prop) {
        if (prop != null && prop.railModel != null && !prop.railModel.isEmpty()) {
            lastUsedProperty = prop;
        }
    }

    public static RailProperty getLastUsedProperty() {
        return lastUsedProperty;
    }

    /**
     * 本家: 手持ちの ItemRail から RailProperty を取得。
     * TODO(Phase 4): ItemRail 移植後に接続。暫定: Remaster の RailItem 選択中モデルを反映。
     */
    public RailProperty hasRail(Player player, boolean par2) {
        if (player == null) {
            return RailProperty.getDefaultProperty();
        }

        ItemStack item = player.getInventory().getSelected();
        //本家 ItemRail (Property NBT 保持)
        if (!item.isEmpty() && item.getItem() instanceof jp.ngt.rtm.item.ItemRail) {
            RailProperty prop = jp.ngt.rtm.item.ItemRail.getProperty(item);
            if (prop != null) {
                setLastUsedProperty(prop);
                return prop;
            }
            return RailProperty.getDefaultProperty();
        }
        //Remaster RailItem: アイテム自身の選択モデル ID を最優先 (本家 ItemRail.getProperty(item) 相当)
        if (!item.isEmpty() && item.getItem() instanceof com.portofino.realtrainmodunofficial.item.RailItem) {
            String model = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedModelId(item);
            if (model == null || model.isEmpty()) {
                com.portofino.realtrainmodunofficial.rail.RailDefinition def =
                        com.portofino.realtrainmodunofficial.rail.RailRegistry.getSelected();
                model = def != null ? def.getId() : "";
            }
            RailProperty prop = new RailProperty(model == null ? "" : model,
                    net.minecraft.world.level.block.Blocks.GRAVEL, 0, 0.0625F);
            setLastUsedProperty(prop);
            return prop;
        }

        if (player.getAbilities().instabuild || !par2) {
            //空手: 最後に使ったレールを最優先 (ユーザー要望)、
            //無ければ 選択中 or 先頭のレール定義 (railModel="" だと描画もスクリプトも無い)
            if (lastUsedProperty != null) {
                return lastUsedProperty;
            }
            com.portofino.realtrainmodunofficial.rail.RailDefinition def =
                    com.portofino.realtrainmodunofficial.rail.RailRegistry.getSelected();
            if (def == null) {
                java.util.List<com.portofino.realtrainmodunofficial.rail.RailDefinition> all =
                        com.portofino.realtrainmodunofficial.rail.RailRegistry.getAll();
                def = all.isEmpty() ? null : all.get(0);
            }
            if (def != null) {
                return new RailProperty(def.getId(), net.minecraft.world.level.block.Blocks.GRAVEL, 0, 0.0625F);
            }
            return RailProperty.getDefaultProperty();
        }

        return null;
    }
}

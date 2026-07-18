package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.RailCollisionBlockEntity;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import jp.ngt.rtm.rail.util.RailMap;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

public class TrainItem extends Item {
    private static final double PLACEMENT_OCCUPANCY_HALF_WIDTH = 0.45D;

    public enum Category {
        ELECTRIC,
        DIESEL,
        TEST
    }

    private final Category category;

    public TrainItem() {
        this(Category.ELECTRIC);
    }

    public TrainItem(Category category) {
        super(new Properties());
        this.category = category == null ? Category.ELECTRIC : category;
    }

    /** スポーンクールダウン: 0.2秒 = 4 ticks */
    private static final int SPAWN_COOLDOWN_TICKS = 4;

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            boolean canSpawn = findNearestRailSpawn(level, context.getClickedPos(), context.getClickLocation(), player.getYRot()) != null;
            if (canSpawn) {
                //専用サーバー保険 (アイテム同期に一切依存しない確実版): 設置直前に、クライアントが
                //持っている選択IDをサーバーへ送る。この選択パケットは使用パケット (UseItemOn) より
                //先に送られる (startPrediction はクライアント側 useOn を実行してから使用パケットを送る)
                //ので、サーバーの useOn は ServerVehicleSelection から確実に読める。Bukkit 系で
                //アイテムのカスタムデータが剥がれても選択が効く。
                ItemStack held = context.getItemInHand();
                String sel = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedModelId(held);
                if (sel != null && !sel.isBlank()) {
                    String dm = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedDataMap(held);
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.portofino.realtrainmodunofficial.network.SelectModelPayload(sel, dm == null ? "" : dm));
                }
            }
            return canSpawn ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        // クールダウン中はスポーン不可
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        //自動車 (CarItem) と同じく getSelectedModelId で読む。これはモダンコンポーネントが
        //null のとき minecraft:custom_data NBT (LEGACY_MODEL_NAME) にフォールバックする。
        //専用サーバー (Youer/Mohist 等 Bukkit 系ハイブリッド) はカスタムデータコンポーネントを
        //ItemStack 変換で剥がすが custom_data NBT は保持するため、これで選択が生き残る。
        //(以前は stack.get(SELECTED_MODEL_ID) だけで、剥がれて全列車が先頭=223系5000番台Tcに)。
        String itemSel = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedModelId(stack);
        String serverSel = com.portofino.realtrainmodunofficial.vehicle.ServerVehicleSelection.get(player.getUUID());
        String selectedId = (itemSel == null || itemSel.isBlank()) ? serverSel : itemSel;
        VehicleDefinition def = VehicleRegistry.getById(selectedId);
        boolean fellBack = def == null || !accepts(def);
        if (fellBack) {
            def = VehicleRegistry.getAll().stream()
                .filter(this::accepts)
                .findFirst()
                .orElse(null);
        }
        if (fellBack) {
            //診断 (専用サーバーの「全列車が223になる」問題の切り分け用): フォールバック時だけ残す。
            RealTrainModUnofficial.LOGGER.warn("[RTMU] train fell back to default: itemSel={}, serverSel={}, resolved={}",
                itemSel == null || itemSel.isBlank() ? "-" : itemSel,
                serverSel == null || serverSel.isBlank() ? "-" : serverSel,
                def == null ? "-" : def.getId());
            //223 フォールバックの主因は「サーバーに選択した列車パックが入っていない」こと
            //(README 同意ゲートが専用サーバーでパックを弾いていた。PackConsent で修正済み)。
            //serverSel が出ているのに getById=null なら = サーバーにそのパックが無い。
            //serverSel が空なら = 選択がサーバーに届いていない (旧クライアント/パケット遮断)。
            String detail = (serverSel != null && !serverSel.isBlank())
                ? "サーバーに『" + serverSel + "』のパックが入っていません (サーバーの mods/packs を確認)"
                : "選択がサーバーに届いていません (クライアント/サーバーの RTMU 更新を確認)";
            player.displayClientMessage(Component.literal("[RTMU] 既定車両を配置: " + detail), false);
        }
        if (def == null) {
            return InteractionResult.PASS;
        }
        // 本家 ItemTrain.onItemUse 準拠 (Phase 2: jp.ngt.rtm.entity.train.EntityTrain をスポーン)
        BlockPos cp = context.getClickedPos();
        RailMap rm0 = jp.ngt.rtm.rail.TileEntityLargeRailBase.getRailMapFromCoordinates(
                level, player, cp.getX(), cp.getY(), cp.getZ());
        if (rm0 == null) {
            player.displayClientMessage(Component.translatable("message.realtrainmodunofficial.train.must_be_on_rail"), true);
            return InteractionResult.FAIL;
        }

        //本家: 半径16の既存車両との干渉チェック
        int r = 16;
        List<net.minecraft.world.entity.Entity> list = level.getEntities(player,
                new net.minecraft.world.phys.AABB(cp.getX() - r, cp.getY() - 4, cp.getZ() - r,
                        cp.getX() + r + 1, cp.getY() + 8, cp.getZ() + r + 1));
        for (net.minecraft.world.entity.Entity entity : list) {
            if (entity instanceof jp.ngt.rtm.entity.train.EntityTrainBase
                    || entity instanceof jp.ngt.rtm.entity.train.EntityBogie) {
                double distanceSq = entity.distanceToSqr(cp.getX(), cp.getY(), cp.getZ());
                float f0 = jp.ngt.rtm.modelpack.cfg.TrainConfigAdapter.get(def.getId()).trainDistance + 4.0F;
                RailMap rm1 = jp.ngt.rtm.rail.TileEntityLargeRailBase.getRailMapFromCoordinates(
                        level, player, entity.getX(), entity.getY(), entity.getZ());
                if (distanceSq < f0 * f0 && rm0.equals(rm1)) {
                    player.displayClientMessage(Component.translatable("message.realtrainmodunofficial.train.already_exists"), true);
                    return InteractionResult.FAIL;
                }
            }
        }

        int i0 = rm0.getNearlestPoint(128, cp.getX() + 0.5D, cp.getZ() + 0.5D);
        float yw0 = Mth.wrapDegrees(rm0.getRailYaw(128, i0));
        //本家 ItemTrain: yaw = fixBogieYaw(-playerYaw, railYaw)
        //
        //fixBogieYaw は「レールの向き (yw0) か、その 180 度反対か」のうち第1引数に近い方を返す。
        //つまり第1引数に 180 を足すと<b>選ばれる向きが反転する</b>。ここには本家に無い +180 が
        //入っており、そのせいで本家と逆向きに列車が置かれていた。本家どおり -playerYaw を渡す。
        float yaw = jp.ngt.rtm.entity.train.EntityBogie.fixBogieYaw(
                Mth.wrapDegrees(-player.getYRot()), yw0);
        float pitch = jp.ngt.rtm.entity.train.EntityBogie.fixBogiePitch(rm0.getRailPitch(128, i0), yw0, yaw);
        double posX = rm0.getRailPos(128, i0)[1];
        double posY = rm0.getRailHeight(128, i0) + jp.ngt.rtm.entity.train.EntityTrainBase.TRAIN_HEIGHT;
        double posZ = rm0.getRailPos(128, i0)[0];

        jp.ngt.rtm.entity.train.EntityTrain train =
                new jp.ngt.rtm.entity.train.EntityTrain(jp.ngt.rtm.entity.RTMEntities.TRAIN.get(), level);
        train.moveTo(posX, posY, posZ, yaw, pitch);
        train.setModelName(def.getId());
        train.spawnTrain(level);
        // クールダウン付与（サーバー側。クライアントにも自動同期される）
        player.getCooldowns().addCooldown(this, SPAWN_COOLDOWN_TICKS);
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            if (!isLookingAtBlock(level, player)) {
                ClientHooks.openTrainSelectScreen(player, player.getItemInHand(hand), category);
            }
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    private static boolean isLookingAtBlock(Level level, Player player) {
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 end = start.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
        HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String selectedId = stack.get(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get());
        if (selectedId != null && !selectedId.isBlank()) {
            VehicleDefinition def = VehicleRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            tooltip.add(Component.translatable("tooltip.realtrainmodunofficial.model.selected", name));
        } else {
            tooltip.add(Component.translatable("tooltip.realtrainmodunofficial.model.none"));
        }
    }

    public Category getCategory() {
        return category;
    }

    public boolean accepts(VehicleDefinition definition) {
        return accepts(category, definition);
    }

    public static boolean accepts(Category category, VehicleDefinition definition) {
        if (definition == null || definition.isCarType()) {
            return false;
        }
        String type = safe(definition.getVehicleType()).toUpperCase(java.util.Locale.ROOT);
        boolean test = "TEST".equals(type);
        return switch (category == null ? Category.ELECTRIC : category) {
            case TEST -> test;
            case DIESEL -> false;
            case ELECTRIC -> !test;
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isOccupiedSpawnArea(Level level, double x, double y, double z, float yaw, VehicleDefinition def) {
        return isOccupiedSpawnArea(level, x, y, z, yaw, def, null);
    }

    private boolean isOccupiedSpawnArea(Level level, double x, double y, double z, float yaw, VehicleDefinition def,
                                        jp.ngt.rtm.rail.util.RailMap spawnMap) {
        double halfLength = Math.max(1.75D, def.getTrainDistance());
        for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
            halfLength = Math.max(halfLength, Math.abs(bogie.position().z) + 0.95D);
        }
        for (Vec3 seat : def.getAllSeatPositions()) {
            halfLength = Math.max(halfLength, Math.abs(seat.z) + 0.95D);
        }
        double halfWidth = PLACEMENT_OCCUPANCY_HALF_WIDTH;
        double radius = Math.max(halfLength, halfWidth) + 1.0D;
        var bounds = new net.minecraft.world.phys.AABB(
            x - radius,
            y - 0.75D,
            z - radius,
            x + radius,
            y + 4.0D,
            z + radius
        );
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            TrainEntity.purgeDanglingTrainResidue(serverLevel, bounds);
        }
        final double finalHalfLength = halfLength;
        final double finalHalfWidth = halfWidth;
        List<TrainEntity> overlaps = level.getEntitiesOfClass(TrainEntity.class, bounds, entity -> entity != null && entity.isAlive() && !entity.isRemoved()).stream()
            .filter(entity -> Math.abs(entity.getY() - y) <= 3.5D)
            // 別レール上の列車は占有とみなさない（隣接レールへの真横配置を許可）。
            .filter(entity -> spawnMap == null || entity.getActiveRailMap() == null || entity.getActiveRailMap() == spawnMap)
            .filter(entity -> rectanglesOverlap(
                x, z, yaw, finalHalfWidth, finalHalfLength,
                entity.getX(), entity.getZ(), entity.getYRot(),
                PLACEMENT_OCCUPANCY_HALF_WIDTH,
                entity.getBodyHalfLengthForPlacement() * 0.9D
            ))
            .toList();
        if (!overlaps.isEmpty()) {
            logSpawnOccupancy(level, "train_item", x, y, z, yaw, finalHalfWidth, finalHalfLength, overlaps);
            return true;
        }
        return false;
    }

    private static void logSpawnOccupancy(Level level, String source, double x, double y, double z, float yaw, double halfWidth, double halfLength, List<TrainEntity> overlaps) {
        StringBuilder entities = new StringBuilder();
        for (TrainEntity entity : overlaps) {
            if (entities.length() > 0) {
                entities.append(" | ");
            }
            entities.append(String.format(
                Locale.ROOT,
                "%s uuid=%s pos=(%.2f,%.2f,%.2f) yaw=%.1f half=(%.2f,%.2f) removed=%s alive=%s",
                entity.getVehicleId(),
                entity.getUUID(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getYRot(),
                entity.getBodyHalfWidthForPlacement(),
                entity.getBodyHalfLengthForPlacement(),
                entity.isRemoved(),
                entity.isAlive()
            ));
        }
        RealTrainModUnofficial.LOGGER.warn(
            String.format(
                Locale.ROOT,
                "Spawn blocked [%s] level=%s spawn=(%.2f,%.2f,%.2f) yaw=%.1f half=(%.2f,%.2f) overlaps=%s",
                source,
                level.dimension().location(),
                x, y, z, yaw, halfWidth, halfLength,
                entities
            )
        );
    }

    private static boolean rectanglesOverlap(
        double ax, double az, float ayaw, double ahx, double ahz,
        double bx, double bz, float byaw, double bhx, double bhz
    ) {
        double[][] axes = new double[][]{
            axisFromYaw(ayaw),
            perpendicularAxisFromYaw(ayaw),
            axisFromYaw(byaw),
            perpendicularAxisFromYaw(byaw)
        };
        double dx = bx - ax;
        double dz = bz - az;
        for (double[] axis : axes) {
            double centerProjection = Math.abs(dx * axis[0] + dz * axis[1]);
            double aExtent = projectedExtent(ahx, ahz, ayaw, axis);
            double bExtent = projectedExtent(bhx, bhz, byaw, axis);
            if (centerProjection > aExtent + bExtent) {
                return false;
            }
        }
        return true;
    }

    private static double[] axisFromYaw(float yaw) {
        double yawRad = Math.toRadians(-yaw);
        return new double[]{Math.cos(yawRad), Math.sin(yawRad)};
    }

    private static double[] perpendicularAxisFromYaw(float yaw) {
        double[] axis = axisFromYaw(yaw);
        return new double[]{-axis[1], axis[0]};
    }

    private static double projectedExtent(double halfWidth, double halfLength, float yaw, double[] axis) {
        double[] forward = axisFromYaw(yaw);
        double[] side = perpendicularAxisFromYaw(yaw);
        return Math.abs(axis[0] * side[0] + axis[1] * side[1]) * halfWidth
            + Math.abs(axis[0] * forward[0] + axis[1] * forward[1]) * halfLength;
    }

    private static double getSpawnHalfWidth(VehicleDefinition def) {
        double halfWidth = 1.1D;
        for (Vec3 seat : def.getAllSeatPositions()) {
            halfWidth = Math.max(halfWidth, Math.abs(seat.x) + 0.3D);
        }
        for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
            halfWidth = Math.max(halfWidth, Math.abs(bogie.position().x) + 0.45D);
        }
        return halfWidth;
    }

    private static RailSpawnData findNearestRailSpawn(Level level, BlockPos clickedPos, Vec3 clickedPoint, float preferredYaw) {
        RailMap clickedRailMap = getRailMapAt(level, clickedPos, clickedPoint);
        if (clickedRailMap != null) {
            return findNearestPointOnMap(clickedRailMap, clickedPoint, preferredYaw);
        }

        RailSpawnData best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        int radius = 16;
        double cx = clickedPoint.x;
        double cy = clickedPoint.y;
        double cz = clickedPoint.z;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = clickedPos.offset(dx, dy, dz);
                    RailMap map = getRailMapAt(level, p, clickedPoint);
                    if (map == null) continue;
                    int max = getSpawnSplit(map);
                    for (int i = 0; i <= max; i++) {
                        double[] pos = map.getRailPos(max, i);
                        double x = pos[1];
                        double y = map.getRailHeight(max, i);
                        double z = pos[0];
                        double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
                        if (d2 < bestDistSq) {
                            bestDistSq = d2;
                            best = createSpawnData(map, max, i, preferredYaw);
                        }
                    }
                }
            }
        }
        // 吸着距離を約2ブロックに制限する。以前は 8 ブロック(64)圏内の最寄りレールを掴むため、
        // 隣の平行レールに列車が吸い付いていた (ユーザー報告)。クリックしたレール付近にのみ置く。
        if (bestDistSq <= 4.0 && best != null) {
            return best;
        }
        // フォールバック: 検出ブロック(当たり判定/道床)が無いレール(ballastWidth=0 や本修正前に
        // 敷設した既存レール)は、上のブロック走査ではコア(=端)から16ブロック以内しか見つからず
        // 「レール端しか列車が置けない」状態になる。周辺チャンクの BlockEntity からレールコアを
        // 直接拾い、クリック近傍を通るレールを探すことで、コア位置・レール長・敷設時期に依らず
        // レール全長のどこでも設置できるようにする(本家RTM準拠の挙動)。
        return scanNearbyCoresForSpawn(level, clickedPos, clickedPoint, preferredYaw);
    }

    /**
     * クリック位置周辺のチャンクに含まれるレールコア(LargeRailCoreBlockEntity)を直接走査し、
     * クリック点に最も近いレール上の点(約2ブロック以内)を返す。検出用の当たり判定ブロックが
     * 無いレールでも、コアの位置に依らずレール全長のどこでも列車を設置できるようにするための保険。
     */
    private static RailSpawnData scanNearbyCoresForSpawn(Level level, BlockPos clickedPos, Vec3 clickedPoint, float preferredYaw) {
        final int chunkRadius = 3; // ±3 チャンク(約 112 ブロック)を走査。コアは疎なので軽量。
        int baseChunkX = clickedPos.getX() >> 4;
        int baseChunkZ = clickedPos.getZ() >> 4;
        RailSpawnData best = null;
        double bestDistSq = 4.0; // 吸着しきい値(約2ブロック)
        java.util.Set<BlockPos> visitedCores = new java.util.HashSet<>();
        for (int cx = baseChunkX - chunkRadius; cx <= baseChunkX + chunkRadius; cx++) {
            for (int cz = baseChunkZ - chunkRadius; cz <= baseChunkZ + chunkRadius; cz++) {
                net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (!(chunk instanceof net.minecraft.world.level.chunk.LevelChunk levelChunk)) {
                    continue;
                }
                for (net.minecraft.world.level.block.entity.BlockEntity be : levelChunk.getBlockEntities().values()) {
                    RailMap[] coreMaps = null;
                    BlockPos corePos = null;
                    if (be instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
                        coreMaps = core.getAllRailMaps();
                        corePos = core.getBlockPos();
                    } else if (be instanceof jp.ngt.rtm.rail.TileEntityLargeRailCore newCore && newCore.isLoaded()) {
                        //jp.ngt.rtm.rail (Phase 1 本家忠実システム)
                        coreMaps = newCore.getAllRailMaps();
                        corePos = newCore.getBlockPos();
                    }
                    if (coreMaps == null || corePos == null) {
                        continue;
                    }
                    if (!visitedCores.add(corePos)) {
                        continue;
                    }
                    for (RailMap map : coreMaps) {
                        if (map == null) continue;
                        int max = getSpawnSplit(map);
                        for (int i = 0; i <= max; i++) {
                            double[] pos = map.getRailPos(max, i);
                            double dx = pos[1] - clickedPoint.x;
                            double dy = map.getRailHeight(max, i) - clickedPoint.y;
                            double dz = pos[0] - clickedPoint.z;
                            double d2 = dx * dx + dy * dy + dz * dz;
                            if (d2 < bestDistSq) {
                                bestDistSq = d2;
                                best = createSpawnData(map, max, i, preferredYaw);
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private static RailMap getRailMapAt(Level level, BlockPos pos, Vec3 targetPoint) {
        if (level.getBlockEntity(pos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
            return getNearestRailMap(core, targetPoint);
        }
        if (level.getBlockEntity(pos) instanceof RailCollisionBlockEntity collision) {
            BlockPos corePos = collision.getCorePos();
            if (corePos != null && level.getBlockEntity(corePos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
                return getNearestRailMap(core, targetPoint);
            }
        }
        // 道床ブロック(BallastBlock)もレールコアを保持する。道床はカーブ全長に敷かれるため、
        // レール中央の道床をクリックしても列車設置のレール判定が効く (ユーザー報告対応)。
        if (level.getBlockEntity(pos) instanceof com.portofino.realtrainmodunofficial.blockentity.BallastBlockEntity ballast) {
            BlockPos corePos = ballast.getCorePos();
            if (corePos != null && level.getBlockEntity(corePos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
                return getNearestRailMap(core, targetPoint);
            }
        }
        // jp.ngt.rtm.rail (Phase 1 本家忠実システム): ベース/コアどちらもコア経由で解決
        if (level.getBlockEntity(pos) instanceof jp.ngt.rtm.rail.TileEntityLargeRailBase railBase) {
            jp.ngt.rtm.rail.TileEntityLargeRailCore core = railBase.getRailCore();
            if (core != null && core.isLoaded()) {
                return getNearestRailMapNew(core, targetPoint);
            }
        }
        return null;
    }

    private static RailMap getNearestRailMapNew(jp.ngt.rtm.rail.TileEntityLargeRailCore core, Vec3 targetPoint) {
        RailMap[] maps = core.getAllRailMaps();
        if (maps == null || maps.length == 0) return null;
        if (maps.length == 1) return maps[0];
        RailMap best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (RailMap map : maps) {
            if (map == null) continue;
            int max = getSpawnSplit(map);
            for (int i = 0; i <= max; i++) {
                double[] posData = map.getRailPos(max, i);
                double x = posData[1];
                double y = map.getRailHeight(max, i);
                double z = posData[0];
                double d2 = (x - targetPoint.x) * (x - targetPoint.x) + (y - targetPoint.y) * (y - targetPoint.y) + (z - targetPoint.z) * (z - targetPoint.z);
                if (d2 < bestDistSq) {
                    bestDistSq = d2;
                    best = map;
                }
            }
        }
        return best;
    }

    private static RailMap getNearestRailMap(LargeRailCoreBlockEntity core, Vec3 targetPoint) {
        RailMap[] maps = core.getAllRailMaps();
        if (maps.length == 0) return null;
        if (maps.length == 1) return maps[0];
        double cx = targetPoint.x;
        double cy = targetPoint.y;
        double cz = targetPoint.z;
        RailMap best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (RailMap map : maps) {
            int max = getSpawnSplit(map);
            for (int i = 0; i <= max; i++) {
                double[] posData = map.getRailPos(max, i);
                double x = posData[1];
                double y = map.getRailHeight(max, i);
                double z = posData[0];
                double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
                if (d2 < bestDistSq) {
                    bestDistSq = d2;
                    best = map;
                }
            }
        }
        return best;
    }

    private static RailSpawnData findNearestPointOnMap(RailMap map, Vec3 clickedPoint, float preferredYaw) {
        if (map == null) return null;
        RailSpawnData best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        int max = getSpawnSplit(map);
        double cx = clickedPoint.x;
        double cy = clickedPoint.y;
        double cz = clickedPoint.z;
        for (int i = 0; i <= max; i++) {
            double[] pos = map.getRailPos(max, i);
            double x = pos[1];
            double y = map.getRailHeight(max, i);
            double z = pos[0];
            double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = createSpawnData(map, max, i, preferredYaw);
            }
        }
        return best;
    }

    private static RailSpawnData createSpawnData(RailMap map, int max, int index, float preferredYaw) {
        double[] p = map.getRailPos(max, index);
        double x = p[1];
        double y = map.getRailHeight(max, index);
        double z = p[0];
        float yaw = choosePreferredRailYaw(map.getRailYaw(max, index), preferredYaw);
        return new RailSpawnData(map, max, index, x, y, z, yaw);
    }

    private static float choosePreferredRailYaw(float railYaw, float preferredYaw) {
        float forwardDiff = Math.abs(Mth.wrapDegrees(railYaw - preferredYaw));
        float reverseYaw = Mth.wrapDegrees(railYaw + 180.0F);
        float reverseDiff = Math.abs(Mth.wrapDegrees(reverseYaw - preferredYaw));
        return reverseDiff < forwardDiff ? reverseYaw : railYaw;
    }

    private static int getSpawnSplit(RailMap map) {
        if (map == null) {
            return 64;
        }
        return Math.max(96, RailMap.curveSplitForLength(map.getHorizontalPathLength()) * 6);
    }

    private record RailSpawnData(RailMap map, int split, int index, double x, double y, double z, float yaw) {
    }
}

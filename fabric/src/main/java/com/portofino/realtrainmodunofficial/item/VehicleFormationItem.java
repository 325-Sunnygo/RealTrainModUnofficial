package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.List;
import java.util.ArrayList;

public class VehicleFormationItem extends Item {
    private static final double FORMATION_CLEARANCE = 0.25D;

    public VehicleFormationItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        TrainFormation formation = getFormation(stack);
        
        if (formation.vehicles.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.realtrainmodunofficial.formation.empty"), true);
            return InteractionResult.FAIL;
        }

        // Find rail spawn location
        RailSpawnData spawnData = findNearestRailSpawn(level, context.getClickedPos());
        if (spawnData == null) {
            player.displayClientMessage(Component.translatable("message.realtrainmodunofficial.train.must_be_on_rail"), true);
            return InteractionResult.FAIL;
        }

        // Spawn train formation
        spawnFormation(level, player, formation, spawnData);
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            ClientHooks.openVehicleFormationScreen(player.getItemInHand(hand));
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        TrainFormation formation = getFormation(stack);
        int size = formation.vehicles.size();
        if (size > 0) {
            tooltip.add(Component.translatable("tooltip.realtrainmodunofficial.formation.size", size));
            tooltip.add(Component.translatable("tooltip.realtrainmodunofficial.formation.vehicles", 
                formation.vehicles.stream().map(v -> v.name).reduce((a, b) -> a + ", " + b).orElse("")));
        } else {
            tooltip.add(Component.translatable("tooltip.realtrainmodunofficial.formation.empty"));
        }
    }

    public static TrainFormation getFormation(ItemStack stack) {
        TrainFormation formation = new TrainFormation();
        CompoundTag tag = stack.get(RealTrainModUnofficialComponents.TRAIN_FORMATION.get());
        if (tag != null && tag.contains("vehicles", 9)) { // 9 = ListTag
            ListTag vehiclesList = tag.getList("vehicles", 8); // 8 = StringTag
            for (int i = 0; i < vehiclesList.size() && i < 30; i++) {
                String vehicleId = vehiclesList.getString(i);
                VehicleDefinition def = VehicleRegistry.getById(vehicleId);
                if (def != null) {
                    formation.vehicles.add(new VehicleEntry(vehicleId, def.getDisplayName()));
                }
            }
        }
        return formation;
    }

    public static void setFormation(ItemStack stack, TrainFormation formation) {
        CompoundTag tag = new CompoundTag();
        ListTag vehiclesList = new ListTag();
        for (VehicleEntry vehicle : formation.vehicles) {
            vehiclesList.add(StringTag.valueOf(vehicle.id));
        }
        tag.put("vehicles", vehiclesList);
        stack.set(RealTrainModUnofficialComponents.TRAIN_FORMATION.get(), tag);
    }

    private void spawnFormation(Level level, Player player, TrainFormation formation, RailSpawnData spawnData) {
        double currentX = spawnData.x;
        double currentY = spawnData.y + 1.0;
        double currentZ = spawnData.z;
        float currentYaw = spawnData.yaw;
        
        
        List<TrainEntity> spawnedTrains = new ArrayList<>();
        double previousHalfLength = 0.0D;
        
        for (int i = 0; i < formation.vehicles.size(); i++) {
            VehicleEntry entry = formation.vehicles.get(i);
            VehicleDefinition def = VehicleRegistry.getById(entry.id);
            if (def == null) continue;
            double halfLength = estimateTrainHalfLength(def);
            if (i > 0) {
                double centerGap = previousHalfLength + halfLength + FORMATION_CLEARANCE;
                double yawRad = Math.toRadians(currentYaw);
                currentX += Math.sin(yawRad) * centerGap;
                currentZ -= Math.cos(yawRad) * centerGap;
            }
            double offsetY = Math.max(0.0, def.getModelOffset().y);
            
            if (isOccupiedSpawnArea(level, currentX, currentY + offsetY, currentZ, halfLength)) {
                player.displayClientMessage(Component.literal("編成に含まれる車両のスポーン位置に既に電車があります。"), true);
                return;
            }

            TrainEntity train = TrainEntity.create(level, entry.id, currentX, currentY + offsetY, currentZ, currentYaw, def.getTrainDistance());
            if (train != null) {
                level.addFreshEntity(train);
                spawnedTrains.add(train);
                previousHalfLength = halfLength;
            }
        }
        
        // Link trains together (simplified connection)
        for (int i = 1; i < spawnedTrains.size(); i++) {
            // TODO: Implement proper train connection system
        }
    }

    private boolean isOccupiedSpawnArea(Level level, double x, double y, double z, double halfLength) {
        double radius = Math.max(1.75D, halfLength);
        var bounds = new net.minecraft.world.phys.AABB(
            x - radius,
            y - 1.0,
            z - radius,
            x + radius,
            y + 3.0,
            z + radius
        );
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            TrainEntity.purgeDanglingTrainResidue(serverLevel, bounds);
        }
        return !level.getEntitiesOfClass(TrainEntity.class, bounds, entity -> entity != null && entity.isAlive() && !entity.isRemoved()).isEmpty();
    }

    private static double estimateTrainHalfLength(VehicleDefinition def) {
        double halfLength = Math.max(1.75D, def.getTrainDistance());
        for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
            halfLength = Math.max(halfLength, Math.abs(bogie.position().z) + 0.95D);
        }
        for (Vec3 seat : def.getAllSeatPositions()) {
            halfLength = Math.max(halfLength, Math.abs(seat.z) + 0.95D);
        }
        return halfLength;
    }

    private RailSpawnData findNearestRailSpawn(Level level, BlockPos clickedPos) {
        // クリックした位置からレールを検索
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = clickedPos.offset(dx, dy, dz);
                    var blockState = level.getBlockState(pos);
                    if (blockState.getBlock() instanceof com.portofino.realtrainmodunofficial.block.LargeRailCoreBlock) {
                        var blockEntity = level.getBlockEntity(pos);
                        if (blockEntity instanceof com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity railEntity) {
                            // レールの方向をRailPositionから取得
                            jp.ngt.rtm.rail.util.RailPosition[] railPositions = railEntity.getRailPositions();
                            float yaw = 0;
                            if (railPositions != null && railPositions.length > 0 && railPositions[0] != null) {
                                yaw = railPositions[0].anchorYaw;
                            }
                            // レールの中心に配置
                            return new RailSpawnData(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw);
                        }
                    }
                }
            }
        }
        // レールが見つからない場合はクリックした位置に配置
        return new RailSpawnData(clickedPos.getX() + 0.5, clickedPos.getY(), clickedPos.getZ() + 0.5, 0);
    }

    public static class TrainFormation {
        public final List<VehicleEntry> vehicles = new ArrayList<>();
        
        public void addVehicle(String id, String name) {
            if (vehicles.size() < 30) {
                vehicles.add(new VehicleEntry(id, name));
            }
        }
        
        public void removeVehicle(int index) {
            if (index >= 0 && index < vehicles.size()) {
                vehicles.remove(index);
            }
        }
        
        public void clear() {
            vehicles.clear();
        }
    }

    public static class VehicleEntry {
        public final String id;
        public final String name;
        
        public VehicleEntry(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private record RailSpawnData(double x, double y, double z, float yaw) {}
}

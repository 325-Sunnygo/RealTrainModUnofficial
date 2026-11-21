package jp.ngt.rtm.block.tileentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import jp.ngt.ngtlib.block.BlockSet;
import jp.ngt.ngtlib.block.NGTObject;
import jp.ngt.rtm.entity.EntityMMBoundingBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * 移動装置。本家 {@code jp.ngt.rtm.block.tileentity.TileEntityMovingMachine} の移植。
 *
 * <p>2 つ設置してバールで繋ぐと、その 2 点を結ぶ向きへ、指定した大きさのブロックの塊を運ぶ。
 * 運んでいる間はブロックを消して {@link EntityMMBoundingBox} を置き、着いたら戻す。
 * どちらの端にレッドストーン入力が来ているかで進む向きが決まる。
 *
 * <p>★本家の「乗り物生成器」(メタ 1 / {@code generateVehicle}) は未移植。
 */
public class TileEntityMovingMachine extends BlockEntity {

    public boolean guideVisibility = true;
    public int width = 1;
    public int height = 1;
    public int depth = 1;
    public int offsetX;
    public int offsetY;
    public int offsetZ;
    public float speed = 0.0625F;

    public int pairBlockX;
    public int pairBlockY;
    public int pairBlockZ;
    public boolean isCore;

    public double posX;
    public double posY;
    public double posZ;
    public double prevPosX;
    public double prevPosY;
    public double prevPosZ;

    /** 1 = 相手側へ / -1 = 元へ / 0 = 停止 / -2 = 次の tick で戻す */
    public byte moveDir;

    private double motionX;
    private double motionY;
    private double motionZ;
    private final List<EntityMMBoundingBox> bbList = new ArrayList<>();

    /** 運んでいるブロックの塊。動かしていない間は null。 */
    public NGTObject blocksObject;

    public TileEntityMovingMachine(BlockPos pos, BlockState state) {
        super(RealTrainModUnofficialBlockEntities.MOVING_MACHINE.get(), pos, state);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        this.width = nbt.getInt("width");
        this.height = nbt.getInt("height");
        this.depth = nbt.getInt("depth");
        this.offsetX = nbt.getInt("offsetX");
        this.offsetY = nbt.getInt("offsetY");
        this.offsetZ = nbt.getInt("offsetZ");
        this.speed = nbt.contains("speed") ? nbt.getFloat("speed") : 0.0625F;
        this.guideVisibility = !nbt.contains("guide") || nbt.getBoolean("guide");
        this.pairBlockX = nbt.getInt("pairX");
        this.pairBlockY = nbt.getInt("pairY");
        this.pairBlockZ = nbt.getInt("pairZ");
        this.isCore = nbt.getBoolean("isCore");
        this.posX = nbt.getDouble("posX");
        this.posY = nbt.getDouble("posY");
        this.posZ = nbt.getDouble("posZ");
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.moveDir = nbt.getByte("moveDir");
        this.vehicleType = nbt.getInt("vehicleType");
        if (nbt.contains("blocks")) {
            this.blocksObject = NGTObject.readFromNBT(nbt.getCompound("blocks"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        nbt.putInt("width", this.width);
        nbt.putInt("height", this.height);
        nbt.putInt("depth", this.depth);
        nbt.putInt("offsetX", this.offsetX);
        nbt.putInt("offsetY", this.offsetY);
        nbt.putInt("offsetZ", this.offsetZ);
        nbt.putFloat("speed", this.speed);
        nbt.putBoolean("guide", this.guideVisibility);
        nbt.putInt("pairX", this.pairBlockX);
        nbt.putInt("pairY", this.pairBlockY);
        nbt.putInt("pairZ", this.pairBlockZ);
        nbt.putBoolean("isCore", this.isCore);
        nbt.putDouble("posX", this.posX);
        nbt.putDouble("posY", this.posY);
        nbt.putDouble("posZ", this.posZ);
        nbt.putByte("moveDir", this.moveDir);
        nbt.putInt("vehicleType", this.vehicleType);
        if (this.blocksObject != null) {
            nbt.put("blocks", this.blocksObject.writeToNBT());
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }

    // ───── 毎 tick ─────

    public void tick() {
        if (!this.isCore || this.level == null) {
            return;
        }

        if (this.moveDir == 1 || this.moveDir == -1) {
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;
            this.posX += this.motionX;
            this.posY += this.motionY;
            this.posZ += this.motionZ;

            boolean arrived = false;
            if (this.moveDir == 1) {
                if (Math.abs(this.posX) > Math.abs(this.pairBlockX)) { this.posX = this.pairBlockX; arrived = true; }
                if (Math.abs(this.posY) > Math.abs(this.pairBlockY)) { this.posY = this.pairBlockY; arrived = true; }
                if (Math.abs(this.posZ) > Math.abs(this.pairBlockZ)) { this.posZ = this.pairBlockZ; arrived = true; }
            } else {
                if (Math.abs(this.pairBlockX - this.posX) > Math.abs(this.pairBlockX)) { this.posX = 0.0D; arrived = true; }
                if (Math.abs(this.pairBlockY - this.posY) > Math.abs(this.pairBlockY)) { this.posY = 0.0D; arrived = true; }
                if (Math.abs(this.pairBlockZ - this.posZ) > Math.abs(this.pairBlockZ)) { this.posZ = 0.0D; arrived = true; }
            }

            this.moveEntities();

            if (arrived && !this.level.isClientSide()) {
                this.setMovement((byte) 0);
            }
        } else if (this.moveDir == -2) {
            this.moveDir = 0;
            this.editBlock(1);
            this.onBlockChanged();
        }
    }

    private void moveEntities() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        double mX = this.posX - this.prevPosX;
        double mY = this.posY - this.prevPosY;
        double mZ = this.posZ - this.prevPosZ;
        //本家: 先に全部の箱を動かしてから、当たり判定を回す
        for (EntityMMBoundingBox entity : this.bbList) {
            entity.setPos(entity.getX() + mX, entity.getY() + mY, entity.getZ() + mZ);
        }
        for (EntityMMBoundingBox entity : this.bbList) {
            entity.moveMM(mX, mY, mZ);
        }
    }

    // ───── レッドストーン ─────

    /** サーバー専用。両端の信号を見て進む向きを決める。 */
    public void onBlockChanged() {
        if (!this.hasPair() || this.level == null) {
            return;
        }
        if (!this.isCore) {
            TileEntityMovingMachine core = this.getCore();
            if (core != this) {
                core.onBlockChanged();
            }
            return;
        }
        boolean atStart = this.level.hasNeighborSignal(this.getBlockPos());
        boolean atEnd = this.level.hasNeighborSignal(
            this.getBlockPos().offset(this.pairBlockX, this.pairBlockY, this.pairBlockZ));
        byte md = 0;
        if (atEnd && !atStart) {
            md = 1;
        } else if (atStart && !atEnd) {
            md = -1;
        }
        if (this.moveDir != md) {
            this.setMovement(md);
        }
    }

    public void setMovement(byte dir) {
        this.moveDir = dir;
        if (dir == 0) {
            this.motionX = this.motionY = this.motionZ = 0.0D;
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;
            this.editBlock(1);
        } else {
            Vec3 vec = new Vec3(this.pairBlockX, this.pairBlockY, this.pairBlockZ).normalize();
            double d0 = (dir == 1) ? 1.0D : -1.0D;
            this.motionX = vec.x * this.speed * d0;
            this.motionY = vec.y * this.speed * d0;
            this.motionZ = vec.z * this.speed * d0;
            this.editBlock(0);
        }
        this.sync();
    }

    /**
     * @param mode 0 = ブロックを消して箱を置く / 1 = ブロックを戻す
     */
    private void editBlock(int mode) {
        if (this.level == null || (mode == 1 && this.blocksObject == null)) {
            return;
        }
        int x0 = this.getBlockPos().getX() + (int) this.posX + 1 + this.offsetX;
        int y0 = this.getBlockPos().getY() + (int) this.posY + 1 + this.offsetY;
        int z0 = this.getBlockPos().getZ() + (int) this.posZ + 1 + this.offsetZ;

        if (mode == 0) {
            List<BlockSet> list = new ArrayList<>();
            //本家と同じ 2 パス: 先に全部読んでから消す (途中で崩れないように)
            for (int j = 0; j < this.height; ++j) {
                for (int i = 0; i < this.width; ++i) {
                    for (int k = 0; k < this.depth; ++k) {
                        BlockPos pos = new BlockPos(x0 + i, y0 + j, z0 + k);
                        BlockState st = this.level.getBlockState(pos);
                        BlockEntity be = this.level.getBlockEntity(pos);
                        CompoundTag beTag = be == null ? null
                            : be.saveWithFullMetadata(this.level.registryAccess());
                        list.add(new BlockSet(pos.getX(), pos.getY(), pos.getZ(), st, beTag));
                        if (!this.level.isClientSide()) {
                            this.spawnBoundingBox(pos);
                        }
                    }
                }
            }
            if (!this.level.isClientSide()) {
                for (int j = 0; j < this.height; ++j) {
                    for (int i = 0; i < this.width; ++i) {
                        for (int k = 0; k < this.depth; ++k) {
                            this.level.setBlock(new BlockPos(x0 + i, y0 + j, z0 + k),
                                Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
            this.blocksObject = NGTObject.createNGTO(list, this.width, this.height, this.depth, x0, y0, z0);
        } else {
            if (!this.level.isClientSide()) {
                int count = 0;
                for (int j = 0; j < this.height; ++j) {
                    for (int i = 0; i < this.width; ++i) {
                        for (int k = 0; k < this.depth; ++k) {
                            if (count < this.blocksObject.blockList.size()) {
                                BlockSet set = this.blocksObject.blockList.get(count);
                                if (set != null && set.block != Blocks.AIR) {
                                    this.level.setBlock(new BlockPos(x0 + i, y0 + j, z0 + k),
                                        set.state != null ? set.state : set.block.defaultBlockState(), 3);
                                }
                            }
                            ++count;
                        }
                    }
                }
                for (EntityMMBoundingBox entity : this.bbList) {
                    entity.discard();
                }
                this.bbList.clear();
            }
            this.blocksObject = null;
        }
    }

    /** そのブロックに当たり判定があるなら、動かしている間ぶんの箱を置く。 */
    private void spawnBoundingBox(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        VoxelShape shape = state.getCollisionShape(this.level, pos);
        if (shape.isEmpty()) {
            return;
        }
        AABB aabb = shape.bounds();
        boolean topFree = this.level.getBlockState(pos.above()).isAir();
        EntityMMBoundingBox entity = new EntityMMBoundingBox(this.level);
        entity.setPos(pos.getX(), pos.getY(), pos.getZ());
        entity.setShape(aabb, topFree);
        this.level.addFreshEntity(entity);
        this.bbList.add(entity);
    }

    // ───── ペア ─────

    public void setData(int w, int h, int d, int ox, int oy, int oz, float speed, boolean guide) {
        this.width = Math.max(1, w);
        this.height = Math.max(1, h);
        this.depth = Math.max(1, d);
        this.offsetX = ox;
        this.offsetY = oy;
        this.offsetZ = oz;
        this.speed = speed;
        this.guideVisibility = guide;
        this.sync();
    }

    public boolean hasPair() {
        return !(this.pairBlockX == 0 && this.pairBlockY == 0 && this.pairBlockZ == 0);
    }

    public TileEntityMovingMachine getPair() {
        if (this.level == null) {
            return null;
        }
        BlockPos pos = this.getBlockPos().offset(this.pairBlockX, this.pairBlockY, this.pairBlockZ);
        return this.level.getBlockEntity(pos) instanceof TileEntityMovingMachine tile ? tile : null;
    }

    public void setPair(TileEntityMovingMachine other) {
        this.pairBlockX = other.getBlockPos().getX() - this.getBlockPos().getX();
        this.pairBlockY = other.getBlockPos().getY() - this.getBlockPos().getY();
        this.pairBlockZ = other.getBlockPos().getZ() - this.getBlockPos().getZ();
        this.sync();
    }

    /** 本家 searchMM: 近くの未接続の移動装置を探して繋ぐ。 */
    public void searchMM() {
        if (this.level == null) {
            return;
        }
        BlockPos self = this.getBlockPos();
        int range = 128;
        for (int i = -range; i < range; ++i) {
            for (int k = -range; k < range; ++k) {
                for (int j = this.level.getMinBuildHeight(); j < this.level.getMaxBuildHeight(); ++j) {
                    if (i == 0 && k == 0 && j == self.getY()) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(self.getX() + i, j, self.getZ() + k);
                    if (this.level.getBlockEntity(pos) instanceof TileEntityMovingMachine tile
                        && !tile.hasPair()) {
                        this.isCore = true;
                        this.setPair(tile);
                        tile.setPair(this);
                        return;
                    }
                }
            }
        }
    }

    public TileEntityMovingMachine getCore() {
        if (this.isCore || !this.hasPair()) {
            return this;
        }
        TileEntityMovingMachine pair = this.getPair();
        return pair == null ? this : pair;
    }

    public void reset(boolean withPair) {
        if (withPair && this.hasPair()) {
            TileEntityMovingMachine pair = this.getPair();
            if (pair != null) {
                pair.reset(false);
            }
        }
        //運んでいる途中なら戻してから解除する
        if (this.blocksObject != null) {
            this.editBlock(1);
        }
        this.isCore = false;
        this.pairBlockX = this.pairBlockY = this.pairBlockZ = 0;
        this.posX = this.posY = this.posZ = 0.0D;
        this.motionX = this.motionY = this.motionZ = 0.0D;
        this.moveDir = 0;
        this.sync();
    }

    /** 描画用。運んでいる塊は元の位置からこれだけずれている。 */
    public Vec3 getRenderOffset(float partialTick) {
        return new Vec3(
            this.prevPosX + (this.posX - this.prevPosX) * partialTick,
            this.prevPosY + (this.posY - this.prevPosY) * partialTick,
            this.prevPosZ + (this.posZ - this.prevPosZ) * partialTick);
    }

    /** 乗り物生成器: 作る乗り物の種類 (0=車 / 1=船 / 2=飛行機)。 */
    public int vehicleType;

    /**
     * 本家 generateVehicle: 範囲のブロックを NGTO に写して消し、その姿の乗り物を湧かせる。
     */
    public void generateVehicle(net.minecraft.world.entity.player.Player player) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        int startX = this.getBlockPos().getX() + 1 + this.offsetX;
        int startY = this.getBlockPos().getY() + 1 + this.offsetY;
        int startZ = this.getBlockPos().getZ() + 1 + this.offsetZ;

        java.util.List<BlockSet> list = new java.util.ArrayList<>();
        for (int j = 0; j < this.height; ++j) {
            for (int i = 0; i < this.width; ++i) {
                for (int k = 0; k < this.depth; ++k) {
                    BlockPos pos = new BlockPos(startX + i, startY + j, startZ + k);
                    BlockState st = this.level.getBlockState(pos);
                    list.add(new BlockSet(i, j, k, st, null));
                }
            }
        }
        NGTObject ngto = NGTObject.createNGTO(list, this.width, this.height, this.depth, 0, 0, 0);
        for (int j = 0; j < this.height; ++j) {
            for (int i = 0; i < this.width; ++i) {
                for (int k = 0; k < this.depth; ++k) {
                    this.level.setBlock(new BlockPos(startX + i, startY + j, startZ + k),
                        Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        jp.ngt.rtm.entity.vehicle.EntityVehicle vehicle = switch (this.vehicleType) {
            case 1 -> new jp.ngt.rtm.entity.vehicle.EntityShip(this.level);
            case 2 -> new jp.ngt.rtm.entity.vehicle.EntityPlane(this.level);
            default -> new jp.ngt.rtm.entity.vehicle.EntityCar(this.level);
        };
        vehicle.setPos(this.getBlockPos().getX() + 0.5D,
            this.getBlockPos().getY() + 0.5D, this.getBlockPos().getZ() + 0.5D);
        jp.ngt.rtm.entity.vehicle.VehicleNGTO obj =
            new jp.ngt.rtm.entity.vehicle.VehicleNGTO(ngto, 0.5F, 0.5F, 0.5F, 1.0F);
        //乗る位置は塊の上に置く (本家は GUI で調整するが、まず実用値)
        obj.riderPosY = this.height + 0.5F;
        vehicle.setNGTO(obj);
        this.level.addFreshEntity(vehicle);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileEntityMovingMachine tile) {
        tile.tick();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TileEntityMovingMachine tile) {
        tile.tick();
    }
}

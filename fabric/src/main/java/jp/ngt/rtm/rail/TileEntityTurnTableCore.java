package jp.ngt.rtm.rail;

import jp.ngt.rtm.rail.util.RailMapTurntable;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 本家 jp.ngt.rtm.rail.TileEntityTurnTableCore (KaizPatchX) の忠実移植。
 * TODO(Phase 2): updateTrainYaw (回転中の列車追従) は EntityTrainBase/BogieController 移植後に接続。
 * TODO(Phase 1 flip): 回転値のクライアント通知 (本家 PacketNotice "TT:") は markBlockForUpdate の
 * フル NBT 同期で代替中。
 */
public class TileEntityTurnTableCore extends TileEntityLargeRailCore {
    public static final float ROTATION_INC = 0.5F;
    public static final float ROTATION_STEP = 15.0F;

    private boolean isGettingPower;
    private float prevRotation;
    private float rotation;

    public TileEntityTurnTableCore(BlockPos pos, BlockState state) {
        super(RTMRailBlockEntities.TURNTABLE_CORE.get(), pos, state);
    }

    @Override
    protected void readRailData(CompoundTag nbt) {
        super.readRailData(nbt);
        this.rotation = nbt.getFloat("Rotation");
    }

    @Override
    protected void writeRailData(CompoundTag nbt) {
        super.writeRailData(nbt);
        nbt.putFloat("Rotation", this.rotation);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level == null) {
            return;
        }
        boolean b = this.level.hasNeighborSignal(this.worldPosition);
        if (this.isGettingPower ^ b) {
            this.isGettingPower = b;
        }

        if (!this.level.isClientSide) {
            float f0 = this.rotation % ROTATION_STEP;
            if (this.isGettingPower || (f0 != 0.0F)) {
                this.rotation += ROTATION_INC;
                if (this.rotation >= 360.0F) {
                    this.rotation = 0.0F;
                }
                this.setChanged();
                this.markBlockForUpdate();

                RailMap rm = this.getRailMap(null);
                if (rm instanceof RailMapTurntable) {
                    ((RailMapTurntable) rm).setRotation(this.rotation);
                }
                //TODO(Phase 2): updateTrainYaw()
            }
        } else {
            float f0 = this.rotation % ROTATION_STEP;
            if (!(this.isGettingPower || (f0 != 0.0F))) {
                this.prevRotation = this.rotation;
            }
        }
    }

    @Override
    public void createRailMap() {
        if (this.isLoaded())//同期ができてない状態でのRailMapの生成を防ぐ
        {
            RailPosition start = this.railPositions[0];
            RailPosition end = this.railPositions[1];
            int r = 0;
            if (start.blockX == end.blockX) {
                r = Math.abs(start.blockZ - end.blockZ) / 2;
            } else if (start.blockZ == end.blockZ) {
                r = Math.abs(start.blockX - end.blockX) / 2;
            }
            this.railmap = new RailMapTurntable(start, end,
                    this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), r, fixRTMRailMapVersion);
            if (!this.level.isClientSide) {
                ((RailMapTurntable) this.railmap).setRotation(this.rotation);
            }
        }
    }

    public float getRotation() {
        return this.rotation;
    }

    public void setRotation(float rotation) {
        this.prevRotation = this.rotation;
        this.rotation = rotation;
    }

    public float getPrevRotation() {
        return this.prevRotation;
    }

    @Override
    protected AABB getRenderAABB() {
        if (this.isLoaded()) {
            int startX = this.railPositions[0].blockX;
            int startZ = this.railPositions[0].blockZ;
            int endX = this.railPositions[1].blockX;
            int endZ = this.railPositions[1].blockZ;
            int lenHalf = (startX == endX) ? Math.abs(endZ - startZ) / 2 : Math.abs(endX - startX) / 2;
            int x = this.worldPosition.getX();
            int y = this.worldPosition.getY();
            int z = this.worldPosition.getZ();
            return new AABB(x - lenHalf, y, z - lenHalf, x + lenHalf + 1, y + 3, z + lenHalf + 1);
        }
        return null;
    }

    @Override
    public String getRailShapeName() {
        RailMap map = this.getRailMap(null);
        return "Type:TurnTable, " +
                "X:" + (map.getEndRP().blockX - map.getStartRP().blockX) + ", " +
                "Y:" + (map.getEndRP().blockY - map.getStartRP().blockY) + ", " +
                "Z:" + (map.getEndRP().blockZ - map.getStartRP().blockZ);
    }
}

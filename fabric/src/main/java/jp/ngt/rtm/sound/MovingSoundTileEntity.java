package jp.ngt.rtm.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 本家 {@code jp.ngt.rtm.sound.MovingSoundTileEntity} の移植。
 * 設置物 (TileEntity) に追従するループ音。スクリプトが {@code func_73660_a} を上書きする。
 */
public class MovingSoundTileEntity extends MovingSoundCustom<BlockEntity> {

    public MovingSoundTileEntity(BlockEntity tileEntity, ResourceLocation sound, boolean repeat) {
        this(tileEntity, sound, repeat, 16.0F);
    }

    public MovingSoundTileEntity(BlockEntity tileEntity, ResourceLocation sound, boolean repeat, float range) {
        super(tileEntity, sound, repeat, range);
        BlockPos pos = tileEntity.getBlockPos();
        this.x = pos.getX() + 0.5D;
        this.y = pos.getY() + 0.5D;
        this.z = pos.getZ() + 0.5D;
    }

    @Override
    public void func_73660_a() {
        // 本家: タイルが消えたら停止
        if (this.entity.isRemoved()) {
            this.stop();
            return;
        }
        this.updatePosition();
        super.func_73660_a();
    }

    @Override
    protected void updatePosition() {
        if (this.entity.getLevel() == null) {
            return;
        }
        BlockPos pos = this.entity.getBlockPos();
        this.x = pos.getX() + 0.5D;
        this.y = pos.getY() + 0.5D;
        this.z = pos.getZ() + 0.5D;
    }
}

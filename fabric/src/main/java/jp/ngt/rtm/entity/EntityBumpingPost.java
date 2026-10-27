package jp.ngt.rtm.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 本家 {@code jp.ngt.rtm.entity.EntityBumpingPost} の 1.21 移植。
 * 車止め。本家は配線を持たない ({@code EntityInstalledObject} 直系)。
 */
public class EntityBumpingPost extends EntityInstalledObject {

    public EntityBumpingPost(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void dropItems() {
        this.spawnAtLocation(new ItemStack(
            com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.BUMPING_POST_ITEM.get()));
    }

    @Override
    protected String getDefaultName() {
        return "BumpingPost_Type2";
    }
}

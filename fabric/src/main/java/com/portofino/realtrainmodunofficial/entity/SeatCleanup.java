package com.portofino.realtrainmodunofficial.entity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.rtm.entity.train.parts.EntityFloor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * 座席 (EntityFloor) の後始末。
 * 座席は車体に追従するだけの当たり判定で、単独で存在してよいものではない。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID)
public final class SeatCleanup {
    /**
     * 掃除の間隔 (tick)。5 秒に 1 回。
     * 取り残された座席は見つかり次第クリック不可 (isPickable) になるので、
     * ここは実体を片付けるだけの後片付け。頻繁に回す必要はない。
     */
    private static final int INTERVAL = 100;

    private static int counter;

    private SeatCleanup() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++counter < INTERVAL) {
            return;
        }
        counter = 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            sweep(level);
        }
    }

    private static void sweep(ServerLevel level) {
        List<? extends EntityFloor> orphans = level.getEntities(
                EntityTypeTest.forClass(EntityFloor.class), EntityFloor::isOrphan);
        for (EntityFloor floor : orphans) {
            floor.discard();
        }
    }
}

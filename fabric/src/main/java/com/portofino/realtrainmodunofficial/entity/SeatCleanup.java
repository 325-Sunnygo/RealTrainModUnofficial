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
 * 座席 ({@link EntityFloor}) の後始末。
 *
 * <p>座席は車体に追従するだけの当たり判定で、単独で存在してよいものではない。
 * ところが「車体が消えたら座席も消える」を座席自身の tick に任せると、その tick が
 * 回らない状況 (チャンクのエンティティ tick 対象外など) で座席だけがワールドに
 * 取り残される。取り残された座席は車体から離れた場所に浮いたまま、クリックしても
 * 座れない当たり判定になり、本物の座席を隠してしまう。
 *
 * <p>そこで掃除はサーバー tick 側から行う。座席自身が動いているかどうかに関係なく、
 * 親を失った座席を確実に回収できる。</p>
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID)
public final class SeatCleanup {
    /**
     * 掃除の間隔 (tick)。5 秒に 1 回。
     * <p>取り残された座席は見つかり次第クリック不可 ({@code isPickable}) になるので、
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

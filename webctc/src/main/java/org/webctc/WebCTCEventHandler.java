package org.webctc;

import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.webctc.railgroup.RailGroupData;
import org.webctc.tecon.TeConData;
import org.webctc.tecon.TeConRuntimeManager;

import java.util.List;

/**
 * 本家 org.webctc.WebCTCEventHandler の移植。
 * 毎 tick: RailGroup の更新 (20 tick ごとに信号/RS 反映) + ロック監視 + TeCon 実行系。
 * レール破壊保護: RailGroup 管理下のレールは壊せない (本家 onBreakBlock)。
 */
public final class WebCTCEventHandler {

    private WebCTCEventHandler() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        //シングル/LAN ホストなら /webctc auth のリンクを localhost にする
        WebCTCConfig.setDedicatedServer(event.getServer().isDedicatedServer());
        RailGroupData.onServerStarted(event.getServer());
        TeConData.onServerStarted(event.getServer());
        TeConRuntimeManager.setServer(event.getServer());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        RailGroupData.onServerStopping();
        TeConData.onServerStopping();
        TeConRuntimeManager.setServer(null);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        RailGroupData.tick();
        TeConRuntimeManager.tick();
    }

    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        BlockEntity tile = event.getLevel().getBlockEntity(event.getPos());
        if (!(tile instanceof TileEntityLargeRailBase rail)) {
            return;
        }
        var core = rail.getRailCore();
        if (core == null) {
            return;
        }
        List<String> uuids = RailGroupData.managedBy(core.getBlockPos());
        if (uuids.isEmpty()) {
            return;
        }
        event.getPlayer().sendSystemMessage(Component.literal(
                "This rail is managed by WebCTC(RailGroup).").withStyle(ChatFormatting.RED));
        event.getPlayer().sendSystemMessage(Component.literal(
                        "If you want to break this rail, first remove it from " + String.join(", ", uuids) + ".")
                .withStyle(ChatFormatting.RED));
        event.setCanceled(true);
    }
}

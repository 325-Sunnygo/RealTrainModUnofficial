package com.portofino.realtrainmodunofficial.client.render;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import jp.ngt.rtm.rail.*;
import jp.ngt.rtm.rail.util.*;
import com.portofino.realtrainmodunofficial.client.renderer.MarkerPanel;

/** Opt-in real in-world six-button interaction smoke test. */
@EventBusSubscriber(modid="realtrainmodunofficial",value=Dist.CLIENT)
public final class MarkerPanelClientSmoke {
    private static int ticks,expected,clicked;
    private static java.util.concurrent.CompletableFuture<Void> verification;
    private static final BlockPos START=new BlockPos(8,85,12),END=new BlockPos(8,85,28);
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if(!Boolean.getBoolean("rtmu.markerSmoke"))return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;ticks++;
        if(ticks==1) {
            jp.ngt.rtm.RTMConfig.use1122Marker=true;
            var id=mc.player.getUUID();var server=mc.getSingleplayerServer();
            server.execute(()->{
                var player=server.getPlayerList().getPlayer(id);var level=player.serverLevel();
                level.setBlockAndUpdate(START,RTMRailBlocks.MARKER.get().defaultBlockState());level.setBlockAndUpdate(END,RTMRailBlocks.MARKER.get().defaultBlockState());
                var a=(TileEntityMarker)level.getBlockEntity(START);var b=(TileEntityMarker)level.getBlockEntity(END);
                a.setMarkerRP(new RailPosition(8,85,12,(byte)0));b.setMarkerRP(new RailPosition(8,85,28,(byte)4));
                a.setMarkersPos(java.util.List.of(new int[]{8,85,12},new int[]{8,85,28}));
                expected=MarkerState.LINE1.set(MarkerState.DISTANCE.set(MarkerState.FIT_NEIGHBOR.set(0,true),true),true);
                for(var marker:java.util.List.of(a,b)){marker.setMarkerState(expected);marker.setChanged();level.sendBlockUpdated(marker.getBlockPos(),marker.getBlockState(),marker.getBlockState(),3);}
                player.teleportTo(8.5,85,4.5);player.setYRot(0);player.setXRot(-15);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
            });
        }
        if(ticks>=60&&ticks<180&&ticks%20==0) {
            int index=(ticks-60)/20;var marker=(TileEntityMarker)mc.level.getBlockEntity(START);
            if(marker==null)throw new AssertionError("Marker was not synchronized");
            double height=marker.getState(MarkerState.LINE1)?1:.5;if(marker.getState(MarkerState.LINE2))height=Math.max(height,marker.getMarkerRP().constLimitHP);
            double y=85+height+.85+.6*index;var eye=mc.player.getEyePosition();float pitch=(float)-Math.toDegrees(Math.atan2(y-eye.y,12.5-eye.z));
            mc.player.setYRot(0);mc.player.setYHeadRot(0);mc.player.setXRot(pitch);mc.player.xRotO=pitch;mc.player.yRotO=0;
        }
        if(ticks>=68&&ticks<188&&(ticks-68)%20==0) {
            int index=(ticks-68)/20;
            if(!MarkerPanel.click())throw new AssertionError("Real marker panel click was not picked: "+index);
            expected=MarkerPanel.BUTTONS[index].flip(expected);clicked++;
        }
        if(ticks==200) {
            var server=mc.getSingleplayerServer();verification=new java.util.concurrent.CompletableFuture<>();
            server.execute(()->{
                try {
                    for(var pos:java.util.List.of(START,END)) {
                        var marker=(TileEntityMarker)server.overworld().getBlockEntity(pos);
                        if(marker.getMarkerState()!=expected||marker.fitNeighbor)throw new AssertionError("Real panel state did not reach server: "+marker.getMarkerState()+" expected "+expected);
                        var saved=marker.saveWithFullMetadata(server.overworld().registryAccess());
                        if(saved.getInt("MarkerState")!=expected)throw new AssertionError("Panel state not persisted");
                    }
                    verification.complete(null);
                }catch(Throwable failure){verification.completeExceptionally(failure);}
            });
        }
        if(ticks>=210&&verification!=null&&verification.isDone()) {
            verification.join();if(clicked!=6)throw new AssertionError("Not all six panel buttons exercised");
            try(var shot=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(mc.gameDirectory.toPath().resolve("marker-panel-smoke.png"));}
            System.out.println("MARKER PANEL CLIENT SMOKE PASSED: six real ray-picked button clicks, server synchronization and persistence");mc.stop();
        }
    }
}

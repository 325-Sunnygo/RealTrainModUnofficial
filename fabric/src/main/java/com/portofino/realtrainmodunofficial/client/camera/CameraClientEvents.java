package com.portofino.realtrainmodunofficial.client.camera;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Original first-person/main-hand activation and four-line camera HUD. */
@EventBusSubscriber(modid=RealTrainModUnofficial.MODID,value=Dist.CLIENT)
public final class CameraClientEvents {
    private CameraClientEvents() {}
    private static boolean cameraView(Minecraft mc) {
        return mc.level!=null && mc.player!=null && mc.options.getCameraType().isFirstPerson()
                && mc.player.getMainHandItem().getItem() instanceof com.portofino.realtrainmodunofficial.item.CameraItem
                && !(mc.player.getInventory().armor.get(3).getItem() instanceof jp.ngt.rtm.item.ItemNVD);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        com.portofino.realtrainmodunofficial.client.model.GifTextures.tick();
        RtmCamera.INSTANCE.setActive(cameraView(mc));RtmCamera.INSTANCE.tick(mc);
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event) {
        if(RtmCamera.INSTANCE.isActive()) event.setFOV(RtmCamera.INSTANCE.computeFov(event.getFOV()));
    }
    @SubscribeEvent public static void beforeGui(RenderGuiEvent.Pre event) {
        var mc=Minecraft.getInstance();RtmCamera.INSTANCE.setActive(cameraView(mc));
        if(RtmCamera.INSTANCE.isActive()) CameraPostProcessor.process(mc,RtmCamera.INSTANCE.state());
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        if(!RtmCamera.INSTANCE.isActive() || Minecraft.getInstance().screen!=null) return;
        var g=event.getGuiGraphics();var font=Minecraft.getInstance().font;int color=0xFF00FF10;
        String[] lines={RtmCamera.INSTANCE.state().status(),"Zoom:[Z][X]","Sensitivity:[C][V]","Focus:[B][N] [M]"};
        for(int i=0;i<lines.length;i++) g.drawString(font,lines[i],5,5+i*10,color,true);
    }
}

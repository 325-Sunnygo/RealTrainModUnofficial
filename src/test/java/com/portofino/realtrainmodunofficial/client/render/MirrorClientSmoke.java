package com.portofino.realtrainmodunofficial.client.render;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.client.mirror.MirrorRenderer;

/** Opt-in isolated developer-world smoke check. Never included in production jars. */
@EventBusSubscriber(modid="realtrainmodunofficial",value=Dist.CLIENT)
public final class MirrorClientSmoke {
    private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if(!Boolean.getBoolean("rtmu.mirrorSmoke"))return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        mc.options.pauseOnLostFocus=false;
        if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);
        if(++ticks==1) {
            var id=mc.player.getUUID();var server=mc.getSingleplayerServer();
            server.execute(()->{
                var player=server.getPlayerList().getPlayer(id);var level=player.serverLevel();
                for(int x=6;x<12;x++)for(int z=5;z<18;z++)level.setBlockAndUpdate(new BlockPos(x,84,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                for(int x=8;x<=9;x++)for(int y=85;y<=86;y++)level.setBlockAndUpdate(new BlockPos(x,y,12),RealTrainModUnofficialBlocks.MIRROR.get().defaultBlockState().setValue(jp.ngt.rtm.block.BlockMirror.FACING,Direction.NORTH));
                level.setBlockAndUpdate(new BlockPos(9,85,7),net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
                player.teleportTo(8.5,85,8.5);player.setYRot(0);player.setXRot(0);
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
            });
        }
        if(ticks==60){jp.ngt.rtm.RTMConfig.mirrorTextureSize=256;jp.ngt.rtm.RTMConfig.mirrorRenderingFrequency=3;}
        if(ticks>=150){mc.player.setYRot(0);mc.player.setYHeadRot(0);mc.player.setXRot(0);mc.player.xRotO=0;mc.player.yRotO=0;}
        if(ticks==160) {
            if(Boolean.getBoolean("rtmu.expectIris")&&!com.portofino.realtrainmodunofficial.client.ShaderCompat.active())throw new AssertionError("Shader pack was not active during mirror test");
            if(Boolean.getBoolean("rtmu.expectFabulous")&&!Minecraft.useShaderTransparency())throw new AssertionError("Fabulous mode was not active during mirror test");
            if(mc.getMainRenderTarget().width!=mc.getWindow().getWidth())throw new AssertionError("Reflection target leaked into the main view");
            var imagesField=MirrorRenderer.class.getDeclaredField("IMAGES");imagesField.setAccessible(true);
            var images=(java.util.Map<?,?>)imagesField.get(null);
            if(images.isEmpty())throw new AssertionError("Visible mirror did not allocate a reflection framebuffer");
            for(var image:images.values()) {
                var view=image.getClass().getDeclaredField("view");view.setAccessible(true);
                if(view.get(image)==null)throw new AssertionError("Mirror never captured the world");
                var target=image.getClass().getDeclaredField("target");target.setAccessible(true);
                var framebuffer=(com.mojang.blaze3d.pipeline.TextureTarget)target.get(image);
                if(framebuffer.width!=256)throw new AssertionError("Mirror did not resize after config change");
                try(var reflection=net.minecraft.client.Screenshot.takeScreenshot(framebuffer)) {
                    reflection.writeToFile(mc.gameDirectory.toPath().resolve("mirror-reflection.png"));
                    int red=0;
                    for(int y=0;y<reflection.getHeight();y++)for(int x=0;x<reflection.getWidth();x++) {
                        int pixel=reflection.getPixelRGBA(x,y),r=pixel&255,g=pixel>>>8&255,b=pixel>>>16&255;
                        if(r>40&&r>g*1.4&&r>b*1.4)red++;
                    }
                    if(red<10)throw new AssertionError("Mirror lost reflected world geometry");
                    int skyPixel=reflection.getPixelRGBA(4,4);
                    if((skyPixel&255)+(skyPixel>>>8&255)+(skyPixel>>>16&255)<40)throw new AssertionError("Mirror sky is black");
                }
                mc.getMainRenderTarget().bindWrite(true);
            }
            var downloaded=com.portofino.realtrainmodunofficial.modelpack.transfer.ServerPackStorage.activeDirectory();
            if(downloaded==null||!java.nio.file.Files.isRegularFile(downloaded.resolve("ModelPack_config_smoke.zip")))throw new AssertionError("Server model pack did not transfer and activate");
            System.out.println("SERVER PACK CLIENT SMOKE PASSED: manifest/chunks/digest/activation over actual play connection");
            try(var screenshot=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                screenshot.writeToFile(mc.gameDirectory.toPath().resolve("mirror-smoke.png"));
            }
            System.out.println("MIRROR CLIENT SMOKE PASSED: reflected world rendered, main camera restored, native config stable");
            mc.stop();
        }
    }
}

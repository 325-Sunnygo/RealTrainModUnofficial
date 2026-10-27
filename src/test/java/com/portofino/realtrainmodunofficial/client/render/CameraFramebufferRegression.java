package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.Tesselator;
import com.portofino.realtrainmodunofficial.client.camera.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Executes actual Minecraft camera FBO passes without creating a Minecraft client/world. */
final class CameraFramebufferRegression {
    private static int checks;
    private static void check(boolean v,String reason) { checks++;if(!v) throw new AssertionError(reason); }
    private static int[] pixel(TextureTarget target) {
        target.bindWrite(false);var bytes=BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(8,8,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
        return new int[]{bytes.get(0)&255,bytes.get(1)&255,bytes.get(2)&255};
    }
    static void run() throws Exception {
        Tesselator.init();TextureTarget target=new TextureTarget(16,16,true,net.minecraft.client.Minecraft.ON_OSX);
        var process=CameraPostProcessor.class.getDeclaredMethod("processTarget",com.mojang.blaze3d.pipeline.RenderTarget.class,
                CameraState.class,int.class,int.class,java.nio.file.Path.class);process.setAccessible(true);
        try {
            target.setClearColor(0,1,0,1);target.clear(net.minecraft.client.Minecraft.ON_OSX);
            CameraState state=new CameraState();state.cycleFocusMode();
            process.invoke(null,target,state,16,16,null);
            check(state.focus()==1,"AUTO reads and remaps actual framebuffer center depth");
            int[] color=pixel(target);
            check(color[0]<=1 && color[1]>=254 && color[2]<=1,"eight depth-band passes retain uniform scene color");
            state.cycleFocusMode();state.cycleFocusMode();
            for(int i=0;i<20;i++) state.update(false,false,false,true,false,false);
            CameraPostProcessor.reset();
            for(int i=0;i<11;i++) { target.setClearColor(0,1,0,1);target.clear(net.minecraft.client.Minecraft.ON_OSX);process.invoke(null,target,state,16,16,null); }
            target.setClearColor(1,0,0,1);target.clear(net.minecraft.client.Minecraft.ON_OSX);
            process.invoke(null,target,state,16,16,null);color=pixel(target);
            int expected=Math.round(255*state.delayBlend());
            check(Math.abs(color[1]-expected)<=1 && Math.abs(color[0]-(255-expected))<=1,
                    "sensitivity blends the ten-frame snapshot rather than recursively accumulating");
            CameraPostProcessor.reset();target.setClearColor(1,0,0,1);target.clear(net.minecraft.client.Minecraft.ON_OSX);
            process.invoke(null,target,state,16,16,null);color=pixel(target);
            check(color[0]==255 && color[1]==0,"reopening camera does not reuse a stale snapshot");
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"camera passes leave no GL errors");
            System.out.println("Actual Minecraft camera framebuffer regressions: "+checks+" passed");
        } finally { target.destroyBuffers();CameraPostProcessor.reset(); }
    }
}

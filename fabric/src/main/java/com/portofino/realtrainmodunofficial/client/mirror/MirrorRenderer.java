package com.portofino.realtrainmodunofficial.client.mirror;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.*;
import java.lang.Math;
import java.util.*;
import jp.ngt.rtm.RTMConfig;

/** Coplanar mirrors share an off-axis reflection framebuffer and configured refresh interval. */
@EventBusSubscriber(modid="realtrainmodunofficial",value=Dist.CLIENT)
public final class MirrorRenderer {
    private record Plane(Direction direction,double coordinate){}
    private record Face(Vec3[] corners,int alpha){}
    private static final Map<Plane,List<Face>> PENDING=new LinkedHashMap<>();
    private static final Map<Plane,Image> IMAGES=new LinkedHashMap<>(16,.75F,true);
    private static boolean rendering;
    private static long frame;
    private static Object level;
    private MirrorRenderer(){}
    public static boolean rendering(){return rendering;}
    public static boolean due(long frame,long previous,int frequency){return frame-previous>=Math.max(1,frequency);}
    public static int textureSize(){return Math.clamp(RTMConfig.mirrorTextureSize,256,2048);}
    private static final class Image implements AutoCloseable {
        final TextureTarget target;
        final ResourceLocation texture=ResourceLocation.fromNamespaceAndPath("realtrainmodunofficial","mirror/"+UUID.randomUUID());
        MirrorGeometry.View view;long updated=Long.MIN_VALUE/2;
        Image(int size) {
            target=new TextureTarget(size,size,true,Minecraft.ON_OSX);
            Minecraft.getInstance().getTextureManager().register(texture,new AbstractTexture(){
                @Override public int getId(){return target.getColorTextureId();}
                @Override public void load(ResourceManager manager){}
                @Override public void close(){}
            });
        }
        @Override public void close(){Minecraft.getInstance().getTextureManager().release(texture);target.destroyBuffers();}
    }
    private static final class ReflectedCamera extends Camera {
        void configure(Minecraft mc,MirrorGeometry.View view) {
            setup(mc.level,mc.getCameraEntity()==null?mc.player:mc.getCameraEntity(),true,false,mc.getTimer().getGameTimeDeltaPartialTick(false));
            setPosition(view.eye());setRotation(view.yaw(),view.pitch());
        }
    }
    public static void face(BlockPos position,Direction direction,boolean panel,int alpha,PoseStack pose,MultiBufferSource buffers) {
        if(rendering)return;
        var mc=Minecraft.getInstance();
        if(level!=mc.level){clear();level=mc.level;}
        double inset=panel?1D/16:1;
        Vec3 n=Vec3.atLowerCornerOf(direction.getNormal());
        Vec3 center=Vec3.atCenterOf(position).add(n.scale(panel?-.5+inset:.5001));
        var eye=mc.gameRenderer.getMainCamera().getPosition();
        if(eye.distanceToSqr(center)>4096 || eye.subtract(center).dot(n)<=.005)return;
        Vec3 u=direction.getAxis()==Direction.Axis.Y?new Vec3(1,0,0):new Vec3(0,1,0).cross(n).normalize();
        Vec3 v=n.cross(u);
        Vec3[] corners={center.subtract(u.scale(.5)).subtract(v.scale(.5)),center.add(u.scale(.5)).subtract(v.scale(.5)),center.add(u.scale(.5)).add(v.scale(.5)),center.subtract(u.scale(.5)).add(v.scale(.5))};
        Plane plane=new Plane(direction,center.dot(n));
        PENDING.computeIfAbsent(plane,p->new ArrayList<>()).add(new Face(corners,alpha));
        Image image=IMAGES.get(plane);if(image==null||image.view==null)return;
        ResourceLocation texture=image.texture;
        var state=new jp.ngt.ngtlib.renderer.LegacyRenderState();state.enable(0x0B50,false);
        try(var ignored=jp.ngt.ngtlib.renderer.LegacyRenderState.use(state.snapshot())) {
            var type=com.portofino.realtrainmodunofficial.client.render.LegacyRenderTypes.select(RenderType.entitySolid(texture),texture,alpha<255,false,alpha==255,VertexFormat.Mode.QUADS);
            var consumer=buffers.getBuffer(type);
            for(int index=0;index<corners.length;index++) {
                Vec3 corner=corners[index];
                float[] uv=MirrorGeometry.uv(image.view,corner);var p=corner.subtract(position.getX(),position.getY(),position.getZ());
                consumer.addVertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).setColor(255,255,255,alpha).setUv(uv[0],uv[1])
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(pose.last(),(float)n.x,(float)n.y,(float)n.z);
            }
        }
    }
    @SubscribeEvent
    public static void afterLevel(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL || rendering)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){clear();return;}
        frame++;var pending=new LinkedHashMap<>(PENDING);PENDING.clear();
        mc.renderBuffers().bufferSource().endBatch();
        for(var group:pending.entrySet()) {
            var image=IMAGES.get(group.getKey());
            if(image!=null&&image.target.width!=textureSize()){image.close();IMAGES.remove(group.getKey());image=null;}
            if(image==null){image=new Image(textureSize());IMAGES.put(group.getKey(),image);}
            if(!due(frame,image.updated,RTMConfig.mirrorRenderingFrequency))continue;
            var corners=group.getValue().stream().flatMap(face->Arrays.stream(face.corners)).toList();
            var view=MirrorGeometry.view(event.getCamera().getPosition(),group.getKey().direction,corners,mc.options.getEffectiveRenderDistance()*32F);
            if(view==null)continue;
            capture(mc,image,view);image.view=view;image.updated=frame;
        }
        while(IMAGES.size()>64){var iterator=IMAGES.entrySet().iterator();var entry=iterator.next();entry.getValue().close();iterator.remove();}
    }
    private static void capture(Minecraft mc,Image image,MirrorGeometry.View view) {
        var access=(MirrorTargetAccess)mc;var previous=access.rtmu$getMainTarget();
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());Matrix4f modelView=new Matrix4f(RenderSystem.getModelViewMatrix());
        ShaderInstance previousShader=RenderSystem.getShader();
        int[] textures={RenderSystem.getShaderTexture(0),RenderSystem.getShaderTexture(1),RenderSystem.getShaderTexture(2)};
        float[] shaderColor=RenderSystem.getShaderColor().clone();
        var lights=com.portofino.realtrainmodunofficial.client.render.RailDrawQueue.currentShaderLights();
        float[] fog=RenderSystem.getShaderFogColor().clone();float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();var fogShape=RenderSystem.getShaderFogShape();
        int drawFramebuffer=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4];org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT,viewport);
        var camera=new ReflectedCamera();camera.configure(mc,view);rendering=true;
        Runnable restoreTargets=((MirrorLevelTargets)mc.levelRenderer).rtmu$suspendAuxiliaryTargets();
        mc.getProfiler().push("rtm_mirror");
        boolean shaders=com.portofino.realtrainmodunofficial.client.ShaderCompat.active();
        try {
            for(int pass=0;pass<(shaders?2:1);pass++) {
                try(var iris=IrisMirrorView.open(image.target.width,shaders&&pass==0)) {
                    RenderTarget target=iris==null?image.target:iris.target();
                    access.rtmu$setMainTarget(target);target.bindWrite(true);
                    RenderSystem.setProjectionMatrix(view.projection(),VertexSorting.DISTANCE_TO_ORIGIN);
                    mc.levelRenderer.prepareCullFrustum(view.eye(),view.rotation(),view.projection());
                    mc.levelRenderer.renderLevel(mc.getTimer(),false,camera,mc.gameRenderer,mc.gameRenderer.lightTexture(),view.rotation(),view.projection());
                    mc.renderBuffers().bufferSource().endBatch();
                    if(iris!=null&&pass==1)MirrorResolve.draw(iris.skyTarget(),target,iris.depthTexture(),image.target);
                }
            }
        } finally {
            mc.getProfiler().pop();restoreTargets.run();rendering=false;access.rtmu$setMainTarget(previous);previous.bindWrite(true);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER,drawFramebuffer);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,readFramebuffer);
            org.lwjgl.opengl.GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.getModelViewStack().set(modelView);RenderSystem.applyModelViewMatrix();
            RenderSystem.setShader(()->previousShader);
            for(int i=0;i<textures.length;i++)RenderSystem.setShaderTexture(i,textures[i]);
            RenderSystem.setShaderColor(shaderColor[0],shaderColor[1],shaderColor[2],shaderColor[3]);
            if(lights!=null)RenderSystem.setShaderLights(lights[0],lights[1]);
            RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);RenderSystem.setShaderFogColor(fog[0],fog[1],fog[2],fog[3]);RenderSystem.setShaderFogShape(fogShape);
            var original=mc.gameRenderer.getMainCamera();
            mc.levelRenderer.prepareCullFrustum(original.getPosition(),new Matrix4f().rotation(original.rotation().conjugate(new Quaternionf())),projection);
            mc.getBlockEntityRenderDispatcher().prepare(mc.level,original,mc.hitResult);
            mc.getEntityRenderDispatcher().prepare(mc.level,original,mc.crosshairPickEntity);
        }
    }
    public static void clear(){if(!RenderSystem.isOnRenderThread()){RenderSystem.recordRenderCall(MirrorRenderer::clear);return;}IrisMirrorView.clear();IMAGES.values().forEach(Image::close);IMAGES.clear();PENDING.clear();}
}

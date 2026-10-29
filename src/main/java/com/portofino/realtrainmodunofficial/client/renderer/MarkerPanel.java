package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.*;
import jp.ngt.rtm.rail.TileEntityMarker;
import jp.ngt.rtm.rail.util.MarkerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Original 1.12 marker's six in-world buttons and dimensions, with ray picking. */
public final class MarkerPanel {
    public static final MarkerState[] BUTTONS={MarkerState.ANCHOR21,MarkerState.LINE2,MarkerState.LINE1,MarkerState.GRID,MarkerState.DISTANCE,MarkerState.FIT_NEIGHBOR};
    public record Hit(int button,double distance){}
    private static TileEntityMarker hovered;
    private static int button=-1;
    private static long picked;
    private static double distance=Double.MAX_VALUE;
    private MarkerPanel(){}
    public static Hit pick(Vec3 eye,Vec3 look,Vec3 origin,float yaw) {
        double angle=Math.toRadians(180-yaw);var right=new Vec3(Math.cos(angle),0,-Math.sin(angle));
        var normal=right.cross(new Vec3(0,1,0));double denominator=look.dot(normal);
        if(Math.abs(denominator)<1e-8)return null;
        double t=origin.subtract(eye).dot(normal)/denominator;if(t<0||t>64)return null;
        var local=eye.add(look.scale(t)).subtract(origin);double x=local.dot(right),y=local.y;
        if(x< -1.4||x>1.4)return null;
        for(int i=0;i<6;i++){double bottom=.6+i*.6;if(y>=bottom&&y<=bottom+.5)return new Hit(i,t);}
        return null;
    }
    public static boolean click() {
        var mc=Minecraft.getInstance();
        if(!jp.ngt.rtm.RTMConfig.use1122Marker||mc.screen!=null||hovered==null||hovered.isRemoved()||button<0||System.nanoTime()-picked>200_000_000)return false;
        if(mc.hitResult!=null&&mc.hitResult.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&mc.gameRenderer.getMainCamera().getPosition().distanceTo(mc.hitResult.getLocation())<distance-.05)return false;
        hovered.flipState(BUTTONS[button]);
        var core=hovered.getCoreMarker();if(core==null)core=hovered;
        for(var rp:core.getAllRP()) {
            var tag=rp.writeToNBT();tag.putInt("MarkerState",core.getMarkerState());
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.portofino.realtrainmodunofficial.network.MarkerAnchorPayload(new net.minecraft.core.BlockPos(rp.blockX,rp.blockY,rp.blockZ),tag));
        }
        if(mc.player!=null)mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),1,1);
        return true;
    }
    public static void render(TileEntityMarker marker,PoseStack pose,MultiBufferSource buffers,Font font) {
        var mc=Minecraft.getInstance();var rp=marker.getMarkerRP();if(mc.player==null||rp==null)return;
        double height=marker.getState(MarkerState.LINE1)?1:.5;if(marker.getState(MarkerState.LINE2))height=Math.max(height,rp.constLimitHP);
        var origin=new Vec3(marker.getBlockPos().getX()+.5,marker.getBlockPos().getY()+height,marker.getBlockPos().getZ()+.5);
        var camera=mc.gameRenderer.getMainCamera();float yaw=camera.getYRot();
        Hit hit=pick(camera.getPosition(),Vec3.directionFromRotation(camera.getXRot(),yaw),origin,yaw);
        if(hit!=null&&(System.nanoTime()-picked>50_000_000||hovered==marker||hit.distance<distance)){hovered=marker;button=hit.button;distance=hit.distance;picked=System.nanoTime();}
        pose.pushPose();pose.translate(.5,height,.5);pose.mulPose(new Quaternionf().rotationY((180-yaw)*(float)Math.PI/180));
        quad(buffers.getBuffer(RenderType.debugQuads()),pose,-1.5F,.5F,3,3.7F,0,0xB000FFFF);
        for(int i=0;i<6;i++) {
            float bottom=.6F+.6F*i;boolean active=hovered==marker&&button==i&&System.nanoTime()-picked<100_000_000;
            quad(buffers.getBuffer(RenderType.debugQuads()),pose,-1.4F,bottom,2.8F,.5F,.01F,active?0xFF00C300:0xFF00F000);
            pose.pushPose();pose.translate(-1.35,bottom+.5,.025);pose.scale(.05F,-.05F,.05F);
            font.drawInBatch(marker.getStateString(BUTTONS[i]),0,0,0xFFFFFFFF,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);pose.popPose();
        }
        pose.popPose();
    }
    private static void quad(VertexConsumer out,PoseStack pose,float x,float y,float w,float h,float z,int color) {
        var matrix=pose.last().pose();out.addVertex(matrix,x,y,z).setColor(color);out.addVertex(matrix,x+w,y,z).setColor(color);out.addVertex(matrix,x+w,y+h,z).setColor(color);out.addVertex(matrix,x,y+h,z).setColor(color);
    }
}

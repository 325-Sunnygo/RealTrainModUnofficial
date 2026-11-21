package com.portofino.realtrainmodunofficial.script;

import jp.ngt.ngtlib.renderer.*;
import jp.ngt.rtm.sound.*;
import net.minecraft.resources.ResourceLocation;
import javax.script.ScriptEngine;

public final class LegacyRenderStateRegression {
    private static int checks;
    private static void check(boolean result,String message) { checks++; if(!result) throw new AssertionError(message); }
    private static final class ProbeSound extends MovingSoundCustom<Object> {
        ProbeSound() { super(new Object(),ResourceLocation.withDefaultNamespace("test"),true,16); }
        float rawVolume() { return this.volume; }
        float rawPitch() { return this.pitch; }
    }
    public static void main(String[] args) throws Exception {
        GLRecorder recorder=new GLRecorder(); GLRecorder.activate(recorder);
        try {
            check(!GL11Facade.glIsEnabled(GL11Facade.GL_BLEND),"initial blend state");
            GL11Facade.glPushAttrib(GL11Facade.GL_ENABLE_BIT|GL11Facade.GL_COLOR_BUFFER_BIT|GL11Facade.GL_DEPTH_BUFFER_BIT);
            GL11Facade.glEnable(GL11Facade.GL_BLEND);
            GL11Facade.glDisable(GL11Facade.GL_DEPTH_TEST);
            GL11Facade.glBlendFunc(GL11Facade.GL_SRC_ALPHA,GL11Facade.GL_ONE);
            GL11Facade.glDepthMask(false);
            GL11Facade.glAlphaFunc(GL11Facade.GL_LESS,0.5);
            recorder.renderParts("first");
            var first=recorder.getCommands().get(0).state;
            GL11Facade.glPopAttrib();
            recorder.renderParts("second");
            var second=recorder.getCommands().get(1).state;
            check(first.enabled(GL11Facade.GL_BLEND,false),"blend enable captured");
            check(!first.enabled(GL11Facade.GL_DEPTH_TEST,true),"depth disable captured");
            check(!first.depthWrite(),"depth mask captured");
            check(first.sourceBlend()==770 && first.destinationBlend()==1,"additive factors captured");
            check(first.alphaFunction()==513 && first.alphaReference()==0.5F,"alpha comparison captured");
            check(second.empty(),"attrib restore returns inherited state");
            check(!GL11Facade.glIsEnabled(GL11Facade.GL_BLEND),"query observes restored state");
            GLRecorder copied=new GLRecorder(); copied.appendFrom(recorder);
            check(copied.contentKey()==recorder.contentKey(),"display-list copying retains state hash");
            check(copied.getCommands().get(0).state.equals(first),"display-list copying retains immutable snapshot");
            try(var outer=LegacyRenderState.use(first)) {
                try(var inner=LegacyRenderState.use(second)) { check(LegacyRenderState.playback()==second,"nested replay state"); }
                check(LegacyRenderState.playback()==first,"restore nested replay state");
            }
            check(LegacyRenderState.playback()==null,"replay never leaks state");
            GL11Facade.glEnable(GL11Facade.GL_BLEND);
            GL11Facade.glPushAttrib(GL11Facade.GL_DEPTH_BUFFER_BIT);
            GL11Facade.glDisable(GL11Facade.GL_BLEND);
            GL11Facade.glDepthMask(false);
            GL11Facade.glPopAttrib();
            check(!GL11Facade.glIsEnabled(GL11Facade.GL_BLEND),"unmasked states are not restored");
            check(recorder.state().snapshot().depthWrite()==null,"masked depth restored");
            OpenGlHelperCompat.func_148821_a(770,771,1,0);
            check(recorder.state().snapshot().sourceAlphaBlend()==1,"separate alpha blending recorded without touching GPU");
            recorder.clear(); check(recorder.state().snapshot().empty(),"new recording clears state");
            GL11Facade.glColor4f(0.2,0.3,0.4,0.5);
            GL11Facade.glNormal3f(1,0,0);
            GL11Facade.glPushAttrib(GL11Facade.GL_CURRENT_BIT);
            GL11Facade.glColor4f(1,1,1,1); GL11Facade.glNormal3f(0,1,0);
            GL11Facade.glPopAttrib();
            var color=recorder.getCommands().get(recorder.getCommands().size()-1);
            check(color.op==GLRecorder.Op.COLOR && color.a==0.2F && color.d==0.5F,"current color restored by attrib mask");
            var tess=NGTTessellator.instance;
            tess.startDrawing(GL11Facade.GL_TRIANGLES);
            tess.addVertex(0,0,0); tess.addVertex(1,0,0); tess.addVertex(0,1,0); tess.draw();
            var draw=(GLRecorder.TessDraw)recorder.getCommands().get(recorder.getCommands().size()-1).payload;
            check(draw.normals.length==9 && draw.normals[0]==1 && draw.normals[1]==0,"restored current normal reaches recorded vertices");
            GL11Facade.glPushAttrib(GL11Facade.GL_DEPTH_BUFFER_BIT|GL11Facade.GL_POLYGON_BIT);
            GL11Facade.glDepthFunc(GL11Facade.GL_ALWAYS); GL11Facade.glCullFace(GL11Facade.GL_FRONT);
            var depthState=recorder.state().snapshot();
            check(depthState.depthFunction()==519 && depthState.cullFace()==1028,"depth function and front-face culling captured");
            GL11Facade.glPopAttrib();
            check(recorder.state().snapshot().depthFunction()==null && recorder.state().snapshot().cullFace()==null,"depth and polygon attrib restore");
            ScriptEngine script=jp.ngt.ngtlib.io.ScriptUtil.createEngine();
            script.eval("var GL=Java.type('jp.ngt.ngtlib.renderer.GL11Facade'); GL.glEnable(GL.GL_BLEND);");
            check(GL11Facade.glIsEnabled(GL11Facade.GL_BLEND),"Nashorn changes production recorder");
        } finally { GLRecorder.deactivate(); }
        ProbeSound sound=new ProbeSound();
        check(sound.rawVolume()==1,"initial volume is immediately audible");
        sound.setVolume(0.25F); sound.setPitch(1.5F);
        check(sound.rawVolume()==1,"volume changes apply next tick");
        sound.tick();
        check(sound.rawVolume()==0.25F && sound.rawPitch()==1.5F,"live sound volume/pitch updates");
        sound.stop(); check(sound.isStopped(),"public legacy stop reaches native tickable sound");
        MovingSoundEntity entitySound=new MovingSoundEntity(null,ResourceLocation.withDefaultNamespace("test"),true);
        entitySound.tick(); check(entitySound.isStopped(),"entity removal stops attached sound");
        var updater=new com.portofino.realtrainmodunofficial.script.SoundScriptExecutor(null);
        updater.atsSound[0]=entitySound;
        Object reflected=jp.ngt.ngtlib.util.NGTUtil.getField(SoundUpdaterTrain.class,updater,new String[]{"atsSound"});
        check(reflected==updater.atsSound && updater.atsSound[0]==entitySound,"legacy reflection sees real alarm handles");
        System.out.println("Legacy render/sound state regressions: "+checks+" passed");
    }
}

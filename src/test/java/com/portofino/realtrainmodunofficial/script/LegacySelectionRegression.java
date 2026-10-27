package com.portofino.realtrainmodunofficial.script;
import jp.ngt.ngtlib.renderer.*;
import org.joml.Matrix4f;

public final class LegacySelectionRegression {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    private static void triangle(float x,float z) {
        var tess=NGTTessellator.instance; tess.startDrawing(4);
        tess.addVertex(x-0.5,-0.5,z); tess.addVertex(x+0.5,-0.5,z); tess.addVertex(x,0.5,z); tess.draw();
    }
    public static void main(String[] args) throws Exception {
        GLRecorder recorder=new GLRecorder(); GLRecorder.activate(recorder);
        try(var context=LegacySelection.context(new Matrix4f(),new Matrix4f(),null,100,100)) {
            GLHelper.startMousePicking(1); GL11Facade.glLoadName(7); triangle(0,0);
            GL11Facade.glLoadName(9); triangle(0,-0.5F);
            GL11Facade.glLoadName(11); triangle(3,0);
            check(GLHelper.finishMousePicking()==2,"center pick excludes offscreen polygon");
            check(GLHelper.getPickedObjId(0)==7 && GLHelper.getPickedObjId(1)==9,"original identifiers");
            check(Math.abs(GLHelper.getPickedObjDepth(0)-0.5)<1e-6 && Math.abs(GLHelper.getPickedObjDepth(1)-0.25)<1e-6,"normalized nearest depth");
            recorder.clear(); GL11Facade.glTranslatef(3,0,0);
            GLHelper.startMousePicking(1); GL11Facade.glLoadName(1); triangle(0,0);
            check(GLHelper.finishMousePicking()==0,"transforms before pick start remain active");
            recorder.clear(); GLHelper.startMousePicking(1); GL11Facade.glLoadName(2);
            GL11Facade.glPushMatrix(); GL11Facade.glTranslatef(3,0,0); triangle(0,0); GL11Facade.glPopMatrix(); triangle(0,0);
            check(GLHelper.finishMousePicking()==1 && GLHelper.getPickedObjId(0)==2,"matrix stack and repeated names");
            recorder.clear(); GLHelper.startMousePicking(1); triangle(0,2);
            check(GLHelper.finishMousePicking()==0,"far plane clips geometry");
            recorder.clear(); GLHelper.startMousePicking(1);
            var tess=NGTTessellator.instance; tess.startDrawing(4);
            tess.addVertex(-1,-1,-2); tess.addVertex(1,-1,0); tess.addVertex(0,1,0); tess.draw();
            check(GLHelper.finishMousePicking()==1 && GLHelper.getPickedObjDepth(0)>=0,"near-plane crossing is clipped before depth calculation");
            recorder.clear(); var script=jp.ngt.ngtlib.io.ScriptUtil.createEngine();
            script.eval("var G=Java.type('jp.ngt.ngtlib.renderer.GLHelper'); var GL=Java.type('jp.ngt.ngtlib.renderer.GL11Facade'); G.startMousePicking(1); GL.glLoadName(31);");
            triangle(0,0); script.eval("if(G.finishMousePicking()!==1 || G.getPickedObjId(0)!==31) throw 'pick';");
            check(true,"Nashorn selection API");
        } finally { GLRecorder.deactivate(); }
        System.out.println("Legacy selection regressions: "+checks+" passed");
    }
}

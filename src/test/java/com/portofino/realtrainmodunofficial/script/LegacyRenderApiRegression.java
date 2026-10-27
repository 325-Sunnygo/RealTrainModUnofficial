package com.portofino.realtrainmodunofficial.script;

import jp.ngt.ngtlib.renderer.*;
import jp.ngt.ngtlib.renderer.model.*;
import jp.ngt.rtm.render.Parts;

/** Behavioral cases based on the supplied 1.7.10 reference implementation. */
public final class LegacyRenderApiRegression {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    private static Face face(int material,float x) {
        Face face=new Face(new Vertex[]{new Vertex(x,0,0),new Vertex(x+1,0,0),new Vertex(x,1,0)},null,material);
        face.faceNormal=new Vertex(0,0,1);
        face.vertexNormals=new Vertex[]{new Vertex(1,0,0),new Vertex(0,1,0),new Vertex(0,0,1)};
        return face;
    }
    private static GLRecorder.TessDraw last(GLRecorder recorder) {
        return (GLRecorder.TessDraw)recorder.getCommands().get(recorder.getCommands().size()-1).payload;
    }
    public static void main(String[] args) throws Exception {
        PolygonModel model=new PolygonModel();
        GroupObject body=new GroupObject("Body"); body.faces.add(face(0,10)); body.faces.add(face(1,20));
        GroupObject door=new GroupObject("Door"); door.faces.add(face(0,30));
        model.groupObjects.add(body); model.groupObjects.add(door);
        GLRecorder recorder=new GLRecorder(); GLRecorder.activate(recorder);
        try {
            NGTRenderHelper.renderCustomModel(model,(byte)0,false,"Body");
            var draw=last(recorder);
            check(draw.verts.length==27 && draw.verts[0]==10,"helper filters material and captures supplied geometry");
            check(draw.normals[0]==0 && draw.normals[2]==1,"flat face normal");
            NGTRenderHelper.renderCustomModel(model,(byte)1,true,"Body");
            check(last(recorder).verts[0]==20 && last(recorder).normals[0]==1,"smoothing and alternate material");
            int beforeCase=recorder.getCommands().size();
            NGTRenderHelper.renderCustomModel(model,(byte)0,false,"body");
            check(recorder.getCommands().size()==beforeCase,"selection is case sensitive like reference");
            NGTRenderHelper.renderCustomModelExcept(model,(byte)0,false,"Body");
            check(last(recorder).verts[0]==30,"except selects remaining group");
            NGTRenderHelper.renderCustomModelAll(model,(byte)0,false);
            check(last(recorder).verts.length==54,"all selects requested material across groups");
            NGTRenderHelper.renderCustomModelEveryParts(model,(byte)0,true,false,new String[0]);
            check(last(recorder).verts.length==54,"empty except list includes all per reference");
            Parts parts=new Parts("missing","Body");
            check(parts.objNames==parts.getNames(),"public objNames shares original array");
            check(parts.getObjects(model).length==2 && parts.getObjects(model)[0]==null && parts.getObjects(model)[1]==body,"missing names retain positions");
            GLHelper.disableLighting(); check(!GL11Facade.glIsEnabled(GL11Facade.GL_LIGHTING),"helper disables actual recorded lighting");
            GLHelper.enableLighting(); check(GL11Facade.glIsEnabled(GL11Facade.GL_LIGHTING),"helper restores lighting");
            GLHelper.preMoveTexUV(0.25F,0.5F); recorder.renderParts("Body");
            var shifted=recorder.getCommands().get(recorder.getCommands().size()-1).state;
            GLHelper.preMoveTexUV(0.125F,0); check(recorder.state().snapshot().textureU()==0.375F,"nested UV translations compose");
            GLHelper.postMoveTexUV(); check(recorder.state().snapshot().textureU()==0.25F,"nested UV pop");
            GLHelper.postMoveTexUV(); check(recorder.state().snapshot().textureU()==0 && shifted.textureU()==0.25F,"UV restored and snapshot stable");
            DisplayList outer=GLHelper.generateGLList(); DisplayList inner=GLHelper.generateGLList();
            check(GLHelper.isValid(outer) && outer.value>0,"native display-list shape");
            GLHelper.startCompile(outer); GLRecorder outerRecorder=GLRecorder.active();
            check(GLHelper.isCompiling(),"compile query true"); outerRecorder.renderParts("outer");
            GLHelper.startCompile(inner); GLRecorder.active().renderParts("inner"); GLHelper.endCompile();
            check(GLRecorder.active()==outerRecorder && GLHelper.isCompiling(),"nested compile restores parent");
            GLHelper.callList(inner); GLHelper.endCompile();
            check(GLRecorder.active()==recorder && !GLHelper.isCompiling(),"compile ends at original recorder");
            int size=recorder.getCommands().size(); GLHelper.callList(outer);
            check(recorder.getCommands().size()==size+2,"nested list replays both objects");
            check(GLHelper.generateGLList(outer).value==outer.value,"valid list reused");
            GLHelper.deleteGLList(outer); check(!GLHelper.isValid(outer),"deletion invalidates handle");
            var script=jp.ngt.ngtlib.io.ScriptUtil.createEngine(); script.put("model",model);
            script.eval("var R=Java.type('jp.ngt.ngtlib.renderer.NGTRenderHelper'); R.renderCustomModel(model,0,true,'Door');");
            check(last(recorder).verts[0]==30,"Nashorn resolves original typed varargs");
            script.eval("var G=Java.type('jp.ngt.ngtlib.renderer.GLHelper'); var dl=G.generateGLList(); G.startCompile(dl); R.renderCustomModelAll(model,0,false); G.endCompile(); G.callList(dl); if(dl.value<=0) throw 'invalid list';");
            check(last(recorder).verts.length==54,"Nashorn display list and helper execute together");
            GLHelper.initGLList(); check(!GLHelper.isValid(inner),"resource reload invalidates live handles");
            check(model instanceof IModelNGT,"original model interface available");
            PolygonModel parsed=ModelLoader.parse("Metasequoia Document\nFormat Text Ver 1.0\nEof\n".getBytes(java.nio.charset.StandardCharsets.UTF_8),"model.mqo");
            check(parsed.getType()==jp.ngt.ngtlib.io.FileType.MQO,"model preserves file type");
        } finally { GLRecorder.deactivate(); GLHelper.initGLList(); }
        System.out.println("Legacy render API regressions: "+checks+" passed");
    }
}

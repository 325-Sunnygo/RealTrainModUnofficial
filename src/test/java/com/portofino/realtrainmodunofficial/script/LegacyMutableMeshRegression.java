package com.portofino.realtrainmodunofficial.script;
import jp.ngt.ngtlib.renderer.model.*;
/** Actual script renderer cache, after standalone registry bootstrap. */
public final class LegacyMutableMeshRegression {
    private static int checks;
    private static void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        var cached=new PolygonModel();var group=new GroupObject("part");cached.groupObjects.add(group);
        var face=new Face(new Vertex[]{new Vertex(0,0,0),new Vertex(1,0,0),new Vertex(0,1,0)},new float[]{0,0,1,0,0,1},0);
        face.calculateFaceNormal(VecAccuracy.MEDIUM);group.faces.add(face);
        var flatMethod=com.portofino.realtrainmodunofficial.client.render.VehicleScriptRenderers.class
            .getDeclaredMethod("flatGroupVerts",PolygonModel.class,String.class,boolean.class);flatMethod.setAccessible(true);
        float[] original=(float[])flatMethod.invoke(null,cached,"part",false);
        check(original==flatMethod.invoke(null,cached,"part",false),"unchanged group retains cached storage");
        cached.groupObjects.get(0).faces.get(0).vertices[0].setVec(5,6,7);
        float[] moved=(float[])flatMethod.invoke(null,cached,"part",false);
        check(moved!=original && moved[0]==5 && moved[1]==6,"runtime vertex deformation invalidates the real renderer cache");
        cached.groupObjects.get(0).faces.get(0).getTextureCoordinates()[0]=new TextureCoordinate(0.125F,0.875F);
        float[] textured=(float[])flatMethod.invoke(null,cached,"part",false);
        check(textured!=moved && textured[3]==0.125F && textured[4]==0.875F,"runtime UV replacement invalidates the real renderer cache");
        cached.groupObjects.get(0).faces.clear();
        check(((float[])flatMethod.invoke(null,cached,"part",false)).length==0,"runtime removal of faces does not leave stale geometry");

        System.out.println("Legacy live mesh cache regressions: "+checks+" passed");
    }
}

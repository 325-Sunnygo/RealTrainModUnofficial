package com.portofino.realtrainmodunofficial.script;

import jp.ngt.ngtlib.renderer.model.*;
import java.util.*;

/** Numerical source oracles and real parser/API behavior. */
public final class LegacyPrecisionRegression {
    private static int checks;
    private static void check(boolean ok,String reason) {checks++;if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args) throws Exception {
        for(float value:new float[]{-16.3846F,-0.00026F,-0F,0F,0.00026F,0.123456F,16.3846F}) {
            int integer=(int)(value*2000F);if(value*2000F<integer)integer--;
            float expected=(short)integer*0.0005F;
            var vertex=Vertex.create(value,value,value,VecAccuracy.LOW);
            check(vertex.x==expected && vertex.y==expected && vertex.z==expected,"LOW vertex floor and short wrap "+value);
            var uv=TextureCoordinate.create(value,value,VecAccuracy.LOW);
            check(uv.getU()==(short)(value*2000F)*0.0005F,"LOW UV truncation differs from vertex floor "+value);
            check(Vertex.create(value,0,0,VecAccuracy.MEDIUM).x==value,"MEDIUM precision preserved");
        }
        var plus=new Vertex(0,0,0);var minus=new Vertex(-0F,0,0);
        check(plus.equals(minus) && plus.hashCode()==minus.hashCode(),"signed zero shares mirror/smoothing vertex map");
        var low=Vertex.create(0,0,0,VecAccuracy.LOW);low.setVec(-0.00026F,0,0);
        check(low.x==-0.0005F,"LOW mutation retains precision");
        check(new Vertex(0.00026F,0,0).copy(VecAccuracy.LOW).x==0F,"copy precision is honored");
        String source="""
          Metasequoia Document
          Format Text Ver 1.0
          Object "part" {
            facet 60
            vertex 4 {
              -0.026 0 0
              100 0 0
              100 100 0
              0 100 0
            }
            face 2 {
              4 V(0 1 2 3) UV(0.123456 0 1 0 1 1 0 1)
              4 V(0 1 2 3) UV(0.123456 0 1 0 1 1 0 1)
            }
          }
          Eof
          """;
        byte[] bytes=source.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var model=ModelLoader.parse(bytes,"precision.mqo",VecAccuracy.LOW,4);
        var group=model.groupObjects.get(0);var face=group.faces.get(0);
        check(model.accuracy==VecAccuracy.LOW && face.vertices[0].x==-0.0005F,"real model parse honors accuracy");
        check(face.vertices[0]==group.faces.get(1).vertices[0],"source faces share the same authored vertex object");
        face.vertices[0].setVec(2,3,4);
        check(Math.abs(group.faces.get(1).vertices[0].getY()-3)<0.001F,"deformation reaches all faces sharing a vertex");
        check(model.getSize()[0]==-0.0005F && model.getSize()[4]==1,"MQO bounds populated");
        var quad=ModelLoader.parse(bytes,"precision.mqo",VecAccuracy.MEDIUM,7);
        check(quad.drawMode==7 && quad.groupObjects.get(0).drawMode==7 && quad.groupObjects.get(0).faces.get(0).vertices.length==4,"GL_QUADS loader mode honored");
        var coordinates=face.getTextureCoordinates();coordinates[0]=new TextureCoordinate(0.25F,0.75F);
        var mesh=com.portofino.realtrainmodunofficial.client.model.LegacyModelGeometry.flatten(group,false);
        check(mesh[3]==0.25F && mesh[4]==0.75F,"authored textureCoordinates array changes reach the render mesh");
        var cacheField=jp.ngt.ngtlib.io.NGTFileLoader.class.getDeclaredField("CONTENT_CACHE");cacheField.setAccessible(true);
        @SuppressWarnings("unchecked") var contents=(Map<String,byte[]>)cacheField.get(null);
        contents.put("models/precision-regression.mqo",bytes);
        try {
            var a=ModelLoader.loadModel("models/precision-regression.mqo",VecAccuracy.MEDIUM,4);
            var b=ModelLoader.loadModel("models/precision-regression.mqo",VecAccuracy.LOW,4);
            var c=ModelLoader.loadModel("models/precision-regression.mqo",VecAccuracy.LOW,7);
            check(a!=b && b!=c,"cache separates precision and draw mode");
            check(a.groupObjects.get(0).faces.get(0).vertices[0].x!=b.groupObjects.get(0).faces.get(0).vertices[0].x,"cache does not reuse MEDIUM coordinates for LOW");
        } finally {contents.remove("models/precision-regression.mqo");}
        var temporary=java.nio.file.Files.createTempFile("rtmu-model-api-", ".mqo");
        try {
            java.nio.file.Files.write(temporary,bytes);
            var fileModel=ModelLoader.loadModel(temporary.toFile(),VecAccuracy.LOW,new Object[]{7});
            check(fileModel.drawMode==7 && fileModel.accuracy==VecAccuracy.LOW,"File overload honors original options array and accuracy");
            var engine=new org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory().getScriptEngine();
            var jsOptions=engine.eval("[7]");
            check(ModelLoader.loadModel(temporary.toFile(),VecAccuracy.MEDIUM,jsOptions).drawMode==7,"Nashorn options array selects GL_QUADS");
            engine.put("modelFile",temporary.toFile());
            check(((Number)engine.eval("Java.type('jp.ngt.ngtlib.renderer.model.ModelLoader').loadModel(modelFile,Java.type('jp.ngt.ngtlib.renderer.model.VecAccuracy').LOW,7,'config').getDrawMode()")).intValue()==7,"Nashorn original varargs signature accepts draw mode and model config");
        } finally {java.nio.file.Files.deleteIfExists(temporary);}
        var shared=new Vertex(0,0,0);var first=new Face(3,0);var second=new Face(3,0);
        for(int i=0;i<3;i++) {first.addVertex(i,shared,new TextureCoordinate(0,0));second.addVertex(i,shared,new TextureCoordinate(0,0));}
        first.faceNormal=Vertex.create(0.707F,0.707F,0,VecAccuracy.LOW);
        second.faceNormal=Vertex.create(0.708F,0.706F,0,VecAccuracy.LOW);
        first.calcVertexNormals(Map.of(shared,List.of(first,second)),0.99999F,VecAccuracy.LOW);
        check(first.vertexNormals[0].getX()>0.7072F,"smoothing compares angles after accounting for quantized normal lengths");
        System.out.println("Legacy precision and mutable model regressions: "+checks+" passed");
    }
}

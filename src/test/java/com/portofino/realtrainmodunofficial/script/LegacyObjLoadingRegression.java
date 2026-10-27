package com.portofino.realtrainmodunofficial.script;
import jp.ngt.ngtlib.renderer.model.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class LegacyObjLoadingRegression {
    private static int checks;
    private static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    private static byte[] bytes(String s) {return s.getBytes(StandardCharsets.UTF_8);}
    public static void main(String[] args) throws Exception {
        var index=jp.ngt.ngtlib.io.NGTFileLoader.class.getDeclaredField("index");index.setAccessible(true);var previousIndex=index.get(null);index.set(null,Map.of());
        try {
        String obj="v 0.0 0.0 0.0\nv 1.0 0.0 0.0\nv 1.0 1.0 0.0\nv 0.0 1.0 0.0\nvt 0.25 0.75\ng body\nusemtl second\nf 1/1 2/1 3/1 4/1\n";
        var model=ModelLoader.loadModel(new InputStream[]{new ByteArrayInputStream(bytes(obj)),new ByteArrayInputStream(bytes("newmtl first\nnewmtl second\nmap_Kd ignored.png\n"))},"test.obj",VecAccuracy.LOW,7);
        var face=model.groupObjects.get(0).faces.get(0);
        check(model.getDrawMode()==4 && face.vertices.length==6,"OBJ always triangulates with original forward winding");
        check(face.vertices[1].x==1 && model.vertices.size()==4,"OBJ uses block units and retains shared authored vertices");
        check(face.vertices[0]==face.vertices[3],"fan corners share original vertex");
        check(face.materialId==1 && model.materials.get("second").texture==null,"MTL declaration order and original texture semantics");
        check(face.getTextureCoordinates()[0].getV()==0.25F,"OBJ V flipped before precision encoding");
        check(face.faceNormal.z==1,"OBJ normal orientation");
        try {ModelLoader.parse(bytes("v 0 0 0\nf 1 2 3\n"),"broken.obj");throw new AssertionError("broken accepted");}catch(ModelFormatException expected) {check(expected.getMessage().contains("line 2"),"parse error reports line");}
        try {ModelLoader.loadModel("missing-regression-model.mqo",VecAccuracy.MEDIUM);throw new AssertionError("missing accepted");}catch(ModelFormatException expected) {check(expected.getCause() instanceof FileNotFoundException,"missing model fails with source cause");}
        try {ModelLoader.parse(bytes("garbage"),"broken.mqo");throw new AssertionError("corrupt accepted");}catch(ModelFormatException expected) {checks++;}
        try {ModelLoader.parse(bytes("Metasequoia Document\nvertex 1 {\nnot numbers\n}\nEof"),"broken-list.mqo");throw new AssertionError("bad list accepted");}catch(ModelFormatException expected) {check(expected.getMessage().contains("line"),"invalid MQO numbers report a source location");}
        var cache=jp.ngt.ngtlib.io.NGTFileLoader.class.getDeclaredField("CONTENT_CACHE");cache.setAccessible(true);
        @SuppressWarnings("unchecked") var contents=(Map<String,byte[]>)cache.get(null);
        contents.put("assets/alpha/models/domain.obj",bytes(obj));contents.put("assets/beta/models/domain.obj",bytes(obj.replace("1.0 0.0 0.0","2.0 0.0 0.0")));
        try {
            var a=ModelLoader.loadModel(new jp.ngt.mccompat.ResourceLocation("alpha","models/domain.obj"),VecAccuracy.MEDIUM);
            var b=ModelLoader.loadModel(new jp.ngt.mccompat.ResourceLocation("beta","models/domain.obj"),VecAccuracy.MEDIUM);
            check(a.vertices.get(1).x==1 && b.vertices.get(1).x==2,"resource domains resolve and cache separately");
        } finally {contents.remove("assets/alpha/models/domain.obj");contents.remove("assets/beta/models/domain.obj");}
        var folder=java.nio.file.Files.createTempDirectory("rtmu-resource-domain-");
        try {
            java.nio.file.Files.write(folder.resolve("isolated.obj"),bytes(obj));
            var refClass=Class.forName("jp.ngt.ngtlib.io.NGTFileLoader$AssetRef");var constructor=refClass.getDeclaredConstructor(java.nio.file.Path.class,String.class);constructor.setAccessible(true);
            index.set(null,Map.of("assets/alpha/models/isolated-domain.obj",constructor.newInstance(folder,"isolated.obj")));
            try {ModelLoader.loadModel(new jp.ngt.mccompat.ResourceLocation("minecraft","models/isolated-domain.obj"),VecAccuracy.MEDIUM);throw new AssertionError("minecraft borrowed another domain");}catch(ModelFormatException expected) {checks++;}
            check(ModelLoader.loadModel(new jp.ngt.mccompat.ResourceLocation("alpha","models/isolated-domain.obj"),VecAccuracy.MEDIUM).vertices.size()==4,"explicit namespace loads its own indexed asset");
        } finally {java.nio.file.Files.deleteIfExists(folder.resolve("isolated.obj"));java.nio.file.Files.deleteIfExists(folder);}
        System.out.println("Legacy OBJ/MTL/resource regressions: "+checks+" passed");
        } finally {index.set(null,previousIndex);}
    }
}

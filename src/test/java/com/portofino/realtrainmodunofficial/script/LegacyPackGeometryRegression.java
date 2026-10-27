package com.portofino.realtrainmodunofficial.script;

import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import jp.ngt.ngtlib.renderer.model.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Compares the actual pack's script graph with the submitted mesh, including UVs and winding. */
public final class LegacyPackGeometryRegression {
    private static String vertex(float x,float y,float z,float u,float v) {
        return bits(x)+","+bits(y)+","+bits(z)+","+bits(u)+","+bits(v);
    }
    private static String bits(float x) { return Integer.toHexString(Float.floatToIntBits(x==0?0:x)); }
    private static String triangle(String a,String b,String c) {
        // Cyclic rotations preserve winding; reversing it must fail the comparison.
        String x=a+";"+b+";"+c,y=b+";"+c+";"+a,z=c+";"+a+";"+b;
        return Collections.min(List.of(x,y,z));
    }
    private static void add(Map<String,Integer> out,String key) { out.merge(key,1,Integer::sum); }
    private static Object field(Object object,String name) throws Exception {
        Field f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Expected external RTM pack ZIP path");
        Class<?> openerType=Class.forName(MqoModelLoader.class.getName()+"$TextureOpener");
        Object opener=Proxy.newProxyInstance(openerType.getClassLoader(),new Class<?>[]{openerType},
                (proxy,method,values)->method.getName().equals("getPackKey")?"geometry-oracle":null);
        Method bake=MqoModelLoader.class.getDeclaredMethod("bake",String.class,openerType,Map.class,boolean.class);
        bake.setAccessible(true);
        org.apache.logging.log4j.core.config.Configurator.setLevel(MqoModelLoader.class.getName(),org.apache.logging.log4j.Level.ERROR);
        int models=0,triangles=0;
        try(var zip=new ZipFile(args[0])) {
            for(var entry:Collections.list(zip.entries())) {
                if(!entry.getName().endsWith(".mqo") && !entry.getName().endsWith(".mqoz")) continue;
                byte[] bytes;
                try(var input=zip.getInputStream(entry)) { bytes=input.readAllBytes(); }
                var graph=ModelLoader.parse(bytes,entry.getName());
                var expected=new HashMap<String,Integer>();
                var expectedOrder=new HashMap<String,List<String>>();
                var expectedNormals=new HashMap<String,List<float[]>>();
                for(var group:graph.groupObjects) for(var face:group.faces) {
                    for(int i=0;i<face.vertices.length;i+=3) {
                        String[] vertices=new String[3];
                        for(int j=0;j<3;j++) {
                            var p=face.vertices[i+j];
                            vertices[j]=vertex(p.x,p.y,p.z,face.uvs[(i+j)*2],face.uvs[(i+j)*2+1]);
                        }
                        for(int j=0;j<3;j++) {
                            var normal=face.vertexNormals[i+j];
                            expectedNormals.computeIfAbsent(group.name+"|"+(face.materialId&255),k->new ArrayList<>()).add(new float[]{normal.x,normal.y,normal.z});
                        }
                        add(expected,group.name+"|"+(face.materialId&255)+"|"+triangle(vertices[0],vertices[1],vertices[2]));
                        expectedOrder.computeIfAbsent(group.name+"|"+(face.materialId&255),k->new ArrayList<>())
                                .add(triangle(vertices[0],vertices[1],vertices[2]));
                        triangles++;
                    }
                }
                var baked=bake.invoke(null,MqoReader.extractText(bytes,entry.getName()),opener,Map.of("default","geometry-oracle.png"),true);
                var actual=new HashMap<String,Integer>();
                var actualOrder=new HashMap<String,List<String>>();
                var actualNormals=new HashMap<String,List<float[]>>();
                int smoothBatches=0;
                for(Object batch:(List<?>)field(baked,"batches")) {
                    float[] data=(float[])field(batch,"data");
                    if(com.portofino.realtrainmodunofficial.client.model.LegacyIrisGeometry.hasVaryingNormals(data)) smoothBatches++;
                    Method indicesMethod=batch.getClass().getDeclaredMethod("triangleIndices");indicesMethod.setAccessible(true);
                    int[] indices=(int[])indicesMethod.invoke(batch);
                    if(entry.getName().endsWith("E131/mari/Mc.mqo") && field(batch,"groupName").equals("alpha")) {
                        int unique=com.portofino.realtrainmodunofficial.client.model.LegacyCoplanarFaces.unique(data,indices).length/3;
                        System.out.println("E131 alpha native mesh: triangles="+indices.length/3+" exact unique="+unique);
                        if(indices.length/3!=92 || unique!=84) throw new AssertionError("Shadow mirror overlap regression changed");
                    }

                    for(int i=0;i<indices.length;i+=3) {
                        String[] vertices=new String[3];
                        for(int j=0;j<3;j++) {
                            int o=indices[i+j]*8;
                            vertices[j]=vertex(data[o],data[o+1],data[o+2],data[o+6],data[o+7]);
                        }
                        String group=field(batch,"groupName")+"|"+field(batch,"materialId");
                        for(int j=0;j<3;j++) { int o=indices[i+j]*8;actualNormals.computeIfAbsent(group,k->new ArrayList<>()).add(new float[]{data[o+3],data[o+4],data[o+5]}); }
                        String tri=triangle(vertices[0],vertices[1],vertices[2]);
                        add(actual,group+"|"+tri);
                        actualOrder.computeIfAbsent(group,k->new ArrayList<>()).add(tri);
                    }
                }
                if(!actualOrder.equals(expectedOrder)) {
                    for(var expectedGroup:expectedOrder.entrySet()) {
                        var got=actualOrder.get(expectedGroup.getKey());var wanted=expectedGroup.getValue();
                        if(!wanted.equals(got)) {
                            int diff=0;while(got!=null&&diff<Math.min(got.size(),wanted.size())&&got.get(diff).equals(wanted.get(diff)))diff++;
                            throw new AssertionError(entry.getName()+" source face/mirror draw order differs group="+expectedGroup.getKey()+" at="+diff+" expectedCount="+wanted.size()+" actualCount="+(got==null?0:got.size())+" expected="+wanted.get(Math.min(diff,wanted.size()-1))+" actual="+(got==null?"missing":got.get(Math.min(diff,got.size()-1))));
                        }
                    }
                    throw new AssertionError(entry.getName()+" unexpected extra groups");
                }
                if(!actual.equals(expected)) {
                    var missing=new HashMap<>(expected);actual.forEach((k,v)->missing.computeIfPresent(k,(a,n)->n.equals(v)?null:n-v));
                    throw new AssertionError(entry.getName()+" mesh/UV/winding mismatch: "+missing.entrySet().stream().limit(3).toList());
                }
                if(entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith("modeltrain_spacia.mqo")) {
                    int corners=0,errors=0;float max=0;
                    for(var e:expectedNormals.entrySet()) {
                        var got=actualNormals.get(e.getKey()); if(got==null || got.size()!=e.getValue().size()) throw new AssertionError("Missing normal stream "+e.getKey());
                        for(int i=0;i<got.size();i++) {
                            float[] a=e.getValue().get(i),b=got.get(i);float d=(float)Math.sqrt((a[0]-b[0])*(a[0]-b[0])+(a[1]-b[1])*(a[1]-b[1])+(a[2]-b[2])*(a[2]-b[2]));
                            if(d>1e-4F)errors++;max=Math.max(max,d);corners++;
                        }
                    }
                    System.out.println("Spacia normal comparison corners="+corners+" mismatch="+errors+" max="+max+" smoothBatches="+smoothBatches);
                    if(smoothBatches==0)throw new AssertionError("Spacia smooth batches were not detected");
                    if(errors>0)throw new AssertionError("Spacia native normals diverge from legacy graph");
                }
                models++;
            }
        }
        System.out.println("External pack geometry: models="+models+" triangles="+triangles+"; script graph and render mesh agree (positions/UV/winding/multiplicity/submission order)");
    }
}

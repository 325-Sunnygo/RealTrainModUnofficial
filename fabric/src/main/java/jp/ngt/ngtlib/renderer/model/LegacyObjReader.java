package jp.ngt.ngtlib.renderer.model;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** OBJ/MTL graph semantics from KaizPatchX ObjModel and MtlParser. */
final class LegacyObjReader {
    private LegacyObjReader() {}
    static PolygonModel parse(byte[] bytes,byte[] mtl,String name,VecAccuracy accuracy) {
        PolygonModel model=new PolygonModel();model.fileName=name;model.accuracy=accuracy;model.drawMode=4;
        if(mtl!=null) for(String line:new String(mtl,StandardCharsets.UTF_8).split("\\R")) {
            String[] words=line.trim().split("\\s+");
            if(words.length>1 && words[0].equals("newmtl")) model.materials.put(words[1],new Material((byte)model.materials.size(),null));
        }
        List<TextureCoordinate> textures=new ArrayList<>();List<Vertex> normals=new ArrayList<>();
        GroupObject group=null;int material=0,lineNumber=0;
        try {
            for(String raw:new String(bytes,StandardCharsets.UTF_8).split("\\R")) {
                lineNumber++;String line=raw.trim();if(line.isEmpty() || line.startsWith("#"))continue;
                String[] words=line.split("\\s+");
                switch(words[0]) {
                    case "v" -> {
                        if(words.length!=4)throw new IllegalArgumentException("Expected three vertex coordinates");
                        Vertex v=Vertex.create(Float.parseFloat(words[1]),Float.parseFloat(words[2]),Float.parseFloat(words[3]),accuracy);
                        model.vertices.add(v);model.calcSizeBox(v);
                    }
                    case "vt" -> {
                        if(words.length<3)throw new IllegalArgumentException("Expected UV coordinates");
                        textures.add(TextureCoordinate.create(Float.parseFloat(words[1]),1F-Float.parseFloat(words[2]),accuracy));
                    }
                    case "vn" -> normals.add(Vertex.create(Float.parseFloat(words[1]),Float.parseFloat(words[2]),Float.parseFloat(words[3]),accuracy));
                    case "g","o" -> {
                        if(words.length<2)throw new IllegalArgumentException("Missing group name");
                        group=new GroupObject(line.substring(2).trim(),4);group.smoothingAngle=60;model.groupObjects.add(group);
                    }
                    case "usemtl" -> {Material selected=model.materials.get(words[1]);if(selected!=null)material=selected.id;}
                    case "f" -> {
                        int corners=words.length-1;
                        if(corners<3 || corners>4)throw new IllegalArgumentException("Expected triangle or quad");
                        if(group==null) {group=new GroupObject("Default",4);group.smoothingAngle=60;model.groupObjects.add(group);}
                        Face face=new Face((corners-2)*3,material);
                        for(int i=0;i<face.vertices.length;i++) {
                            int corner=i%3==0?0:i/3+i%3;
                            String[] token=words[corner+1].split("/",-1);
                            int vi=Integer.parseInt(token[0])-1;
                            TextureCoordinate uv=token.length>1 && !token[1].isEmpty()?textures.get(Integer.parseInt(token[1])-1):null;
                            if(token.length>2 && !token[2].isEmpty())normals.get(Integer.parseInt(token[2])-1);
                            face.addVertex(i,model.vertices.get(vi),uv);
                        }
                        face.calculateFaceNormal(accuracy);group.faces.add(face);
                    }
                    default -> { /* mtllib and smoothing records do not override original defaults. */ }
                }
            }
            for(GroupObject object:model.groupObjects)object.calcVertexNormals(accuracy);
            return model;
        } catch(RuntimeException failure) {throw new ModelFormatException("Invalid OBJ "+name+" at line "+lineNumber,failure);}
    }
}

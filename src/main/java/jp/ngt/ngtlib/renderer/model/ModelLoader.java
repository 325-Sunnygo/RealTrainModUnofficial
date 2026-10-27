package jp.ngt.ngtlib.renderer.model;

import jp.ngt.ngtlib.io.NGTFileLoader;
import jp.ngt.ngtlib.io.NGTLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本家 jp.ngt.ngtlib.renderer.model.ModelLoader のスクリプト互換移植。
 * パック内 .mqo/.mqoz を PolygonModel (グループ/面/頂点グラフ) として読む。
 */
public final class ModelLoader {
    private static final Map<String, PolygonModel> CACHE = new ConcurrentHashMap<>();

    private ModelLoader() {
    }

    public static PolygonModel loadModel(Object resource, VecAccuracy accuracy) {
        return loadModel(resource, accuracy, new Object[0]);
    }

    /** スクリプト形: ModelLoader.loadModel(resource, VecAccuracy.LOW, []) */
    public static PolygonModel loadModel(Object resource, VecAccuracy accuracy, Object... options) {
        VecAccuracy precision=accuracy==null?VecAccuracy.MEDIUM:accuracy;
        int mode=drawMode(options);
        if(resource instanceof java.io.File file) {
            try {
                byte[] mtl=null;var path=file.toPath();
                if(file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".obj")) {
                    var companion=path.resolveSibling(file.getName().substring(0,file.getName().length()-4)+".mtl");
                    if(java.nio.file.Files.exists(companion))mtl=java.nio.file.Files.readAllBytes(companion);
                }
                return parse(java.nio.file.Files.readAllBytes(path),mtl,file.getName(),precision,mode);
            } catch(IOException failure) {throw new ModelFormatException("Failed to load model: "+file,failure);}
        }
        String path=pathOf(resource);
        if(path==null)throw new ModelFormatException("Unsupported model resource: "+resource);
        String cacheKey=path+"|"+precision+"|"+mode;
        return CACHE.computeIfAbsent(cacheKey,key -> {
            byte[] bytes=NGTFileLoader.findAsset(path);
            if(bytes==null)throw new ModelFormatException("Failed to load model: "+path,new java.io.FileNotFoundException(path));
            try {
                byte[] mtl=path.toLowerCase(java.util.Locale.ROOT).endsWith(".obj")
                    ?NGTFileLoader.findAsset(path.substring(0,path.length()-4)+".mtl"):null;
                return parse(bytes,mtl,path,precision,mode);
            } catch(ModelFormatException failure) {throw failure;}
            catch(Exception failure) {throw new ModelFormatException("Failed to parse model: "+path,failure);}
        });
    }

    /** Original InputStream[] overload; streams are owned and closed by the loader. */
    public static PolygonModel loadModel(java.io.InputStream[] streams,String name,VecAccuracy accuracy,Object... options) {
        try {
            if(streams==null || streams.length==0 || streams[0]==null)throw new java.io.FileNotFoundException(name);
            byte[] bytes=streams[0].readAllBytes();
            byte[] mtl=streams.length>1 && streams[1]!=null?streams[1].readAllBytes():null;
            return parse(bytes,mtl,name,accuracy,drawMode(options));
        } catch(IOException failure) {throw new ModelFormatException("Failed to load model: "+name,failure);}
        finally {if(streams!=null)for(var stream:streams)if(stream!=null)try {stream.close();}catch(IOException ignored) {}}
    }

    /** MQO テキストから直接 (VehicleScriptRenderers が車体モデルグラフ生成に使用)。 */
    public static PolygonModel parse(byte[] bytes, String name) throws IOException {
        return parse(bytes,name,VecAccuracy.MEDIUM,4);
    }
    public static PolygonModel parse(byte[] bytes,String name,VecAccuracy accuracy,int drawMode) throws IOException {
        return parse(bytes,null,name,accuracy,drawMode);
    }
    private static PolygonModel parse(byte[] bytes,byte[] mtl,String name,VecAccuracy accuracy,int drawMode) throws IOException {
        try {return parseDocument(bytes,mtl,name,accuracy,drawMode);}
        catch(ModelFormatException failure) {throw failure;}
        catch(Exception failure) {throw new ModelFormatException("Failed to parse model: "+name,failure);}
    }
    private static PolygonModel parseDocument(byte[] bytes,byte[] mtl,String name,VecAccuracy accuracy,int drawMode) throws IOException {
        accuracy=accuracy==null?VecAccuracy.MEDIUM:accuracy;
        String extension=name.toLowerCase(java.util.Locale.ROOT);
        if(extension.endsWith(".obj"))return LegacyObjReader.parse(bytes,mtl,name,accuracy);
        if(!extension.endsWith(".mqo") && !extension.endsWith(".mqoz"))return null;
        final int mode=extension.endsWith(".mqoz")?4:drawMode;
        if(mode!=4 && mode!=7) throw new IllegalArgumentException("Unsupported polygon draw mode: "+drawMode);
        String text = MqoReader.extractText(bytes, name);
        PolygonModel model = new PolygonModel();
        model.fileName=name;
        model.accuracy=accuracy==null?VecAccuracy.MEDIUM:accuracy;model.drawMode=mode;
        if (text == null)throw new ModelFormatException("Unreadable model archive: "+name);
        if(!text.contains("Metasequoia Document") || !text.contains("Eof"))throw new ModelFormatException("Invalid MQO document: "+name);
        MqoValidation.validate(text,name);
        // ★書式の解釈は MqoReader だけが持つ ([[MqoReader]])。ここは「スクリプトが見る
        // グラフ」への組み立てに徹する: 座標は 0.01 倍、面の頂点は逆順格納。
        MqoReader.read(text, new MqoReader.Handler() {
            private GroupObject current;
            private int mirrorAxis = -1;
            private final java.util.Map<Vertex, Vertex> mirroredVertices = new java.util.HashMap<>();
            private final List<Vertex> verts = new ArrayList<>();

            @Override
            public void material(int index, String name, String texture, float r, float g, float b, float a) {
                model.materials.put(name, new Material((byte) index, null));
            }

            @Override
            public void objectStart(String objectName) {
                this.mirrorAxis = -1;
                this.mirroredVertices.clear();
                this.current = new GroupObject(objectName,mode);
                model.groupObjects.add(this.current);
                this.verts.clear();
            }

            @Override
            public void facet(float angle) {
                if (this.current != null) {
                    this.current.smoothingAngle = angle;
                }
            }

            @Override
            public void mirrorAxis(int axis) {
                this.mirrorAxis = axis == 1 ? 0 : axis == 2 ? 1 : 2;
            }

            @Override
            public void verticesStart() {
                this.verts.clear();
            }

            @Override
            public void vertex(float x, float y, float z) {
                Vertex vertex=Vertex.create(x*0.01F,y*0.01F,z*0.01F,model.accuracy);
                this.verts.add(vertex);model.calcSizeBox(vertex);
            }

            @Override
            public void face(int count, int materialId, int[] vertexIndices, float[] uvs) {
                if (this.current != null) {
                    if (count < 3) return;
                    addFace(this.current, this.verts, count, materialId, vertexIndices, uvs,model.accuracy,mode);
                    if (this.mirrorAxis >= 0) {
                        Face original = this.current.faces.get(this.current.faces.size() - 1);
                        Face mirror = original.getMirror(this.mirrorAxis, this.mirroredVertices, model.accuracy);
                        mirror.calculateFaceNormal(model.accuracy);
                        this.current.faces.add(mirror);
                    }
                }
            }
        });
        // 本家 PolygonModel のコンストラクタは init の直後に calcVertexNormals を回す。
        // ★これが無いと face.vertexNormals が null のままで、スクリプト描画経路
        // (drawModelGroup) が面法線だけで描く = 常にフラット陰影になる。
        for (GroupObject group : model.groupObjects) {
            group.calcVertexNormals(model.accuracy);
        }
        return model;
    }

    /**
     * 本家 MqoModel.parseFaceQuads は addVertex(3 - i, ...) で頂点/UV を逆順格納する。
     * 法線 (= CustomAnimator の左右判定 / CustomMonitor の向き) がこれに依存するため必ず一致させる。
     */
    private static void addFace(GroupObject group,List<Vertex> verts,int count,int materialId,
                                int[] vertexIndices,float[] uvs,VecAccuracy accuracy,int drawMode) {
        int[] order;
        if(drawMode==7) {
            if(count!=4) throw new IllegalArgumentException("GL_QUADS requires four corners");
            order=new int[]{3,2,1,0};
        } else order=LegacyPolygon.triangleIndices(count);
        Face face=new Face(order.length,materialId);
        for(int i=0;i<order.length;i++) {
            int source=order[i];
            float u=uvs==null?0:uvs[source*2],v=uvs==null?0:uvs[source*2+1];
            face.addVertex(i,verts.get(vertexIndices[source]),TextureCoordinate.create(u,v,accuracy));
        }
        face.calculateFaceNormal(accuracy);group.faces.add(face);
    }

    private static int drawMode(Object options) {
        if(options instanceof Number value) return value.intValue();
        if(options instanceof Object[] array && array.length>0) return drawMode(array[0]);
        if(options instanceof java.util.List<?> list && !list.isEmpty()) return drawMode(list.get(0));
        if(options instanceof org.openjdk.nashorn.api.scripting.ScriptObjectMirror array && array.isArray() && array.hasSlot(0)) return drawMode(array.getSlot(0));
        return 4;
    }

    private static String pathOf(Object resource) {
        if (resource instanceof jp.ngt.mccompat.ResourceLocation compat) {
            return compat.toString();
        }
        if (resource instanceof net.minecraft.resources.ResourceLocation rl) {
            return rl.toString();
        }
        if (resource instanceof String s) {
            return s;
        }
        return null;
    }
}

package jp.ngt.ngtlib.renderer;

import jp.ngt.ngtlib.renderer.model.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Quaternionf;
import java.util.*;

/** CPU equivalent of legacy GL_SELECT over recorded model geometry. */
public final class LegacySelection {
    private LegacySelection() {}
    private record Context(Matrix4f clip, PolygonModel model, int width, int height) {}
    private record Session(Context context, GLRecorder recorder, int from, float range) {}
    public record Hit(int id,double depth) {}
    private static final ThreadLocal<Context> CONTEXT=new ThreadLocal<>();
    private static final ThreadLocal<Session> SESSION=new ThreadLocal<>();
    private static final ThreadLocal<List<Hit>> RESULTS=ThreadLocal.withInitial(List::of);
    public static LegacyRenderState.Scope context(Matrix4f modelView,Matrix4f projection,PolygonModel model,int width,int height) {
        Context previous=CONTEXT.get();
        CONTEXT.set(new Context(new Matrix4f(projection).mul(modelView),model,Math.max(1,width),Math.max(1,height)));
        return ()->{ if(previous==null) CONTEXT.remove(); else CONTEXT.set(previous); SESSION.remove(); };
    }
    public static void start(float range) {
        if(!(range>0)) throw new IllegalArgumentException("Picking range must be positive");
        if(SESSION.get()!=null) throw new IllegalStateException("Picking already active");
        GLRecorder recorder=GLRecorder.active();
        RESULTS.set(List.of());
        SESSION.set(new Session(CONTEXT.get(),recorder,recorder==null?0:recorder.getCommands().size(),range));
    }
    public static int finish() {
        Session session=SESSION.get(); SESSION.remove();
        if(session==null) throw new IllegalStateException("Picking has not started");
        if(session.context==null || session.recorder==null) return 0;
        Map<Integer,Double> hits=new LinkedHashMap<>();
        Matrix4f transform=new Matrix4f(); Deque<Matrix4f> stack=new ArrayDeque<>(); int name=0,index=0;
        for(GLRecorder.Cmd command:session.recorder.getCommands()) {
            boolean draw=index++>=session.from;
            switch(command.op) {
                case PUSH -> stack.push(new Matrix4f(transform));
                case POP -> { if(!stack.isEmpty()) transform=stack.pop(); }
                case TRANSLATE -> transform.translate(command.a,command.b,command.c);
                case SCALE -> transform.scale(command.a,command.b,command.c);
                case ROTATE -> { if(command.payload instanceof Quaternionf rotation) transform.rotate(rotation); }
                case MULT_MATRIX -> { if(command.payload instanceof float[] matrix) transform.mul(new Matrix4f().set(matrix)); }
                case LOAD_NAME -> name=(Integer)command.payload;
                case RENDER_GROUPS, RENDER_PARTS -> {
                    if(!draw || session.context.model==null || !(command.payload instanceof Set<?> names)) break;
                    int id=command.partsRef instanceof jp.ngt.rtm.render.ActionParts part ? part.id:name;
                    for(GroupObject group:session.context.model.getGroupObjects()) {
                        if(!names.contains(group.name.trim().toLowerCase(Locale.ROOT))) continue;
                        faces(session,group,transform,id,hits);
                    }
                }
                case DRAW_MODEL_GROUP -> {
                    if(draw && command.payload instanceof PolygonModel model) {
                        GroupObject group=model.getGroupObject(command.name); if(group!=null) faces(session,group,transform,name,hits);
                    }
                }
                case DRAW_TESS -> {
                    if(draw && command.payload instanceof GLRecorder.TessDraw tess) {
                        int count=tess.verts.length/9;
                        for(int[] triangle:triangles(tess.mode,count)) {
                            Vector4f[] polygon=new Vector4f[3];
                            for(int i=0;i<3;i++) { int offset=triangle[i]*9; polygon[i]=new Vector4f(tess.verts[offset],tess.verts[offset+1],tess.verts[offset+2],1); }
                            hit(session,polygon,transform,name,hits);
                        }
                    }
                }
                default -> {}
            }
        }
        List<Hit> results=new ArrayList<>(); hits.forEach((id,depth)->results.add(new Hit(id,depth)));
        RESULTS.set(List.copyOf(results)); return results.size();
    }
    public static Hit hit(int index) { return RESULTS.get().get(index); }
    private static void faces(Session session,GroupObject group,Matrix4f transform,int name,Map<Integer,Double> hits) {
        for(Face face:group.faces) {
            for(int[] triangle:triangles(face.vertices.length%3==0?4:7,face.vertices.length)) {
                Vector4f[] polygon=new Vector4f[3]; boolean valid=true;
                for(int i=0;i<3;i++) { Vertex v=face.vertices[triangle[i]]; if(v==null) { valid=false; break; } polygon[i]=new Vector4f(v.getX(),v.getY(),v.getZ(),1); }
                if(valid) hit(session,polygon,transform,name,hits);
            }
        }
    }
    private static List<int[]> triangles(int mode,int count) {
        List<int[]> out=new ArrayList<>();
        switch(mode) {
            case 4 -> { for(int i=0;i+2<count;i+=3) out.add(new int[]{i,i+1,i+2}); }
            case 5 -> { for(int i=0;i+2<count;i++) out.add((i&1)==0?new int[]{i,i+1,i+2}:new int[]{i+1,i,i+2}); }
            case 6,9 -> { for(int i=1;i+1<count;i++) out.add(new int[]{0,i,i+1}); }
            case 7 -> { for(int i=0;i+3<count;i+=4) { out.add(new int[]{i,i+1,i+2}); out.add(new int[]{i,i+2,i+3}); } }
            case 8 -> { for(int i=0;i+3<count;i+=2) { out.add(new int[]{i,i+1,i+3}); out.add(new int[]{i,i+3,i+2}); } }
            default -> {}
        }
        return out;
    }
    private static void hit(Session session,Vector4f[] vertices,Matrix4f transform,int name,Map<Integer,Double> hits) {
        Matrix4f clip=new Matrix4f(session.context.clip).mul(transform);
        List<Vector4f> polygon=new ArrayList<>();
        for(Vector4f vertex:vertices) { clip.transform(vertex); if(!vertex.isFinite()) return; polygon.add(vertex); }
        float x=session.range/session.context.width,y=session.range/session.context.height;
        // Clip the polygon against the small center pick box and the near/far planes.
        Vector4f[] planes={new Vector4f(1,0,0,x),new Vector4f(-1,0,0,x),new Vector4f(0,1,0,y),new Vector4f(0,-1,0,y),new Vector4f(0,0,1,1),new Vector4f(0,0,-1,1)};
        for(Vector4f plane:planes) {
            if(polygon.isEmpty()) return;
            List<Vector4f> clipped=new ArrayList<>(); Vector4f previous=polygon.get(polygon.size()-1); float previousDistance=previous.dot(plane);
            for(Vector4f current:polygon) {
                float distance=current.dot(plane);
                if((distance>=0)!=(previousDistance>=0)) clipped.add(new Vector4f(previous).lerp(current,previousDistance/(previousDistance-distance)));
                if(distance>=0) clipped.add(current);
                previous=current; previousDistance=distance;
            }
            polygon=clipped;
        }
        double depth=Double.POSITIVE_INFINITY;
        for(Vector4f vertex:polygon) if(vertex.w>0) depth=Math.min(depth,(vertex.z/vertex.w+1)*0.5);
        if(Double.isFinite(depth)) hits.merge(name,Math.max(0,Math.min(1,depth)),Math::min);
    }
}

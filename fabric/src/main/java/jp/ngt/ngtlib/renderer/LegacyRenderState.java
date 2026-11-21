package jp.ngt.ngtlib.renderer;

import java.util.*;

/** Script-local fixed-function state. Snapshots travel with geometry, never with GPU globals. */
public final class LegacyRenderState {
    public static final int CURRENT_BIT=1, POINT_BIT=2, LINE_BIT=4, POLYGON_BIT=8, LIGHTING_BIT=64,
            DEPTH_BUFFER_BIT=256, ENABLE_BIT=8192, COLOR_BUFFER_BIT=16384, TEXTURE_BIT=262144;

    public record Snapshot(Map<Integer,Boolean> capabilities, Integer sourceBlend, Integer destinationBlend,
                           Boolean depthWrite, Integer alphaFunction, Float alphaReference,
                           Integer shadeModel, Float lineWidth, Integer sourceAlphaBlend, Integer destinationAlphaBlend, float textureU, float textureV, Integer depthFunction, Integer cullFace, Float pointSize) {
        public Snapshot { capabilities=Map.copyOf(capabilities); }
        public boolean enabled(int capability, boolean inherited) { return capabilities.getOrDefault(capability,inherited); }
        public Snapshot withDefaultAlpha(int function,float reference) {
            if(alphaFunction!=null) return this;
            return new Snapshot(capabilities,sourceBlend,destinationBlend,depthWrite,function,reference,
                    shadeModel,lineWidth,sourceAlphaBlend,destinationAlphaBlend,textureU,textureV,depthFunction,cullFace,pointSize);
        }
        public boolean empty() { return capabilities.isEmpty() && sourceBlend==null && depthWrite==null
                && alphaFunction==null && shadeModel==null && lineWidth==null && textureU==0 && textureV==0 && depthFunction==null && cullFace==null && pointSize==null; }
    }
    private record Saved(int mask, Snapshot state, float[] color, float[] normal, float[] uv) {}
    private final Map<Integer,Boolean> capabilities=new HashMap<>();
    private final Map<Integer,Boolean> baseline=new HashMap<>();
    private final Deque<Saved> attributes=new ArrayDeque<>();
    private Integer sourceBlend,destinationBlend,alphaFunction,shadeModel,sourceAlphaBlend,destinationAlphaBlend;
    private Integer depthFunction,cullFace;
    private Boolean depthWrite;
    private Float alphaReference,lineWidth,pointSize;
    private Snapshot cached;
    private float textureU,textureV;
    private final Deque<float[]> textureStack=new ArrayDeque<>();
    private float[] currentColor={1,1,1,1}, currentNormal={0,0,1}, currentUV={0,0};
    private static final ThreadLocal<LegacyRenderState> FALLBACK=ThreadLocal.withInitial(LegacyRenderState::new);
    private static final ThreadLocal<Snapshot> PLAYBACK=new ThreadLocal<>();

    public static LegacyRenderState current() {
        GLRecorder recorder=GLRecorder.active();
        return recorder==null ? FALLBACK.get() : recorder.state();
    }
    public static Snapshot playback() { return PLAYBACK.get(); }
    public interface Scope extends AutoCloseable { @Override void close(); }
    public static Scope use(Snapshot snapshot) {
        Snapshot previous=PLAYBACK.get();
        if(snapshot==null) PLAYBACK.remove(); else PLAYBACK.set(snapshot);
        return () -> { if(previous==null) PLAYBACK.remove(); else PLAYBACK.set(previous); };
    }
    public void clear() {
        capabilities.clear(); baseline.clear(); attributes.clear(); textureStack.clear(); textureU=textureV=0;
        sourceBlend=destinationBlend=alphaFunction=shadeModel=sourceAlphaBlend=destinationAlphaBlend=null;
        depthFunction=cullFace=null; depthWrite=null; alphaReference=lineWidth=pointSize=null; cached=null;
        currentColor=new float[]{1,1,1,1}; currentNormal=new float[]{0,0,1}; currentUV=new float[]{0,0};
    }
    public void baseline(int capability, boolean enabled) { baseline.put(capability,enabled); }
    public void enable(int capability, boolean enabled) { capabilities.put(capability,enabled); cached=null; }
    public boolean enabled(int capability) {
        return capabilities.getOrDefault(capability, baseline.getOrDefault(capability,
                capability==0x0B50 || capability==0x0DE1 || capability==0x0B44 || capability==0x0B71 || capability==0x0BC0));
    }
    public void blend(int source,int destination) { blendSeparate(source,destination,source,destination); }
    public void blendSeparate(int source,int destination,int sourceAlpha,int destinationAlpha) {
        sourceBlend=source; destinationBlend=destination; sourceAlphaBlend=sourceAlpha; destinationAlphaBlend=destinationAlpha; cached=null;
    }
    public void depthFunction(int function) { if(function<512 || function>519) throw new IllegalArgumentException("Invalid depth function"); depthFunction=function; cached=null; }
    public void cullFace(int mode) { if(mode!=1028 && mode!=1029 && mode!=1032) throw new IllegalArgumentException("Invalid cull face"); cullFace=mode; cached=null; }
    public void depthWrite(boolean value) { depthWrite=value; cached=null; }
    public void alpha(int function,float reference) { alphaFunction=function; alphaReference=Math.max(0,Math.min(1,reference)); cached=null; }
    public void shade(int mode) { shadeModel=mode; cached=null; }
    public void pointSize(float size) { if(!(size>0) || !Float.isFinite(size)) throw new IllegalArgumentException("Point size must be finite and positive"); pointSize=size; cached=null; }
    public void lineWidth(float width) { if(!(width>0)) throw new IllegalArgumentException("Line width must be positive"); lineWidth=width; cached=null; }
    public Snapshot snapshot() {
        if(cached==null) cached=new Snapshot(capabilities,sourceBlend,destinationBlend,depthWrite,alphaFunction,alphaReference,shadeModel,lineWidth,sourceAlphaBlend,destinationAlphaBlend,textureU,textureV,depthFunction,cullFace,pointSize);
        return cached;
    }
    public void pushTexture(float u,float v) {
        textureStack.push(new float[]{textureU,textureV}); textureU+=u; textureV+=v; cached=null;
    }
    public void popTexture() {
        if(textureStack.isEmpty()) throw new IllegalStateException("postMoveTexUV without preMoveTexUV");
        float[] old=textureStack.pop(); textureU=old[0]; textureV=old[1]; cached=null;
    }
    public float[] currentColor() { return currentColor.clone(); }
    public float[] currentNormal() { return currentNormal.clone(); }
    public float[] currentUV() { return currentUV.clone(); }
    public void texCoord(float u,float v) { currentUV=new float[]{u,v}; }
    public void color(float r,float g,float b,float a) { currentColor=new float[]{r,g,b,a}; }
    public void normal(float x,float y,float z) { currentNormal=new float[]{x,y,z}; }
    public int attributeDepth() {return attributes.size();}
    public void push(int mask) { attributes.push(new Saved(mask,snapshot(),currentColor,currentNormal,currentUV)); }
    public void pop() {
        if(attributes.isEmpty()) throw new IllegalStateException("glPopAttrib without glPushAttrib");
        Saved saved=attributes.pop(); Snapshot old=saved.state; int mask=saved.mask;
        if((mask&CURRENT_BIT)!=0) {
            currentColor=saved.color; currentNormal=saved.normal; currentUV=saved.uv;
            GLRecorder recorder=GLRecorder.active();
            if(recorder!=null) recorder.color(currentColor[0],currentColor[1],currentColor[2],currentColor[3]);
            NGTTessellator.instance.setNormal(currentNormal[0],currentNormal[1],currentNormal[2]);
            NGTTessellator.instance.setTextureUV(currentUV[0],currentUV[1]);
        }
        if((mask&ENABLE_BIT)!=0) { capabilities.clear(); capabilities.putAll(old.capabilities); }
        else {
            if((mask&COLOR_BUFFER_BIT)!=0) { restoreCapability(old,0x0BE2); restoreCapability(old,0x0BC0); }
            if((mask&DEPTH_BUFFER_BIT)!=0) restoreCapability(old,0x0B71);
            if((mask&LIGHTING_BIT)!=0) restoreCapability(old,0x0B50);
            if((mask&POLYGON_BIT)!=0) restoreCapability(old,0x0B44);
            if((mask&TEXTURE_BIT)!=0) restoreCapability(old,0x0DE1);
        }
        if((mask&COLOR_BUFFER_BIT)!=0) {
            sourceBlend=old.sourceBlend; destinationBlend=old.destinationBlend;
            sourceAlphaBlend=old.sourceAlphaBlend; destinationAlphaBlend=old.destinationAlphaBlend;
            alphaFunction=old.alphaFunction; alphaReference=old.alphaReference;
        }
        if((mask&DEPTH_BUFFER_BIT)!=0) { depthWrite=old.depthWrite; depthFunction=old.depthFunction; }
        if((mask&POLYGON_BIT)!=0) cullFace=old.cullFace;
        if((mask&LIGHTING_BIT)!=0) shadeModel=old.shadeModel;
        if((mask&LINE_BIT)!=0) lineWidth=old.lineWidth;
        if((mask&POINT_BIT)!=0) pointSize=old.pointSize;
        cached=null;
    }
    private void restoreCapability(Snapshot snapshot,int capability) {
        if(snapshot.capabilities.containsKey(capability)) capabilities.put(capability,snapshot.capabilities.get(capability));
        else capabilities.remove(capability);
    }
}

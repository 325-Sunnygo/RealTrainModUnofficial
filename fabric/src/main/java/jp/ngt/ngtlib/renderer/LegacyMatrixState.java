package jp.ngt.ngtlib.renderer;

import org.joml.Matrix4f;
import java.util.*;

/** Virtual fixed-function stacks: no removed GL entry points are called on core contexts. */
public final class LegacyMatrixState {
    private final GLRecorder recorder;
    public int mode=5888,activeTexture=33984;
    private final Map<Integer,Matrix4f> matrices=new HashMap<>();
    private final Map<Integer,Deque<Matrix4f>> stacks=new HashMap<>();
    public final Map<Integer,Integer> textureBindings=new HashMap<>(),textureEnvironments=new HashMap<>();
    public final Map<Integer,Boolean> textureEnabled=new HashMap<>();
    public final Map<Integer,float[]> textureCoords=new HashMap<>();
    public boolean[] colorMask={true,true,true,true};
    LegacyMatrixState(GLRecorder recorder) {this.recorder=recorder;}
    public void clear() {mode=5888;activeTexture=33984;matrices.clear();stacks.clear();textureBindings.clear();textureEnvironments.clear();textureEnabled.clear();textureCoords.clear();colorMask=new boolean[]{true,true,true,true};}
    private int key() {return mode==5890 ? 5890+activeTexture-33984 : mode;}
    public Matrix4f current() {return matrices.computeIfAbsent(key(),k->new Matrix4f());}
    public float[] get(int query) {int old=mode;mode=query==2983?5889:query==2984?5890:5888;float[] result=current().get(new float[16]);mode=old;return result;}
    public int depth(int target) {int old=mode;mode=target;var stack=stacks.get(key());mode=old;return 1+(stack==null?0:stack.size());}
    public void load(Matrix4f matrix) {current().set(matrix);emit();}
    public void multiply(Matrix4f matrix) {current().mul(matrix);emit();}
    public void push() {stacks.computeIfAbsent(key(),k->new ArrayDeque<>()).push(new Matrix4f(current()));if(mode==5888)recorder.push();}
    public void pop() {var stack=stacks.get(key());if(stack==null||stack.isEmpty())throw new IllegalStateException("glPopMatrix without glPushMatrix");current().set(stack.pop());if(mode==5888)recorder.pop();else emit();}
    private void emit() {float[] values=current().get(new float[16]);if(mode==5888)recorder.loadMatrix(values);else if(mode==5889)recorder.projection(values);}
    public float[] uv(float u,float v) {int old=mode;mode=5890;var transformed=current().transform(new org.joml.Vector4f(u,v,0,1));mode=old;return new float[]{transformed.x/transformed.w,transformed.y/transformed.w};}
}

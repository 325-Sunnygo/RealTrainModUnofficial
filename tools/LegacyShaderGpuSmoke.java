import java.nio.*;
import java.nio.file.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Render the shipped compatibility shader to a real hidden GL framebuffer. */
public final class LegacyShaderGpuSmoke {
    static int program;
    static int compile(int type,Path path) throws Exception {
        int shader=glCreateShader(type); glShaderSource(shader,Files.readString(path)); glCompileShader(shader);
        if(glGetShaderi(shader,GL_COMPILE_STATUS)==0) throw new AssertionError(glGetShaderInfoLog(shader));
        return shader;
    }
    static void integer(String name,int value) { glUniform1i(glGetUniformLocation(program,name),value); }
    static void real(String name,float value) { glUniform1f(glGetUniformLocation(program,name),value); }
    static void attribute(String name,float... values) {
        int location=glGetAttribLocation(program,name);
        if(location<0) return;
        glVertexAttrib4f(location,values[0],values.length>1?values[1]:0,values.length>2?values[2]:0,values.length>3?values[3]:1);
    }
    /** Independent OpenGL 2.1 fixed-function oracle for the original RTM alpha calls. */
    static byte[] fixedFunctionAlphaReference() {
        glfwDefaultWindowHints(); glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,2); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,1);
        long reference=glfwCreateWindow(16,16,"Original fixed-function alpha reference",0,0);
        if(reference==0) throw new AssertionError("Original GL 2.1 reference context unavailable");
        byte[] pixels=new byte[256*2*4];
        try {
            glfwMakeContextCurrent(reference); GL.createCapabilities();
            glViewport(0,0,16,16); glDisable(GL_DEPTH_TEST); glDisable(GL_DITHER);
            glEnable(GL_TEXTURE_2D); glBindTexture(GL_TEXTURE_2D,glGenTextures());
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            org.lwjgl.opengl.GL11.glColor4f(1,1,1,1);
            org.lwjgl.opengl.GL11.glEnable(GL_ALPHA_TEST);
            for(int alpha=0;alpha<256;alpha++) {
                ByteBuffer texel=BufferUtils.createByteBuffer(4);
                texel.put(new byte[]{0,0,0,(byte)alpha}).flip();
                glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,texel);
                for(int pass=0;pass<2;pass++) {
                    org.lwjgl.opengl.GL11.glAlphaFunc(pass==0?GL_EQUAL:GL_LESS,1);
                    if(pass==0) glDisable(GL_BLEND);
                    else { glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA); }
                    glClearColor(0,0,1,1); glClear(GL_COLOR_BUFFER_BIT);
                    org.lwjgl.opengl.GL11.glBegin(GL_TRIANGLES);
                    org.lwjgl.opengl.GL11.glTexCoord2f(0.5F,0.5F);
                    org.lwjgl.opengl.GL11.glVertex3f(-1,-1,0);
                    org.lwjgl.opengl.GL11.glVertex3f(3,-1,0);
                    org.lwjgl.opengl.GL11.glVertex3f(-1,3,0);
                    org.lwjgl.opengl.GL11.glEnd();
                    ByteBuffer sample=BufferUtils.createByteBuffer(4);
                    glReadPixels(8,8,1,1,GL_RGBA,GL_UNSIGNED_BYTE,sample);
                    sample.get(pixels,(alpha*2+pass)*4,4);
                }
            }
            return pixels;
        } finally { glfwDestroyWindow(reference); }
    }
    public static void main(String[] args) throws Exception {
        GLFWErrorCallback error=GLFWErrorCallback.createPrint(System.err); error.set();
        if(!glfwInit()) throw new AssertionError("GLFW initialization failed");
        long window=0;
        try {
            byte[] originalAlpha=fixedFunctionAlphaReference();
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,2);
            glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT,GLFW_TRUE);
            window=glfwCreateWindow(16,16,"Legacy shader smoke",0,0);
            if(window==0) throw new AssertionError("OpenGL context unavailable");
            glfwMakeContextCurrent(window); GL.createCapabilities();
            program=glCreateProgram(); Path folder=Path.of(args[0]);
            glAttachShader(program,compile(GL_VERTEX_SHADER,folder.resolve("legacy_entity.vert")));
            glAttachShader(program,compile(GL_FRAGMENT_SHADER,folder.resolve("legacy_entity.frag")));
            glLinkProgram(program);
            if(glGetProgrami(program,GL_LINK_STATUS)==0) throw new AssertionError(glGetProgramInfoLog(program));
            glUseProgram(program); glBindVertexArray(glGenVertexArrays());
            int buffer=glGenBuffers(); glBindBuffer(GL_ARRAY_BUFFER,buffer);
            glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,0, 3,-1,0, -1,3,0},GL_STATIC_DRAW);
            int position=glGetAttribLocation(program,"Position"); glEnableVertexAttribArray(position);
            glVertexAttribPointer(position,3,GL_FLOAT,false,0,0L);
            attribute("Color",1,0,0,0.5F); attribute("Normal",0,0,1); attribute("UV0",0,0);
            for(String uv:new String[]{"UV1","UV2"}) { int location=glGetAttribLocation(program,uv); if(location>=0) glVertexAttribI2i(location,0,0); }
            float[] identity={1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
            for(String matrix:new String[]{"ModelViewMat","ProjMat"}) glUniformMatrix4fv(glGetUniformLocation(program,matrix),false,identity);
            glUniform4f(glGetUniformLocation(program,"ColorModulator"),1,1,1,1);
            for(int unit=0;unit<3;unit++) {
                glActiveTexture(GL_TEXTURE0+unit); glBindTexture(GL_TEXTURE_2D,glGenTextures());
                ByteBuffer white=BufferUtils.createByteBuffer(4); white.putInt(-1).flip();
                glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,white);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
                integer("Sampler"+unit,unit);
            }
            integer("LegacyLighting",0); integer("LegacyTexture",0); integer("LegacyFog",0); integer("LegacyFlat",0);
            glViewport(0,0,16,16); glDisable(GL_BLEND); glDisable(GL_DEPTH_TEST); glDisable(GL_DITHER);
            // Alpha is 0.5 after vertex modulation. Exercise all eight legacy comparisons.
            int[] funcs={512,513,514,515,516,517,518,519};
            boolean[] expected={false,false,true,true,false,false,true,true};
            for(int i=0;i<funcs.length;i++) {
                glClearColor(0,0,1,1); glClear(GL_COLOR_BUFFER_BIT);
                integer("LegacyAlphaFunction",funcs[i]); real("LegacyAlphaReference",0.5F);
                glDrawArrays(GL_TRIANGLES,0,3);
                ByteBuffer pixel=BufferUtils.createByteBuffer(4); glReadPixels(8,8,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
                boolean red=(pixel.get(0)&255)>200 && (pixel.get(2)&255)<20;
                boolean blue=(pixel.get(2)&255)>200 && (pixel.get(0)&255)<20;
                if((expected[i] && !red)||(!expected[i] && !blue)) throw new AssertionError("Alpha function "+funcs[i]+" gave unexpected framebuffer pixel");
            }
            // A two-texel texture makes the UV translation visible in the framebuffer.
            glActiveTexture(GL_TEXTURE0);
            ByteBuffer texels=BufferUtils.createByteBuffer(8);
            texels.put(new byte[]{(byte)255,0,0,(byte)255,0,(byte)255,0,(byte)255}).flip();
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,2,1,0,GL_RGBA,GL_UNSIGNED_BYTE,texels);
            attribute("Color",1,1,1,1); integer("LegacyTexture",1); integer("LegacyAlphaFunction",519);
            for(int shift=0;shift<2;shift++) {
                glUniform2f(glGetUniformLocation(program,"LegacyUvOffset"),shift==0 ? 0.1F : 0.75F,0);
                glClear(GL_COLOR_BUFFER_BIT); glDrawArrays(GL_TRIANGLES,0,3);
                ByteBuffer pixel=BufferUtils.createByteBuffer(4); glReadPixels(8,8,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
                if((pixel.get(shift)&255)<200 || (pixel.get(1-shift)&255)>20) throw new AssertionError("UV translation did not sample expected texel");
            }
            integer("LegacyTexture",0); attribute("Color",1,0,0,1);
            int pointChecks=0;
            float[] positions={-1.001F,-1,-0.9999F,-0.125F,-0.0001F,0,0.0001F,0.03125F,0.0625F,0.9999F,1,1.001F};
            for(float x:positions) for(float y:new float[]{-1,0,0.0001F,0.03125F,1}) for(float size:new float[]{1,1.5F,2,2.5F,3,4,7}) {
                ByteBuffer repeated=BufferUtils.createByteBuffer(4*3*4);
                for(int repeat=0;repeat<4;repeat++) repeated.putFloat(x).putFloat(y).putFloat(0);
                repeated.flip();
                ByteBuffer compact=BufferUtils.createByteBuffer(3*4);
                jp.ngt.ngtlib.renderer.LegacyPointVertices.compact(repeated,compact,12,1); compact.flip();
                glBufferData(GL_ARRAY_BUFFER,new float[]{x,y,0},GL_STATIC_DRAW);
                glDisable(GL_PROGRAM_POINT_SIZE); glPointSize(size);
                glClear(GL_COLOR_BUFFER_BIT); glDrawArrays(GL_POINTS,0,1);
                ByteBuffer nativePixels=BufferUtils.createByteBuffer(16*16*4);
                glReadPixels(0,0,16,16,GL_RGBA,GL_UNSIGNED_BYTE,nativePixels);
                glBufferData(GL_ARRAY_BUFFER,compact,GL_STATIC_DRAW);
                glPointSize(9); glEnable(GL_PROGRAM_POINT_SIZE);
                glClear(GL_COLOR_BUFFER_BIT);
                jp.ngt.ngtlib.renderer.LegacyPointDraw.draw(1,size);
                ByteBuffer actualPixels=BufferUtils.createByteBuffer(16*16*4);
                glReadPixels(0,0,16,16,GL_RGBA,GL_UNSIGNED_BYTE,actualPixels);
                if(!nativePixels.equals(actualPixels)) throw new AssertionError("Native point path mismatch at "+x+","+y+" size="+size);
                if(!glIsEnabled(GL_PROGRAM_POINT_SIZE) || glGetFloat(GL_POINT_SIZE)!=9) throw new AssertionError("Point state leaked");
                pointChecks++;
            }
            glDisable(GL_PROGRAM_POINT_SIZE);
            // Overlapping translucent points must blend once per center, not four times per queued quad.
            attribute("Color",1,0,0,0.5F); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            glBufferData(GL_ARRAY_BUFFER,new float[9],GL_STATIC_DRAW); glPointSize(3);
            glClear(GL_COLOR_BUFFER_BIT); glDrawArrays(GL_POINTS,0,3);
            ByteBuffer nativeBlend=BufferUtils.createByteBuffer(16*16*4); glReadPixels(0,0,16,16,GL_RGBA,GL_UNSIGNED_BYTE,nativeBlend);
            ByteBuffer repeatedBlend=BufferUtils.createByteBuffer(3*4*12),compactBlend=BufferUtils.createByteBuffer(3*12);
            jp.ngt.ngtlib.renderer.LegacyPointVertices.compact(repeatedBlend,compactBlend,12,3); compactBlend.flip();
            glBufferData(GL_ARRAY_BUFFER,compactBlend,GL_STATIC_DRAW); glClear(GL_COLOR_BUFFER_BIT);
            jp.ngt.ngtlib.renderer.LegacyPointDraw.draw(3,3);
            ByteBuffer actualBlend=BufferUtils.createByteBuffer(16*16*4); glReadPixels(0,0,16,16,GL_RGBA,GL_UNSIGNED_BYTE,actualBlend);
            if(!nativeBlend.equals(actualBlend)) throw new AssertionError("Repeated center compaction changed alpha blending");
            pointChecks++; glDisable(GL_BLEND);
            // Original vehicle alpha passes must retain the full 0..255 shadow gradient.
            int alphaGradientChecks=0;
            glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,0,3,-1,0,-1,3,0},GL_STATIC_DRAW);
            attribute("Color",1,1,1,1); integer("LegacyTexture",1); real("LegacyAlphaReference",1);
            glActiveTexture(GL_TEXTURE0);
            for(int alpha=0;alpha<256;alpha++) {
                ByteBuffer texel=BufferUtils.createByteBuffer(4); texel.put(new byte[]{0,0,0,(byte)alpha}).flip();
                glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,texel);
                for(int pass=0;pass<2;pass++) {
                    integer("LegacyAlphaFunction",pass==0?514:513);
                    if(pass==0) glDisable(GL_BLEND); else { glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA); }
                    glClear(GL_COLOR_BUFFER_BIT); glDrawArrays(GL_TRIANGLES,0,3);
                    ByteBuffer sample=BufferUtils.createByteBuffer(4); glReadPixels(8,8,1,1,GL_RGBA,GL_UNSIGNED_BYTE,sample);
                    for(int channel=0;channel<4;channel++) {
                        int actual=sample.get(channel)&255, reference=originalAlpha[(alpha*2+pass)*4+channel]&255;
                        if(Math.abs(actual-reference)>1) throw new AssertionError("Original fixed-function mismatch: alpha="+alpha+" pass="+pass+" channel="+channel+" original="+reference+" port="+actual);
                    }
                    int expectedBlue=pass==0 ? alpha==255 ? 0:255 : alpha==255 ? 255:255-alpha;
                    if(Math.abs((sample.get(2)&255)-expectedBlue)>1) throw new AssertionError("Shadow gradient changed at alpha="+alpha+" pass="+pass);
                    alphaGradientChecks++;
                }
            }
            // An alpha-zero fragment still writes depth with the legacy LESS 1 test.
            // Rails must already be present before the vehicle shadow is blended.
            glEnable(GL_DEPTH_TEST); glDepthFunc(GL_LEQUAL); glDepthMask(true);
            ByteBuffer shadowTexel=BufferUtils.createByteBuffer(4);
            shadowTexel.put(new byte[]{0,0,0,(byte)128}).flip();
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,shadowTexel);
            for(int oldOrder=0;oldOrder<2;oldOrder++) {
                glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
                for(int step=0;step<2;step++) {
                    boolean shadow=oldOrder==0 ? step==1 : step==0;
                    float z=shadow?0:0.5F;
                    glBufferData(GL_ARRAY_BUFFER,new float[]{-1,-1,z,3,-1,z,-1,3,z},GL_STATIC_DRAW);
                    integer("LegacyTexture",shadow?1:0); integer("LegacyAlphaFunction",shadow?513:519);
                    attribute("Color",1,shadow?1:0,shadow?1:0,1);
                    if(shadow) { glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA); }
                    else glDisable(GL_BLEND);
                    glDrawArrays(GL_TRIANGLES,0,3);
                }
                ByteBuffer sample=BufferUtils.createByteBuffer(4);
                glReadPixels(8,8,1,1,GL_RGBA,GL_UNSIGNED_BYTE,sample);
                int red=sample.get(0)&255,blue=sample.get(2)&255;
                if(oldOrder==0 ? Math.abs(red-127)>1 || blue!=0 : red!=0 || Math.abs(blue-127)>1)
                    throw new AssertionError("Rail/shadow depth order regression: "+oldOrder+" red="+red+" blue="+blue);
                alphaGradientChecks++;
            }
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_BLEND);
            String simpleVertex="#version 150\nin vec3 Position; void main(){gl_Position=vec4(Position,1);}";
            String simpleFragment="#version 150\nout vec4 result; void main(){result=vec4(1);}";
            int legacy=jp.ngt.ngtlib.renderer.LegacyShaderProgram.create(simpleVertex,simpleFragment);
            if(legacy<=0 || !glIsProgram(legacy)) throw new AssertionError("Legacy custom shader API did not create a program");
            glDeleteProgram(legacy);
            boolean rejected=false;
            try { jp.ngt.ngtlib.renderer.LegacyShaderProgram.create("invalid shader",simpleFragment); }
            catch(IllegalArgumentException expectedFailure) { rejected=true; }
            if(!rejected) throw new AssertionError("Invalid source was silently accepted");
            int status=glGetError(); if(status!=GL_NO_ERROR) throw new AssertionError("OpenGL error "+status);
            System.out.println("Legacy shader GPU regressions: "+(12+pointChecks+alphaGradientChecks)+" passed (native point boundary comparisons="+pointChecks+", "+glGetString(GL_RENDERER)+")");
        } finally { if(window!=0) glfwDestroyWindow(window); glfwTerminate(); error.free(); }
    }
}

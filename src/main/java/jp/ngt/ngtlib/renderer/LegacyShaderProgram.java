package jp.ngt.ngtlib.renderer;

import static org.lwjgl.opengl.GL20.*;

/** The original source-string API, implemented against the current OpenGL context. */
public final class LegacyShaderProgram {
    private LegacyShaderProgram() {}
    public static int create(String vertexSource,String fragmentSource) {
        int vertex=0,fragment=0,program=0;
        boolean success=false;
        try {
            vertex=compile(GL_VERTEX_SHADER,vertexSource);
            fragment=compile(GL_FRAGMENT_SHADER,fragmentSource);
            program=glCreateProgram(); if(program==0) return -1;
            glAttachShader(program,vertex); glAttachShader(program,fragment); glLinkProgram(program);
            if(glGetProgrami(program,GL_LINK_STATUS)==0) {
                System.getLogger(LegacyShaderProgram.class.getName()).log(System.Logger.Level.WARNING,glGetProgramInfoLog(program));
                return -1;
            }
            glValidateProgram(program);
            if(glGetProgrami(program,GL_VALIDATE_STATUS)==0) {
                System.getLogger(LegacyShaderProgram.class.getName()).log(System.Logger.Level.WARNING,glGetProgramInfoLog(program));
                return -1;
            }
            success=true; return program;
        } finally {
            if(program!=0) {
                if(vertex!=0) glDetachShader(program,vertex);
                if(fragment!=0) glDetachShader(program,fragment);
                if(!success) glDeleteProgram(program);
            }
            if(vertex!=0) glDeleteShader(vertex);
            if(fragment!=0) glDeleteShader(fragment);
        }
    }
    private static int compile(int type,String source) {
        int shader=glCreateShader(type);
        if(shader==0) throw new IllegalStateException("Could not allocate legacy shader");
        try {
            glShaderSource(shader,source); glCompileShader(shader);
            if(glGetShaderi(shader,GL_COMPILE_STATUS)==0) throw new IllegalArgumentException(glGetShaderInfoLog(shader));
            return shader;
        } catch(RuntimeException exception) { glDeleteShader(shader); throw exception; }
    }
}

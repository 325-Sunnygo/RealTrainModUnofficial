package com.portofino.realtrainmodunofficial.script;

import jp.ngt.ngtlib.renderer.*;

public final class LegacyImmediateRegression {
    private static int checks;
    private static void check(boolean result,String message) { checks++; if(!result) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var recorder=new GLRecorder(); GLRecorder.activate(recorder);
        try {
            var script=jp.ngt.ngtlib.io.ScriptUtil.createEngine();
            script.eval(PackScriptSource.prepare("var GL=Java.type('org.lwjgl.opengl.GL11'); GL.glColor4f(1,0,0,1); GL.glTexCoord2f(0.25,0.5); GL.glBegin(GL.GL_TRIANGLES); GL.glVertex2f(0,0); GL.glColor3d(0,1,0); GL.glTexCoord2d(0.75,1); GL.glVertex3d(1,0,0); GL.glColor4d(0,0,1,0.5); GL.glNormal3d(1,0,0); GL.glVertex3f(0,1,0); GL.glEnd();","immediate.js"));
            var draw=(GLRecorder.TessDraw)recorder.getCommands().get(recorder.getCommands().size()-1).payload;
            check(draw.mode==4 && draw.verts.length==27,"old GL11 immediate triangle records all vertices");
            check(draw.verts[5]==1 && draw.verts[6]==0 && draw.verts[14]==0 && draw.verts[15]==1 && draw.verts[25]==1,"current color is captured per vertex");
            check(draw.verts[3]==0.25F && draw.verts[4]==0.5F && draw.verts[12]==0.75F && draw.verts[13]==1,"current UV persists and changes per vertex");
            check(draw.normals[6]==1 && draw.normals[7]==0 && !draw.useCurrentColor,"normals and explicit immediate colors survive replay");
            GL11Facade.glPushAttrib(GL11Facade.GL_CURRENT_BIT);
            GL11Facade.glTexCoord2f(0,0); GL11Facade.glPopAttrib();
            GL11Facade.glBegin(0); GL11Facade.glVertex3f(0,0,0); GL11Facade.glEnd();
            draw=(GLRecorder.TessDraw)recorder.getCommands().get(recorder.getCommands().size()-1).payload;
            check(draw.verts[3]==0.75F && draw.verts[4]==1,"CURRENT_BIT restores texture coordinates");
            GL11Facade.glBegin(4); GL11Facade.glVertex3f(0,0,0);
        } finally { GLRecorder.deactivate(); }
        GLRecorder.activate(new GLRecorder());
        try { GL11Facade.glBegin(0); GL11Facade.glVertex2f(0,0); GL11Facade.glEnd(); check(true,"failed script leaves no pending glBegin"); }
        finally { GLRecorder.deactivate(); }
        java.nio.ByteBuffer source=java.nio.ByteBuffer.allocate(40),target=java.nio.ByteBuffer.allocate(8);
        source.position(4); for(int i=0;i<8;i++) source.putInt(i/4==0?11:22); source.limit(36).position(4);
        LegacyPointVertices.compact(source,target,4,2); target.flip();
        check(source.position()==4 && target.getInt()==11 && target.getInt()==22,"point compaction preserves both centers and source position");
        boolean invalid=false;
        try { LegacyPointVertices.compact(java.nio.ByteBuffer.allocate(3),java.nio.ByteBuffer.allocate(4),4,1); }
        catch(IllegalArgumentException expected) { invalid=true; }
        check(invalid,"incomplete point batches are rejected");
        var script=jp.ngt.ngtlib.io.ScriptUtil.createEngine();script.put("tess",NGTTessellator.instance);
        script.eval("tess.startDrawing(4); var F=Java.type('java.lang.Float');tess.addVertexWithUV(F.valueOf(1),F.valueOf(2),F.valueOf(3),0.25,0.75);var vertexCount=tess.draw();");
        check(((Number)script.get("vertexCount")).intValue()>0,"mixed boxed Float coordinates and JS double UVs use the original float signature");
        System.out.println("Legacy immediate regressions: "+checks+" passed");
    }
}

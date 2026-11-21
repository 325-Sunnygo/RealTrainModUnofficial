package com.portofino.realtrainmodunofficial.client.mirror;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import java.lang.Math;

/** Off-axis frustum through the actual mirror corners, seen from the reflected eye. */
public final class MirrorGeometry {
    private MirrorGeometry(){}
    public record View(Vec3 eye,float yaw,float pitch,Matrix4f rotation,Matrix4f projection,float left,float right,float bottom,float top){}
    public static Vec3 reflect(Vec3 eye,Vec3 plane,Direction face) {
        Vec3 n=Vec3.atLowerCornerOf(face.getNormal());return eye.subtract(n.scale(2*eye.subtract(plane).dot(n)));
    }
    public static View view(Vec3 eye,Direction face,java.util.List<Vec3> corners,float far) {
        Vec3 plane=corners.get(0),n=Vec3.atLowerCornerOf(face.getNormal());
        double distance=eye.subtract(plane).dot(n);if(distance<=.005)return null;
        Vec3 reflected=reflect(eye,plane,face);
        float yaw=(float)Math.toDegrees(Math.atan2(-n.x,n.z));float pitch=(float)-Math.toDegrees(Math.asin(n.y));
        Quaternionf quaternion=new Quaternionf().rotationYXZ((float)Math.PI-yaw*(float)Math.PI/180F,-pitch*(float)Math.PI/180F,0);
        Matrix4f rotation=new Matrix4f().rotation(quaternion.conjugate());
        float l=Float.POSITIVE_INFINITY,r=Float.NEGATIVE_INFINITY,b=Float.POSITIVE_INFINITY,t=Float.NEGATIVE_INFINITY;
        for(Vec3 corner:corners){var q=corner.subtract(reflected);var p=new Vector3f((float)q.x,(float)q.y,(float)q.z).mulDirection(rotation);l=Math.min(l,p.x);r=Math.max(r,p.x);b=Math.min(b,p.y);t=Math.max(t,p.y);}
        float near=(float)distance;
        Matrix4f projection=new Matrix4f().frustum(l,r,b,t,near,Math.max(far,near+16));
        return new View(reflected,yaw,pitch,rotation,projection,l,r,b,t);
    }
    /** Sky vertices are camera-centered and must not use the mirror surface as their near plane. */
    public static Matrix4f skyProjection(Matrix4f projection) {
        float near=.05F,far=projection.perspectiveFar();
        return new Matrix4f(projection).m22(-(far+near)/(far-near)).m32(-2*far*near/(far-near));
    }
    public static float[] uv(View view,Vec3 vertex) {
        Vec3 q=vertex.subtract(view.eye);var p=new Vector3f((float)q.x,(float)q.y,(float)q.z).mulDirection(view.rotation);
        return new float[]{(p.x-view.left)/(view.right-view.left),(p.y-view.bottom)/(view.top-view.bottom)};
    }
}

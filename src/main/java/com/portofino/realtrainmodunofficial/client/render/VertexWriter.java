package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** 頂点を「確保なし」で VertexConsumer へ書き込むヘルパ。 */
public final class VertexWriter {

    private static final ThreadLocal<org.joml.Vector3f> POSITION = ThreadLocal.withInitial(org.joml.Vector3f::new);

    private VertexWriter() {
    }

    private static boolean isIdentity(Matrix4f m) {
        return (m.properties() & Matrix4fc.PROPERTY_IDENTITY) != 0;
    }

    /** addVertex(Matrix4f, x, y, z) と同じ結果を確保なしで。 */
    public static VertexConsumer addVertex(VertexConsumer consumer, Matrix4f m, float x, float y, float z) {
        if (isIdentity(m)) {
            return consumer.addVertex(x, y, z);
        }
        // Match VertexConsumer.addVertex(Matrix4f,...) exac…
        var position=m.transformPosition(x,y,z,POSITION.get());
        return consumer.addVertex(position.x,position.y,position.z);
    }

    /** setNormal(Pose, x, y, z) と同じ結果を確保なしで (バニラ同様、正規化はし… */
    public static VertexConsumer setNormal(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z) {
        // 位置行列が単位なら法線行列も単位。Matrix3f は properties を持たないのでこちら…
        if (isIdentity(pose.pose())) {
            return consumer.setNormal(x, y, z);
        }
        Matrix3f n = pose.normal();
        return consumer.setNormal(
            n.m00() * x + n.m10() * y + n.m20() * z,
            n.m01() * x + n.m11() * y + n.m21() * z,
            n.m02() * x + n.m12() * y + n.m22() * z);
    }
}

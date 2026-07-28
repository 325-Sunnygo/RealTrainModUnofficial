package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 頂点を「確保なし」で VertexConsumer へ書き込むヘルパ。
 *
 * バニラの以下のデフォルト実装は、呼ぶたびに new Vector3f() を確保する (1.21.1 で確認済み):
 * - VertexConsumer.addVertex(Matrix4f, x, y, z)  → Matrix4f.transformPosition(x,y,z,new Vector3f())
 * - VertexConsumer.setNormal(Pose, x, y, z)      → Pose.transformNormal(x,y,z,new Vector3f())
 *
 * レール・設置物・車両スクリプトは 1 フレームに数万〜数十万頂点を流すため、
 * これだけで毎秒 1 億回規模の確保になり、GC が CPU を食い潰して FPS が落ちる
 * (レールを大量に敷くと重くなる主因)。
 *
 * ここの変換式は JOML の transformPosition / transformNormal と完全に同一で、
 * 確保だけを取り除いたもの。描画結果は一切変わらない。
 */
public final class VertexWriter {

    private VertexWriter() {
    }

    /**
     * addVertex(Matrix4f, x, y, z) と同じ結果を確保なしで。
     */
    public static VertexConsumer addVertex(VertexConsumer consumer, Matrix4f m, float x, float y, float z) {
        return consumer.addVertex(
            m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
            m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
            m.m02() * x + m.m12() * y + m.m22() * z + m.m32());
    }

    /**
     * setNormal(Pose, x, y, z) と同じ結果を確保なしで (バニラ同様、正規化はしない)。
     */
    public static VertexConsumer setNormal(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z) {
        Matrix3f n = pose.normal();
        return consumer.setNormal(
            n.m00() * x + n.m10() * y + n.m20() * z,
            n.m01() * x + n.m11() * y + n.m21() * z,
            n.m02() * x + n.m12() * y + n.m22() * z);
    }
}

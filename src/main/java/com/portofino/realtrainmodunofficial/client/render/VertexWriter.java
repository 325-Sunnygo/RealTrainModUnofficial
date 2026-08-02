package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * 頂点を「確保なし」で VertexConsumer へ書き込むヘルパ。
 * バニラの以下のデフォルト実装は、呼ぶたびに new Vector3f を確保する (1.21.1 で確認済み):
 *
 * <p><b>行列が単位行列なら、掛け算そのものを飛ばす。</b>
 * 本家 1.7.10 は頂点をモデルローカルのまま Tessellator へ投げ、位置と角度は
 * {@code glPushMatrix} / {@code glRotatef} で GPU 側の行列スタックに任せていた。
 * つまり<b>頂点あたりの CPU 演算はゼロ</b>。1.21 の {@code addVertex(pose, x, y, z)} は
 * 頂点ごとに 4x4 を掛け、さらに法線でもう 1 回掛けるので、そこが車両描画の CPU コストの主だった。
 *
 * <p>{@link LocalSpaceBatch} が pose を GPU 側 (ModelViewMat) へ移したので、
 * ここへ渡ってくる行列は単位行列になる。JOML は単位行列かどうかを
 * {@code properties()} のビットで持っているので、判定は int 1 個の検査で済む。
 */
public final class VertexWriter {

    private VertexWriter() {
    }

    private static boolean isIdentity(Matrix4f m) {
        return (m.properties() & Matrix4fc.PROPERTY_IDENTITY) != 0;
    }

    /** addVertex(Matrix4f, x, y, z) と同じ結果を確保なしで。単位行列なら掛け算もしない。 */
    public static VertexConsumer addVertex(VertexConsumer consumer, Matrix4f m, float x, float y, float z) {
        if (isIdentity(m)) {
            return consumer.addVertex(x, y, z);
        }
        return consumer.addVertex(
            m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
            m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
            m.m02() * x + m.m12() * y + m.m22() * z + m.m32());
    }

    /** setNormal(Pose, x, y, z) と同じ結果を確保なしで (バニラ同様、正規化はしない)。 */
    public static VertexConsumer setNormal(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z) {
        //位置行列が単位なら法線行列も単位。Matrix3f は properties を持たないのでこちらで見る
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

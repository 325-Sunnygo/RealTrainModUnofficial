package com.portofino.realtrainmodunofficial.client.model;

import org.joml.Vector3f;

/**
 * 面法線の正規化。<b>モデルの大きさに依存しない</b>ことだけを目的にした小物。
 *
 * <h2>なぜ要るか (2026-07-25 に丸一日溶かした所)</h2>
 * 外積 {@code n = e1 × e2} の大きさは三角形の面積に比例する = <b>辺の長さの2乗</b>で効く。
 * RTM のモデルは MQO 座標を 0.01 倍してブロック単位にするので、文字のような細かい三角形は
 * {@code |n|² ≈ 6e-11} にしかならない。ここで {@code |n|² > 1e-8} のような「潰れた面」判定を
 * 入れると、<b>正常な面まで潰れた扱いになって法線が真上 (0,1,0) に差し替わる</b>。
 *
 * <p>実測: D51 (D51-498_1.mqoz) の全 327118 三角形のうち <b>69.7%</b> が {@code |n|² ≤ 1e-8}。
 * これがナンバープレートなど<b>文字のポリゴンだけ陰影が飛ぶ</b>症状の正体だった。
 * 本家 ngtlib は長さ基準で {@code 1e-7} (二乗ではない) なので、この規模では素通りする。
 *
 * <p>正しいのは「大きさで捨てない」こと。向きは大きさに関係なく決まるので、
 * <b>最大成分で割ってから</b>正規化すれば、極小の三角形でも精度を落とさず向きが出る。
 * 本当に面積 0 (3 点が一直線/同一点) の時だけ諦める。
 */
public final class FaceNormals {

    private FaceNormals() {
    }

    /**
     * 外積ベクトルを正規化する。面積 0 の時だけ {@code (0, 1, 0)} を入れる。
     *
     * @return 正規化できたら true、潰れた面なら false
     */
    public static boolean normalize(Vector3f n) {
        float max = Math.max(Math.abs(n.x), Math.max(Math.abs(n.y), Math.abs(n.z)));
        if (!(max > 0.0F) || !Float.isFinite(max)) {
            n.set(0.0F, 1.0F, 0.0F);
            return false;
        }
        //先に最大成分で割って桁を 1 付近へ戻す。こうしないと |n|² が float の下限で 0 に落ちる。
        float x = n.x / max;
        float y = n.y / max;
        float z = n.z / max;
        float length = (float) Math.sqrt((double) x * x + (double) y * y + (double) z * z);
        if (!(length > 0.0F) || !Float.isFinite(length)) {
            n.set(0.0F, 1.0F, 0.0F);
            return false;
        }
        n.set(x / length, y / length, z / length);
        return true;
    }

    /** 3 点から面法線を作る (右手系: {@code (p1-p0) × (p2-p0)})。 */
    public static Vector3f of(float x0, float y0, float z0,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2) {
        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
        float bx = x2 - x0, by = y2 - y0, bz = z2 - z0;
        Vector3f n = new Vector3f(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx);
        normalize(n);
        return n;
    }
}

package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

/**
 * <b>本家 1.7.10 と同じ「頂点はモデルローカルのまま、位置と角度は GPU の行列で」を再現する。</b>
 *
 * <p>1.7.10 の車両描画はこうなっている (jp.ngt.ngtlib.renderer.model.GroupObject)。
 * <pre>
 *   glPushMatrix();
 *   glTranslated(x, y, z); glRotatef(yaw, ...);   ← 位置と角度は GPU 側の行列スタック
 *     tessellator.startDrawing(mode);
 *     ... addVertexWithUV(x, y, z, u, v) ...      ← モデルローカル座標をそのまま投入
 *     tessellator.draw();
 *   glPopMatrix();
 * </pre>
 * <b>頂点あたりの CPU 演算はゼロ。</b> 1 両が数万頂点あっても、CPU がやるのは配列への書き込みだけ。
 *
 * <p>対して 1.21 の素直な書き方はこう。
 * <pre>
 *   consumer.addVertex(poseStack.last().pose(), x, y, z)   ← 頂点ごとに 4x4 を CPU で掛ける
 *           .setNormal(poseStack.last(), nx, ny, nz);      ← 法線でもう 1 回 3x3
 * </pre>
 * しかも {@code addVertex(Matrix4f, ...)} の既定実装は<b>頂点ごとに {@code new Vector3f} を確保</b>する。
 * これが車両描画の CPU コストの正体だった。
 *
 * <p>ここでやるのは、その 3 つを全部消すこと。
 * <ol>
 *   <li>pose を {@code RenderSystem} の ModelView スタックへ積む (= {@code glPushMatrix} 相当)</li>
 *   <li>描画側には<b>単位行列の PoseStack</b> を渡す
 *       → {@link VertexWriter} が単位行列を見て掛け算を飛ばす</li>
 *   <li>専用の {@code BufferSource} へ貯めて、この場で流す (= {@code tessellator.draw()} 相当)</li>
 * </ol>
 *
 * <p><b>焼き込み (VBO キャッシュ) はしない。</b>
 * 焼くと「いつ焼き直すか」の判定が必要になり、そこがバグの温床になった
 * (動かない車両の判定・内容キーの衝突・可動部との陰影の食い違い)。
 * 本家 1.7.10 も車両は焼いておらず、毎フレーム投げ直している。それで軽い。
 *
 * <p><b>平行光は逆回転させる。</b>
 * 頂点法線を車両ローカルのまま出すので、ビュー空間で与えられている平行光の向きを
 * 車両ローカルへ回してから描く。これを忘れると、車両の向きによって陰影が乗らなくなる。
 */
public final class LocalSpaceBatch {

    /**
     * 切り戻し用。false にすると全部が今までどおり CPU 変換＋バニラのバッファへ流れる。
     */
    public static final boolean ENABLED = true;

    /**
     * 貯める先。1 本を使い回す。
     * <p>RenderType が切り替わると前の分が自動で流れる。これは本家が材質ごとに
     * {@code tessellator.draw()} を呼ぶのと同じ挙動なので、そろえてある。
     */
    private static final ByteBufferBuilder BUFFER = new ByteBufferBuilder(1 << 20);
    private static final MultiBufferSource.BufferSource SOURCE = MultiBufferSource.immediate(BUFFER);

    /** 使い回す単位行列の PoseStack。描画側は push/pop するので、抜けたら深さを確かめる。 */
    private static PoseStack identity = new PoseStack();

    /** push が戻っていなかったときの作り直し。汚れた行列を次の車両へ持ち越さない。 */
    private static void resetIdentity() {
        identity = new PoseStack();
    }

    /** 入れ子防止。中でもう一度呼ばれたら、そこは今までどおりの経路にする。 */
    private static boolean inside;

    private LocalSpaceBatch() {
    }

    /** {@link #run} に渡す中身。 */
    public interface Body {
        boolean run(PoseStack pose, MultiBufferSource buffer);
    }

    /**
     * モデルローカル空間で描く。
     *
     * @param poseStack 車両の位置と角度 (カメラ相対)。これは GPU 側へ移す
     * @param fallback  この経路が使えないときに渡すバッファ
     * @param body      実際に頂点を出す処理
     */
    public static boolean run(PoseStack poseStack, MultiBufferSource fallback, Body body) {
        if (!ENABLED || inside || poseStack == null) {
            return body.run(poseStack, fallback);
        }

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        //★ここが glPushMatrix + glMultMatrix。以降 GPU が頂点を変換する
        modelView.pushMatrix();
        modelView.mul(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();

        Vector3f[] savedLights = pushLocalLights(poseStack);
        inside = true;
        try {
            return body.run(identity, SOURCE);
        } finally {
            inside = false;
            try {
                //★ここが tessellator.draw()。実際の描画はこの瞬間
                SOURCE.endBatch();
            } catch (Throwable t) {
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
                    "Local-space batch flush failed", t);
            }
            if (savedLights != null) {
                RenderSystem.setShaderLights(savedLights[0], savedLights[1]);
            }
            //★glPopMatrix
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            if (!identity.clear()) {
                //描画側が push を戻し忘れた場合。次の車両へ持ち越さないよう捨てて作り直す
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
                    "Local-space batch left the pose stack unbalanced");
                resetIdentity();
            }
        }
    }

    /**
     * 平行光を車両ローカルへ回す。戻り値は元の向き (復元用)。取れなければ null。
     *
     * <p>頂点法線をモデルローカルのまま出すので、ビュー空間の光の向きをそのまま使うと
     * 車両の向きによって陰影が消える。以前ドアだけ影がかからなかったのがこれ。
     */
    private static Vector3f[] pushLocalLights(PoseStack poseStack) {
        java.lang.reflect.Field field = lightDirsField();
        if (field == null) {
            return null;
        }
        try {
            if (!(field.get(null) instanceof Vector3f[] dirs)
                || dirs.length < 2 || dirs[0] == null || dirs[1] == null) {
                return null;
            }
            Vector3f orig0 = new Vector3f(dirs[0]);
            Vector3f orig1 = new Vector3f(dirs[1]);
            Matrix3f invRot = new Matrix3f(poseStack.last().normal()).transpose();
            RenderSystem.setShaderLights(
                invRot.transform(new Vector3f(orig0)),
                invRot.transform(new Vector3f(orig1)));
            return new Vector3f[]{orig0, orig1};
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** RenderSystem.shaderLightDirections。取れなければ null (補正なしで描く)。 */
    private static java.lang.reflect.Field shaderLightDirsField;
    private static boolean shaderLightDirsFailed;

    private static java.lang.reflect.Field lightDirsField() {
        if (shaderLightDirsField != null || shaderLightDirsFailed) {
            return shaderLightDirsField;
        }
        try {
            java.lang.reflect.Field f = RenderSystem.class.getDeclaredField("shaderLightDirections");
            f.setAccessible(true);
            shaderLightDirsField = f;
        } catch (Throwable t) {
            shaderLightDirsFailed = true;
        }
        return shaderLightDirsField;
    }
}

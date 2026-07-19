package jp.ngt.ngtlib.renderer;

import java.util.ArrayList;
import java.util.List;

/**
 * スクリプトの GL11 呼び出しを記録し、PoseStack へ再生するためのオペコードバッファ。
 * (1.21 にイミディエイトモード GL が無いための本家 GL 呼び出し互換層)
 */
public final class GLRecorder {

    public enum Op {
        PUSH, POP, TRANSLATE, ROTATE, SCALE, COLOR, BRIGHTNESS, RENDER_PARTS, RENDER_GROUPS,
        /**
         * テクスチャ差し替え (payload: ResourceLocation, null=デフォルトに戻す)
         */
        BIND_TEXTURE,
        /**
         * NGTTessellator の即時描画 (payload: TessDraw)
         */
        DRAW_TESS,
        /**
         * ModelLoader で読んだ PolygonModel のグループ描画 (payload: Object[]{PolygonModel, groupName})
         */
        DRAW_MODEL_GROUP,
        /**
         * バニラ BlockState のゴースト描画 (payload: BlockState、a/b/c=相対座標)。
         * NGTO Builder のミニチュアプレビュー (NGTRenderer.renderNGTObject) 用。
         */
        RENDER_BLOCK
    }

    /**
     * NGTTessellator.draw() の記録データ。verts は {x,y,z,u,v,r,g,b,a} × n。
     */
    public static final class TessDraw {
        public final int mode;
        public final float[] verts;

        public TessDraw(int mode, float[] verts) {
            this.mode = mode;
            this.verts = verts;
        }
    }

    public static final class Cmd {
        public final Op op;
        public final float a, b, c, d;
        public final String name;
        public final Object payload;

        Cmd(Op op, float a, float b, float c, float d, String name) {
            this(op, a, b, c, d, name, null);
        }

        Cmd(Op op, float a, float b, float c, float d, String name, Object payload) {
            this.op = op;
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.name = name;
            this.payload = payload;
        }
    }

    private static final ThreadLocal<GLRecorder> ACTIVE = new ThreadLocal<>();

    private final List<Cmd> cmds = new ArrayList<>();

    public static GLRecorder active() {
        return ACTIVE.get();
    }

    public static void activate(GLRecorder recorder) {
        ACTIVE.set(recorder);
    }

    public static void deactivate() {
        ACTIVE.remove();
    }

    public void clear() {
        this.cmds.clear();
    }

    public List<Cmd> getCommands() {
        return this.cmds;
    }

    public boolean isEmpty() {
        return this.cmds.isEmpty();
    }

    /**
     * 実際にジオメトリを 1 つでも描いたか。
     * <p>
     * 行列操作 (PUSH/TRANSLATE/…) だけの記録は「何も描いていない」。スクリプトが 1 行目で
     * 落ちると glPushMatrix だけが残るが、{@link #isEmpty()} は false になるため、呼び出し側が
     * 「スクリプトが描画を担当した」と誤判定して素のモデル描画をスキップし、車体が透明になる。
     * <p>
     * 逆に、スクリプトが<b>途中まで描いてから</b>落ちた場合は、そこまでの描画は活かしたい
     * (発光パスの途中で落ちる車両が多く、記録ごと捨てるとライトが消える)。
     * そのため「失敗したか」ではなく「何か描いたか」で判定する。
     */
    public boolean hasGeometry() {
        for (Cmd cmd : this.cmds) {
            switch (cmd.op) {
                case RENDER_PARTS, RENDER_GROUPS, DRAW_TESS, DRAW_MODEL_GROUP, RENDER_BLOCK -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    /**
     * NGTTessellator の即時描画 (DRAW_TESS) を 1 つでも含むか。
     * マテリアル別 tessellator オーバーレイ (方向幕/速度計/ATC/モニタ等) を持つ
     * マテリアルを発見するために使う (VehicleScriptRenderers)。
     */
    public boolean hasTess() {
        for (Cmd cmd : this.cmds) {
            if (cmd.op == Op.DRAW_TESS) {
                return true;
            }
        }
        return false;
    }

    public void push() {
        this.cmds.add(new Cmd(Op.PUSH, 0, 0, 0, 0, null));
    }

    public void pop() {
        this.cmds.add(new Cmd(Op.POP, 0, 0, 0, 0, null));
    }

    public void translate(float x, float y, float z) {
        this.cmds.add(new Cmd(Op.TRANSLATE, x, y, z, 0, null));
    }

    public void rotate(float deg, float x, float y, float z) {
        //再生毎の Quaternion 生成を避けるため記録時に確定させる (payload)
        org.joml.Quaternionf quat = null;
        org.joml.Vector3f axis = new org.joml.Vector3f(x, y, z);
        if (axis.lengthSquared() > 1.0e-6F) {
            axis.normalize();
            quat = new org.joml.Quaternionf().rotationAxis(deg * ((float) Math.PI / 180.0F), axis);
        }
        this.cmds.add(new Cmd(Op.ROTATE, deg, x, y, z, null, quat));
    }

    public void scale(float x, float y, float z) {
        this.cmds.add(new Cmd(Op.SCALE, x, y, z, 0, null));
    }

    public void color(float r, float g, float b, float a) {
        this.cmds.add(new Cmd(Op.COLOR, r, g, b, a, null));
    }

    public void brightness(int packedLight) {
        this.cmds.add(new Cmd(Op.BRIGHTNESS, packedLight, 0, 0, 0, null));
    }

    public void renderParts(String objName) {
        //再生毎の Set 生成を避け、renderNamedGroups の IdentityHashMap キャッシュに
        //同一インスタンスでヒットさせるため、正規化 Set を記録時に確定させる (payload)
        java.util.Set<String> names = java.util.Set.of(
                objName.trim().toLowerCase(java.util.Locale.ROOT));
        this.cmds.add(new Cmd(Op.RENDER_PARTS, 0, 0, 0, 0, objName, names));
    }

    /**
     * 正規化済みグループ名 Set を一括描画 (デフォルトレール配置用)。
     */
    public void renderGroups(java.util.Set<String> normalizedNames) {
        this.cmds.add(new Cmd(Op.RENDER_GROUPS, 0, 0, 0, 0, null, normalizedNames));
    }

    /**
     * テクスチャ差し替え (null でデフォルトへ復帰)。
     */
    public void bindTexture(net.minecraft.resources.ResourceLocation texture) {
        this.cmds.add(new Cmd(Op.BIND_TEXTURE, 0, 0, 0, 0, null, texture));
    }

    public void drawTess(TessDraw draw) {
        this.cmds.add(new Cmd(Op.DRAW_TESS, 0, 0, 0, 0, null, draw));
    }

    /** バニラブロックのゴースト描画を記録 (プレビュー用)。 */
    public void renderBlock(Object blockState, float x, float y, float z) {
        this.cmds.add(new Cmd(Op.RENDER_BLOCK, x, y, z, 0, null, blockState));
    }

    /**
     * 別レコーダーの記録を末尾に追記する (GLHelper のディスプレイリスト callList 用)。
     */
    public void appendFrom(GLRecorder other) {
        if (other != null && other != this) {
            this.cmds.addAll(other.cmds);
        }
    }

    public void drawModelGroup(Object model, String groupName) {
        this.cmds.add(new Cmd(Op.DRAW_MODEL_GROUP, 0, 0, 0, 0, groupName, model));
    }
}

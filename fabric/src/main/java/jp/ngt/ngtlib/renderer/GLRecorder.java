package jp.ngt.ngtlib.renderer;

import java.util.ArrayList;
import java.util.List;

/**
 * スクリプトの GL11 呼び出しを記録し、PoseStack へ再生するためのオペコードバッファ。
 * (1.21 にイミディエイトモード GL が無いための本家 GL 呼び出し互換層)
 */
public final class GLRecorder {

    public enum Op {
        PUSH, POP, TRANSLATE, ROTATE, SCALE, MULT_MATRIX, COLOR, BRIGHTNESS, RENDER_PARTS, RENDER_GROUPS,
        /** テクスチャ差し替え (payload: ResourceLocation, null=デフォルトに戻す) */
        BIND_TEXTURE,
        /** NGTTessellator の即時描画 (payload: TessDraw) */
        DRAW_TESS,
        /** ModelLoader で読んだ PolygonModel のグループ描画 (payload: Object[]{PolygonModel, groupName}) */
        DRAW_MODEL_GROUP,
        /**
         * バニラ BlockState のゴースト描画 (payload: BlockState、a/b/c=相対座標)。
         * NGTO Builder のミニチュアプレビュー (NGTRenderer.renderNGTObject) 用。
         */
        RENDER_BLOCK
    }

    /** NGTTessellator.draw の記録データ。verts は {x,y,z,u,v,r,g,b,a} × n。 */
    public static final class TessDraw {
        public final int mode;
        public final float[] verts;

        public TessDraw(int mode, float[] verts) {
            this.mode = mode;
            this.verts = verts;
        }
    }

    /**
     * 1 コマンド。
     * ★フィールドは使い回しのため final ではない。
     */
    public static final class Cmd {
        public Op op;
        public float a, b, c, d;
        public String name;
        public Object payload;

        Cmd(Op op, float a, float b, float c, float d, String name) {
            this(op, a, b, c, d, name, null);
        }

        Cmd(Op op, float a, float b, float c, float d, String name, Object payload) {
            set(op, a, b, c, d, name, payload);
        }

        void set(Op op, float a, float b, float c, float d, String name, Object payload) {
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

    /**
     * 記録と同時に集計しておく値。
     * 以前は contentKey / hasGeometry / hasTess が呼ばれるたびに
     * コマンド列を頭から走査していた。
     */
    private int contentHash = 1;
    private boolean geometry;
    private boolean tess;

    /** 生きているコマンド数 (cmds.size ではない。使い回しのため配列は縮めない)。 */
    private int size;

    /** 別の記録から取り込むとき用。 */
    private void add(Cmd cmd) {
        addCmd(cmd.op, cmd.a, cmd.b, cmd.c, cmd.d, cmd.name, cmd.payload);
    }

    /** payload 無し版。 */
    private void addCmd(Op op, float a, float b, float c, float d, String name) {
        addCmd(op, a, b, c, d, name, null);
    }

    /**
     * コマンドを積む唯一の入口。集計もここでまとめる。
     * 既存インスタンスがあれば上書きして使う (毎フレームの Cmd 生成を無くすため)。
     */
    private void addCmd(Op op, float a, float b, float c, float d, String name, Object payload) {
        Cmd cmd;
        if (this.size < this.cmds.size()) {
            cmd = this.cmds.get(this.size);
            cmd.set(op, a, b, c, d, name, payload);
        } else {
            cmd = new Cmd(op, a, b, c, d, name, payload);
            this.cmds.add(cmd);
        }
        this.size++;
        this.contentHash = 31 * this.contentHash + cmdKey(cmd);
        switch (op) {
            case RENDER_PARTS, RENDER_GROUPS, DRAW_MODEL_GROUP, RENDER_BLOCK -> this.geometry = true;
            case DRAW_TESS -> {
                this.geometry = true;
                this.tess = true;
            }
            default -> {
            }
        }
    }

    public void clear() {
        // ★配列は空にしない。Cmd を使い回すために書き込み位置だけ戻す。
        this.size = 0;
        this.contentHash = 1;
        this.geometry = false;
        this.tess = false;
        this.lightSignature = 0L;
    }

    /** 生きている範囲のビュー。記録が作り直されると中身が変わるので持ち越さないこと。 */
    public List<Cmd> getCommands() {
        return this.size == this.cmds.size() ? this.cmds : this.cmds.subList(0, this.size);
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    /**
     * 実際にジオメトリを 1 つでも描いたか。
     * 行列操作 (PUSH/TRANSLATE/…) だけの記録は「何も描いていない」。
     */
    public boolean hasGeometry() {
        return this.geometry;
    }

    /**
     * NGTTessellator の即時描画 (DRAW_TESS) を 1 つでも含むか。
     * マテリアル別 tessellator オーバーレイ (方向幕/速度計/ATC/モニタ等) を持つ
     * マテリアルを発見するために使う (VehicleScriptRenderers)。
     */
    public boolean hasTess() {
        return this.tess;
    }

    public void push() {
        this.addCmd(Op.PUSH, 0, 0, 0, 0, null);
    }

    public void pop() {
        this.addCmd(Op.POP, 0, 0, 0, 0, null);
    }

    /**
     * NaN/Inf を弾く。スクリプトが未実装フィールドを読むと undefined→NaN が入り、
     * そのまま行列に載るとモデルが丸ごと消える。壊れた値は 0 に潰して描画を守る。
     */
    private static float finite(float v) {
        return Float.isFinite(v) ? v : 0.0F;
    }

    public void translate(float x, float y, float z) {
        this.addCmd(Op.TRANSLATE, finite(x), finite(y), finite(z), 0, null);
    }

    public void rotate(float deg, float x, float y, float z) {
        deg = finite(deg);
        x = finite(x);
        y = finite(y);
        z = finite(z);
        // 再生毎の Quaternion 生成を避けるため記録時に確定させる (payload)
        org.joml.Quaternionf quat = null;
        org.joml.Vector3f axis = new org.joml.Vector3f(x, y, z);
        if (axis.lengthSquared() > 1.0e-6F) {
            axis.normalize();
            quat = new org.joml.Quaternionf().rotationAxis(deg * ((float) Math.PI / 180.0F), axis);
        }
        this.addCmd(Op.ROTATE, deg, x, y, z, null, quat);
    }

    /**
     * glMultMatrix 相当。列優先 16 要素をそのまま payload で運び、再生側で掛ける。
     * NGTO Builder 2 が任意姿勢のプレビューに使う。
     */
    public void multMatrix(float[] matrix) {
        if (matrix == null || matrix.length < 16) {
            return;
        }
        for (float v : matrix) {
            if (!Float.isFinite(v)) {
                return;
            }
        }
        this.addCmd(Op.MULT_MATRIX, 0, 0, 0, 0, null, matrix.clone());
    }

    public void scale(float x, float y, float z) {
        // NaN/Inf に加えて 0 倍も潰す (0 スケールは面が消える)
        float sx = Float.isFinite(x) ? x : 1.0F;
        float sy = Float.isFinite(y) ? y : 1.0F;
        float sz = Float.isFinite(z) ? z : 1.0F;
        this.addCmd(Op.SCALE, sx, sy, sz, 0, null);
    }

    public void color(float r, float g, float b, float a) {
        this.addCmd(Op.COLOR, r, g, b, a, null);
    }

    /**
     * 記録に含まれる明るさの指紋。
     * 明るさは頂点に焼き込まれるので、ワールドの光が変われば記録ごと作り直す必要がある。
     */
    private long lightSignature;

    public long getLightSignature() {
        return this.lightSignature;
    }

    public void brightness(int packedLight) {
        this.lightSignature = this.lightSignature * 31L + packedLight;
        this.addCmd(Op.BRIGHTNESS, packedLight, 0, 0, 0, null);
    }

    public void renderParts(String objName) {
        // 再生毎の Set 生成を避け、renderNamedGroups の IdentityHashMap キャッシュに
        // 同一インスタンスでヒットさせるため、正規化 Set を記録時に確定させる (payload)
        java.util.Set<String> names = java.util.Set.of(
                objName.trim().toLowerCase(java.util.Locale.ROOT));
        this.addCmd(Op.RENDER_PARTS, 0, 0, 0, 0, objName, names);
    }

    /** 正規化済みグループ名 Set を一括描画 (デフォルトレール配置用)。 */
    public void renderGroups(java.util.Set<String> normalizedNames) {
        this.addCmd(Op.RENDER_GROUPS, 0, 0, 0, 0, null, normalizedNames);
    }

    /** テクスチャ差し替え (null でデフォルトへ復帰)。 */
    public void bindTexture(net.minecraft.resources.ResourceLocation texture) {
        this.addCmd(Op.BIND_TEXTURE, 0, 0, 0, 0, null, texture);
    }

    /** bindTexture の元パス付き版。再生側が「素テクスチャへの復帰」を判定できる。 */
    public record TexBind(net.minecraft.resources.ResourceLocation location, String rawPath) {}

    public void bindTexture(net.minecraft.resources.ResourceLocation texture, String rawPath) {
        this.addCmd(Op.BIND_TEXTURE, 0, 0, 0, 0, null, new TexBind(texture, rawPath));
    }

    public void drawTess(TessDraw draw) {
        this.addCmd(Op.DRAW_TESS, 0, 0, 0, 0, null, draw);
    }

    /** バニラブロックのゴースト描画を記録 (プレビュー用)。 */
    public void renderBlock(Object blockState, float x, float y, float z) {
        this.addCmd(Op.RENDER_BLOCK, x, y, z, 0, null, blockState);
    }

    /** 別レコーダーの記録を末尾に追記する (GLHelper のディスプレイリスト callList 用)。 */
    public void appendFrom(GLRecorder other) {
        if (other != null && other != this) {
            for (Cmd cmd : other.getCommands()) {
                add(cmd);
            }
        }
    }

    public void drawModelGroup(Object model, String groupName) {
        this.drawModelGroup(model, groupName, false);
    }

    /**
     * 再生側 (VehicleScriptRenderers) が頂点法線を使うか面法線かの判断に使う。
     * a スロットに 1/0 で載せる。
     * @param smoothing 本家 renderPart(smoothing, ...) の平滑指定。
     */
    public void drawModelGroup(Object model, String groupName, boolean smoothing) {
        this.addCmd(Op.DRAW_MODEL_GROUP, smoothing ? 1.0F : 0.0F, 0, 0, 0, groupName, model);
    }

    /**
     * 記録の内容から作る焼き直し判定キー。
     * にあたるもの。
     * ★payload の Set は中身でハッシュする。
     */
    public int contentKey() {
        return this.contentHash;
    }

    /**
     * 値を撹拌する (雪崩関数)。
     * 焼き込みキーを組み立てるときは、足し合わせる前に必ずこれを通すこと。
     */
    public static int mixKey(int value) {
        int e = value;
        e *= 0x9E3779B1;
        e ^= e >>> 15;
        e *= 0x85EBCA6B;
        e ^= e >>> 13;
        return e;
    }

    /**
     * Set の内容キー。順序非依存 (総和) は保ったまま、要素ごとに撹拌して線形性を壊す。
     * Set.hashCode をそのまま使ってはいけない (#mixKey 参照)。
     */
    public static int setKey(java.util.Set<?> set) {
        if (set == null) {
            return 0;
        }
        int h = 0;
        for (Object element : set) {
            h += mixKey(element == null ? 0 : element.hashCode());
        }
        return 31 * h + set.size();
    }

    /** 1 コマンドぶんの内容ハッシュ。 */
    private static int cmdKey(Cmd cmd) {
        int key = cmd.op.ordinal();
        key = 31 * key + Float.floatToIntBits(cmd.a);
        key = 31 * key + Float.floatToIntBits(cmd.b);
        key = 31 * key + Float.floatToIntBits(cmd.c);
        key = 31 * key + Float.floatToIntBits(cmd.d);
        key = 31 * key + (cmd.name == null ? 0 : cmd.name.hashCode());
        key = 31 * key + payloadKey(cmd.payload);
        return key;
    }

    private static int payloadKey(Object payload) {
        if (payload == null) {
            return 0;
        }
        // 中身で比べるもの
        if (payload instanceof java.util.Set<?> set) {
            // ★Set.hashCode をそのまま使ってはいけない。
            // あれは要素ハッシュの単純な総和なので、差が打ち消し合うと別物が同じ値になる。
            return setKey(set);
        }
        if (payload instanceof float[] fa) {
            return java.util.Arrays.hashCode(fa);
        }
        if (payload instanceof TessDraw td) {
            return 31 * td.mode + java.util.Arrays.hashCode(td.verts);
        }
        if (payload instanceof net.minecraft.resources.ResourceLocation
                || payload instanceof TexBind
                || payload instanceof org.joml.Quaternionf
                || payload instanceof net.minecraft.world.level.block.state.BlockState) {
            return payload.hashCode();
        }
        // モデルグラフ (DRAW_MODEL_GROUP の payload) は読み込み後は不変で、
        // 同じモデルには同じインスタンスが渡る。中身を歩くと重いので同一性で足りる。
        return System.identityHashCode(payload);
    }
}

package jp.ngt.rtm.render;

import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.ngtlib.renderer.GLRecorder;

/**
 * 本家 jp.ngt.rtm.render.WirePartsRenderer の移植。
 *
 * <p>架線・架線柱パック (ModelWire_*.json の rendererPath) はこのクラスを renderClass に指定し、
 * 描画を {@code renderWireStatic} / {@code renderWireDynamic} に書く。本家 RenderElectricalWiring は
 * <b>接続元の取付点 (wirePos) を原点にして</b>この 2 つを順に呼ぶ。
 *
 * <p>移植前は WirePartsRenderer 自体が存在せず、架線系はすべて自前の近似描画で描いていた。
 * そのため Baru's Pole のように<b>ビームを Fix / Loop / Fix に分割して長さに合わせて敷き詰める</b>
 * ような作り込んだパックが、まったく違う見た目 (モデルを等間隔で並べただけ) になっていた。
 */
public class WirePartsRenderer extends TileEntityPartsRenderer {

    /**
     * 定義 (ModelWire_*.json) の値。本家は ModelSetWire/WireConfig から引くが、
     * こちらは呼び出し側 (WireScriptRenderers) が設置物定義から流し込む。
     */
    public float sectionLength = 1.0F;
    public float deflectionCoefficient = 0.0F;
    public float lengthCoefficient = 0.0F;
    public boolean smoothing;

    /**
     * モデルの全グループ名。parts 未指定 (本家 {@code model.renderAll()}) のときに使う。
     */
    public java.util.Set<String> modelGroupNames = java.util.Set.of();

    public WirePartsRenderer(String... par1) {
        super(par1);
    }

    /**
     * 本家 renderWire: static → dynamic の順に呼ぶ。
     */
    public void renderWire(Object tile, Object connection, Object vec, float partialTicks, int pass) {
        this.renderWireStatic(tile, connection, vec, partialTicks, pass);
        this.renderWireDynamic(tile, connection, vec, partialTicks, pass);
    }

    public void renderWireStatic(Object tile, Object connection, Object vec, float partialTicks, int pass) {
        if (this.script != null) {
            ScriptUtil.doScriptIgnoreError(this.script, "renderWireStatic", tile, connection, vec, partialTicks, pass);
        }
    }

    public void renderWireDynamic(Object tile, Object connection, Object vec, float partialTicks, int pass) {
        if (this.script != null) {
            ScriptUtil.doScriptIgnoreError(this.script, "renderWireDynamic", tile, connection, vec, partialTicks, pass);
        }
    }

    /**
     * セクション i を描くか (端の間引き等)。スクリプトが未定義なら常に描く。
     */
    public boolean shouldRenderObject(Object tile, int split, int index, int pass) {
        if (this.script == null) {
            return true;
        }
        Object result = ScriptUtil.doScriptIgnoreError(this.script, "shouldRenderObject", tile, split, index, pass);
        if (result instanceof Boolean b) {
            return b;
        }
        return true;
    }

    /**
     * 本家 renderWireStraight: 取付点から相手までを sectionLength ごとに真っ直ぐ敷き詰める。
     */
    public void renderWireStraight(Object tile, Object connection, Object vecObj, float partialTicks, int pass, Parts parts) {
        GLRecorder rec = GLRecorder.active();
        Vec3 target = toVec3(vecObj);
        if (rec == null || target == null || this.sectionLength <= 0.0F) {
            return;
        }
        double length = target.length();
        int split = (int) Math.floor(length / (double) this.sectionLength);
        if (split < 1) {
            return;
        }
        float scaleY = (float) (length / (double) split / (double) this.sectionLength);

        rec.push();
        rec.rotate(target.getYaw() + 180.0F, 0.0F, 1.0F, 0.0F);
        rec.rotate(target.getPitch() - 90.0F, 1.0F, 0.0F, 0.0F);
        rec.scale(1.0F, scaleY, 1.0F);
        for (int i = 0; i < split; i++) {
            if (this.shouldRenderObject(tile, split, i, pass)) {
                this.renderPartsOrModel(parts);
            }
            rec.translate(0.0F, this.sectionLength, 0.0F);
        }
        rec.pop();
    }

    /**
     * 本家 renderWireDeflection: 電線のたるみ (放物線) に沿って敷き詰める。
     */
    public void renderWireDeflection(Object tile, Object connection, Object vecObj, float partialTicks, int pass, Parts parts) {
        GLRecorder rec = GLRecorder.active();
        Vec3 target = toVec3(vecObj);
        if (rec == null || target == null || this.sectionLength <= 0.0F) {
            return;
        }
        double lx = Math.sqrt(target.getX() * target.getX() + target.getZ() * target.getZ());
        if (lx == 0.0D) {
            this.renderWireStraight(tile, connection, vecObj, partialTicks, pass, parts);
            return;
        }
        float pitch = target.getPitch();
        double ly = target.getY();
        float lc = 1.0F + this.lengthCoefficient;
        double alpha = (double) (this.deflectionCoefficient * NGTMath.cos(pitch)) / Math.pow((double) lc, lx);
        double a = (lx - ly / (alpha * lx)) / 2.0D;

        rec.push();
        rec.rotate(target.getYaw(), 0.0F, 1.0F, 0.0F);
        double x = 0.0D;
        for (int i = 0; x < lx && i < 4096; i++) {
            rec.push();
            double y = alpha * (x * x - 2.0D * a * x);
            double slope = 2.0D * alpha * (x - a);
            float dx = (float) Math.cos(Math.atan(slope)) * this.sectionLength * 0.99F;
            double nextX = x + (double) dx;
            double nextY = alpha * (nextX * nextX - 2.0D * a * nextX);
            double cX = (x + nextX) * 0.5D;
            double cY = (y + nextY) * 0.5D;
            float pitchC = -((float) Math.toDegrees(Math.atan2(nextY - y, nextX - x)));
            rec.translate(0.0F, (float) cY, (float) cX);
            rec.rotate(pitchC + 90.0F, 1.0F, 0.0F, 0.0F);
            rec.translate(0.0F, -this.sectionLength * 0.5F, 0.0F);
            if (this.shouldRenderObject(tile, 0, i, pass)) {
                this.renderPartsOrModel(parts);
            }
            rec.pop();
            if (dx <= 0.0F) {
                break;
            }
            x = nextX;
        }
        rec.pop();
    }

    /**
     * parts 指定ならそのパーツ、無指定なら本家 {@code model.renderAll()} 相当でモデル全体。
     */
    private void renderPartsOrModel(Parts parts) {
        if (parts != null) {
            parts.render(this);
            return;
        }
        GLRecorder rec = GLRecorder.active();
        if (rec != null && !this.modelGroupNames.isEmpty()) {
            rec.renderGroups(this.modelGroupNames);
        }
    }

    private static Vec3 toVec3(Object obj) {
        if (obj instanceof Vec3 v) {
            return v;
        }
        return null;
    }
}

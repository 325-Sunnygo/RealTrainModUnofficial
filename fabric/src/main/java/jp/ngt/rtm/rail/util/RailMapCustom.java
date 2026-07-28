package jp.ngt.rtm.rail.util;

import jp.ngt.ngtlib.io.ScriptUtil;
import net.minecraft.util.Mth;

import javax.script.ScriptEngine;

/**
 * 本家 jp.ngt.rtm.rail.util.RailMapCustom (KaizPatchX) の忠実移植。
 * スクリプト駆動の自由形状レール。
 * TODO(Phase 4): スクリプト取得は本家では ModelPackManager.INSTANCE.getScript(name)。
 * 移植完了までは loadScript フックで直接ソースを渡す。
 */
public final class RailMapCustom extends RailMap {

    /**
     * スクリプトソース解決フック (Phase 4 で ModelPackManager に置換)。
     */
    public interface ScriptResolver {
        String getScript(String scriptName);
    }

    public static ScriptResolver scriptResolver = name -> {
        throw new IllegalStateException("RailMapCustom script resolver not installed (Phase 4): " + name);
    };

    private RailPosition startRP;

    private RailPosition endRP;

    private ScriptEngine script;

    public RailMapCustom(RailPosition rp, String scriptName, String args) {
        this.startRP = rp;
        this.init(scriptName, args);
    }

    private void init(String scriptName, String args) {
        this.script = ScriptUtil.doScript(scriptResolver.getScript(scriptName));
        int split = (int) (getLength() * 4.0D);
        double[] dzx = getRailPos(split, split);
        double dy = getRailHeight(split, split);
        float yaw = getRailYaw(split, split);
        int x = Mth.floor(dzx[1]);
        int y = Mth.floor(dy);
        int z = Mth.floor(dzx[0]);
        int dir = Mth.floor((yaw + 360.0F) % 360.0F / 45.0F);
        this.endRP = new RailPosition(x, y, z, (byte) dir, (byte) 0);
    }

    public static String getDefaultArgs(String scriptName) {
        return getDefaultArgs(ScriptUtil.doScript(scriptResolver.getScript(scriptName)));
    }

    public static String getDefaultArgs(ScriptEngine se) {
        return (String) ScriptUtil.doScriptFunction(se, "getDefaultArgs", new Object[0]);
    }

    @Override
    public RailPosition getStartRP() {
        return this.startRP;
    }

    @Override
    public RailPosition getEndRP() {
        return this.endRP;
    }

    @Override
    public double getLength() {
        return ((Number) ScriptUtil.doScriptFunction(this.script, "getLength", new Object[0])).doubleValue();
    }

    @Override
    public int getNearlestPoint(int split, double x, double z) {
        return ((Number) ScriptUtil.doScriptFunction(this.script, "getNearlestPoint", new Object[]{split, x, z})).intValue();
    }

    @Override
    public double[] getRailPos(int split, int index) {
        return (double[]) ScriptUtil.doScriptFunction(this.script, "getPos", new Object[]{split, index});
    }

    @Override
    public double getRailHeight(int split, int index) {
        return ((Number) ScriptUtil.doScriptFunction(this.script, "getHeight", new Object[]{split, index})).doubleValue();
    }

    @Override
    public float getRailYaw(int split, int index) {
        float yaw = ((Number) ScriptUtil.doScriptFunction(this.script, "getYaw", new Object[]{split, index})).floatValue();
        return yaw + this.startRP.anchorYaw;
    }

    @Override
    public float getRailPitch(int split, int index) {
        return ((Number) ScriptUtil.doScriptFunction(this.script, "getPitch", new Object[]{split, index})).floatValue();
    }

    @Override
    public float getRailRoll(int split, int index) {
        return ((Number) ScriptUtil.doScriptFunction(this.script, "getRoll", new Object[]{split, index})).floatValue();
    }
}

package jp.tokyokinkou.tims;

import jp.ngt.rtm.render.Parts;
import jp.ngt.rtm.render.PartsRenderer;
import com.portofino.realtrainmodunofficial.script.TrainScriptSystem;

/** E233 pack のドアパーツ登録ブリッジ。 */
public final class E233DoorGeometry {
    private E233DoorGeometry() {
    }

    public static Object parts(Object renderer, Object model, String names, boolean smooth) {
        String[] split = names == null || names.isBlank() ? new String[0] : names.split("\\s*,\\s*");
        Parts parts = new Parts(split);
        if (renderer instanceof TrainScriptSystem.ScriptModelRenderer smr) {
            return smr.registerParts(parts);
        }
        if (renderer instanceof PartsRenderer pr) {
            return pr.registerParts(parts);
        }
        return parts;
    }

    public static Split split(Object renderer, Object model, String name, float min, float max, boolean smooth) {
        Object part = parts(renderer, model, name, smooth);
        return new Split(part, part);
    }

    public static boolean isDoorPart(Object part) {
        if (part instanceof TrainScriptSystem.ScriptModelRenderer.ScriptParts sp) {
            for (String name : sp.getGroupNames()) {
                String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
                if (n.contains("door") || n.contains("lamp")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Script の split(...).selected / .rest 用。 */
    public static final class Split {
        public final Object selected;
        public final Object rest;

        Split(Object selected, Object rest) {
            this.selected = selected;
            this.rest = rest;
        }
    }
}

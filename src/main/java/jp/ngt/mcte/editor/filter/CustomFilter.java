package jp.ngt.mcte.editor.filter;

import jp.ngt.mcte.editor.EditorSelection;
import jp.ngt.ngtlib.io.ScriptUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLPaths;

import javax.script.ScriptEngine;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * スクリプトで書いた独自フィルタ (neo mcte)。本家 MCTE EditFilterCustom の移植。
 * <ゲームフォルダ>/mcte/filter/*.js を読み、本家と同じ 3 つの関数を呼ぶ。
 */
public class CustomFilter extends EditFilter {

    private final ScriptEngine engine;
    private final String fileName;
    private String cachedName;

    private CustomFilter(ScriptEngine engine, String fileName) {
        this.engine = engine;
        this.fileName = fileName;
    }

    /** <ゲームフォルダ>/mcte/filter を読み込む。読めないものは飛ばす。 */
    public static List<EditFilter> loadAll() {
        List<EditFilter> out = new ArrayList<>();
        Path dir = FMLPaths.GAMEDIR.get().resolve("mcte").resolve("filter");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            return out;
        }
        File[] files = dir.toFile().listFiles(f -> f.isFile() && f.getName().endsWith(".js"));
        if (files == null) {
            return out;
        }
        for (File f : files) {
            try {
                String src = Files.readString(f.toPath());
                ScriptEngine engine = ScriptUtil.doScript(src);
                if (engine != null) {
                    out.add(new CustomFilter(engine, f.getName()));
                }
            } catch (Exception e) {
                jp.ngt.ngtlib.io.NGTLog.debug("[CustomFilter] 読み込み失敗: " + f.getName() + " / " + e);
            }
        }
        return out;
    }

    @Override
    public String name() {
        if (cachedName == null) {
            try {
                Object n = ScriptUtil.doScriptFunction(engine, "getFilterName", new Object[0]);
                cachedName = n == null ? fileName : String.valueOf(n);
            } catch (Exception e) {
                cachedName = fileName;
            }
        }
        return cachedName;
    }

    @Override
    protected void initConfig(FilterConfig cfg) {
        try {
            ScriptUtil.doScriptIgnoreError(engine, "initFilter", new Object[]{cfg});
        } catch (Exception ignored) {
            // initFilter を持たないスクリプトもある (本家も省略可)
        }
    }

    @Override
    public int apply(ServerLevel level, Player player, EditorSelection editor) {
        ScriptEditor se = new ScriptEditor(level, editor);
        try {
            ScriptUtil.doScriptFunction(engine, "edit", new Object[]{se, this});
        } catch (Exception e) {
            jp.ngt.ngtlib.io.NGTLog.debug("[CustomFilter] " + name() + " が失敗: " + e);
        }
        se.commit();
        return se.getChanged();
    }
}

package jp.ngt.mcte.editor.filter;

import jp.ngt.mcte.editor.EditorSelection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * 編集フィルタ (neo mcte)。本家 MCTE {@code EditFilterBase} の移植。
 *
 * <p>フィルタは「選択範囲に対して何かをする」処理の単位。
 * パラメータは {@link #initConfig} で宣言するだけでよく、入力 UI は自動で組まれる。
 */
public abstract class EditFilter {

    private final FilterConfig config = new FilterConfig();
    private boolean initialized;

    /** 一覧やボタンに出る名前 (翻訳キーの一部にもなる)。 */
    public abstract String name();

    /** パラメータの宣言。無ければ何もしなくてよい。 */
    protected void initConfig(FilterConfig cfg) {
    }

    public final FilterConfig config() {
        if (!initialized) {
            initialized = true;
            initConfig(config);
        }
        return config;
    }

    /**
     * 実行。
     *
     * @return 変更したブロック数。0 なら「何も起きなかった」として扱う
     */
    public abstract int apply(ServerLevel level, Player player, EditorSelection editor);
}

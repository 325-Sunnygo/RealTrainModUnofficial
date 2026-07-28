package jp.ngt.mccompat.tileentity;

/**
 * 1.7.10 net.minecraft.tileentity.CommandBlockLogic のスクリプト互換。
 *
 * <p>スクリプトはコマンド文字列を<b>リフレクションで</b>抜く:
 * <pre>
 *   NGTUtil.getField(CommandBlockLogic.class, logic, ["Command", "field_145763_e"])
 * </pre>
 * MOD 越しに private フィールドを読む前提の書き方なので、こちらも
 * <b>両方の名前のフィールド</b>を実際に持たせて成立させる。
 */
@SuppressWarnings("unused")
public final class CommandBlockLogic {

    /** 1.7.10 の難読化前フィールド名 */
    public final String Command;
    /** 1.7.10 の SRG フィールド名 */
    public final String field_145763_e;

    public CommandBlockLogic(String command) {
        this.Command = command == null ? "" : command;
        this.field_145763_e = this.Command;
    }

    /** func_145753_i = getCommand */
    public String func_145753_i() {
        return this.Command;
    }

    public String getCommand() {
        return this.Command;
    }

    @Override
    public String toString() {
        return this.Command;
    }
}

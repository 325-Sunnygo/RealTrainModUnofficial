package jp.ngt.mccompat.tileentity;

/**
 * 1.7.10 net.minecraft.tileentity.CommandBlockLogic のスクリプト互換。
 * スクリプトはコマンド文字列をリフレクションで抜く:
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

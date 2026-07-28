package jp.ngt.rtm.entity.npc.macro;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本家 jp.ngt.rtm.entity.npc.macro.TrainCommand の移植。
 * マクロの 1 行 = {@code 時刻 コマンド:引数} (# 以降はコメント)。
 * <pre>
 *   0 Notch:5        … 開始 0tick でノッチ+5
 *   200 Notch:-8     … 200tick で非常ブレーキ
 *   300 Door:Door_OpenLeft
 *   400 Horn:
 * </pre>
 */
public class TrainCommand {
    public static final String SEPARATOR = "//";
    private static final String FORMAT = "%s %s:%s";
    private static final Pattern PATTERN = Pattern.compile("([0-9]+)\\s+(.+):(.*)");

    public final long time;
    public final CommandType type;
    public final Object parameter;

    public TrainCommand(long par1, CommandType par2, Object par3) {
        this.time = par1;
        this.type = par2;
        this.parameter = par3;
    }

    public static TrainCommand parse(String par1) {
        String s = par1.split("#")[0]; //コメント除去
        try {
            Matcher matcher = PATTERN.matcher(s);
            if (matcher.find()) {
                long t1 = Long.parseLong(matcher.group(1));
                CommandType type = CommandType.valueOf(matcher.group(2));
                String param = matcher.group(3).replace(" ", ""); //コメントあり時のスペース除去
                return new TrainCommand(t1, type, param);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format(FORMAT, this.time, this.type.toString(), this.parameter.toString());
    }

    public enum CommandType {
        Notch,
        Horn,
        Chime,
        Door
    }
}

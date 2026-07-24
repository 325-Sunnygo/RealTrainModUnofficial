package jp.ngt.rtm.modelpack;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * 本家 jp.ngt.rtm.modelpack.ScriptExecuter 相当。
 *
 * <p>サーバースクリプト (serverScriptPath) は毎 tick
 * {@code onUpdate(entity, scriptExecuter)} として呼ばれ、第 2 引数にこれが渡る。
 * スクリプトはここから<b>バニラのコマンドを実行</b>できる。
 *
 * <p>本家は ICommandSender を実装していたが、1.21 のコマンドは
 * {@link CommandSourceStack} を取るので、実行のたびに組み立てる。
 * 権限レベルは本家と同じ 2 (コマンドブロック相当)、出力は捨てる。
 */
public class ScriptExecuter {

    /** 本家 count: スクリプトから経過 tick として読まれる。 */
    public long count;

    private final ServerLevel level;
    private final Vec3 pos;
    private final String name;

    public ScriptExecuter(ServerLevel level, Vec3 pos, String name) {
        this.level = level;
        this.pos = pos;
        this.name = name;
    }

    /**
     * スクリプトから呼ばれる。バニラコマンドを実行する。
     * 例: {@code scriptExecuter.execCommand("setblock 100 64 100 redstone_block")}
     */
    public void execCommand(String command) {
        if (command == null || command.isBlank() || this.level == null) {
            return;
        }
        MinecraftServer server = this.level.getServer();
        if (server == null) {
            return;
        }
        try {
            CommandSourceStack source = new CommandSourceStack(
                    CommandSource.NULL,          //出力は捨てる (本家も結果を見ない)
                    this.pos,
                    Vec2.ZERO,
                    this.level,
                    2,                           //本家 func_70003_b: permLevel <= 2
                    this.name,
                    Component.literal(this.name),
                    server,
                    null);
            server.getCommands().performPrefixedCommand(source, command);
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("[serverScript] コマンド実行に失敗: {}", command, t);
        }
    }

    /** 本家 func_70005_c_ = getCommandSenderName */
    public String func_70005_c_() {
        return this.name;
    }

    public String getName() {
        return this.name;
    }

    /** 本家 func_130014_f_ = getEntityWorld */
    public jp.ngt.mccompat.WorldCompat func_130014_f_() {
        return new jp.ngt.mccompat.WorldCompat(this.level);
    }

    public jp.ngt.mccompat.WorldCompat getEntityWorld() {
        return this.func_130014_f_();
    }

    public long getCount() {
        return this.count;
    }

    /** 本家 getCommandSenderName。 */
    public String getCommandSenderName() {
        return this.name;
    }

    /** 本家 func_145748_c_: 表示名。 */
    public Component func_145748_c_() {
        return Component.literal(this.name);
    }

    /** 本家 addChatMessage: スクリプト実行者への出力は捨てる。 */
    public void addChatMessage(Object message) {
    }

    /** 本家 canCommandSenderUseCommand: コマンドブロック相当 (レベル 2 まで)。 */
    public boolean canCommandSenderUseCommand(int permLevel, String commandName) {
        return permLevel <= 2;
    }

    /** 本家 getPlayerCoordinates: {チャンクX, Y, チャンクZ}。 */
    public int[] getPlayerCoordinates() {
        if (this.pos == null) {
            return new int[]{0, 0, 0};
        }
        return new int[]{
                net.minecraft.util.Mth.floor(this.pos.x) >> 4,
                net.minecraft.util.Mth.floor(this.pos.y),
                net.minecraft.util.Mth.floor(this.pos.z) >> 4};
    }

    /**
     * 本家 callMethod: 対象のサーバースクリプト側の関数を名前で呼ぶ。
     * 対象が {@code getServerScriptEngine()} を持てばそれを使う。
     */
    public Object callMethod(Object selector, String name, Object... args) {
        if (selector == null || name == null) {
            return null;
        }
        try {
            Object engine = selector.getClass().getMethod("getServerScriptEngine").invoke(selector);
            if (engine instanceof javax.script.ScriptEngine se) {
                return jp.ngt.ngtlib.io.ScriptUtil.doScriptIgnoreError(se, name, args);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            //サーバースクリプトを持たない対象なら何もしない (本家も null を返す)
        }
        return null;
    }

    /** 本家 execScript: 対象の onUpdate を 1 回呼び、count を進める。 */
    public void execScript(Object selector) {
        this.callMethod(selector, "onUpdate", selector, this);
        ++this.count;
    }
}

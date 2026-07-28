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
 * サーバースクリプト (serverScriptPath) は毎 tick
 * onUpdate(entity, scriptExecuter) として呼ばれ、第 2 引数にこれが渡る。
 */
public class ScriptExecuter {

    /** 本家 count: スクリプトから経過 tick として読まれる。 */
    public long count;

    private ServerLevel level;
    private Vec3 pos;
    private final String name;

    /**
     * 本家と同じ引数なし生成 (KaizPatchX ScriptExecuter は new ScriptExecuter)。
     * world / 座標は本家同様 #execScript 時の caller から都度導出する
     * (本家 getEntityWorld / getPlayerCoordinates が caller を見るのと同じ)。
     */
    public ScriptExecuter() {
        this.name = "RTM Script Executer";
    }

    public ScriptExecuter(ServerLevel level, Vec3 pos, String name) {
        this.level = level;
        this.pos = pos;
        this.name = name;
    }

    /** 本家 callMethod/getEntityWorld 相当: caller から world と座標を取り込む。 */
    private void bindCaller(Object caller) {
        if (caller instanceof net.minecraft.world.entity.Entity e) {
            if (e.level() instanceof ServerLevel sl) {
                this.level = sl;
            }
            this.pos = e.position();
        } else if (caller instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            if (be.getLevel() instanceof ServerLevel sl) {
                this.level = sl;
            }
            net.minecraft.core.BlockPos p = be.getBlockPos();
            this.pos = new Vec3(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D);
        }
    }

    /**
     * スクリプトから呼ばれる。バニラコマンドを実行する。
     * 例: scriptExecuter.execCommand("setblock 100 64 100 redstone_block")
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
        return this.level == null ? null : new jp.ngt.mccompat.WorldCompat(this.level);
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
     * 対象が getServerScriptEngine を持てばそれを使う。
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
            // サーバースクリプトを持たない対象なら何もしない (本家も null を返す)
        }
        return null;
    }

    /** 本家 execScript: 対象の onUpdate を 1 回呼び、count を進める。 */
    public void execScript(Object selector) {
        this.bindCaller(selector);
        this.callMethod(selector, "onUpdate", selector, this);
        ++this.count;
    }

    /**
     * 本家 execScript と同じ意味だが、エンジンを呼び出し側が持っている経路用。
     * caller を束縛してから count を進める (呼び出し自体は呼び出し側が行う)。
     */
    public void beginScript(Object caller) {
        this.bindCaller(caller);
    }
}

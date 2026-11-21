package jp.ngt.rtm.modelpack;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 本家 jp.ngt.rtm.modelpack.ScriptExecuter 相当。 */
public class ScriptExecuter {


    /** 本家 count: スクリプトから経過 tick として読まれる。 */
    public long count;

    private ServerLevel level;
    private Vec3 pos;
    private final String name;

    /** 本家と同じ引数なし生成 (KaizPatchX ScriptExecuter は new Scri… */
    public ScriptExecuter() {
        this.name = "RTM Script Executer";
    }

    public ScriptExecuter(ServerLevel level, Vec3 pos, String name) {
        this.level = level;
        this.pos = pos;
        this.name = name;
    }

    /** 本家 callMethod/getEntityWorld 相当: caller から world… */
    private void bindCaller(Object caller) {
        this.level = null;
        this.pos = Vec3.ZERO;
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

    /** スクリプトから呼ばれる。バニラコマンドを実行する。 */
    /** 本家 ScriptExecuter.fireBullet (1.12 系): 火砲スクリプトが弾を… */
    public void fireBullet(Object world, Object entity, String type, double x, double y, double z,
                           double motionX, double motionY, double motionZ) {
        net.minecraft.world.level.Level level = null;
        if (entity instanceof net.minecraft.world.entity.Entity e) {
            level = e.level();
        }
        if (level == null && world instanceof net.minecraft.world.level.Level l) {
            level = l;
        }
        if (level == null || level.isClientSide) {
            return;
        }
        jp.ngt.rtm.item.ItemAmmunition.BulletType bulletType =
            jp.ngt.rtm.item.ItemAmmunition.BulletType.getBulletType(type);
        jp.ngt.rtm.entity.EntityBullet bullet = new jp.ngt.rtm.entity.EntityBullet(level);
        if (bulletType != null) {
            bullet.setBulletType(bulletType);
        }
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            bullet.setOwner(living);
        }
        bullet.setPos(x, y, z);
        bullet.setDeltaMovement(motionX, motionY, motionZ);
        level.addFreshEntity(bullet);
    }

    public void execCommand(String command) {
        if (command == null || command.isBlank() || this.level == null) {
            return;
        }
        MinecraftServer server = this.level.getServer();
        if (server == null || !server.isCommandBlockEnabled()) {
            return;
        }
        try {
            CommandSourceStack source = new CommandSourceStack(
                    CommandSource.NULL,          // 出力は捨てる (本家も結果を見ない)
                    this.pos == null ? Vec3.ZERO : this.pos,
                    Vec2.ZERO,
                    this.level,
                    2,                           // 本家 func_70003_b: permLevel <= 2
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

    /** 本家 canCommandSenderUseCommand: コマンドブロック相当 (レベル 2… */
    public boolean canCommandSenderUseCommand(int permLevel, String commandName) {
        return permLevel <= 2;
    }

    /** 本家 getPlayerCoordinates: {チャンクX, Y, チャンクZ}。 */
    public jp.ngt.mccompat.ChunkCoordinates getPlayerCoordinates() {
        Vec3 position = this.pos == null ? Vec3.ZERO : this.pos;
        return new jp.ngt.mccompat.ChunkCoordinates(
                net.minecraft.util.Mth.floor(position.x) >> 4,
                net.minecraft.util.Mth.floor(position.y),
                net.minecraft.util.Mth.floor(position.z) >> 4);
    }

    public jp.ngt.mccompat.ChunkCoordinates func_71502_e() {
        return this.getPlayerCoordinates();
    }

    public void func_145747_a(Object message) {
        this.addChatMessage(message);
    }

    public boolean func_70003_b(int level, String command) {
        return this.canCommandSenderUseCommand(level, command);
    }

    /** 本家 callMethod: 対象のサーバースクリプト側の関数を名前で呼ぶ。 */
    public Object callMethod(Object selector, String name, Object... args) {
        if (selector == null || name == null) {
            return null;
        }
        // 本家 ScriptExecuter.callMethod と同じ: スクリプトエンジンは必ず ge…
        javax.script.ScriptEngine engine = null;
        if (selector instanceof IModelSelector modelSelector) {
            Object modelSet = modelSelector.getModelSet();
            if (modelSet instanceof jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> set) {
                engine = set.serverSE;
            }
        }
        return jp.ngt.ngtlib.io.ScriptUtil.doScriptIgnoreError(engine, name, args);
    }

    /** Named events bind the caller without advancing th… */
    public Object execScript(Object selector, String eventName, Object... args) {
        this.bindCaller(selector);
        return this.callMethod(selector, eventName, args);
    }

    /** The original advances count even if onUpdate is m… */
    public void execScript(Object selector) {
        try {
            this.execScript(selector, "onUpdate", selector, this);
        } finally {
            ++this.count;
        }
    }

    /** Shared execution path for loaders that already ho… */
    public void updateScript(javax.script.ScriptEngine engine, Object caller) {
        this.bindCaller(caller);
        try {
            jp.ngt.ngtlib.io.ScriptUtil.doScriptIgnoreError(engine, "onUpdate", caller, this);
        } finally {
            ++this.count;
        }
    }

    /** 本家 execScript と同じ意味だが、エンジンを呼び出し側が持っている経路用。 */
    public void beginScript(Object caller) {
        this.bindCaller(caller);
    }
}

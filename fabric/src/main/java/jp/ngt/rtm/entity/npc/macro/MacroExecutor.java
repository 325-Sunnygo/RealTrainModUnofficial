package jp.ngt.rtm.entity.npc.macro;

import com.portofino.realtrainmodunofficial.network.TrainSoundPayload;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import jp.ngt.rtm.entity.npc.macro.TrainCommand.CommandType;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.TrainState;
import jp.ngt.rtm.entity.train.util.TrainState.TrainStateType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 本家 jp.ngt.rtm.entity.npc.macro.MacroExecutor の移植。
 * マクロ (TrainCommand の列) を開始時刻からの相対時刻で順に実行する。
 */
public class MacroExecutor {
    private final List<TrainCommand> commands = new ArrayList<>();
    private boolean executing;
    private long startTime;

    public MacroExecutor(String[] args) {
        Arrays.stream(args).map(TrainCommand::parse).filter(Objects::nonNull).forEach(this.commands::add);
        this.executing = false;
    }

    public boolean start(Level level) {
        if (this.executing) {
            return false;
        }
        this.executing = true;
        //本家は worldTime だが、/time set の影響を受けない gameTime を使う (相対時刻なので挙動は同じ)
        this.startTime = level.getGameTime();
        return true;
    }

    public boolean stop(Level level) {
        if (this.executing) {
            this.executing = false;
            this.startTime = 0L;
            return true;
        }
        return false;
    }

    public boolean finished() {
        return this.commands.isEmpty();
    }

    public void tick(Level level, EntityTrainBase train) {
        if (this.commands.isEmpty()) {
            return;
        }
        TrainCommand command = this.commands.get(0);
        long time = level.getGameTime() - this.startTime;
        if (time >= command.time) {
            this.execCommand(train, command.type, command.parameter);
            this.commands.remove(0);
        }
    }

    private void execCommand(EntityTrainBase train, CommandType type, Object param) {
        try {
            switch (type) {
                case Notch -> this.execNotch(train, Integer.parseInt(param.toString()));
                case Horn -> this.execHorn(train);
                case Chime -> this.execChime(train, param.toString());
                case Door -> this.execDoor(train, TrainState.valueOf(param.toString()));
            }
        } catch (Exception ignored) {
            //不正な行はスキップ (本家は printStackTrace)
        }
    }

    private void execNotch(EntityTrainBase train, int notch) {
        train.addNotch(train.getFirstPassenger(), notch);
    }

    private void execHorn(EntityTrainBase train) {
        VehicleDefinition def = VehicleRegistry.getById(train.getModelName());
        if (def != null && !def.getHornSound().isBlank()) {
            TrainSoundPayload.broadcast(train, def.getHornSound(), 1.0F, 1.0F);
        }
    }

    private void execChime(EntityTrainBase train, String name) {
        if (name != null && name.contains(":")) {
            TrainSoundPayload.broadcast(train, name, 1.0F, 1.0F);
        }
    }

    private void execDoor(EntityTrainBase train, TrainState state) {
        train.setTrainStateData(TrainStateType.State_Door.id, state.data);
    }
}

package jp.ngt.rtm.sound;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** 本家 jp.ngt.rtm.sound.MovingSoundTrain の移植。 */
public class MovingSoundTrain extends MovingSoundVehicle {
    public MovingSoundTrain(Entity train, ResourceLocation sound, boolean repeat, boolean changePitch) {
        this(train, sound, repeat, changePitch, 16.0F);
    }

    public MovingSoundTrain(Entity train, ResourceLocation sound, boolean repeat,
                            boolean changePitch, float range) {
        super(train, sound, repeat, changePitch, range);
    }

    @Override
    public void func_73660_a() {
        super.func_73660_a();
        if (!this.changePitch) {
            return;
        }
        TrainConfig cfg = SoundUpdaterTrain.configOf(this.entity);
        if (cfg == null || cfg.maxSpeed == null || cfg.maxSpeed.length < 2) {
            return;
        }
        float speed = SoundUpdaterTrain.speedOf(this.entity);
        float low = cfg.maxSpeed[0];
        float high = cfg.maxSpeed[cfg.maxSpeed.length - 1];
        if (high > low) {
            this.pitch = (Math.abs(speed) - low) / (high - low) + 1.0F;
        }
    }
}

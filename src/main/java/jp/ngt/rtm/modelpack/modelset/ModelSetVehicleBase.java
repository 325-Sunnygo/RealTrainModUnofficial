package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig;
import net.minecraft.resources.ResourceLocation;

/** 本家 jp.ngt.rtm.modelpack.modelset.ModelSetVehicleB… */
public abstract class ModelSetVehicleBase<T extends VehicleBaseConfig> extends ModelSetBase<T> {
    public final ResourceLocation sound_doorOpen;
    public final ResourceLocation sound_doorClose;

    protected ModelSetVehicleBase() {
        super();
        this.sound_doorOpen = null;
        this.sound_doorClose = null;
    }

    @SuppressWarnings("unchecked")
    protected ModelSetVehicleBase(VehicleBaseConfig config) {
        super((T) config);
        this.sound_doorOpen = this.getSoundResource(config.sound_DoorOpen);
        this.sound_doorClose = this.getSoundResource(config.sound_DoorClose);
    }

    @Override
    public T getConfig() {
        return this.cfg;
    }
}

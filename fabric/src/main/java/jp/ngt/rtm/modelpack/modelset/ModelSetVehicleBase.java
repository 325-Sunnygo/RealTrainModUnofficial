package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig;

/**
 * 本家 KaizPatchX jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBase の移植。
 * 車両系 ModelSet (ModelSetTrain / ModelSetVehicle) の共通親。
 */
public abstract class ModelSetVehicleBase<T extends VehicleBaseConfig> extends ModelSetBase<T> {

    public ModelSetVehicleBase() {
        super();
    }

    public ModelSetVehicleBase(VehicleBaseConfig par1) {
        super((T) par1);
    }

    @Override
    public T getConfig() {
        return this.cfg;
    }
}

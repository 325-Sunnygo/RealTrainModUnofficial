package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.rtm.modelpack.cfg.VehicleConfig;

/** 本家 jp.ngt.rtm.modelpack.modelset.ModelSetVehicle… */
public class ModelSetVehicle extends ModelSetVehicleBase<VehicleConfig> {
    public ModelSetVehicle() {
        super();
    }

    public ModelSetVehicle(VehicleConfig config) {
        super(config);
    }

    @Override
    public VehicleConfig getDummyConfig() {
        return VehicleConfig.getDummy();
    }
}

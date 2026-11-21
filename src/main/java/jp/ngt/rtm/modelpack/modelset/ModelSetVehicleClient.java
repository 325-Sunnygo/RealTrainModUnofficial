package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.rtm.modelpack.cfg.VehicleConfig;

/** 本家 jp.ngt.rtm.modelpack.modelset.ModelSetVehicleC… */
public class ModelSetVehicleClient extends ModelSetVehicleBaseClient<VehicleConfig> {
    public ModelSetVehicleClient() {
        super();
    }

    public ModelSetVehicleClient(VehicleConfig config) {
        super(config);
    }

    @Override
    public VehicleConfig getDummyConfig() {
        return VehicleConfig.getDummy();
    }
}

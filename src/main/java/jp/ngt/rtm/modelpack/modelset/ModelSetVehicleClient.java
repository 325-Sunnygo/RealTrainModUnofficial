package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.rtm.modelpack.cfg.VehicleConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 本家 jp.ngt.rtm.modelpack.modelset.ModelSetVehicleC… */
@OnlyIn(Dist.CLIENT)
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

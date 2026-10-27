package jp.ngt.rtm.modelpack.modelset;
import jp.ngt.rtm.modelpack.cfg.VehicleConfig;
public final class ModelSetVehicle extends ModelSetBase<VehicleConfig> {
    public ModelSetVehicle() {super();}
    public ModelSetVehicle(VehicleConfig config) {super(config);}
    public VehicleConfig getDummyConfig() {return VehicleConfig.getDummy();}
}

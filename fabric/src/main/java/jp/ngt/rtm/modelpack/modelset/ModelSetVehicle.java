package jp.ngt.rtm.modelpack.modelset;
import jp.ngt.rtm.modelpack.cfg.VehicleConfig;

/**
 * 本家 KaizPatchX jp.ngt.rtm.modelpack.modelset.ModelSetVehicle の移植。
 */
public final class ModelSetVehicle extends ModelSetVehicleBase<VehicleConfig> {
    public ModelSetVehicle() {super();}
    public ModelSetVehicle(VehicleConfig config) {super(config);}
    public VehicleConfig getDummyConfig() {return VehicleConfig.getDummy();}
}

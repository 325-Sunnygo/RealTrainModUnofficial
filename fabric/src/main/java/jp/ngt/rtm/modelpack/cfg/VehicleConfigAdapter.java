package jp.ngt.rtm.modelpack.cfg;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
/** Keeps ModelVehicle's two-element ground/air profiles separate from train notches. */
public final class VehicleConfigAdapter {
    private static final java.util.Map<VehicleDefinition,VehicleConfig> CACHE=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private VehicleConfigAdapter() {}
    public static VehicleConfig get(VehicleDefinition definition) {
        return CACHE.computeIfAbsent(definition,def -> {
            var json=def.getLegacyConfig();
            if(json==null) {
                json=new com.google.gson.JsonObject();json.addProperty("name",def.getId());json.addProperty("vehicleType",def.getVehicleType());
                json.addProperty("smoothing",def.isSmoothing());json.addProperty("doCulling",def.isDoCulling());json.addProperty("scale",def.getModelScale());
                var model=new com.google.gson.JsonObject();model.addProperty("modelFile",def.getModelFile());json.add("model",model);
            }
            var config=new com.google.gson.Gson().fromJson(json,VehicleConfig.class);config.init();return config;
        });
    }
    public static void clearCache() {CACHE.clear();}
}

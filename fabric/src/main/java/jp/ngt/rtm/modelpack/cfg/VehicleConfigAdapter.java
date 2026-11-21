package jp.ngt.rtm.modelpack.cfg;

import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * VehicleDefinition → 本家 VehicleConfig アダプタ。
 * ModelPackManager.registerModelset (KaizPatchX 処理系) で登録済みの
 * VehicleConfig を {@link VehicleDefinition#getVehicleConfig()} からそのまま参照する。
 */
public final class VehicleConfigAdapter {
    private static final Map<VehicleDefinition, VehicleConfig> CACHE = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private VehicleConfigAdapter() {
    }

    public static VehicleConfig get(VehicleDefinition definition) {
        VehicleConfig registered = definition != null ? definition.getVehicleConfig() : null;
        if (registered != null) {
            // KaizPatchX 処理系で登録済み → cfg.init() 済みのオブジェクトをそのまま使う
            return registered;
        }
        return CACHE.computeIfAbsent(definition, def -> {
            VehicleConfig config = new VehicleConfig();
            config.setName(def.getId());
            config.vehicleType = def.getVehicleType();
            config.smoothing = def.isSmoothing();
            config.doCulling = def.isDoCulling();
            config.scale = def.getModelScale();
            ModelConfig.ModelSource model = new ModelConfig.ModelSource();
            model.modelFile = def.getModelFile();
            config.model = model;
            config.init();
            return config;
        });
    }

    public static void clearCache() {
        CACHE.clear();
    }
}

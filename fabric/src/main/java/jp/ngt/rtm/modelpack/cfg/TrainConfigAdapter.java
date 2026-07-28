package jp.ngt.rtm.modelpack.cfg;

import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 暫定アダプタ (Phase 2): VehicleDefinition → 本家 TrainConfig。
 * Phase 4 で ModelPackManager による ModelTrain_*.json 直読に置き換えて削除する。
 */
public final class TrainConfigAdapter {
    private static final Map<String, TrainConfig> CACHE = new ConcurrentHashMap<>();
    private static final TrainConfig DEFAULT = create(null);

    private TrainConfigAdapter() {
    }

    public static TrainConfig get(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return DEFAULT;
        }
        return CACHE.computeIfAbsent(modelName, name -> create(VehicleRegistry.getById(name)));
    }

    private static TrainConfig create(VehicleDefinition def) {
        TrainConfig cfg = new TrainConfig();
        if (def != null) {
            cfg.trainName = def.getId();

            List<net.minecraft.world.phys.Vec3> bogies = def.getBogiePositions();
            if (bogies != null && bogies.size() >= 2) {
                cfg.setBogiePos(new float[][]{
                        {(float) bogies.get(0).x, (float) bogies.get(0).y, (float) bogies.get(0).z},
                        {(float) bogies.get(1).x, (float) bogies.get(1).y, (float) bogies.get(1).z}});
            }

            if (def.getTrainDistance() > 0.0F) {
                //VehicleDefinition の trainDistance は 1 両分 (本家と同義)
                cfg.trainDistance = def.getTrainDistance();
            }

            List<Float> speeds = def.getNotchMaxSpeeds();
            if (speeds != null && speeds.size() == 5) {
                float[] ms = new float[speeds.size()];
                for (int i = 0; i < ms.length; ++i) {
                    ms[i] = speeds.get(i);
                }
                cfg.maxSpeed = ms;
            }

            if (def.getAcceleration() > 0.0F) {
                cfg.accelerateion = def.getAcceleration();
            }

            List<String> rollsigns = def.getRollsignNames();
            if (rollsigns != null && !rollsigns.isEmpty()) {
                cfg.rollsignNames = rollsigns.toArray(new String[0]);
            }

            List<float[]> slots = def.getSlotPositions();
            if (slots != null && !slots.isEmpty()) {
                cfg.setSlotPos(slots.toArray(new float[0][]));
            }

            //運転席位置: JSON playerPos をそのまま使用 (本家と同義)
            List<net.minecraft.world.phys.Vec3> players = def.getPlayerPositions();
            if (players != null && !players.isEmpty()) {
                net.minecraft.world.phys.Vec3 p0 = players.get(0);
                net.minecraft.world.phys.Vec3 p1 = players.size() >= 2 ? players.get(1)
                        : new net.minecraft.world.phys.Vec3(-p0.x, p0.y, -p0.z);
                cfg.playerPos = new float[][]{
                        {(float) p0.x, (float) p0.y, (float) p0.z},
                        {(float) p1.x, (float) p1.y, (float) p1.z}};
            }
        }
        cfg.init();
        return cfg;
    }

    public static void clearCache() {
        CACHE.clear();
    }
}

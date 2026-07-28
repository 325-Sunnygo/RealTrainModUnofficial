package jp.kaiz.atsassistmod.controller;

import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationEntry;
import jp.ngt.rtm.entity.train.util.FormationManager;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本家 jp.kaiz.atsassistmod.controller.TrainControllerManager の移植。
 * 編成 ID → 車上装置 (TrainController)。制御車が tick ごとに onUpdate される。
 * (本家のクライアント同期パケットは、RTMU では DataMap 同期が担うため省略)
 */
public final class TrainControllerManager {
    private static final Map<Long, TrainController> trackingTrainMap = new HashMap<>();

    private TrainControllerManager() {
    }

    public static TrainController getTrainController(EntityTrainBase train) {
        if (train == null || train.getFormation() == null) {
            return TrainController.NULL;
        }
        long fid = train.getFormation().id;
        TrainController tc = trackingTrainMap.get(fid);
        if (tc == null) {
            tc = new TrainController(train);
            //再起動後の復元: 列車の DataMap に保存された保安装置を引き継ぐ
            int savedType = train.getResourceState().getDataMap().getInt("ATSAssist_TPType");
            if (savedType != 0) {
                tc.setTrainProtection(TrainProtectionType.getType(savedType));
            }
            trackingTrainMap.put(fid, tc);
        }
        return tc;
    }

    /** サーバー tick (本家 onTick の移植)。 */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (trackingTrainMap.isEmpty()) {
            return;
        }
        List<Long> delList = new ArrayList<>();
        trackingTrainMap.forEach((fid, tcs) -> {
            Formation formation = FormationManager.getInstance().getFormation(fid);
            EntityTrainBase controlCar = null;
            if (formation != null && formation.size() != 0) {
                for (FormationEntry formationEntry : formation.entries) {
                    if (formationEntry == null || formationEntry.train == null) {
                        continue;
                    }
                    if (formationEntry.train.isControlCar()) {
                        controlCar = formationEntry.train;
                        if (controlCar.getId() == tcs.getSavedEntityID()) {
                            try {
                                tcs.onUpdate();
                            } catch (Exception e) {
                                jp.kaiz.atsassistmod.ATSAssistCore.LOGGER.debug("TrainController update failed", e);
                            }
                            break;
                        } else {
                            delList.add(fid);
                        }
                    }
                }
                if (controlCar == null) {
                    delList.add(fid);
                }
            }
        });
        delList.forEach(trackingTrainMap::remove);
    }
}

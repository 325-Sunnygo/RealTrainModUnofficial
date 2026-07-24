package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.entity.TrainSeatEntity;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record TrainControlPayload(int trainEntityId, String action, int value) implements CustomPacketPayload {

    public static final Type<TrainControlPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "train_control")
    );

    public static final StreamCodec<ByteBuf, TrainControlPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT,
        TrainControlPayload::trainEntityId,
        ByteBufCodecs.STRING_UTF8,
        TrainControlPayload::action,
        ByteBufCodecs.INT,
        TrainControlPayload::value,
        TrainControlPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(TrainControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // Phase 2: 本家忠実列車 (jp.ngt) — 乗車中の車両を TrainState 系で直接操作
            if (player.getVehicle() instanceof jp.ngt.rtm.entity.train.EntityTrainBase rtmTrain) {
                handleRtmTrain(rtmTrain, player, payload.action(), payload.value());
                return;
            }
            // slotPos 座席 (EntityFloor): 降車のみ
            if (player.getVehicle() instanceof jp.ngt.rtm.entity.train.parts.EntityFloor) {
                if ("dismount".equals(payload.action())) {
                    player.stopRiding();
                }
                return;
            }
            if (!(player.level().getEntity(payload.trainEntityId()) instanceof TrainEntity train)) {
                return;
            }
            TrainEntity sourceTrain = train;
            if (player.getVehicle() instanceof TrainEntity ridden && ridden.isAlive()) {
                TrainEntity riddenHead = ridden.getFormationHead();
                TrainEntity requestedHead = train.getFormationHead();
                if (ridden == train || riddenHead == requestedHead) {
                    sourceTrain = ridden;
                }
            } else if (player.getVehicle() instanceof TrainSeatEntity seat) {
                TrainEntity seatedTrain = seat.getTrain();
                if (seatedTrain != null && seatedTrain.isAlive()) {
                    TrainEntity riddenHead = seatedTrain.getFormationHead();
                    TrainEntity requestedHead = train.getFormationHead();
                    if (seatedTrain == train || riddenHead == requestedHead) {
                        sourceTrain = seatedTrain;
                    }
                }
            }
            TrainEntity controlTrain = sourceTrain.getFormationHead();
            boolean sameFormationRide =
                (player.getVehicle() instanceof TrainEntity ridden
                    && ridden.isAlive()
                    && ridden.getFormationHead() == controlTrain)
                || (player.getVehicle() instanceof TrainSeatEntity seat
                    && seat.getTrain() != null
                    && seat.getTrain().isAlive()
                    && seat.getTrain().getFormationHead() == controlTrain);
            boolean assignedSeat = controlTrain.formationHasAssignedSeat(player.getUUID());
            boolean driverPassenger = sourceTrain.isDriverPassenger(player) || train.isDriverPassenger(player);
            boolean dismountAction = "dismount".equals(payload.action());
            if (!dismountAction && !sameFormationRide && !assignedSeat && !driverPassenger) {
                return;
            }
            if (!dismountAction && driverPassenger) {
                controlTrain.markDriverControl(player);
                sourceTrain.markDriverControl(player);
            }

            switch (payload.action()) {
                case "mascon_power" -> {
                    if (!driverPassenger) {
                        return;
                    }
                    sourceTrain.ensureDriverReady(player);
                    controlTrain.ensureDriverReady(player);
                    controlTrain.stepMascon(1);
                }
                case "mascon_brake" -> {
                    if (!driverPassenger) {
                        return;
                    }
                    sourceTrain.ensureDriverReady(player);
                    controlTrain.ensureDriverReady(player);
                    controlTrain.stepMascon(-1);
                }
                case "mascon_neutral" -> {
                    if (!driverPassenger) {
                        return;
                    }
                    controlTrain.setNotch(0);
                }
                case "dismount" -> {
                    player.stopRiding();
                    controlTrain.clearSeatAssignment(player.getUUID());
                    sourceTrain.clearSeatAssignment(player.getUUID());
                }
                case "toggle_headlight" -> controlTrain.setHeadlightOn(!controlTrain.isHeadlightOn());
                case "set_light_mode" -> controlTrain.setLightModeForFormation(payload.value());
                case "toggle_interior_light" -> controlTrain.setInteriorLightOnForFormation(!controlTrain.isInteriorLightOn());
                case "toggle_door" -> controlTrain.toggleDoorForFormation();
                case "toggle_door_left" -> controlTrain.toggleDoorSideForFormation(true);
                case "toggle_door_right" -> controlTrain.toggleDoorSideForFormation(false);
                case "toggle_pantograph" -> controlTrain.setPantographUpForFormation(!controlTrain.isPantographUp());
                case "toggle_reverse" -> controlTrain.setReverse(!controlTrain.isReverse());
                case "set_reverser" -> controlTrain.setReverser(payload.value());
                //↑↓ キーのレバーサ操作。↑=前(+1)/↓=後(-1) に 1 段ずつ。中立(0)を挟む 3 段。
                //マスコンが 0 (力行/制動なし) のときだけ動かせる = 走行中の逆転を防ぐ。
                case "reverser_up", "reverser_down" -> {
                    if (!driverPassenger) {
                        return;
                    }
                    if (controlTrain.getNotch() == 0) {
                        int delta = payload.action().equals("reverser_up") ? 1 : -1;
                        int next = Math.max(-1, Math.min(1, controlTrain.getReverser() + delta));
                        if (next != controlTrain.getReverser()) {
                            controlTrain.setReverser(next);
                            TrainSoundPayload.broadcast(controlTrain, "rtm:sounds/train/lever.ogg", 1.0F, 1.0F);
                        }
                    }
                }
                case "next_destination" -> {
                    int count = Math.max(1, controlTrain.getResourceState().getResourceSet().getConfig().rollsignNames.length);
                    controlTrain.setDestinationIndexForFormation((controlTrain.getDestinationIndex() + 1) % count);
                }
                case "prev_destination" -> {
                    int count = Math.max(1, controlTrain.getResourceState().getResourceSet().getConfig().rollsignNames.length);
                    controlTrain.setDestinationIndexForFormation(Math.floorMod(controlTrain.getDestinationIndex() - 1, count));
                }
                case "next_sound" -> controlTrain.setSoundIndex(resolveNextSoundIndex(controlTrain, 1));
                case "prev_sound" -> controlTrain.setSoundIndex(resolveNextSoundIndex(controlTrain, -1));
                case "play_selected_announcement" -> playSelectedAnnouncement(sourceTrain, controlTrain);
                case "play_horn" -> playHorn(sourceTrain, controlTrain);
                case "couple_nearest" -> sourceTrain.coupleNearest();
                case "decouple" -> sourceTrain.decouple();
                case "toggle_custom_button" -> controlTrain.toggleCustomButton(payload.value());
                case "cycle_custom_button" -> {
                    int index = (payload.value() >>> 8) & 0xFF;
                    int currentValue = payload.value() & 0xFF;
                    VehicleDefinition definition = VehicleRegistry.getById(controlTrain.getVehicleId());
                    int nextValue = currentValue == 0 ? 1 : 0;
                    if (definition != null && index >= 0 && index < definition.getCustomButtonOptions().size()) {
                        List<String> options = definition.getCustomButtonOptions().get(index);
                        if (!options.isEmpty()) {
                            nextValue = (currentValue + 1) % options.size();
                        }
                    }
                    controlTrain.setCustomButtonValue(index, nextValue);
                }
                default -> {
                }
            }
        });
    }

    /**
     * jp.ngt.rtm.entity.train.EntityTrainBase 用の操作処理 (Phase 2 先行版)。
     * 本家の TrainState 系 API で処理する。
     */
    private static void handleRtmTrain(jp.ngt.rtm.entity.train.EntityTrainBase train,
                                       ServerPlayer player, String action, int value) {
        var doorType = jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Door;
        var lightType = jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Light;
        var pantoType = jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Pantograph;
        var interiorType = jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_InteriorLight;
        switch (action) {
            case "mascon_power" -> train.addNotch(player, 1);
            case "mascon_brake" -> train.addNotch(player, -1);
            //ノッチ 0 に戻すのもマスコン操作なので、本家 addNotch と同じレバー音を鳴らす
            case "mascon_neutral" -> {
                if (train.setNotch(0)) {
                    TrainSoundPayload.broadcast(train, "rtm:sounds/train/lever.ogg", 1.0F, 1.0F);
                }
            }
            case "dismount" -> player.stopRiding();
            case "toggle_reverse" -> {
                if (train.getNotch() == 0) {
                    train.setTrainDirection(1 - train.getTrainDirection());
                }
            }
            //↑↓ キーのレバーサ操作。<b>物理の前後判定は getTrainState(10)=State_Direction (Front/Back)</b>
            //を読む (updateMotion)。以前は State_TrainDir を変えていたため列車が逆転せず
            //「レバーサが切り替わらない」不具合になっていた。ここは State_Direction を書く。
            //運転台が編成の向きと逆なら Front/Back を入れ替えて、↑ が常に「運転士の前方」になるようにする。
            //マスコン 0 のときだけ動かせる = 走行中の逆転防止。
            case "reverser_up", "reverser_down" -> {
                if (train.getNotch() == 0) {
                    var dirType = jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Direction;
                    boolean flip = (train.getCabDirection() & 1) != (train.getTrainDirection() & 1);
                    byte front = flip
                            ? jp.ngt.rtm.entity.train.util.TrainState.Direction_Back.data
                            : jp.ngt.rtm.entity.train.util.TrainState.Direction_Front.data;
                    byte back = flip
                            ? jp.ngt.rtm.entity.train.util.TrainState.Direction_Front.data
                            : jp.ngt.rtm.entity.train.util.TrainState.Direction_Back.data;
                    byte target = action.equals("reverser_up") ? front : back;
                    if (train.getTrainStateData(dirType.id) != target) {
                        train.setTrainStateData(dirType.id, target);
                        TrainSoundPayload.broadcast(train, "rtm:sounds/train/lever.ogg", 1.0F, 1.0F);
                    }
                }
            }
            case "toggle_door" -> {
                byte data = train.getTrainStateData(doorType.id);
                byte next = (byte) (data == 0 ? 3 : 0);
                train.setTrainStateData(doorType.id, next);
                jp.ngt.rtm.entity.npc.macro.MacroRecorder.recDoor(player,
                        jp.ngt.rtm.entity.train.util.TrainState.getState(doorType.id, next));
            }
            //ドアボタンの左右→ドアビット変換。車両自身のドア byte は「その車両の物理左右」
            //なので、運転士から見た左右は運転台の向き (cabDirection) だけで決まる
            //(編成内でその車両が逆向きに繋がれていても、運転士から見える左右は変わらない)。
            //編成の他車への配布は Formation が操作車両基準で反転する。
            //GUI の DoorButton も同じ cabDirection 基準で開閉表示している。
            case "toggle_door_left" -> {
                byte data = train.getTrainStateData(doorType.id);
                boolean dir = (train.getCabDirection() & 1) == 0;
                byte next = (byte) (data ^ (dir ? 1 : 2));
                train.setTrainStateData(doorType.id, next);
                jp.ngt.rtm.entity.npc.macro.MacroRecorder.recDoor(player,
                        jp.ngt.rtm.entity.train.util.TrainState.getState(doorType.id, next));
            }
            case "toggle_door_right" -> {
                byte data = train.getTrainStateData(doorType.id);
                boolean dir = (train.getCabDirection() & 1) == 0;
                byte next = (byte) (data ^ (dir ? 2 : 1));
                train.setTrainStateData(doorType.id, next);
                jp.ngt.rtm.entity.npc.macro.MacroRecorder.recDoor(player,
                        jp.ngt.rtm.entity.train.util.TrainState.getState(doorType.id, next));
            }
            //運転席GUI (ドアタブ) の速度設定。JSON から読まなくなったぶんをここで決める。
            //value = 最高速度 (km/h)。0 で既定へ戻す。
            case "set_max_speed" -> train.setConfiguredMaxSpeedKmh(value);
            //value = 加速度 (km/h/s ×100)。0 で既定へ戻す。
            case "set_acceleration" -> train.setConfiguredAccelCentiKmhS(value);
            //ドアカット: この運転車両の開扉をカットする (物理でドアを開けない)。
            case "toggle_door_cut" -> {
                var cutType = jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_DoorCut;
                byte data = train.getTrainStateData(cutType.id);
                train.setTrainStateData(cutType.id, (byte) (data == 0 ? 1 : 0));
            }
            //連結解除: value = 解除する台車側 (0/1)。運転台メニューのボタンから。
            case "decouple_rtm" -> {
                jp.ngt.rtm.entity.train.util.Formation formation = train.getFormation();
                if (formation != null) {
                    formation.onDisconnectedTrain(train, value == 0 ? 0 : 1);
                }
            }
            case "toggle_headlight" -> {
                byte data = train.getTrainStateData(lightType.id);
                train.setTrainStateData(lightType.id, (byte) (data == 0 ? 1 : 0));
            }
            case "toggle_chunk_loader" -> {
                var loaderType = jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_ChunkLoader;
                byte data = train.getTrainStateData(loaderType.id);
                train.setTrainStateData(loaderType.id, (byte) (data == 0 ? 1 : 0));
            }
            case "set_light_mode" -> train.setTrainStateData(lightType.id, (byte) value);
            case "toggle_interior_light" -> {
                byte data = train.getTrainStateData(interiorType.id);
                train.setTrainStateData(interiorType.id, (byte) (data == 0 ? 1 : 0));
            }
            case "toggle_pantograph" -> {
                byte data = train.getTrainStateData(pantoType.id);
                train.setTrainStateData(pantoType.id, (byte) (data == 0 ? 1 : 0));
            }
            case "set_direction" -> train.setTrainStateData(
                    jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Direction.id, (byte) value);
            case "set_destination" -> train.setTrainStateData(
                    jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Destination.id, (byte) value);
            //方向幕の送り/戻し。GUI (TrainControlScreen) はこの action を送るが、旧 EntityTrain
            //(=設置される列車) 側ハンドラに case が無く、行先が一切変わらなかった (方向幕が空白のまま)。
            //rollsignNames のコマ数で循環させ、スクリプトが読む State_Destination を更新する。
            case "next_destination", "prev_destination" -> {
                VehicleDefinition destDef = VehicleRegistry.getById(train.getModelName());
                int count = destDef != null ? Math.max(1, destDef.getRollsignNames().size()) : 1;
                var destType = jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Destination;
                int cur = train.getTrainStateData(destType.id);
                int step = "next_destination".equals(action) ? 1 : -1;
                int next = Math.floorMod(cur + step, count);
                train.setTrainStateData(destType.id, (byte) next);
            }
            //RTMU 追加: 種別幕の選択 (方向幕とは独立した State_Type)
            case "set_type" -> train.setTrainStateData(
                    jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Type.id, (byte) value);
            case "set_announcement" -> train.setTrainStateData(
                    jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Announcement.id, (byte) value);
            //車内放送: 選択中のアナウンス (TrainState の Announcement が本家のインデックス) を鳴らす。
            //本家 GuiVehicleControlPanel のボタン 129 と同じ。
            case "play_selected_announcement" -> {
                VehicleDefinition def = VehicleRegistry.getById(train.getModelName());
                List<String> sounds = def != null ? def.getAnnouncementSounds() : List.of();
                if (!sounds.isEmpty()) {
                    int index = Math.floorMod(train.getTrainStateData(
                            jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Announcement.id),
                            sounds.size());
                    TrainSoundPayload.broadcast(train, sounds.get(index), 1.0F, 1.0F);
                }
            }
            case "play_horn" -> {
                VehicleDefinition def = VehicleRegistry.getById(train.getModelName());
                if (def != null && !def.getHornSound().isBlank()) {
                    TrainSoundPayload.broadcast(train, def.getHornSound(), 1.0F, 1.0F);
                    jp.ngt.rtm.entity.npc.macro.MacroRecorder.recHorn(player);
                }
            }
            case "cycle_custom_button" -> {
                int index = (value >>> 8) & 0xFF;
                int currentValue = value & 0xFF;
                VehicleDefinition definition = VehicleRegistry.getById(train.getModelName());
                List<List<String>> options = definition != null ? definition.getCustomButtonOptions() : List.of();
                int next = (index < options.size() && !options.get(index).isEmpty())
                        ? (currentValue + 1) % options.get(index).size()
                        : (currentValue == 0 ? 1 : 0);
                train.getResourceState().getDataMap().setInt("Button" + index, next, 1);
            }
            //スライダー型カスタムボタン: 上位8bit=index, 下位8bit=値 (0-100)
            case "set_custom_button" -> {
                int index = (value >>> 8) & 0xFF;
                int sliderValue = value & 0xFF;
                train.getResourceState().getDataMap().setInt("Button" + index, sliderValue, 1);
            }
            default -> {
            }
        }
    }

    private static int resolveNextSoundIndex(TrainEntity controlTrain, int delta) {
        VehicleDefinition definition = VehicleRegistry.getById(controlTrain.getVehicleId());
        List<String> announcements = definition != null ? definition.getAnnouncementSounds() : List.of();
        if (announcements.isEmpty()) {
            return Math.max(0, controlTrain.getSoundIndex() + delta);
        }
        return Math.floorMod(controlTrain.getSoundIndex() + delta, announcements.size());
    }

    private static void playSelectedAnnouncement(TrainEntity sourceTrain, TrainEntity controlTrain) {
        VehicleDefinition definition = VehicleRegistry.getById(controlTrain.getVehicleId());
        if (definition == null || definition.getAnnouncementSounds().isEmpty()) {
            return;
        }
        int index = Math.floorMod(controlTrain.getSoundIndex(), definition.getAnnouncementSounds().size());
        controlTrain.setSoundIndex(index);
        broadcastTrainSound(sourceTrain, definition.getAnnouncementSounds().get(index), 1.0F, 1.0F);
    }

    private static void playHorn(TrainEntity sourceTrain, TrainEntity controlTrain) {
        VehicleDefinition definition = VehicleRegistry.getById(controlTrain.getVehicleId());
        if (definition == null || definition.getHornSound().isBlank()) {
            return;
        }
        broadcastTrainSound(sourceTrain, definition.getHornSound(), 1.0F, 1.0F);
    }

    private static void broadcastTrainSound(TrainEntity sourceTrain, String soundId, float volume, float pitch) {
        if (sourceTrain == null || soundId == null || soundId.isBlank()) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
            sourceTrain,
            new TrainSoundPayload(sourceTrain.getId(), soundId, volume, pitch)
        );
    }
}

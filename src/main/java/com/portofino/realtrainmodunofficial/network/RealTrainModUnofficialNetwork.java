package com.portofino.realtrainmodunofficial.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RealTrainModUnofficialNetwork {
    private RealTrainModUnofficialNetwork() {
    }

    /** Registers custom payload handlers used by the mod. */
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SelectModelPayload.TYPE, SelectModelPayload.STREAM_CODEC, SelectModelPayload::handleOnServer);
        registrar.playToServer(SetFormationPayload.TYPE, SetFormationPayload.STREAM_CODEC, SetFormationPayload::handleOnServer);
        registrar.playToServer(MiniatureSettingsPayload.TYPE, MiniatureSettingsPayload.STREAM_CODEC, MiniatureSettingsPayload::handleOnServer);
        registrar.playToServer(MiniatureLoadPayload.TYPE, MiniatureLoadPayload.STREAM_CODEC, MiniatureLoadPayload::handleOnServer);
        registrar.playToServer(RunFilterPayload.TYPE, RunFilterPayload.STREAM_CODEC, RunFilterPayload::handleOnServer);
        registrar.playToServer(PainterSettingsPayload.TYPE, PainterSettingsPayload.STREAM_CODEC, PainterSettingsPayload::handleOnServer);
        registrar.playToServer(EditorPointPayload.TYPE, EditorPointPayload.STREAM_CODEC, EditorPointPayload::handleOnServer);
        registrar.playToServer(EditorSlotPayload.TYPE, EditorSlotPayload.STREAM_CODEC, EditorSlotPayload::handleOnServer);
        registrar.playToServer(ChangeEntityModelPayload.TYPE, ChangeEntityModelPayload.STREAM_CODEC, ChangeEntityModelPayload::handleOnServer);
        registrar.playToServer(TrainControlPayload.TYPE, TrainControlPayload.STREAM_CODEC, TrainControlPayload::handleOnServer);
        registrar.playToServer(DataMapClientSyncPayload.TYPE, DataMapClientSyncPayload.STREAM_CODEC, DataMapClientSyncPayload::handleOnServer);
        registrar.playToServer(DecorationRegisterPayload.TYPE, DecorationRegisterPayload.STREAM_CODEC, DecorationRegisterPayload::handleOnServer);
        registrar.playToServer(NpcTradePayload.TYPE, NpcTradePayload.STREAM_CODEC, NpcTradePayload::handleOnServer);
        registrar.playToClient(DecorationSyncPayload.TYPE, DecorationSyncPayload.STREAM_CODEC, DecorationSyncPayload::handleOnClient);
        registrar.playToClient(TrainSoundPayload.TYPE, TrainSoundPayload.STREAM_CODEC, TrainSoundPayload::handleOnClient);
        registrar.playToServer(MountTrainPayload.TYPE, MountTrainPayload.STREAM_CODEC, MountTrainPayload::handleOnServer);
        registrar.playToServer(RailPreviewAdjustPayload.TYPE, RailPreviewAdjustPayload.STREAM_CODEC, RailPreviewAdjustPayload::handleOnServer);
        registrar.playToServer(BindSignalReceiverPayload.TYPE, BindSignalReceiverPayload.STREAM_CODEC, BindSignalReceiverPayload::handleOnServer);
        registrar.playToServer(SetSignalAspectPayload.TYPE, SetSignalAspectPayload.STREAM_CODEC, SetSignalAspectPayload::handleOnServer);
        registrar.playToServer(SetSignalValuePayload.TYPE, SetSignalValuePayload.STREAM_CODEC, SetSignalValuePayload::handleOnServer);
        registrar.playToServer(ConfigureTrainDetectorPayload.TYPE, ConfigureTrainDetectorPayload.STREAM_CODEC, ConfigureTrainDetectorPayload::handleOnServer);
        registrar.playToServer(ConfigureMarkerPayload.TYPE, ConfigureMarkerPayload.STREAM_CODEC, ConfigureMarkerPayload::handleOnServer);
        registrar.playToServer(MarkerAnchorPayload.TYPE, MarkerAnchorPayload.STREAM_CODEC, MarkerAnchorPayload::handleOnServer);
        registrar.playToServer(UpdateScriptBlockPayload.TYPE, UpdateScriptBlockPayload.STREAM_CODEC, UpdateScriptBlockPayload::handleOnServer);
        registrar.playToClient(TrainScriptDataPayload.TYPE, TrainScriptDataPayload.STREAM_CODEC, TrainScriptDataPayload::handleOnClient);
        registrar.playToServer(CarScriptDataPayload.TYPE, CarScriptDataPayload.STREAM_CODEC, CarScriptDataPayload::handleOnServer);
        registrar.playToClient(CarScriptDataSyncPayload.TYPE, CarScriptDataSyncPayload.STREAM_CODEC, CarScriptDataSyncPayload::handleOnClient);
        registrar.playToClient(SpeakerPlayPayload.TYPE, SpeakerPlayPayload.STREAM_CODEC, SpeakerPlayPayload::handleOnClient);
        registrar.playToClient(SpeakerStopPayload.TYPE, SpeakerStopPayload.STREAM_CODEC, SpeakerStopPayload::handleOnClient);
        registrar.playToServer(ConfigureSpeakerPayload.TYPE, ConfigureSpeakerPayload.STREAM_CODEC, ConfigureSpeakerPayload::handleOnServer);
        registrar.playToServer(RtmuSettingsPayload.TYPE, RtmuSettingsPayload.STREAM_CODEC, RtmuSettingsPayload::handleOnServer);
        registrar.playToClient(SyncSpeakerSoundsPayload.TYPE, SyncSpeakerSoundsPayload.STREAM_CODEC, SyncSpeakerSoundsPayload::handleOnClient);
        // 乗客シミュレーション: 駅設定 GUI (右クリックで開く/タグ保存)
        registrar.playToClient(OpenStationScreenPayload.TYPE, OpenStationScreenPayload.STREAM_CODEC, OpenStationScreenPayload::handleOnClient);
        registrar.playToServer(SetStationTagsPayload.TYPE, SetStationTagsPayload.STREAM_CODEC, SetStationTagsPayload::handleOnServer);
        // SignalControllerMod (masa300) 移植
        registrar.playToServer(SignalControllerPayload.TYPE, SignalControllerPayload.STREAM_CODEC, SignalControllerPayload::handleOnServer);
        // 本家 GuiChangeOffset (設置物の微調整)
        registrar.playToServer(ChangeOffsetPayload.TYPE, ChangeOffsetPayload.STREAM_CODEC, ChangeOffsetPayload::handleOnServer);
        // 本家 GuiSignboard (看板の文字)
        registrar.playToServer(SaveSignboardPayload.TYPE, SaveSignboardPayload.STREAM_CODEC, SaveSignboardPayload::handleOnServer);
        // 本家 列車検知器 (出力先の座標と 置く/消す)
        registrar.playToServer(ConfigureDetectorPayload.TYPE, ConfigureDetectorPayload.STREAM_CODEC, ConfigureDetectorPayload::handleOnServer);
        // 本家 GuiTicketVendor (券売機で切符/回数券を買う)
        registrar.playToServer(BuyTicketPayload.TYPE, BuyTicketPayload.STREAM_CODEC, BuyTicketPayload::handleOnServer);
        // 本家 guiIdSelectTileEntityTexture (標識のテクスチャ変更)
        registrar.playToServer(SetObjectModelPayload.TYPE, SetObjectModelPayload.STREAM_CODEC, SetObjectModelPayload::handleOnServer);
        // 本家 運転士 (EntityMotorman) のマクロ設定
        registrar.playToServer(MotormanMacroPayload.TYPE, MotormanMacroPayload.STREAM_CODEC, MotormanMacroPayload::handleOnServer);
        // 運転士のスキン変更
        registrar.playToServer(MotormanSkinPayload.TYPE, MotormanSkinPayload.STREAM_CODEC, MotormanSkinPayload::handleOnServer);
        // 本家系列車 (EntityVehicleBase) の DataMap 同期 (ATSA HUD 等が使用)
        registrar.playToClient(DataMapSyncPayload.TYPE, DataMapSyncPayload.STREAM_CODEC, DataMapSyncPayload::handleOnClient);
        // 背景パネルの設定 (画像・大きさ・高さ)
        registrar.playToServer(SignalConverterPayload.TYPE, SignalConverterPayload.STREAM_CODEC,
            SignalConverterPayload::handleOnServer);
        registrar.playToServer(MovingMachinePayload.TYPE, MovingMachinePayload.STREAM_CODEC,
            MovingMachinePayload::handleOnServer);
        registrar.playToServer(CargoModelPayload.TYPE, CargoModelPayload.STREAM_CODEC,
            CargoModelPayload::handleOnServer);
        registrar.playToServer(StationNamePayload.TYPE, StationNamePayload.STREAM_CODEC,
            StationNamePayload::handleOnServer);
        registrar.playToServer(BackgroundPanelPayload.TYPE, BackgroundPanelPayload.STREAM_CODEC,
            BackgroundPanelPayload::handleOnServer);
    }
}

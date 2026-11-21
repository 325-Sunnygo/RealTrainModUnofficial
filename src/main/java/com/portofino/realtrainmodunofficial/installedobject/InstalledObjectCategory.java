package com.portofino.realtrainmodunofficial.installedobject;

public enum InstalledObjectCategory {
    LIGHT,
    SIGNBOARD,
    INSULATOR,
    OVERHEAD_LINE_POLE,
    WIRE,
    SIGNAL,
    CROSSING,
    TICKET_GATE,
    SPEAKER,
    /** 本家: 列車検知器 (EntityTrainDetector / ModelMachine mac… */
    TRAIN_DETECTOR,
    /** 本家: ATC 地上子 (EntityATC / ModelMachine machineType… */
    ATC,
    /** 本家: 入力コネクタ (レッドストーン→配線網) */
    CONNECTOR_INPUT,
    /** 本家: 出力コネクタ (配線網→レッドストーン) */
    CONNECTOR_OUTPUT,
    /** 本家: ガラスの蛍光灯 (BlockFluorescent / ModelOrnament orn… */
    FLUORESCENT,
    /** 本家: 鉄道標識 (BlockRailroadSign / ResourceType RRS)。 */
    RAILROAD_SIGN,
    /** 本家: 車止め (EntityBumpingPost / machineType="Bumping… */
    BUMPING_POST,
    /** 本家: 転轍機 (BlockPoint / machineType="Point")。 */
    POINT,
    /** 本家: 券売機 (BlockTicketVendor / machineType="Vendor"… */
    TICKET_VENDOR,
    /** 本家: パイプ (BlockPipe / ModelOrnament ornamentType="… */
    PIPE,
    /** 本家: 植物 (ModelOrnament_* / ornamentType="Plant")。 */
    PLANT,
    /** 本家: 階段 (ModelOrnament_* / ornamentType="Stair")。 */
    STAIR,
    /** 本家: 足場 (ModelOrnament_* / ornamentType="Scaffold"… */
    SCAFFOLD,
    /** 本家: 旗 (ModelFlag_* / RTMResource.FLAG)。 */
    FLAG,
    /** 本家: 機構 (ModelMechanism_* / RTMResource.MECHANISM)。 */
    MECHANISM
}

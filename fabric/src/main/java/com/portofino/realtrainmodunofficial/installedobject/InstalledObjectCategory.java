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
    /**
     * 本家: 列車検知器 (EntityTrainDetector / ModelMachine machineType="Antenna_Receive")。
     * レールの上に置き、真下のレールに列車が乗っているかを見る。
     */
    TRAIN_DETECTOR,
    /**
     * 本家: ATC 地上子 (EntityATC / ModelMachine machineType="Antenna_Send")。TRAIN_DETECTOR の送信側。
     * レールの上に置き、真下のレールに signal (ATS 信号レベル) を書き込む。通過した列車が拾って ATS/ATS-P に使う。
     */
    ATC,
    /** 本家: 入力コネクタ (レッドストーン→配線網) */
    CONNECTOR_INPUT,
    /** 本家: 出力コネクタ (配線網→レッドストーン) */
    CONNECTOR_OUTPUT,
    /**
     * 本家: ガラスの蛍光灯 (BlockFluorescent / ModelOrnament ornamentType="Lamp")。
     * 天井/壁/床のどこにでも貼れ、向き(0..7)を持つ。光源レベル 15。
     */
    FLUORESCENT,
    /**
     * 本家: 鉄道標識 (BlockRailroadSign / ResourceType RRS)。
     * 唯一モデルを持たず、textures/rrs/*.png から選んだテクスチャを板に貼って
     * ポールの上に立てるだけ。よってモデル選択でなくテクスチャ選択になる。
     */
    RAILROAD_SIGN,
    /**
     * 本家: 車止め (EntityBumpingPost / machineType="BumpingPost")。
     * レールに吸着して置き、先頭台車が近づくと列車を非常停止させる。
     */
    BUMPING_POST,
    /**
     * 本家: 転轍機 (BlockPoint / machineType="Point")。
     * 右クリックで切り替わるレッドストーン源 (ON=15)。分岐器を動かすのに使う。
     */
    POINT,
    /**
     * 本家: 券売機 (BlockTicketVendor / machineType="Vendor")。
     * 右クリックで切符/回数券を購入できる。
     */
    TICKET_VENDOR,
    /**
     * 本家: パイプ (BlockPipe / ModelOrnament ornamentType="Pipe")。
     * 鉄管の飾り。Pipe01 と Pipe01_Connectable (隣接パイプへ接続) がある。
     */
    PIPE,
    /**
     * 本家: 植物 (ModelOrnament_* / ornamentType="Plant")。
     * 木・草・柵などの飾り。当たり判定を持たないものもある。
     */
    PLANT,
    /**
     * 本家: 階段 (ModelOrnament_* / ornamentType="Stair")。
     */
    STAIR,
    /**
     * 本家: 足場 (ModelOrnament_* / ornamentType="Scaffold")。
     */
    SCAFFOLD,
    /**
     * 本家: 旗 (ModelFlag_* / RTMResource.FLAG)。
     */
    FLAG,
    /**
     * 本家: 機構 (ModelMechanism_* / RTMResource.MECHANISM)。
     * 回転・往復など、スクリプトで動く飾り。
     */
    MECHANISM
}

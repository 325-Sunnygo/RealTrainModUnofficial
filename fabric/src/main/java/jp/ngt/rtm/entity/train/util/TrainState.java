package jp.ngt.rtm.entity.train.util;

import java.util.Arrays;

/**
 * 本家 jp.ngt.rtm.entity.train.util.TrainState の忠実移植。
 */
public enum TrainState {
    Door_Close(TrainStateType.State_Door.id, 0, "close"),
    Door_OpenRight(TrainStateType.State_Door.id, 1, "open_right"),
    Door_OpenLeft(TrainStateType.State_Door.id, 2, "open_left"),
    Door_OpenAll(TrainStateType.State_Door.id, 3, "open_all"),

    Light_Off(TrainStateType.State_Light.id, 0, "off"),
    Light_Head(TrainStateType.State_Light.id, 1, "on_0"),
    Light_Head_Tail(TrainStateType.State_Light.id, 2, "on_1"),

    Pantograph_Down(TrainStateType.State_Pantograph.id, 0, "down"),
    Pantograph_Up(TrainStateType.State_Pantograph.id, 1, "up"),

    Direction_Front(TrainStateType.State_Direction.id, 0, "front"),
    Direction_Center(TrainStateType.State_Direction.id, 1, "center"),
    Direction_Back(TrainStateType.State_Direction.id, 2, "back"),

    InteriorLight_Off(TrainStateType.State_InteriorLight.id, 0, "off"),
    InteriorLight_On(TrainStateType.State_InteriorLight.id, 1, "on_0"),
    InteriorLight_Rainbow(TrainStateType.State_InteriorLight.id, 2, "on_1"),
    ;

    public final int id;
    public final byte data;
    public final String stateName;

    TrainState(int par1, int par2, String par3) {
        this.id = par1;
        this.data = (byte) par2;
        this.stateName = par3;
    }

    public static TrainState getState(int par1Id, byte par2Data) {
        return Arrays.stream(TrainState.values()).filter(state -> state.id == par1Id && state.data == par2Data).findFirst().orElse(Door_Close);
    }

    public static TrainStateType getStateType(int par1Id) {
        return Arrays.stream(TrainStateType.values()).filter(state -> state.id == par1Id).findFirst().orElse(TrainStateType.State_Door);
    }

    public enum TrainStateType {
        State_TrainDir(0, "train_dir", 0, 1),
        State_Notch(1, "notch", -8, 5),
        State_Signal(2, "signal", -1, 127),//6
        /**
         * RTMU 追加: ドアカット。この車両を 1 にすると、開扉指令 (State_Door) が来ても
         * ドアを開けない (物理: doorState を 0 扱い)。id 3 は 16 バイト状態配列の空きスロット。
         */
        State_DoorCut(3, "door_cut", 0, 1),
        State_Door(4, "door", 0, 3),
        State_Light(5, "light", 0, 2),
        State_Pantograph(6, "pantograph", 0, 1),
        State_ChunkLoader(7, "chunk_loader", 0, 8),
        State_Destination(8, "destination", 0, 127),
        State_Announcement(9, "announcement", 0, 127),
        /**
         * 編成内の位置(前,中,後)
         */
        State_Direction(10, "direction", 0, 2),
        State_InteriorLight(11, "interior_light", 0, 2),
        /**
         * RTMU 追加: 種別幕。方向幕 (State_Destination) とは別に「種別」を選ぶための状態。
         * id 12 は 16 バイトの状態配列 (0..15) の空き。0..127 で種別インデックスを持つ。
         */
        State_Type(12, "type", 0, 127);

        public final int id;
        public final String stateName;
        public final byte min;
        public final byte max;

        TrainStateType(int par1, String par2, int par3, int par4) {
            this.id = par1;
            this.stateName = par2;
            this.min = (byte) par3;
            this.max = (byte) par4;
        }

        // ---- 本家スクリプト互換のエイリアス ----
        //
        // 本家のレンダースクリプトは TrainState.TrainStateType.Door のように「本家の定数名」で書く。
        // RTMU は定数名に State_ を付けているため、そのままだとスクリプトからは undefined になり、
        // 呼んだ瞬間に TypeError で落ちる (RTM 標準の Render223.js は 1 行目でこれを踏み、
        // 223 系の車体が丸ごと透明になっていた)。
        //
        // ID は本家と同一なので、本家名の別名を張るだけで互換になる。
        // ★注意: 本家 Direction は id 0 (編成の進行方向)、本家 Role は id 10 (編成内の位置)。
        //   RTMU の State_Direction は id 10 = 本家 Role にあたる。取り違えないこと。
        public static final TrainStateType Direction = State_TrainDir;
        public static final TrainStateType Notch = State_Notch;
        public static final TrainStateType Signal = State_Signal;
        public static final TrainStateType Door = State_Door;
        public static final TrainStateType Light = State_Light;
        public static final TrainStateType Pantograph = State_Pantograph;
        public static final TrainStateType ChunkLoader = State_ChunkLoader;
        public static final TrainStateType Destination = State_Destination;
        public static final TrainStateType Announcement = State_Announcement;
        public static final TrainStateType Role = State_Direction;
        public static final TrainStateType InteriorLight = State_InteriorLight;
    }
}

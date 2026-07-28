package jp.ngt.rtm;

/**
 * 本家 jp.ngt.rtm.RTMCore の段階的移植。
 * VERSION はパックスクリプトが isLegacy 判定 (indexOf("1.7.10")) に使うため
 * "1.7.10" を含む文字列にする (legacy パス = RailProperty API を使わせる)。
 */
@SuppressWarnings("unused")
public final class RTMCore {
    public static final String MODID = "RTM";
    public static final String NAME = "RealTrainMod";
    public static final String VERSION = "1.7.10.41 KaizPatchX/remaster-1.21.1";

    /** 運転操作のキー種別 (スクリプトがキー入力で分岐する)。 */
    public static final byte KEY_Forward = 0;
    public static final byte KEY_Back = 1;
    public static final byte KEY_Horn = 2;
    public static final byte KEY_Chime = 3;
    public static final byte KEY_ControlPanel = 4;
    public static final byte KEY_Fire = 5;
    public static final byte KEY_ATS = 6;
    public static final byte KEY_LEFT = 7;
    public static final byte KEY_RIGHT = 8;
    public static final byte KEY_JUMP = 9;
    public static final byte KEY_SNEAK = 10;
    public static final byte KEY_EB = 11;

    /** 権限チェック用のアクション名。 */
    public static final String EDIT_VEHICLE = "editVehicle";
    public static final String EDIT_RAIL = "editRail";
    public static final String DRIVE_TRAIN = "driveTrain";
    public static final String CHANGE_MODEL = "changeModel";
    public static final String EDIT_ORNAMENT = "editOrnament";

    /** 分割送信のパケット単位 (本家のモデルパック配信で使用)。 */
    public static final int PacketSize = 512;
    public static final int ATOMIC_BOM_META = 2;

    // GUI ID。本家は起動時に連番で振る。参照だけするスクリプトのため同順で固定値を置く
    public static short guiIdSelectEntityModel = 0;
    public static short guiIdSelectTileEntityModel = 1;
    public static short guiIdSelectItemModel = 2;
    public static short guiIdRailItemSettings = 3;
    public static short guiIdFreightCar = 4;
    public static short guiIdItemContainer = 5;
    public static short guiIdSelectTexture = 6;
    public static short guiIdTrainControlPanel = 7;
    public static short guiIdTrainWorkBench = 8;
    public static short guiIdSignalConverter = 9;
    public static short guiIdTicketVendor = 10;
    public static short guiIdStation = 11;
    public static short guiIdPaintTool = 12;
    public static short guiIdMovingMachine = 13;
    public static short guiIdTurnplate = 14;
    public static short guiIdNPC = 15;
    public static short guiIdMotorman = 16;
    public static short guiIdRailMarker = 17;
    public static short guiIdSpeaker = 18;
    public static short guiIdCamera = 19;
    public static short guiIdChangeOffset = 20;

    private RTMCore() {
    }
}

package jp.ngt.rtm;

/**
 * 本家 jp.ngt.rtm.RTMResource のスクリプト互換 (リソース種別の static 定数)。
 * 本家は ResourceType<Config, ModelSet> の static 群で、スクリプトは
 * ModelPackManager.INSTANCE.getResourceSet(RTMResource.CONNECTOR_RELAY, name) 等の
 * 種別引数に使う。
 */
@SuppressWarnings("unused")
public final class RTMResource {
    private RTMResource() {
    }

    public static final Object VEHICLE = "ModelTrain";
    public static final Object TRAIN = "ModelTrain";
    public static final Object RAIL = "ModelRail";
    public static final Object SIGNAL = "ModelSignal";
    public static final Object WIRE = "ModelWire";
    public static final Object ORNAMENT = "ModelOrnament";
    public static final Object MACHINE = "ModelMachine";
    public static final Object CONNECTOR = "ModelConnector";
    public static final Object CONNECTOR_RELAY = "ModelConnector";
    public static final Object SIGNBOARD = "Signboard";
    public static final Object NPC = "ModelNPC";
    public static final Object CONTAINER = "ModelContainer";
    public static final Object FIREARM = "ModelFirearm";
    public static final Object FLAG = "Flag";
    public static final Object RRS = "RRS";
    public static final Object MECHANISM = "ModelMechanism";
}

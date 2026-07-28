package jp.kaiz.atsassistmod.controller.trainprotection;

/**
 * 本家 TrainProtectionType の移植。ATACS は次段階で完全移植予定 (現状は速度照査なしの枠のみ)。
 */
public enum TrainProtectionType {
    NONE("ATSAssistMod.trainprotection.none", 0, TrainProtection.class),
    STATION_PREMISES("ATSAssistMod.trainprotection.station_premises", 1, StationPremisesController.class),
    ATACS("ATSAssistMod.trainprotection.atacs", 10, ATACSController.class),
    ATSPs("ATSAssistMod.trainprotection.atsps", 11, ATSPsController.class),
    RATS("ATSAssistMod.trainprotection.rats", 12, RATSController.class),
    RnATS("ATSAssistMod.trainprotection.rnats", 13, RnATSController.class);

    public final String name;
    public final int id;
    public final Class<? extends TrainProtection> aClass;

    TrainProtectionType(String name, int id, Class<? extends TrainProtection> aClass) {
        this.name = name;
        this.id = id;
        this.aClass = aClass;
    }

    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable(this.name);
    }

    public static TrainProtectionType getType(int par1) {
        for (TrainProtectionType type : TrainProtectionType.values()) {
            if (type.id == par1) {
                return type;
            }
        }
        return NONE;
    }

    public TrainProtection newInstance() {
        try {
            return this.aClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return new TrainProtection();
        }
    }
}

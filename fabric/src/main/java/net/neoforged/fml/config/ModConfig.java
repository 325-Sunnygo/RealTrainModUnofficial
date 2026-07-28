package net.neoforged.fml.config;

/** シム: 種別 enum のみ (RTMU は独自 properties 保存なので実体は不要)。 */
public class ModConfig {
    public enum Type {
        COMMON,
        CLIENT,
        SERVER,
        STARTUP
    }
}

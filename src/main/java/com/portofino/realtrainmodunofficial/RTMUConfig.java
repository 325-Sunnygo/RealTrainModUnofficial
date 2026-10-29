package com.portofino.realtrainmodunofficial;

import jp.ngt.rtm.RTMConfigOptions;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.LinkedHashMap;
import java.util.Map;

/** RTM.cfg is the authoritative file; NeoForge's native screen mirrors its values. */
public final class RTMUConfig {
    private static final Map<RTMConfigOptions.Option, ModConfigSpec.ConfigValue<?>> VALUES = new LinkedHashMap<>();
    public static final ModConfigSpec SPEC;
    static {
        var builder = new ModConfigSpec.Builder();
        Map<RTMConfigOptions.Option,Object> imported = Map.of();
        var configDir = FMLPaths.CONFIGDIR.get();
        try { imported = RTMConfigOptions.readLegacy(configDir.resolve("RTM.cfg")); }
        catch (java.io.IOException e) { throw new IllegalStateException("Could not read RTM.cfg", e); }
        for (String category : RTMConfigOptions.ALL.stream().map(RTMConfigOptions.Option::category).distinct().toList()) {
            builder.push(category);
            for (var option : RTMConfigOptions.ALL) if (option.category().equals(category)) {
                Object value = imported.getOrDefault(option,option.defaultValue());
                // NeoForge comment correction treats dots as paths, even for list-defined keys.
                // RTM.cfg retains the original name; this key is only the native screen mirror.
                String nativeKey = option.key().replace('.', '_');
                builder.comment("RTM setting: " + option.field() + ".");
                ModConfigSpec.ConfigValue<?> property;
                if (value instanceof Boolean b) property = builder.define(java.util.List.of(nativeKey),b);
                else if (option.defaultValue() instanceof Integer) property = builder.defineInRange(java.util.List.of(nativeKey),((Number)value).intValue(),(int)option.minimum(),(int)option.maximum());
                else property = builder.defineInRange(java.util.List.of(nativeKey),((Number)value).doubleValue(),option.minimum(),option.maximum());
                VALUES.put(option,property);
            }
            builder.pop();
        }
        SPEC = builder.build();
    }
    private RTMUConfig() {}
    public static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC || !SPEC.isLoaded()) return;
        if (!(event instanceof ModConfigEvent.Loading) && !(event instanceof ModConfigEvent.Reloading)) return;
        var file = FMLPaths.CONFIGDIR.get().resolve("RTM.cfg");
        try {
            // File edits take priority on every startup, including when the native
            // screen's TOML cache already exists. Reloading comes from that screen.
            boolean readFile = event instanceof ModConfigEvent.Loading && java.nio.file.Files.isRegularFile(file);
            Map<RTMConfigOptions.Option,Object> fileValues = readFile ? RTMConfigOptions.readLegacy(file) : Map.of();
            Map<RTMConfigOptions.Option,Object> current = new LinkedHashMap<>();
            for (var entry : VALUES.entrySet()) {
                var option = entry.getKey();
                Object value = readFile ? fileValues.getOrDefault(option,option.defaultValue()) : entry.getValue().get();
                value = option.validate(value);
                if (readFile) setValue(entry.getValue(),value);
                option.apply(value);
                current.put(option,value);
            }
            RTMConfigOptions.writeLegacy(file,current);
            if (readFile) SPEC.save();
        } catch (java.io.IOException e) { throw new IllegalStateException("Could not load or save RTM.cfg", e); }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setValue(ModConfigSpec.ConfigValue property,Object value) { property.set(value); }
}

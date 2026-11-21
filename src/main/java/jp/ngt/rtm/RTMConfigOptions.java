/* Decompiled with CFR 0.152. */
package jp.ngt.rtm;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.ngt.rtm.RTMConfig;

public final class RTMConfigOptions {
    public static final List<Option> ALL = List.of(new Option("trainSoundVol", "Sound", "sound train", 100, 0.0, 100.0), new Option("crossingGateSoundType", "Sound", "sound crossing gate", 0, 0.0, 1.0), new Option("gunSoundVol", "Sound", "sound gun", 100, 0.0, 100.0), new Option("expandPlayableSoundCount", "Sound", "Expand playable sound count", true, 0.0, 1.0), new Option("trainRunningSoundRange", "Sound", "audible range train running", 48.0, 0.0, 256.0), new Option("trainJointSoundRange", "Sound", "audible range train joint", 48.0, 0.0, 256.0), new Option("trainBrakeReleaseSoundRange", "Sound", "audible range train brake release", 32.0, 0.0, 256.0), new Option("trainHornSoundRange", "Sound", "audible range train horn", 96.0, 0.0, 256.0), new Option("crossingGateSoundRange", "Sound", "audible range crossing gate", 32.0, 0.0, 256.0), new Option("railGeneratingDistance", "Rail", "GeneratingDistance", 64, 0.0, 1024.0), new Option("railGeneratingHeight", "Rail", "GeneratingHeight", 8, 0.0, 256.0), new Option("markerDisplayDistance", "Rail", "MarkerDisplayDistance", 100, 0.0, 32767.0), new Option("gunBreakBlock", "Item", "Gun Break Block", true, 0.0, 1.0), new Option("deleteBat", "Entity", "delete bat", false, 0.0, 1.0), new Option("useServerModelPack", "Model", "use ServerModelPack", false, 0.0, 1.0), new Option("smoothing", "Model", "do smoothing", true, 0.0, 1.0), new Option("versionCheck", "MOD", "version check", true, 0.0, 1.0), new Option("mirrorTextureSize", "Block", "mirror texture size", 512, 256.0, 2048.0), new Option("mirrorRenderingFrequency", "Block", "mirror render frequency", 1, 1.0, 127.0), new Option("use1122Marker", "Marker", "Use like 1.12", false, 0.0, 1.0), new Option("loadSpeed", "Load", "ModelPack load speed", 2, 1.0, 3.0), new Option("fixRTMCachedModelMemoryLimitMiB", "Load", "cached model memory limit MiB", 256, 16.0, 8192.0), new Option("fixRTMCachedModelProtectSeconds", "Load", "cached model protect seconds", 10, 0.0, 600.0), new Option("markerDistanceMoreRealPosition", "Marker", "markerDistancesMoreRealPosition", true, 0.0, 1.0));

    private RTMConfigOptions() {
    }

    public static Map<Option, Object> readLegacy(Path path) throws IOException {
        LinkedHashMap<Option, Object> values = new LinkedHashMap<Option, Object>();
        if (!Files.isRegularFile(path, new LinkOption[0])) {
            return values;
        }
        String category = "";
        block2: for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.endsWith("{")) {
                category = RTMConfigOptions.unquote(line.substring(0, line.length() - 1).trim());
                continue;
            }
            if (line.equals("}")) {
                category = "";
                continue;
            }
            int colon = line.indexOf(58);
            int equals = line.indexOf(61);
            if (colon != 1 || equals < 0) continue;
            String name = RTMConfigOptions.unquote(line.substring(colon + 1, equals).trim());
            for (Option option : ALL) {
                if (!option.category.equalsIgnoreCase(category) || !option.key.equals(name)) continue;
                try {
                    values.put(option, option.validate(line.substring(equals + 1).trim()));
                }
                catch (IllegalArgumentException invalid) {
                    values.put(option, option.defaultValue);
                }
                continue block2;
            }
        }
        return values;
    }

    private static String unquote(String text) {
        return text.startsWith("\"") && text.endsWith("\"") ? text.substring(1, text.length() - 1) : text;
    }

    public static void loadLegacy(Path path) throws IOException {
        Map<Option, Object> values = RTMConfigOptions.readLegacy(path);
        for (Option option : ALL) {
            option.apply(values.getOrDefault(option, option.defaultValue));
        }
        RTMConfigOptions.writeLegacy(path, values);
    }

    public static void writeLegacy(Path path, Map<Option, Object> values) throws IOException {
        List<String> original = Files.isRegularFile(path, new LinkOption[0]) ? Files.readAllLines(path, StandardCharsets.UTF_8) : List.of("# RTM configuration. Restart after changing this file.");
        ArrayList<String> output = new ArrayList<String>();
        HashSet<Option> written = new HashSet<Option>();
        String category = "";
        for (String raw : original) {
            String line = raw.trim();
            if (line.endsWith("{") && !line.startsWith("#")) {
                category = RTMConfigOptions.unquote(line.substring(0, line.length() - 1).trim());
            }
            if (line.equals("}")) {
                RTMConfigOptions.appendMissing(output, category, values, written);
                category = "";
            }
            int colon = line.indexOf(58);
            int equals = line.indexOf(61);
            Option found = null;
            if (colon == 1 && equals > colon) {
                String name = RTMConfigOptions.unquote(line.substring(colon + 1, equals).trim());
                for (Option option : ALL) {
                    if (!option.category.equalsIgnoreCase(category) || !option.key.equals(name)) continue;
                    found = option;
                    break;
                }
            }
            if (found == null) {
                output.add(raw);
                continue;
            }
            output.add(RTMConfigOptions.configLine(found, values.getOrDefault(found, found.defaultValue)));
            written.add(found);
        }
        for (String missingCategory : ALL.stream().map(Option::category).distinct().toList()) {
            boolean missing = ALL.stream().anyMatch(o -> o.category.equals(missingCategory) && !written.contains(o));
            if (!missing) continue;
            output.add("");
            output.add(missingCategory + " {");
            RTMConfigOptions.appendMissing(output, missingCategory, values, written);
            output.add("}");
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
        }
        Files.write(path, output, StandardCharsets.UTF_8, new OpenOption[0]);
    }

    private static void appendMissing(List<String> output, String category, Map<Option, Object> values, Set<Option> written) {
        for (Option option : ALL) {
            if (!option.category.equalsIgnoreCase(category) || !written.add(option)) continue;
            output.add(RTMConfigOptions.configLine(option, values.getOrDefault(option, option.defaultValue)));
        }
    }

    private static String configLine(Option option, Object value) {
        char kind = option.defaultValue instanceof Boolean ? (char)'B' : (option.defaultValue instanceof Integer ? (char)'I' : 'D');
        return "    " + kind + ":\"" + option.key + "\"=" + String.valueOf(option.validate(value));
    }

    public record Option(String field, String category, String key, Object defaultValue, double minimum, double maximum) {
        public Object validate(Object value) {
            double number;
            if (this.defaultValue instanceof Boolean) {
                if (value instanceof Boolean) {
                    return value;
                }
                if ("true".equalsIgnoreCase(String.valueOf(value))) {
                    return true;
                }
                if ("false".equalsIgnoreCase(String.valueOf(value))) {
                    return false;
                }
                throw new IllegalArgumentException("Expected boolean: " + this.category + "." + this.key);
            }
            if (value instanceof Number) {
                Number n = (Number)value;
                number = n.doubleValue();
            } else {
                number = Double.parseDouble(String.valueOf(value));
            }
            if (!Double.isFinite(number) || number < this.minimum || number > this.maximum) {
                throw new IllegalArgumentException("Out of range: " + this.category + "." + this.key);
            }
            if (this.defaultValue instanceof Integer) {
                if (number != Math.rint(number)) {
                    throw new IllegalArgumentException("Expected integer: " + this.key);
                }
                return (int)number;
            }
            return number;
        }

        public void apply(Object value) {
            value = this.validate(value);
            try {
                Field target = RTMConfig.class.getField(this.field);
                if (value instanceof Boolean) {
                    target.setBoolean(null, (Boolean)value);
                } else {
                    Class<?> type;
                    double number = ((Number)value).doubleValue();
                    if (this.field.equals("trainSoundVol") || this.field.equals("gunSoundVol")) {
                        number /= 100.0;
                    }
                    if ((type = target.getType()) == Short.TYPE) {
                        target.setShort(null, (short)number);
                    } else if (type == Byte.TYPE) {
                        target.setByte(null, (byte)number);
                    } else if (type == Integer.TYPE) {
                        target.setInt(null, (int)number);
                    } else {
                        target.setFloat(null, (float)number);
                    }
                }
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Missing RTM setting: " + this.field, e);
            }
        }
    }
}

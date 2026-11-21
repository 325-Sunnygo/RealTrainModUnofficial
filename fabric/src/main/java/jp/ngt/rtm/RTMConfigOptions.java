package jp.ngt.rtm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** The 24 options, defaults and limits from KaizPatchX RTMConfig.syncConfig. */
public final class RTMConfigOptions {
    private RTMConfigOptions() {}
    public record Option(String field, String category, String key, Object defaultValue, double minimum, double maximum) {
        public Object validate(Object value) {
            if (defaultValue instanceof Boolean) {
                if (value instanceof Boolean) return value;
                if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
                if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
                throw new IllegalArgumentException("Expected boolean: " + category + "." + key);
            }
            double number = value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
            if (!Double.isFinite(number) || number < minimum || number > maximum) throw new IllegalArgumentException("Out of range: " + category + "." + key);
            if (defaultValue instanceof Integer) {
                if (number != Math.rint(number)) throw new IllegalArgumentException("Expected integer: " + key);
                return (int) number;
            }
            return number;
        }
        public void apply(Object value) {
            value = validate(value);
            try {
                var target = RTMConfig.class.getField(field);
                if (value instanceof Boolean) target.setBoolean(null, (Boolean) value);
                else {
                    double number = ((Number) value).doubleValue();
                    if (field.equals("trainSoundVol") || field.equals("gunSoundVol")) number /= 100.0;
                    Class<?> type = target.getType();
                    if (type == short.class) target.setShort(null, (short) number);
                    else if (type == byte.class) target.setByte(null, (byte) number);
                    else if (type == int.class) target.setInt(null, (int) number);
                    else target.setFloat(null, (float) number);
                }
            } catch (ReflectiveOperationException e) { throw new IllegalStateException("Missing RTM setting: " + field, e); }
        }
    }
    public static final List<Option> ALL = List.of(
        new Option("trainSoundVol","Sound","sound train",100,0, 100),
        new Option("crossingGateSoundType","Sound","sound crossing gate",0,0, 1),
        new Option("gunSoundVol","Sound","sound gun",100,0, 100),
        new Option("expandPlayableSoundCount","Sound","Expand playable sound count",true,0, 1),
        new Option("trainRunningSoundRange","Sound","audible range train running",48.0,0.0, 256.0),
        new Option("trainJointSoundRange","Sound","audible range train joint",48.0,0.0, 256.0),
        new Option("trainBrakeReleaseSoundRange","Sound","audible range train brake release",32.0,0.0, 256.0),
        new Option("trainHornSoundRange","Sound","audible range train horn",96.0,0.0, 256.0),
        new Option("crossingGateSoundRange","Sound","audible range crossing gate",32.0,0.0, 256.0),
        new Option("railGeneratingDistance","Rail","GeneratingDistance",64,0, 1024),
        new Option("railGeneratingHeight","Rail","GeneratingHeight",8,0, 256),
        new Option("markerDisplayDistance","Rail","MarkerDisplayDistance",100,0, Short.MAX_VALUE),
        new Option("gunBreakBlock","Item","Gun Break Block",true,0, 1),
        new Option("deleteBat","Entity","delete bat",false,0, 1),
        new Option("useServerModelPack","Model","use ServerModelPack",false,0, 1),
        new Option("smoothing","Model","do smoothing",true,0, 1),
        new Option("versionCheck","MOD","version check",true,0, 1),
        new Option("mirrorTextureSize","Block","mirror texture size",512,256, 2048),
        new Option("mirrorRenderingFrequency","Block","mirror render frequency",1,1, Byte.MAX_VALUE),
        new Option("use1122Marker","Marker","Use like 1.12",false,0, 1),
        new Option("loadSpeed","Load","ModelPack load speed",2,1, 3),
        new Option("fixRTMCachedModelMemoryLimitMiB","Load","cached model memory limit MiB",256,16, 8192),
        new Option("fixRTMCachedModelProtectSeconds","Load","cached model protect seconds",10,0, 600),
        new Option("markerDistanceMoreRealPosition","Marker","markerDistancesMoreRealPosition",true,0, 1)
    );
    /** Reads the scalar Forge 1.7.10 .cfg syntax; unknown settings are ignored. */
    public static Map<Option,Object> readLegacy(Path path) throws IOException {
        Map<Option,Object> values = new LinkedHashMap<>();
        if (!Files.isRegularFile(path)) return values;
        String category = "";
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.endsWith("{")) { category = unquote(line.substring(0,line.length()-1).trim()); continue; }
            if (line.equals("}")) { category = ""; continue; }
            int colon = line.indexOf(':'); int equals = line.indexOf('=');
            if (colon != 1 || equals < 0) continue;
            String name = unquote(line.substring(colon+1, equals).trim());
            for (Option option : ALL) if (option.category.equalsIgnoreCase(category) && option.key.equals(name)) {
                try { values.put(option,option.validate(line.substring(equals+1).trim())); }
                catch (IllegalArgumentException invalid) { values.put(option,option.defaultValue); }
                break;
            }
        }
        return values;
    }
    private static String unquote(String text) { return text.startsWith("\"") && text.endsWith("\"") ? text.substring(1,text.length()-1) : text; }
    public static void loadLegacy(Path path) throws IOException {
        var values = readLegacy(path);
        for (Option option : ALL) option.apply(values.getOrDefault(option,option.defaultValue));
        writeLegacy(path, values);
    }
    /** Corrects known scalar values and adds missing entries while preserving foreign options/comments. */
    public static void writeLegacy(Path path, Map<Option,Object> values) throws IOException {
        List<String> original=Files.isRegularFile(path)?Files.readAllLines(path,StandardCharsets.UTF_8):List.of("# RTM configuration. Restart after changing this file.");
        List<String> output=new ArrayList<>();
        Set<Option> written=new HashSet<>();
        String category="";
        for(String raw:original) {
            String line=raw.trim();
            if(line.endsWith("{") && !line.startsWith("#")) category=unquote(line.substring(0,line.length()-1).trim());
            if(line.equals("}")) {
                appendMissing(output,category,values,written);
                category="";
            }
            int colon=line.indexOf(':');int equals=line.indexOf('=');
            Option found=null;
            if(colon==1 && equals>colon) {
                String name=unquote(line.substring(colon+1,equals).trim());
                for(Option option:ALL)if(option.category.equalsIgnoreCase(category) && option.key.equals(name)) {found=option;break;}
            }
            if(found==null)output.add(raw);
            else {output.add(configLine(found,values.getOrDefault(found,found.defaultValue)));written.add(found);}
        }
        for(String missingCategory:ALL.stream().map(Option::category).distinct().toList()) {
            boolean missing=ALL.stream().anyMatch(o->o.category.equals(missingCategory)&&!written.contains(o));
            if(!missing)continue;
            output.add("");output.add(missingCategory+" {");
            appendMissing(output,missingCategory,values,written);output.add("}");
        }
        if(path.getParent()!=null)Files.createDirectories(path.getParent());
        Files.write(path,output,StandardCharsets.UTF_8);
    }
    private static void appendMissing(List<String> output,String category,Map<Option,Object> values,Set<Option> written) {
        for(Option option:ALL)if(option.category.equalsIgnoreCase(category)&&written.add(option))output.add(configLine(option,values.getOrDefault(option,option.defaultValue)));
    }
    private static String configLine(Option option,Object value) {
        char kind=option.defaultValue instanceof Boolean?'B':option.defaultValue instanceof Integer?'I':'D';
        return "    "+kind+":\""+option.key+"\"="+option.validate(value);
    }
}

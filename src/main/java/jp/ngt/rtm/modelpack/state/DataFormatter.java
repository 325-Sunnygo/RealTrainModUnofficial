package jp.ngt.rtm.modelpack.state;

import jp.ngt.rtm.modelpack.cfg.ResourceConfig;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 本家 jp.ngt.rtm.modelpack.state.DataFormatter (Kaiz… */
public final class DataFormatter {
    private final ResourceConfig.DMInitValue[] initValues;
    private final Map<String, String> typeMap = new HashMap<>();

    public DataFormatter(ResourceConfig config) {
        if (config == null || config.defaultValues == null) {
            this.initValues = new ResourceConfig.DMInitValue[0];
        } else {
            this.initValues = config.defaultValues;
            for (ResourceConfig.DMInitValue value : config.defaultValues) {
                if (value != null && value.key != null) {
                    this.typeMap.put(value.key, value.type);
                }
            }
        }
    }

    /** 本家 initDataMap: 未設定キーだけ既定値で埋める。 */
    public void initDataMap(DataMap dataMap) {
        if (dataMap == null) {
            return;
        }
        for (ResourceConfig.DMInitValue value : this.initValues) {
            if (value == null || value.key == null || dataMap.contains(value.key)) {
                continue;
            }
            try {
                this.apply(dataMap, value);
            } catch (RuntimeException ignored) {
                // 本家も不正な既定値はログして続行する。
            }
        }
    }

    private void apply(DataMap dataMap, ResourceConfig.DMInitValue value) {
        String type = value.type == null ? "" : value.type.toLowerCase(Locale.ROOT);
        String raw = value.value;
        switch (type) {
            case "boolean", "b" -> dataMap.setBoolean(value.key, Boolean.parseBoolean(raw), 0);
            case "double", "d" -> dataMap.setDouble(value.key, Double.parseDouble(raw == null ? "0" : raw), 0);
            case "int", "i" -> dataMap.setInt(value.key, Integer.parseInt(raw == null ? "0" : raw), 0);
            case "string", "s" -> dataMap.setString(value.key, raw == null ? "" : raw, 0);
            case "hex", "h" -> dataMap.setHex(value.key, raw == null ? 0 : Integer.decode(raw), 0);
            case "vec", "v" -> {
                String[] parts = raw == null ? new String[0] : raw.split(",");
                double x = parts.length > 0 ? Double.parseDouble(parts[0].trim()) : 0.0D;
                double y = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 0.0D;
                double z = parts.length > 2 ? Double.parseDouble(parts[2].trim()) : 0.0D;
                dataMap.setVec(value.key, new jp.ngt.ngtlib.math.Vec3(x, y, z), 0);
            }
            case "list", "l" -> {
                if (value.values != null) {
                    dataMap.setArray(value.key, value.values.clone(), 0);
                } else if (raw != null && !raw.isEmpty()) {
                    dataMap.setArray(value.key, raw.split(","), 0);
                }
            }
            default -> {
                // 不明な型は本家も無視する。
            }
        }
    }

    public String getType(String key) {
        return this.typeMap.get(key);
    }
}

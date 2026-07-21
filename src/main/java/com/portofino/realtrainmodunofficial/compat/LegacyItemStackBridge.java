package com.portofino.realtrainmodunofficial.compat;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

public final class LegacyItemStackBridge {
    public static final String LEGACY_MODEL_NAME = "ModelName";
    public static final String LEGACY_STATE = "State";
    public static final String LEGACY_DATA_MAP = "DataMap";
    public static final String LEGACY_DATA_LIST = "DataList";
    public static final String LEGACY_DATA_MAP_ARG = "LegacyDataMapArg";
    public static final String LEGACY_STATE_NAME = "Name";
    public static final String LEGACY_STATE_COLOR = "Color";

    private LegacyItemStackBridge() {
    }

    public static String getSelectedModelId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String modern = stack.get(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get());
        if (!isBlank(modern)) {
            return modern;
        }
        CompoundTag tag = getLegacyCustomData(stack);
        return tag.contains(LEGACY_MODEL_NAME, Tag.TAG_STRING) ? tag.getString(LEGACY_MODEL_NAME) : "";
    }

    public static String getSelectedDataMap(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String modern = stack.getOrDefault(RealTrainModUnofficialComponents.SELECTED_MODEL_DATA_MAP.get(), "");
        if (!isBlank(modern)) {
            return modern;
        }
        CompoundTag tag = getLegacyCustomData(stack);
        if (tag.contains(LEGACY_DATA_MAP_ARG, Tag.TAG_STRING)) {
            return tag.getString(LEGACY_DATA_MAP_ARG);
        }
        return extractLegacyDataMapArg(tag);
    }

    public static void setSelectedModelData(ItemStack stack, String modelId, String dataMapValue) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String safeModelId = modelId == null ? "" : modelId;
        String safeDataMap = dataMapValue == null ? "" : dataMapValue;
        stack.set(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get(), safeModelId);
        stack.set(RealTrainModUnofficialComponents.SELECTED_MODEL_DATA_MAP.get(), safeDataMap);

        CompoundTag tag = getLegacyCustomData(stack);
        if (!safeModelId.isBlank()) {
            tag.putString(LEGACY_MODEL_NAME, safeModelId);
        }
        if (!safeDataMap.isBlank()) {
            tag.putString(LEGACY_DATA_MAP_ARG, safeDataMap);
            writeLegacyStateArg(tag, safeDataMap);
        }
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    /**
     * 本家 GuiSelectModel のカスタム名 (State.Name) と色 (State.Color) も併せて確定させる版。
     * dataMap の有無に関わらず State.Name / State.Color を書く。
     */
    public static void setSelectedModelData(ItemStack stack, String modelId, String dataMapValue,
                                            String customName, int color) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        setSelectedModelData(stack, modelId, dataMapValue);
        CompoundTag tag = getLegacyCustomData(stack);
        CompoundTag state = tag.contains(LEGACY_STATE, Tag.TAG_COMPOUND)
            ? tag.getCompound(LEGACY_STATE)
            : new CompoundTag();
        String safeName = (customName == null || customName.isBlank()) ? "no_name" : customName;
        state.putString(LEGACY_STATE_NAME, safeName);
        state.putInt(LEGACY_STATE_COLOR, color);
        tag.put(LEGACY_STATE, state);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    /** State.Name (本家カスタム名)。未設定 / "no_name" は "" を返す。 */
    public static String getSelectedCustomName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CompoundTag tag = getLegacyCustomData(stack);
        if (!tag.contains(LEGACY_STATE, Tag.TAG_COMPOUND)) {
            return "";
        }
        String name = tag.getCompound(LEGACY_STATE).getString(LEGACY_STATE_NAME);
        return (name == null || name.isBlank() || "no_name".equals(name)) ? "" : name;
    }

    /** State.Color (本家色)。未設定は 0xFFFFFF。 */
    public static int getSelectedColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0xFFFFFF;
        }
        CompoundTag tag = getLegacyCustomData(stack);
        if (!tag.contains(LEGACY_STATE, Tag.TAG_COMPOUND)) {
            return 0xFFFFFF;
        }
        CompoundTag state = tag.getCompound(LEGACY_STATE);
        return state.contains(LEGACY_STATE_COLOR, Tag.TAG_ANY_NUMERIC)
            ? state.getInt(LEGACY_STATE_COLOR) : 0xFFFFFF;
    }

    private static CompoundTag getLegacyCustomData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static String extractLegacyDataMapArg(CompoundTag tag) {
        if (!tag.contains(LEGACY_STATE, Tag.TAG_COMPOUND)) {
            return "";
        }
        CompoundTag state = tag.getCompound(LEGACY_STATE);
        if (!state.contains(LEGACY_DATA_MAP, Tag.TAG_COMPOUND)) {
            return "";
        }
        ListTag list = state.getCompound(LEGACY_DATA_MAP).getList(LEGACY_DATA_LIST, Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String name = entry.getString("Name");
            String type = legacyDataTypeKey(entry.getString("Type"));
            String value = readLegacyDataValue(entry);
            if (name.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(name).append("=(").append(type).append(')').append(value);
        }
        return builder.toString();
    }

    private static void writeLegacyStateArg(CompoundTag tag, String dataMapValue) {
        CompoundTag state = tag.contains(LEGACY_STATE, Tag.TAG_COMPOUND)
            ? tag.getCompound(LEGACY_STATE)
            : new CompoundTag();
        if (!state.contains(LEGACY_STATE_NAME, Tag.TAG_STRING)) {
            state.putString(LEGACY_STATE_NAME, "no_name");
        }
        if (!state.contains("Color", Tag.TAG_ANY_NUMERIC)) {
            state.putInt("Color", 0xFFFFFF);
        }
        CompoundTag dataMap = state.contains(LEGACY_DATA_MAP, Tag.TAG_COMPOUND)
            ? state.getCompound(LEGACY_DATA_MAP)
            : new CompoundTag();
        ListTag list = new ListTag();
        for (String part : dataMapValue.split(",")) {
            CompoundTag entry = toLegacyDataEntry(part.trim());
            if (!entry.isEmpty()) {
                list.add(entry);
            }
        }
        dataMap.put(LEGACY_DATA_LIST, list);
        state.put(LEGACY_DATA_MAP, dataMap);
        tag.put(LEGACY_STATE, state);
    }

    private static CompoundTag toLegacyDataEntry(String arg) {
        CompoundTag entry = new CompoundTag();
        int idxEq = arg.indexOf('=');
        int idxOpen = arg.indexOf('(');
        int idxClose = arg.indexOf(')');
        if (idxEq <= 0 || idxOpen != idxEq + 1 || idxClose <= idxOpen + 1) {
            return entry;
        }

        String name = arg.substring(0, idxEq).trim();
        String type = normalizeDataType(arg.substring(idxOpen + 1, idxClose));
        String value = idxClose + 1 < arg.length() ? arg.substring(idxClose + 1).trim() : "";
        if (name.isEmpty()) {
            return entry;
        }

        entry.putString("Name", name);
        entry.putString("Type", legacyDataTypeKey(type));
        entry.putInt("Flag", 3);
        writeLegacyDataValue(entry, type, value);
        return entry;
    }

    private static String readLegacyDataValue(CompoundTag entry) {
        String type = normalizeDataType(entry.getString("Type"));
        return switch (type) {
            case "int", "hex" -> Integer.toString(entry.getInt("Data"));
            case "double" -> Double.toString(entry.getDouble("Data"));
            case "boolean" -> Boolean.toString(entry.getBoolean("Data"));
            default -> entry.getString("Data");
        };
    }

    private static void writeLegacyDataValue(CompoundTag entry, String type, String value) {
        try {
            switch (type) {
                case "int", "hex" -> entry.putInt("Data", value.isBlank() ? 0 : Integer.decode(value));
                case "double" -> entry.putDouble("Data", value.isBlank() ? 0.0D : Double.parseDouble(value));
                case "boolean" -> entry.putBoolean("Data", Boolean.parseBoolean(value));
                default -> entry.putString("Data", value);
            }
        } catch (NumberFormatException ex) {
            entry.putString("Data", value);
        }
    }

    private static String normalizeDataType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "i", "integer" -> "int";
            case "d", "float" -> "double";
            case "b", "bool" -> "boolean";
            case "s" -> "string";
            case "v", "vec3" -> "vec";
            case "h" -> "hex";
            default -> normalized.isBlank() ? "string" : normalized;
        };
    }

    private static String legacyDataTypeKey(String type) {
        return switch (normalizeDataType(type)) {
            case "int" -> "Int";
            case "double" -> "Double";
            case "boolean" -> "Boolean";
            case "vec" -> "Vec";
            case "hex" -> "Hex";
            default -> "String";
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

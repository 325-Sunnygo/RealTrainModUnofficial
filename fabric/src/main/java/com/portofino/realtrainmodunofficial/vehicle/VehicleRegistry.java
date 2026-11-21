package com.portofino.realtrainmodunofficial.vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public final class VehicleRegistry {
    private static final List<VehicleDefinition> DEFINITIONS = new ArrayList<>();
    private static final Map<String, VehicleDefinition> BY_ID = new HashMap<>();
    private static int selectedIndex = 0;

    private VehicleRegistry() {
    }

    public static void setDefinitions(List<VehicleDefinition> defs) {
        DEFINITIONS.clear();
        BY_ID.clear();
        Map<String, VehicleDefinition> unique = new LinkedHashMap<>();
        for (VehicleDefinition d : defs) {
            if (d == null) {
                continue;
            }
            unique.putIfAbsent(dedupeKey(d), d);
        }
        for (VehicleDefinition d : unique.values()) {
            DEFINITIONS.add(d);
            BY_ID.putIfAbsent(d.getId(), d);
        }
        if (selectedIndex >= DEFINITIONS.size() || isHiddenDefault(getSelected())) {
            selectedIndex = firstUsableIndex();
        }
    }

    public static List<VehicleDefinition> getAll() {
        return List.copyOf(DEFINITIONS);
    }

    public static VehicleDefinition getById(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static VehicleDefinition getSelected() {
        if (DEFINITIONS.isEmpty()) return null;
        if (selectedIndex < 0 || selectedIndex >= DEFINITIONS.size()) selectedIndex = firstUsableIndex();
        return DEFINITIONS.get(selectedIndex);
    }

    public static void setSelectedIndex(int i) {
        if (i >= 0 && i < DEFINITIONS.size()) selectedIndex = i;
    }

    private static int firstUsableIndex() {
        for (int i = 0; i < DEFINITIONS.size(); i++) {
            if (!isHiddenDefault(DEFINITIONS.get(i))) {
                return i;
            }
        }
        return 0;
    }

    private static boolean isHiddenDefault(VehicleDefinition definition) {
        if (definition == null) {
            return false;
        }
        String packName = safe(definition.getPackName());
        String id = safe(definition.getId());
        String displayName = safe(definition.getDisplayName());
        return "basic_train".equalsIgnoreCase(packName)
            || id.toLowerCase().contains("basic_train")
            || displayName.toLowerCase().contains("basic_train");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String dedupeKey(VehicleDefinition definition) {
        String display = safe(definition.getDisplayName());
        String button = safe(definition.getButtonTexture());
        String model = safe(definition.getModelFile());
        if (!display.isBlank() && !button.isBlank()) {
            return (display + "|" + button).toLowerCase(Locale.ROOT);
        }
        return (display + "|" + model).toLowerCase(Locale.ROOT);
    }
}

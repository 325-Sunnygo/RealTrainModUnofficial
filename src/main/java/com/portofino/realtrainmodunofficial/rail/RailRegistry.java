package com.portofino.realtrainmodunofficial.rail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RailRegistry {
    private static final List<RailDefinition> DEFINITIONS = new ArrayList<>();
    private static final Map<String, RailDefinition> BY_ID = new HashMap<>();
    private static int selectedIndex = 0;

    private RailRegistry() {
    }

    public static void setDefinitions(List<RailDefinition> defs) {
        DEFINITIONS.clear();
        BY_ID.clear();
        Map<String, RailDefinition> unique = new LinkedHashMap<>();
        for (RailDefinition d : defs) {
            if (d == null || d.getId() == null || d.getId().isBlank()) {
                continue;
            }
            unique.putIfAbsent(d.getId(), d);
        }
        for (RailDefinition d : unique.values()) {
            DEFINITIONS.add(d);
            BY_ID.put(d.getId(), d);
        }
        if (selectedIndex >= DEFINITIONS.size()) selectedIndex = 0;
    }

    public static List<RailDefinition> getAll() {
        return List.copyOf(DEFINITIONS);
    }

    public static RailDefinition getById(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static RailDefinition getSelected() {
        if (DEFINITIONS.isEmpty()) return null;
        if (selectedIndex < 0 || selectedIndex >= DEFINITIONS.size()) selectedIndex = 0;
        return DEFINITIONS.get(selectedIndex);
    }

    public static void setSelectedIndex(int i) {
        if (i >= 0 && i < DEFINITIONS.size()) selectedIndex = i;
    }
}

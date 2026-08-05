package com.portofino.realtrainmodunofficial.cargo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 読み込んだ貨物定義の索引。{@code VehicleRegistry} と同じ作り。 */
public final class CargoRegistry {

    private static final Map<String, CargoDefinition> BY_ID = new LinkedHashMap<>();

    public static synchronized void setDefinitions(List<CargoDefinition> definitions) {
        BY_ID.clear();
        if (definitions == null) {
            return;
        }
        for (CargoDefinition def : definitions) {
            if (def != null && def.getId() != null && !def.getId().isBlank()) {
                BY_ID.put(def.getId(), def);
            }
        }
    }

    public static synchronized CargoDefinition getById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        CargoDefinition def = BY_ID.get(id);
        if (def != null) {
            return def;
        }
        //本家は拡張子や大文字小文字の揺れを許すので、素の名前でも引けるようにする
        for (CargoDefinition candidate : BY_ID.values()) {
            if (candidate.getId().equalsIgnoreCase(id)
                || candidate.getDisplayName().equalsIgnoreCase(id)) {
                return candidate;
            }
        }
        return null;
    }

    public static synchronized List<CargoDefinition> getByKind(CargoDefinition.Kind kind) {
        List<CargoDefinition> list = new ArrayList<>();
        for (CargoDefinition def : BY_ID.values()) {
            if (def.getKind() == kind) {
                list.add(def);
            }
        }
        return list;
    }

    /** 何も選ばれていないときの既定。本家も先頭を使う。 */
    public static synchronized CargoDefinition getDefault(CargoDefinition.Kind kind) {
        for (CargoDefinition def : BY_ID.values()) {
            if (def.getKind() == kind) {
                return def;
            }
        }
        return null;
    }

    public static synchronized List<CargoDefinition> getAll() {
        return new ArrayList<>(BY_ID.values());
    }

    private CargoRegistry() {
    }
}

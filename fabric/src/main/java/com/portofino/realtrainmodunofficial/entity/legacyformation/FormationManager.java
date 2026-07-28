package com.portofino.realtrainmodunofficial.entity.legacyformation;

import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import java.util.HashMap;
import java.util.Map;

public final class FormationManager {
    private static FormationManager INSTANCE;
    private final Map<Long, Formation> formations = new HashMap<>();

    private FormationManager() {}

    public static FormationManager getInstance() {
        if (INSTANCE == null) INSTANCE = new FormationManager();
        return INSTANCE;
    }

    public static void reset() { INSTANCE = null; }

    public Formation getFormation(long id) { return formations.get(id); }
    public void register(long id, Formation f) { formations.put(id, f); }
    public void remove(long id) { formations.remove(id); }

    public long getNewId() {
        long id = System.currentTimeMillis();
        // ensure uniqueness
        while (formations.containsKey(id)) id++;
        return id;
    }

    public Formation createNewFormation(TrainEntity train) {
        long id = getNewId();
        Formation f = new Formation(id, 1);
        f.entries[0] = new FormationEntry(train, 0, 0);
        train.setFormation(f);
        return f;
    }
}

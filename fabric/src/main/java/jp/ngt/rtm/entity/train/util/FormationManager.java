package jp.ngt.rtm.entity.train.util;

import jp.ngt.rtm.entity.train.EntityTrainBase;

import java.util.HashMap;
import java.util.Map;

/**
 * 本家 jp.ngt.rtm.entity.train.util.FormationManager (KaizPatchX) の忠実移植。
 * 本家は RTMCore.proxy が Side ごとのインスタンスを供給 → ここでは static 2 面で再現。
 * TODO: FormationData (SavedData) の永続化。現状は車両 NBT の FormationEntry からの再構築のみ
 * (本家でもエンティティ ID はセッション毎に変わるため、実質の復元経路は車両 NBT 側)。
 */
public final class FormationManager {
    private static final FormationManager SERVER = new FormationManager(false);
    private static final FormationManager CLIENT = new FormationManager(true);

    private final boolean isRemote;
    private final Map<Long, Formation> formationMap = new HashMap<>();

    public FormationManager(boolean par1) {
        this.isRemote = par1;
    }

    public static FormationManager getInstance() {
        return SERVER;
    }

    public static FormationManager getInstance(boolean remote) {
        return remote ? CLIENT : SERVER;
    }

    public Map<Long, Formation> getFormations() {
        return this.formationMap;
    }

    public Formation getFormation(long id) {
        return this.formationMap.get(id);
    }

    public void setFormation(long id, Formation formation) {
        this.formationMap.put(id, formation);
    }

    public void removeFormation(long id) {
        this.formationMap.remove(id);
    }

    public int clearFormations() {
        int count = this.formationMap.size();
        this.formationMap.clear();
        return count;
    }

    public long getNewFormationId() {
        long id = System.currentTimeMillis();
        while (this.formationMap.containsKey(id)) {
            ++id;
        }
        return id;
    }

    /**
     * 編成を新規に作成(車両設置時のみ使用)
     */
    public Formation createNewFormation(EntityTrainBase par1) {
        long newId = this.getNewFormationId();
        Formation formation = new Formation(newId, 1, this.isRemote);
        formation.setTrain(par1, 0, 0);
        return formation;
    }

    public Formation createNewFormation(EntityTrainBase par1, long id, byte pos, byte dir, int size) {
        Formation formation = new Formation(id, size, this.isRemote);
        formation.setTrain(par1, pos, dir);
        return formation;
    }
}

package net.neoforged.neoforge.client.event;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.bus.api.Event;

import java.util.Map;

/**
 * シム: 焼き上がったモデルへの介入。RTMU は {@code customIconTexture} のために
 * アイテムモデルを包み直すのに使う。
 *
 * <h2>NeoForge との形の違い</h2>
 * NeoForge は「焼き上がった<b>全モデルの表</b>」を一度に渡してくる。Fabric の
 * {@code ModelLoadingPlugin#modifyModelAfterBake} は<b>1 個ずつ</b>で、全体を集める場所が無い。
 *
 * <p>そこで Fabric 側は<b>1 個だけ入った表</b>を作って毎回流す。購読側 (共有コード) は
 * 表を回して条件に合うものを差し替えるだけなので、<b>どちらの形でもそのまま動く</b>。
 * 共有コードを分岐させずに済むのでこの形にしている。
 */
public abstract class ModelEvent extends Event {

    public static class ModifyBakingResult extends ModelEvent {
        private final Map<ModelResourceLocation, BakedModel> models;

        public ModifyBakingResult(Map<ModelResourceLocation, BakedModel> models) {
            this.models = models;
        }

        /** 差し替え可能な表。{@code entry.setValue(...)} で置き換える。 */
        public Map<ModelResourceLocation, BakedModel> getModels() {
            return this.models;
        }
    }
}

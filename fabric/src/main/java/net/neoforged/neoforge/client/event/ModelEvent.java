package net.neoforged.neoforge.client.event;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.bus.api.Event;

import java.util.Map;

/**
 * シム: 焼き上がったモデルへの介入。
 * アイテムモデルを包み直すのに使う。
 */
public abstract class ModelEvent extends Event {

    public static class ModifyBakingResult extends ModelEvent {
        private final Map<ModelResourceLocation, BakedModel> models;

        public ModifyBakingResult(Map<ModelResourceLocation, BakedModel> models) {
            this.models = models;
        }

        /** 差し替え可能な表。entry.setValue(...) で置き換える。 */
        public Map<ModelResourceLocation, BakedModel> getModels() {
            return this.models;
        }
    }
}

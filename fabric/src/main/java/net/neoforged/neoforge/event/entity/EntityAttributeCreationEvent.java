package net.neoforged.neoforge.event.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.bus.api.Event;

import java.util.LinkedHashMap;
import java.util.Map;

/** シム: 集めた属性をエントリポイントが FabricDefaultAttributeRegistry へ回す。 */
public class EntityAttributeCreationEvent extends Event {
    private final Map<EntityType<? extends LivingEntity>, AttributeSupplier> attributes = new LinkedHashMap<>();

    public void put(EntityType<? extends LivingEntity> type, AttributeSupplier supplier) {
        attributes.put(type, supplier);
    }

    public Map<EntityType<? extends LivingEntity>, AttributeSupplier> getAttributes() {
        return attributes;
    }
}

package net.neoforged.neoforge.client.event;

import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.List;

/** シム: 集めたキーをエントリポイントが KeyBindingHelper へ回す。 */
public class RegisterKeyMappingsEvent extends Event {
    private final List<KeyMapping> mappings = new ArrayList<>();

    public void register(KeyMapping mapping) {
        mappings.add(mapping);
    }

    public List<KeyMapping> getMappings() {
        return mappings;
    }
}

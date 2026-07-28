package net.neoforged.neoforge.event;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.RepositorySource;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.List;

/** シム: 集めた RepositorySource をエントリポイントがリソースパック登録へ回す。 */
public class AddPackFindersEvent extends Event {
    private final PackType packType;
    private final List<RepositorySource> sources = new ArrayList<>();

    public AddPackFindersEvent(PackType packType) {
        this.packType = packType;
    }

    public PackType getPackType() {
        return packType;
    }

    public void addRepositorySource(RepositorySource source) {
        sources.add(source);
    }

    public List<RepositorySource> getSources() {
        return sources;
    }
}

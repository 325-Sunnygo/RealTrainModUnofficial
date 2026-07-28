package jp.ngt.rtm.rail;

import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * ロード済みマーカー BE の追跡。
 * 本家は world.loadedTileEntityList を走査していたが、1.21 に相当 API がないため
 * onLoad/setRemoved で登録・解除する方式で代替。
 */
public final class MarkerManager {
    private static final Map<Level, Set<TileEntityMarker>> MARKERS = Collections.synchronizedMap(new WeakHashMap<>());

    private MarkerManager() {
    }

    public static void add(TileEntityMarker marker) {
        MARKERS.computeIfAbsent(marker.getLevel(), l -> new CopyOnWriteArraySet<>()).add(marker);
    }

    public static void remove(TileEntityMarker marker) {
        Set<TileEntityMarker> set = MARKERS.get(marker.getLevel());
        if (set != null) {
            set.remove(marker);
        }
    }

    /**
     * 本家 loadedTileEntityList 走査の代替。
     */
    public static List<TileEntityMarker> getMarkers(Level level) {
        Set<TileEntityMarker> set = MARKERS.get(level);
        if (set == null) {
            return List.of();
        }
        return set.stream().filter(m -> !m.isRemoved()).collect(Collectors.toList());
    }
}

package gnu.trove.map.hash;

import java.util.HashMap;
import java.util.Map;

public class TByteObjectHashMap<V> {
    private final Map<Byte, V> values = new HashMap<>();

    public V put(byte key, V value) {
        return values.put(key, value);
    }

    public V get(byte key) {
        return values.get(key);
    }
}

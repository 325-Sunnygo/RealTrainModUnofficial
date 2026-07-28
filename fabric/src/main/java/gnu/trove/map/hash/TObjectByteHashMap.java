package gnu.trove.map.hash;

import java.util.HashMap;
import java.util.Map;

public class TObjectByteHashMap<K> {
    private final Map<K, Byte> values = new HashMap<>();

    public byte put(K key, byte value) {
        Byte previous = values.put(key, value);
        return previous == null ? 0 : previous;
    }

    public byte get(K key) {
        Byte value = values.get(key);
        return value == null ? 0 : value;
    }
}

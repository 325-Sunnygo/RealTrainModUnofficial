package gnu.trove.map.hash;

import java.util.HashMap;
import java.util.Map;

public class TLongLongHashMap {
    private final Map<Long, Long> values = new HashMap<>();

    public long put(long key, long value) {
        Long previous = values.put(key, value);
        return previous == null ? 0L : previous;
    }

    public long get(long key) {
        Long value = values.get(key);
        return value == null ? 0L : value;
    }

    public long remove(long key) {
        Long previous = values.remove(key);
        return previous == null ? 0L : previous;
    }

    public boolean containsKey(long key) {
        return values.containsKey(key);
    }

    public void clear() {
        values.clear();
    }
}

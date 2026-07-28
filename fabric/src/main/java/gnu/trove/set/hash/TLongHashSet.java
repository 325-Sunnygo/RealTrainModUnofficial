package gnu.trove.set.hash;

import java.util.LinkedHashSet;
import java.util.Set;

public class TLongHashSet {
    private final Set<Long> values = new LinkedHashSet<>();

    public boolean add(long value) {
        return values.add(value);
    }

    public boolean remove(long value) {
        return values.remove(value);
    }

    public boolean contains(long value) {
        return values.contains(value);
    }

    public void clear() {
        values.clear();
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public long[] toArray() {
        long[] out = new long[values.size()];
        int i = 0;
        for (long value : values) out[i++] = value;
        return out;
    }
}

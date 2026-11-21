package gnu.trove.set.hash;

import gnu.trove.iterator.TIntIterator;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class TIntHashSet {
    private final Set<Integer> values = new LinkedHashSet<>();

    public boolean add(int value) {
        return values.add(value);
    }

    public boolean remove(int value) {
        return values.remove(value);
    }

    public boolean contains(int value) {
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

    public int[] toArray() {
        int[] out = new int[values.size()];
        int i = 0;
        for (int value : values) out[i++] = value;
        return out;
    }

    public TIntIterator iterator() {
        Iterator<Integer> iterator = values.iterator();
        return new TIntIterator() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public int next() {
                return iterator.next();
            }
        };
    }
}

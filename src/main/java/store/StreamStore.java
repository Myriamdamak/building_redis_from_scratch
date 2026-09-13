package store;

import java.util.HashMap;
import java.util.Map;

/**
 * Backing store for stream commands (XADD).
 * Each stream key maps to its own RadixTree of entries, keyed by a
 * sortable encoding of the entry's ID.
 */
public class StreamStore {

    private final Map<String, RadixTree<StreamEntry>> streams = new HashMap<>();

    public RadixTree<StreamEntry> getOrCreate(String key) {
        return streams.computeIfAbsent(key, k -> new RadixTree<>());
    }

    public RadixTree<StreamEntry> get(String key) {
        return streams.get(key);
    }
}
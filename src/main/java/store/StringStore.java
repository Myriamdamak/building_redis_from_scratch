package store;

import java.util.HashMap;
import java.util.Map;

/**
 * Backing store for SET/GET/TYPE. Wrapping the raw map means SET/GET commands
 * don't need to know about CacheEntry or expiry logic directly - they just
 * call set()/get().
 */
public class StringStore {

    private final Map<String, CacheEntry> cache = new HashMap<>();

    public void set(String key, String value, Long expiryAt) {
        cache.put(key, new CacheEntry(value, expiryAt));
    }

    public String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            cache.remove(key);
            return null;
        }

        return entry.value;
    }
}

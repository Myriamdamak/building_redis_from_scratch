package store;


public class CacheEntry {

    public final String value;
    public final Long expiryAt;

    public CacheEntry(String value, Long expiryAt) {
        this.value = value;
        this.expiryAt = expiryAt;
    }

    public boolean isExpired() {
        return expiryAt != null && System.currentTimeMillis() >= expiryAt;
    }
}

package io.mateu.ecdemo1.contenthub.application.visibility;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Menus, listings, home and slots, cached per profile hash (and language): two employees with the same
 * attributes share every entry. The same events that recalculate the projection invalidate it, and so
 * do the crossings of publication and expiry dates; the short TTL is only a safety net.
 */
@Service
public class ProjectionCache {

    final Cache<String, Object> cache = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats()
            .build();

    @SuppressWarnings("unchecked")
    public <T> T get(String hash, String language, String what, Supplier<T> compute) {
        return (T) cache.get(hash + "|" + language + "|" + what, k -> compute.get());
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    public void invalidateHash(String hash) {
        cache.asMap().keySet().removeIf(k -> k.startsWith(hash + "|"));
    }

    /** How many entries there are now — for the backoffice and the tests. */
    public long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    public long hits() {
        return cache.stats().hitCount();
    }
}

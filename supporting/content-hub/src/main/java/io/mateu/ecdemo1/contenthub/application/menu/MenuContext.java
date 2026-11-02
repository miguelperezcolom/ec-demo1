package io.mateu.ecdemo1.contenthub.application.menu;

import io.mateu.ecdemo1.contenthub.application.security.Reader;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Who a menu is being drawn for, and on which site: what the target resolvers decide by. Lives for one
 * drawing of the menu, and keeps what they work out once for all its entries (the sections the reader
 * sees, say).
 */
public final class MenuContext {

    private final String siteId;
    private final Reader reader;
    private final Set<String> scopes;
    private final Map<String, Object> memo = new HashMap<>();

    /** @param scopes the reader's scopes, as the gateway sent them (X-User-Scopes); empty when unknown */
    public MenuContext(String siteId, Reader reader, Set<String> scopes) {
        this.siteId = siteId;
        this.reader = reader;
        this.scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public String siteId() {
        return siteId;
    }

    public Reader reader() {
        return reader;
    }

    public Set<String> scopes() {
        return scopes;
    }

    public String language() {
        return reader.attributes().language();
    }

    /** Works something out once per drawing of the menu. */
    @SuppressWarnings("unchecked")
    public <T> T memo(String key, Supplier<T> compute) {
        return (T) memo.computeIfAbsent(key, k -> compute.get());
    }
}

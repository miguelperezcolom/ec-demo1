package io.mateu.ecdemo1.frontoffice.infra.persistence;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * An identity map that lives as long as one HTTP request: the second read of the same aggregate in
 * a request does not go back to the database.
 *
 * <p>A screen is a view model built fresh on every request, and it reads the same stay, guest and
 * folio from many places — the booking overview did it more than ten times per render, each one
 * loading the aggregate and its collections, ~70 transactions for one page. Caching here rather
 * than in the screens serves every screen, and cannot be forgotten by the next one.
 *
 * <p>The aggregates are immutable records, so sharing them within a request is safe. A save
 * evicts, so an action that saves and then re-renders in the same request reads what it saved.
 * Outside a request (tests, the API's own threads, startup) it simply loads.
 *
 * <p>Active only in a request that went through {@link RequestCacheFilter}. Request attributes can
 * exist without one — Spring's test support binds a mock request to the test thread for the whole
 * test method, across the MockMvc calls it makes — and caching there would hide a write made in
 * one of those calls from the test that reads afterwards.
 */
final class RequestCache {

  private static final String PREFIX = RequestCache.class.getName() + ":";
  static final String ENABLED = RequestCache.class.getName() + ".enabled";

  static <T> Optional<T> get(String key, Supplier<Optional<T>> load) {
    var attributes = RequestContextHolder.getRequestAttributes();
    if (attributes == null || attributes.getAttribute(ENABLED, RequestAttributes.SCOPE_REQUEST) == null) {
      return load.get();
    }
    @SuppressWarnings("unchecked")
    var cached = (Optional<T>) attributes.getAttribute(PREFIX + key, RequestAttributes.SCOPE_REQUEST);
    if (cached != null) {
      return cached;
    }
    var loaded = load.get();
    attributes.setAttribute(PREFIX + key, loaded, RequestAttributes.SCOPE_REQUEST);
    return loaded;
  }

  static void evict(String... keys) {
    var attributes = RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return;
    }
    for (var key : keys) {
      attributes.removeAttribute(PREFIX + key, RequestAttributes.SCOPE_REQUEST);
    }
  }

  private RequestCache() {
  }
}

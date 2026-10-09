package io.mateu.ecdemo1.frontoffice.infra.customer;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The answers of a read the screens repeat — a header, a panel and the guests' rail ask for the same
 * customer in one render, and again on every interaction: kept a little while, the ones that came
 * (30 s) and the ones that did not (10 s), so a slow service costs the desk its timeout once, not on
 * every click.
 */
final class Answers<T> {

  record Kept<T>(Optional<T> value, Instant until) {}

  final ConcurrentHashMap<String, Kept<T>> kept = new ConcurrentHashMap<>();
  final Duration found;
  final Duration missing;

  Answers(Duration found, Duration missing) {
    this.found = found;
    this.missing = missing;
  }

  /** {@code ask} answers empty for «nothing to say» and null for «did not answer». */
  Optional<T> get(String key, Supplier<Optional<T>> ask) {
    var now = Instant.now();
    var hit = kept.get(key);
    if (hit != null && hit.until().isAfter(now)) {
      return hit.value();
    }
    var answer = ask.get();
    var value = answer == null ? Optional.<T>empty() : answer;
    kept.put(key, new Kept<>(value, now.plus(answer != null && answer.isPresent() ? found : missing)));
    if (kept.size() > 2000) {
      kept.clear();
    }
    return value;
  }

  void forget(String key) {
    kept.remove(key);
  }
}

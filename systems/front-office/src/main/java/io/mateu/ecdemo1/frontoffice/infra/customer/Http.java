package io.mateu.ecdemo1.frontoffice.infra.customer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * What the clients of the desk's customer reads share: a RestClient with short timeouts — the desk is
 * waiting — or none when the service is not configured; and lenient reading of a JSON answer taken as
 * maps and lists (Boot 4's RestClient reads with Jackson 3).
 */
final class Http {

  /** For reads a screen waits on: connect 1 s, read 2 s. */
  static RestClient quick(String url) {
    return client(url, Duration.ofSeconds(1), Duration.ofSeconds(2));
  }

  /** For the demo's seeding writes: connect 2 s, read 3 s, as the rest of the front office's clients. */
  static RestClient writes(String url) {
    return client(url, Duration.ofSeconds(2), Duration.ofSeconds(3));
  }

  static RestClient client(String url, Duration connect, Duration read) {
    if (url == null || url.isBlank()) {
      return null;
    }
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connect);
    factory.setReadTimeout(read);
    return RestClient.builder().baseUrl(url).requestFactory(factory).build();
  }

  static String text(Map<?, ?> map, String field) {
    var value = map == null ? null : map.get(field);
    return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
  }

  static LocalDate date(Map<?, ?> map, String field) {
    var value = text(map, field);
    try {
      // a date, or the date of an instant
      return value == null ? null : LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
    } catch (RuntimeException e) {
      return null;
    }
  }

  static int integer(Map<?, ?> map, String field) {
    var value = map == null ? null : map.get(field);
    if (value instanceof Number n) {
      return n.intValue();
    }
    if (value instanceof List<?> list) {
      return list.size();
    }
    try {
      return value == null ? 0 : (int) Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  static BigDecimal decimal(Map<?, ?> map, String field) {
    var value = map == null ? null : map.get(field);
    try {
      return value == null ? null : new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static Map<?, ?> map(Map<?, ?> map, String field) {
    return map != null && map.get(field) instanceof Map<?, ?> m ? m : null;
  }

  static List<?> list(Map<?, ?> map, String field) {
    return map != null && map.get(field) instanceof List<?> l ? l : List.of();
  }

  private Http() {}
}

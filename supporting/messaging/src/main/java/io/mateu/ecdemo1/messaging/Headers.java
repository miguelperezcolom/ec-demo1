package io.mateu.ecdemo1.messaging;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The headers of a message, in its row: {@code name=value&name=value}, URL-encoded. No JSON library,
 * so the jar needs none (the services on Boot 3 have Jackson 2, those on Boot 4 Jackson 3).
 */
final class Headers {

    private Headers() {
    }

    static String encode(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    static Map<String, String> decode(String stored) {
        if (stored == null || stored.isBlank()) {
            return Map.of();
        }
        var headers = new LinkedHashMap<String, String>();
        for (var pair : stored.split("&")) {
            var eq = pair.indexOf('=');
            if (eq > 0) {
                headers.put(dec(pair.substring(0, eq)), dec(pair.substring(eq + 1)));
            }
        }
        return headers;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String dec(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}

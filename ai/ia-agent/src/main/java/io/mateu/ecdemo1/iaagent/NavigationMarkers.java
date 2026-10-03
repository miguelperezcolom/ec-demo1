package io.mateu.ecdemo1.iaagent;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the {@code [NAVIGATE:{…}]} markers of a finished answer mean: which ONE of them moves the
 * screen, and what the others leave in the text.
 *
 * <p>Every marker used to be stripped and every one fired a navigation. A model that used them as
 * links — {@code "Nora Duarte: [NAVIGATE:{…/4MBZS7…}]"}, one per booking — left the user with
 * {@code "Nora Duarte: "} and nothing after it, and the screen jumping through each record to end
 * on the last. So:
 *
 * <ul>
 *   <li>a marker on a line of its own is a command: the LAST such marker navigates;</li>
 *   <li>with no marker on a line of its own, a single marker still navigates (the old
 *       {@code "Te abro el listado. [NAVIGATE:{…}]"});</li>
 *   <li>every other marker is a link the model meant: it becomes a markdown link to its route,
 *       labelled with the route's last segment (the record's id), which the chat renders as an
 *       in-app link.</li>
 * </ul>
 *
 * <p>The grammar is the controller's: a marker ends at its first {@code ]}.
 */
final class NavigationMarkers {

    static final Pattern MARKER = Pattern.compile("\\[NAVIGATE:(\\{[^]]*})]", Pattern.DOTALL);
    private static final Pattern ROUTE = Pattern.compile("\"route\"\\s*:\\s*\"([^\"]*)\"");

    private NavigationMarkers() {
    }

    /** The text to show and the navigation detail JSON to send (at most one). */
    record Parsed(String cleanText, List<String> navigations) {
    }

    private record Found(int start, int end, String json, boolean standalone) {
    }

    static Parsed parse(String raw, ObjectMapper objectMapper) {
        if (raw == null || raw.isEmpty()) {
            return new Parsed("", List.of());
        }
        var found = new ArrayList<Found>();
        Matcher m = MARKER.matcher(raw);
        while (m.find()) {
            String json = m.group(1);
            if (!valid(json, objectMapper)) {
                found.add(new Found(m.start(), m.end(), null, true));
                continue;
            }
            found.add(new Found(m.start(), m.end(), json, standalone(raw, m.start())));
        }
        int navigating = -1;
        for (int i = 0; i < found.size(); i++) {
            if (found.get(i).json() != null && found.get(i).standalone()) {
                navigating = i;
            }
        }
        if (navigating < 0) {
            var valid = found.stream().filter(f -> f.json() != null).toList();
            if (valid.size() == 1) {
                navigating = found.indexOf(valid.get(0));
            }
        }
        var text = new StringBuilder();
        int last = 0;
        for (int i = 0; i < found.size(); i++) {
            var f = found.get(i);
            text.append(raw, last, f.start());
            if (f.json() != null && i != navigating) {
                var link = link(f.json());
                if (link != null) {
                    text.append(link);
                }
            }
            last = f.end();
        }
        text.append(raw.substring(last));
        List<String> navigations = navigating >= 0 ? List.of(found.get(navigating).json()) : List.of();
        return new Parsed(text.toString().trim(), navigations);
    }

    /** Only whitespace between the line's start and the marker. */
    static boolean standalone(CharSequence text, int markerStart) {
        for (int i = markerStart - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '\n') {
                return true;
            }
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    /** {@code [4MBZS7](/booking/bookings/4MBZS7)}, or null for a marker with no route. */
    static String link(String json) {
        Matcher r = ROUTE.matcher(json);
        if (!r.find() || r.group(1).isBlank()) {
            return null;
        }
        String route = r.group(1);
        String path = route.contains("?") ? route.substring(0, route.indexOf('?')) : route;
        String label = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        label = label.substring(label.lastIndexOf('/') + 1);
        if (label.isBlank()) {
            label = route;
        }
        return "[" + label + "](" + route + ")";
    }

    private static boolean valid(String json, ObjectMapper objectMapper) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

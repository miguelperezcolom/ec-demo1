package io.mateu.ecdemo1.uicommons.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.data.Markdown;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * A content slot of the intranet's content manager (content-hub) drawn in another application's screen:
 * a hole whose content the editors publish from "Editorial", each user seeing the variant their
 * profile admits — or nothing, and the screen keeps its own layout.
 *
 * <p>Asked server-side, inside the cluster, with the user's token: content-hub reads who it is from it
 * and answers the visible variant with the highest priority (its HTML already expanded, media URLs
 * signed), or 204. This is the slot remote as plain JSON (content-hub's SlotController) until Mateu's
 * server-resolved micro frontend ships; the screens then change their plumbing, not their content.
 * Never fails the page: if content-hub does not answer, there is no slot.
 */
public final class ContentSlots {

    /** Where content-hub answers inside the cluster; CONTENT_HUB_URL overrides it. */
    static final String BASE_URL = env("CONTENT_HUB_URL", "http://content-hub:8135");

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    static final ObjectMapper JSON = new ObjectMapper();

    /** The variant a user sees in a slot: its title, its markup and where it leads (blank: nowhere). */
    public record Slot(String key, String variant, String title, String html, String link) {
    }

    private ContentSlots() {
    }

    /** The variant the request's user sees in {@code key}, if any. */
    public static Optional<Slot> fetch(HttpRequest request, String key) {
        var authorization = request == null ? null : request.getHeaderValue("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return Optional.empty();
        }
        try {
            var response = HTTP.send(java.net.http.HttpRequest.newBuilder(
                            URI.create(BASE_URL + "/api/slots/" + URLEncoder.encode(key, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(3)).header("Accept", "application/json")
                    .header("Authorization", authorization).GET().build(), BodyHandlers.ofString());
            return response.statusCode() == 200 ? Optional.of(slot(JSON.readTree(response.body()))) : Optional.empty();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    /**
     * The slot as a block of the screen, or empty. Markdown carrying the variant's HTML inline, not an
     * Element: Redwood draws a welcome tile's atoms — texts, charts, Markdown — and left an Element in
     * one empty; both renderers draw Markdown's allowed inline HTML (sanitised again on their side).
     */
    public static Optional<Component> block(HttpRequest request, String key) {
        return fetch(request, key).map(slot -> new Markdown(markup(slot), "width: 100%;", null));
    }

    static Slot slot(JsonNode json) {
        return new Slot(text(json, "key"), text(json, "variant"), text(json, "title"), text(json, "html"),
                text(json, "link"));
    }

    /** The variant's own markup (content-hub's, already sanitised), under its title and over its link. */
    static String markup(Slot slot) {
        var out = new StringBuilder();
        if (!slot.title().isBlank()) {
            out.append("<h3 style=\"margin: 0 0 .5rem; font-size: 1.05rem; font-weight: 600;\">")
                    .append(Html.escape(slot.title())).append("</h3>");
        }
        out.append(slot.html());
        if (!slot.link().isBlank()) {
            out.append("<p style=\"margin: .5rem 0 0;\"><a href=\"").append(Html.escape(slot.link()))
                    .append("\">Ver más</a></p>");
        }
        return out.toString();
    }

    static String text(JsonNode json, String field) {
        var value = json == null ? null : json.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    static String env(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}

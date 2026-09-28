package io.mateu.ecdemo1.uicommons.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.uidl.data.MetricCard;
import io.mateu.uidl.data.MetricTrend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The external APIs' usage on the consoles: the KPI tiles on the welcome pages, from
 * integrations-service — its cards, asked from inside the cluster when the welcome page renders.
 * Clicking one opens the page behind them, under {@code /_api-usage}. Not in the header: the figures
 * are what one looks at before a meeting or a demo, not on every screen.
 *
 * <p>The tiles are not a micro frontend on the page: a welcome page's tiles are its own
 * {@code @Panel} fields, which is what every renderer draws as KPIs (Redwood's welcome takes its
 * tiles from those panels only).
 */
public final class ApiUsageWidget {

    /** Where integrations-service answers inside the cluster; API_USAGE_URL overrides it. */
    static final String BASE_URL = env("API_USAGE_URL", "http://integrations-service:8126");

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    static final ObjectMapper JSON = new ObjectMapper();

    private ApiUsageWidget() {
    }


    /**
     * The welcome page's tiles: Salesforce's calls left, our pace, Opera's calls today — each one
     * running {@code actionId} when clicked. Never fails the page: if integrations-service does not
     * answer, the three tiles say so.
     */
    public static List<MetricCard> kpis(String actionId) {
        try {
            var response = HTTP.send(java.net.http.HttpRequest.newBuilder(URI.create(BASE_URL + "/usage/kpis"))
                    .timeout(Duration.ofSeconds(3)).header("Accept", "application/json").GET().build(),
                    BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return unavailable(actionId);
            }
            var cards = cards(JSON.readTree(response.body()), actionId);
            // The welcome lays out three tiles: anything else is not an answer it can use.
            return cards.size() == 3 ? cards : unavailable(actionId);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return unavailable(actionId);
        }
    }

    /** The cards integrations-service sent, clicking through to {@code actionId}. */
    static List<MetricCard> cards(JsonNode json, String actionId) {
        var cards = new ArrayList<MetricCard>();
        if (json == null || !json.isArray()) {
            return cards;
        }
        for (var c : json) {
            cards.add(MetricCard.builder()
                    .id(text(c, "id"))
                    .title(text(c, "title"))
                    .value(text(c, "value"))
                    .unit(text(c, "unit"))
                    .trend(trend(text(c, "trend")))
                    .trendLabel(text(c, "trendLabel"))
                    .icon(text(c, "icon"))
                    .description(text(c, "description"))
                    .actionId(actionId)
                    .build());
        }
        return cards;
    }

    /** The three tiles when integrations-service does not answer: the page still renders. */
    static List<MetricCard> unavailable(String actionId) {
        return List.of(
                placeholder("salesforce", "SALESFORCE — LLAMADAS LIBRES", "vaadin:cloud", actionId),
                placeholder("salesforce-pace", "SALESFORCE — NUESTRAS, ÚLTIMA HORA", "vaadin:trending-up", actionId),
                placeholder("opera", "OPERA — LLAMADAS HOY", "vaadin:building", actionId));
    }

    static MetricCard placeholder(String id, String title, String icon, String actionId) {
        return MetricCard.builder().id(id).title(title).icon(icon).value("—")
                .description("El uso de las APIs no está disponible ahora").actionId(actionId).build();
    }

    static MetricTrend trend(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MetricTrend.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String text(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    static String env(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

}

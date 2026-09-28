package io.mateu.ecdemo1.uicommons.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.uidl.data.MetricCard;
import io.mateu.uidl.data.MetricTrend;
import io.mateu.uidl.data.MicroFrontend;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The external APIs' usage on the consoles — "Salesforce 335 libres · Opera 320 hoy" in the header,
 * and the KPI tiles on the welcome pages — from integrations-service: the header's badge is its
 * screen under {@code /_api-usage}, which the gateway routes on every console host; the tiles are its
 * cards, asked from inside the cluster when the welcome page renders.
 *
 * <p>The tiles are not a micro frontend on the page, as the badge is: a welcome page's tiles are its
 * own {@code @Panel} fields, which is what every renderer draws as KPIs (Redwood's welcome takes its
 * tiles from those panels only).
 */
public final class ApiUsageWidget {

    /** Where integrations-service answers inside the cluster; API_USAGE_URL overrides it. */
    static final String BASE_URL = env("API_USAGE_URL", "http://integrations-service:8126");

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    static final ObjectMapper JSON = new ObjectMapper();

    private ApiUsageWidget() {
    }

    /** The header's figures, refreshed every minute. */
    public static MicroFrontend badge() {
        return MicroFrontend.builder().baseUrl("/_api-usage").route("/badge").build();
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

    /**
     * The consoles' header: the APIs' usage, then the inbox badge and the greeting ({@link UserWidget}),
     * in one layout. Nothing for an anonymous call, like them.
     */
    public static List<Component> withInboxBadge(HttpRequest httpRequest) {
        return UserWidget.withInboxBadge(httpRequest, List.of(badge()));
    }
}

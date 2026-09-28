package io.mateu.ecdemo1.uicommons.user;

import io.mateu.uidl.data.MicroFrontend;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;

import java.util.List;

/**
 * The external APIs' usage on the consoles — "Salesforce 335 libres · Opera 320 hoy" in the header,
 * and the tiles on the home pages — served by integrations-service under {@code /_api-usage}, which
 * the gateway routes on every console host.
 */
public final class ApiUsageWidget {

    private ApiUsageWidget() {
    }

    /** The header's figures, refreshed every minute. */
    public static MicroFrontend badge() {
        return MicroFrontend.builder().baseUrl("/_api-usage").route("/badge").build();
    }

    /** The home page's tiles: the calls left, their trend, when they were seen, the pause. */
    public static MicroFrontend tiles() {
        return MicroFrontend.builder().baseUrl("/_api-usage").route("/kpis").build();
    }

    /**
     * The consoles' header: the APIs' usage, then the inbox badge and the greeting ({@link UserWidget}),
     * in one layout. Nothing for an anonymous call, like them.
     */
    public static List<Component> withInboxBadge(HttpRequest httpRequest) {
        return UserWidget.withInboxBadge(httpRequest, List.of(badge()));
    }
}

package io.mateu.ecdemo1.uicommons.user;

import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.data.Anchor;
import io.mateu.uidl.data.HorizontalLayout;
import io.mateu.uidl.data.MicroFrontend;
import io.mateu.uidl.data.Popover;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;

import java.util.List;
import java.util.Map;

/**
 * Who is signed in, and a way out: a greeting that opens a popover with the email and Logout. The
 * header widget of the four consoles' shells and of the front office.
 *
 * <p>The identity is the Keycloak token the page sends with every call, read by
 * {@link DisplayOnlyTokenClaims} — display only; the app's resource server has already verified
 * it. {@code window.logout()} is defined by the bootstrap page Mateu writes for
 * {@code @KeycloakSecured}. The name is escaped: it goes into markup.
 *
 * <p>The popover also says whether this browser receives notifications (Web Push), and turns them on
 * or off: {@code <ec-push-toggle>}, a custom element that the inbox's push script
 * ({@code /_inbox/push/push.js}, loaded by every shell with {@code @Script}) defines. Where that
 * script is not loaded the element stays undefined and draws nothing.
 *
 * <p>Anonymous calls get no widget: the bootstrap page is about to redirect to Keycloak, so there
 * is no one to greet yet.
 */
public final class UserWidget {

    /** The icon a narrow header keeps; the popover still has who and Logout. */
    static final String NARROW_ICON = "<vaadin-icon icon=\"vaadin:user\" style=\"display: var(--mateu-header-narrow-only, none); width: 1em; height: 1em; vertical-align: -0.125em;\"></vaadin-icon>";

    /** Web Push for this browser: its state, and Activar / Desactivar / Enviarme una prueba. */
    static final String PUSH_TOGGLE = "<ec-push-toggle></ec-push-toggle>";

    private UserWidget() {
    }

    /**
     * The banner a simulated Opera outage shows in every console while it is on — «Simulación: Opera no
     * responde» — and nothing at all otherwise: the control plane's Demo page switches it
     * (integrations-service, /_demo-banner, asked again every few seconds).
     */
    public static Component demoBanner() {
        return MicroFrontend.builder()
                .baseUrl("/_demo-banner")
                .route("/banner")
                .build();
    }

    /** The greeting and its popover alone. */
    public static List<Component> of(HttpRequest httpRequest) {
        return DisplayOnlyTokenClaims.of(httpRequest)
                .map(claims -> List.<Component>of(popover(claims)))
                .orElse(List.of());
    }

    /** The front office's header: the demo's banner, then the greeting — one layout, as the consoles'. */
    public static List<Component> withDemoBanner(HttpRequest httpRequest) {
        return DisplayOnlyTokenClaims.of(httpRequest)
                .map(claims -> List.<Component>of(HorizontalLayout.builder()
                        .content(List.of(demoBanner(), popover(claims)))
                        .style("align-items: center;")
                        .build()))
                .orElse(List.of());
    }

    /**
     * The consoles' header: the demo's banner (empty unless an Opera outage is simulated), the inbox badge — what waits for the signed-in user, the notifications
     * and the forms engine's tasks of their roles — and then the greeting.
     */
    public static List<Component> withInboxBadge(HttpRequest httpRequest) {
        return withInboxBadge(httpRequest, List.of());
    }

    /**
     * The same, with other widgets before the inbox badge. One layout for all
     * of them: the header puts each widget in its "widgets" slot, and a bare MicroFrontend does not
     * carry the slot through — it would render outside the header, invisible.
     */
    public static List<Component> withInboxBadge(HttpRequest httpRequest, List<Component> before) {
        return DisplayOnlyTokenClaims.of(httpRequest)
                .map(claims -> {
                    var content = new java.util.ArrayList<Component>();
                    content.add(demoBanner());
                    content.addAll(before);
                    content.add(MicroFrontend.builder()
                            .baseUrl("/_inbox")
                            .route("/badge")
                            .build());
                    content.add(popover(claims));
                    return List.<Component>of(HorizontalLayout.builder()
                            .content(content)
                            .style("align-items: center;") // the inbox is an icon button: centred with the greeting
                            .build());
                })
                .orElse(List.of());
    }

    /** "Hola, name", opening the email, this browser's notifications and Logout. */
    public static Popover popover(Map<String, Object> claims) {
        return Popover.builder()
                .wrapped(Text.builder()
                        .text(NARROW_ICON
                                + "<span style=\"display: var(--mateu-header-wide-only, inline)\">Hola, " + Html.escape(String.valueOf(claims.get("name"))) + "</span>")
                        .style("margin-right: 20px; cursor: pointer;") // it opens the popover: who, and Logout
                        .build())
                .content(VerticalLayout.builder()
                        .content(List.of(
                                new Text("Email: " + claims.get("email")),
                                new Text(PUSH_TOGGLE),
                                new Anchor("Logout", "javascript: window.logout();")))
                        .spacing(true)
                        .padding(true)
                        .build())
                .build();
    }
}

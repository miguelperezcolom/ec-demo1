package io.mateu.ecdemo1.shell.infra.in.ui;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.AI;
import io.mateu.uidl.annotations.FavIcon;
import io.mateu.uidl.annotations.KeycloakSecured;
import io.mateu.uidl.annotations.Script;
import io.mateu.uidl.annotations.Logo;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTemplate;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.PageType;
import io.mateu.uidl.annotations.Style;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.annotations.WelcomeBanner;
import io.mateu.uidl.data.Anchor;
import io.mateu.uidl.data.HorizontalLayout;
import io.mateu.uidl.data.MicroFrontend;
import io.mateu.uidl.data.Popover;
import io.mateu.uidl.data.RemoteMenu;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.WidgetSupplier;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static io.mateu.core.infra.JsonSerializer.fromJson;

/**
 * The one page a user ever loads.
 *
 * <p>Everything below the menu bar is served by another pod: each {@link RemoteMenu} names a
 * path, the gateway routes that path to the app that owns it, and the shell renders whatever
 * menu that app declares. So the orchestrator, the forms engine and the three demo
 * services keep their own UIs — nothing about them is restated here — and the shell only has to
 * know where they live. Adding one is a field below, a route in the gateway and a manifest; the
 * path in all three has to match the {@code @UI} value the service itself declares.
 *
 * <p>The Keycloak URL is baked in at compile time: Mateu writes {@code @KeycloakSecured} into
 * the generated bootstrap page, so it cannot be an environment variable yet. Changing the
 * deployment's hostname means rebuilding this image.
 */
@UI("")
// Which plane this console is, next to the logo: the data plane is what the business uses, the
// control plane what governs the platform.
@Title("Data plane")
@PageTitle("Data plane")
@KeycloakSecured(url = "https://auth.ec1.mateu.io", realm = "ec-demo1", clientId = "demo")
// Web Push: the inbox's script — it offers to enable notifications, and registers this browser for
// what enters the inbox of the user's roles. Served by communication-service, public at the gateway.
@Script(src = "/_inbox/push/push.js")
// Served by this app from src/main/resources/static, so it arrives through the gateway's
// catch-all like the rest of the shell — no route of its own, and no token: the browser loads a
// logo with an <img> tag, which sends no Authorization header.
@Logo("/images/riu.svg")
@FavIcon("/images/riu.svg")
// Edge-to-edge: the pages behind these menus are listings and workflow graphs, and capping the
// content at the default ~900px container squeezes them into cards for lack of horizontal room.
@Style(StyleConstants.FULL_WIDTH)
// This is a home, so it declares the landing template: Mateu tags the wire pageType as "landing"
// and the renderer lays the page out as one. The welcome banner is what LANDING is for — Mateu
// turns it into a HeroSection at the top of the content: an image, a title and a subtitle above
// the menus. The image is a Redwood illustration served from src/main/resources/static like the
// logo, so it arrives through the gateway's catch-all with no route and no token of its own.
@PageTemplate(PageType.LANDING)
@WelcomeBanner(
        title = "EventConductor demo",
        subtitle = "Reservas y workflows, en una sola plataforma.",
        image = "/images/redwood-header-texture.png")
// The chat panel. Mateu's client POSTs the prompt here and reads the answer as a stream; the
// gateway routes /ai/** to the agent pod and requires a token on it, which this client sends.
// The agent itself knows nothing about these menus: it reaches the orchestrator, the forms engine
// and the booking service over MCP, and each of those decides what it is willing to expose.
@AI(sse = "/ai/api/agent/stream")
public class ShellHome implements WidgetSupplier {

    // Every entry names its label, and that is not decoration.
    //
    // A RemoteMenu built from a base URL alone has no label, so Mateu fills one in from the FIELD
    // NAME until the remote pod answers with its own — see ActionableCompleter.completeActionable.
    // The two are then swapped in the browser, which is what makes the menu bar visibly change a
    // moment after it is drawn, and again on every navigation to the app root: clicking the logo
    // is the easiest way to see it. Four of these happened to match the field name and one did
    // not; naming all five is what stops that from being luck, and from breaking the day a field
    // is renamed.
    //
    // The cost is real and worth stating: these strings live in two repositories now. A section
    // renamed in its own pod and not here goes back to flickering, with the shell's version
    // showing first.

    /** Bookings — the CRUD, and the aggregate the booking saga confirms or cancels. */
    @Menu
    RemoteMenu booking = new RemoteMenu("/_booking").withLabel("Call center");

    /**
     * The master of trading partners — tour operators, agencies, companies — that the CRS sells
     * through and the CRS-PMS integration projects to the PMS (PoC ACL, docs/poc-acl).
     */
    @Menu
    RemoteMenu partners = new RemoteMenu("/_partners").withLabel("ERP");

    // Contenidos is no longer on this bar. The pod is untouched and still serves its own @UI, so
    // /content/contents and the rest still resolve for a deep link or an embedder — what went is
    // the menu entry, not the screens.

    // Users, groups, roles and permissions moved to the control console: administering access is a
    // control-plane concern, not part of using the product. It is served by the same users pod,
    // now mounted by the control shell behind the ai-admin gate. See ControlShellHome.

    /**
     * The inbox, hidden from the bar: the badge in the widgets below is the way in, and it says how
     * much is waiting on the way. Still declared, so a deep link or a reload on /inbox/... resolves.
     */
    @Menu
    @Hidden
    RemoteMenu inbox = new RemoteMenu("/_inbox");

    /**
     * Running the platform: Workflow and Forms, behind one entry — last on the bar: it is
     * what runs the product, not the product.
     *
     * <p>They used to sit on the bar beside Booking, which made four equals where there are
     * really two kinds of thing — see AdminMenu.
     */
    @Menu
    AdminMenu admin;

    @Override
    public List<Component> widgets(HttpRequest httpRequest) {
        var widgets = new ArrayList<Component>();

        var authorization = httpRequest.getHeaderValue("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            // Anonymous: the bootstrap page is about to redirect to Keycloak, so there is no
            // identity to greet and no task list to show yet.
            return widgets;
        }

        var claims = fromJson(new String(Base64.getUrlDecoder()
                .decode(authorization.substring("Bearer ".length()).split("\\.")[1])));

        widgets.add(HorizontalLayout.builder()
                .content(List.of(
                        // What waits for the signed-in user — the notifications and the forms engine's
                        // tasks of their roles — so the first thing on screen is work waiting for them.
                        MicroFrontend.builder()
                                .baseUrl("/_inbox")
                                .route("/badge")
                                .build(),
                        // On a narrow screen the header keeps only an icon: the popover still has who and Logout.
                        Popover.builder()
                                .wrapped(Text.builder()
                                        .text("<vaadin-icon icon=\"vaadin:user\" style=\"display: var(--mateu-header-narrow-only, none); width: 1em; height: 1em; vertical-align: -0.125em;\"></vaadin-icon>"
                                                + "<span style=\"display: var(--mateu-header-wide-only, inline)\">Hola, " + claims.get("name") + "</span>")
                                        .style("margin-right: 20px;")
                                        .build())
                                .content(VerticalLayout.builder()
                                        .content(List.of(
                                                new Text("Email: " + claims.get("email")),
                                                new Anchor("Logout", "javascript: window.logout();")))
                                        .spacing(true)
                                        .padding(true)
                                        .build())
                                .build()))
                .style("align-items: flex-end;")
                .build());

        return widgets;
    }
}

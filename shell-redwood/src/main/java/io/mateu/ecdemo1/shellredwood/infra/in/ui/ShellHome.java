package io.mateu.ecdemo1.shellredwood.infra.in.ui;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.AI;
import io.mateu.uidl.annotations.FavIcon;
import io.mateu.uidl.annotations.KeycloakSecured;
import io.mateu.uidl.annotations.Logo;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Style;
import io.mateu.uidl.annotations.UI;
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
 * The demo console rendered by Redwood instead of Vaadin — the same shell, the same menus, the
 * same backends, on a host of its own.
 *
 * <p>It is a copy of {@code shell} with ONE difference, in the pom: {@code io.mateu:redwood}
 * replaces {@code io.mateu:vaadin-lit}. Nothing else about it is renderer-aware, and that is the
 * point being demonstrated — a {@link RemoteMenu} carries UIDL rather than HTML, so the
 * orchestrator, the forms engine and the demo services render through whichever renderer the
 * shell happens to have loaded, with no change on any of them.
 *
 * <p>It runs BESIDE the Vaadin one rather than replacing it, because the two side by side against
 * one cluster are the demonstration; either alone is just an application. It also makes the
 * renderer conformance suite concrete: what Redwood does not cover shows up on real screens here
 * instead of in a fixture.
 *
 * <p>No Keycloak client of its own. {@code @KeycloakSecured} names the Keycloak URL, not this
 * app's host, so the demo client serves both consoles — its redirect URIs list both hosts.
 */
/**
 * The one page a user ever loads.
 *
 * <p>Everything below the menu bar is served by another pod: each {@link RemoteMenu} names a
 * path, the gateway routes that path to the app that owns it, and the shell renders whatever
 * menu that app declares. So the orchestrator, the forms engine, the worker and the three demo
 * services keep their own UIs — nothing about them is restated here — and the shell only has to
 * know where they live. Adding one is a field below, a route in the gateway and a manifest; the
 * path in all three has to match the {@code @UI} value the service itself declares.
 *
 * <p>The Keycloak URL is baked in at compile time: Mateu writes {@code @KeycloakSecured} into
 * the generated bootstrap page, so it cannot be an environment variable yet. Changing the
 * deployment's hostname means rebuilding this image.
 */
@UI("")
// No @Title on purpose. The renderer places the logo and the title side by side with a
// margin-left on the image and nothing between them, so a title here reads as part of the mark —
// "RIU EventConductor demo" as one phrase. The name is not lost: it is still the page heading and
// still the browser tab, via @PageTitle.
@PageTitle("EventConductor demo · Redwood")
@KeycloakSecured(url = "https://auth.ec1.mateu.io", realm = "ec-demo1", clientId = "demo")
// Served by this app from src/main/resources/static, so it arrives through the gateway's
// catch-all like the rest of the shell — no route of its own, and no token: the browser loads a
// logo with an <img> tag, which sends no Authorization header.
@Logo("/images/riu.svg")
@FavIcon("/images/riu.svg")
// Edge-to-edge: the pages behind these menus are listings and workflow graphs, and capping the
// content at the default ~900px container squeezes them into cards for lack of horizontal room.
@Style(StyleConstants.FULL_WIDTH)
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

    /**
     * Running the platform: Workflow, Forms and Worker, behind one entry.
     *
     * <p>They used to sit on the bar beside Booking, which made four equals where there are
     * really two kinds of thing — see AdminMenu.
     */
    @Menu
    AdminMenu admin;

    /** Bookings — the CRUD, and the aggregate the booking saga confirms or cancels. */
    @Menu
    RemoteMenu booking = new RemoteMenu("/_booking").withLabel("Booking");

    // Contenidos is no longer on this bar. The pod is untouched and still serves its own @UI, so
    // /content/contents and the rest still resolve for a deep link or an embedder — what went is
    // the menu entry, not the screens.

    // Users, groups, roles and permissions moved to the control console: administering access is a
    // control-plane concern, not part of using the product. It is served by the same users pod,
    // now mounted by the control shell behind the ai-admin gate. See ControlShellHome.

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
                        // The signed-in user's own pending human tasks, pulled straight from the
                        // forms engine so the first thing on screen is work waiting for them.
                        MicroFrontend.builder()
                                .baseUrl("/_forms")
                                .route("/my-tasks")
                                .build(),
                        Popover.builder()
                                .wrapped(Text.builder()
                                        .text("Hola, " + claims.get("name"))
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

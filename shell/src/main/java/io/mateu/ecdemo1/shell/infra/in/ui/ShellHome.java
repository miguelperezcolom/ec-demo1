package io.mateu.ecdemo1.shell.infra.in.ui;

import io.mateu.uidl.StyleConstants;
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
 * The one page a user ever loads.
 *
 * <p>Everything below the menu bar is served by another pod: each {@link RemoteMenu} names a
 * path, the ingress routes that path to the app that owns it, and the shell renders whatever
 * menu that app declares. So the orchestrator, the forms engine and the worker keep their own
 * UIs — nothing about them is restated here — and the shell only has to know where they live.
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
@PageTitle("EventConductor demo")
@KeycloakSecured(url = "https://auth.ec1.mateu.io", realm = "ec-demo1", clientId = "demo")
// Served by this app from src/main/resources/static, so it arrives through the gateway's
// catch-all like the rest of the shell — no route of its own, and no token: the browser loads a
// logo with an <img> tag, which sends no Authorization header.
@Logo("/images/riu.svg")
@FavIcon("/images/riu.svg")
// Edge-to-edge: the pages behind these menus are listings and workflow graphs, and capping the
// content at the default ~900px container squeezes them into cards for lack of horizontal room.
@Style(StyleConstants.FULL_WIDTH)
public class ShellHome implements WidgetSupplier {

    /** Workflow definitions, running processes, step executions, analytics. */
    @Menu
    RemoteMenu workflow = new RemoteMenu("/_workflow");

    /** Form definitions, the drag-and-drop editor, and the human tasks waiting to be done. */
    @Menu
    RemoteMenu forms = new RemoteMenu("/_forms");

    /** What the test worker was asked to do, and the overrides that answer it by hand. */
    @Menu
    RemoteMenu worker = new RemoteMenu("/_worker");

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

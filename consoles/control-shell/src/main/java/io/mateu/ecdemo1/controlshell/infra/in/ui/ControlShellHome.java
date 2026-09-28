package io.mateu.ecdemo1.controlshell.infra.in.ui;

import io.mateu.ecdemo1.uicommons.user.ApiUsageWidget;
import io.mateu.uidl.StyleConstants;
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
import io.mateu.uidl.data.MicroFrontend;
import io.mateu.uidl.data.RemoteMenu;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.WidgetSupplier;

import java.util.List;

/**
 * The control console — a second shell, on a host of its own.
 *
 * <p>Separate from the demo console rather than a menu inside it, and the separation is the point:
 * what lives behind here changes what the chat agent is and what it spends. Two hosts mean two
 * Keycloak clients, so a token minted for the demo console is not a token for this one, and the
 * gateway can require the {@code admin} realm role on this host without touching the other.
 *
 * <p>Like the demo shell, the Keycloak URL is compiled in — Mateu writes {@code @KeycloakSecured}
 * into the generated bootstrap page — so changing the hostname means rebuilding this image.
 *
 * <p>Built twice, like the demo shell: the default build is ec-demo1-control-shell (Vaadin),
 * {@code -Predwood} is ec-demo1-control-shell-redwood. See ShellHome for why.
 */
@UI("")
// Which plane this console is, next to the logo: the data plane is what the business uses, the
// control plane what governs the platform.
@Title("Control plane")
// The renderer in the tab's title: the Redwood build of this same class says so (Renderer).
@PageTitle("Control plane" + Renderer.TITLE_SUFFIX)
@KeycloakSecured(url = "https://auth.ec1.mateu.io", realm = "ec-demo1", clientId = "control-plane")
// Web Push: the inbox's script — it offers to enable notifications, and registers this browser for
// what enters the inbox of the user's roles. Served by communication-service, public at the gateway.
@Script(src = "/_inbox/push/push.js")
@Logo("/images/riu.svg")
@FavIcon("/images/riu.svg")
// The catalogues are listings with long ids and long URLs in them; the default ~900px container
// wraps those into unreadable columns.
@Style(StyleConstants.FULL_WIDTH)
// This is a home, so it declares the landing template: Mateu tags the wire pageType as "landing"
// and the renderer lays the page out as one. The welcome banner is what LANDING is for — Mateu
// turns it into a HeroSection at the top of the content: an image, a title and a subtitle above
// the menus. The image is a Redwood illustration served from src/main/resources/static like the
// logo, so it arrives through the gateway's catch-all with no route and no token of its own.
@PageTemplate(PageType.LANDING)
@WelcomeBanner(
        title = "Plano de control",
        subtitle = "IA, usuarios, workflows y formularios de la plataforma.",
        image = "/images/redwood-header-texture.png")
public class ControlShellHome implements WidgetSupplier {

    // Both entries name their label. Without one Mateu labels a RemoteMenu from the field name
    // until the remote pod answers — "Control plane" and "Users" here — and then swaps in the real
    // one, which is the menu bar changing under the reader a moment after it is drawn. Neither of
    // these two ever matched, so this console flickered on both. See ShellHome for the same note
    // and the cost that comes with it.

    /** The IA catalogues, served by the control-plane pod, which labels the section "IA". */
    @Menu
    RemoteMenu controlPlane = new RemoteMenu("/_ia-cp").withLabel("IA");

    /**
     * User, group, role and permission management, served by the users pod, which labels the
     * section "Usuarios". It moved here from the demo console on purpose: administering who may
     * access the platform and what they may do is a control-plane concern, not part of using the
     * product. Both halves of the admin console — IA and Usuarios — now live behind the same
     * ai-admin gate on this host.
     */
    @Menu
    RemoteMenu users = new RemoteMenu("/_users").withLabel("Usuarios");

    /**
     * Workflow definitions and analytics, served by the orchestrator pod — the same one that
     * answers Processes and Steps on the demo console, from a second {@code @UI} of its own.
     *
     * <p>Here rather than there for the same reason as Usuarios: a definition is configuration, and
     * analytics measures the engine rather than the business. Neither is part of using the product.
     * What stays on the demo console is the work itself — the processes in flight and the steps
     * they took.
     *
     * <p>The routes underneath are unchanged: {@code /workflow/definitions} is still
     * {@code /workflow/definitions}, only reached from {@code /_workflow-admin} instead of
     * {@code /_workflow}. The engine keeps the field name its menu is built from, so nothing
     * holding one of those routes had to be told.
     */
    @Menu
    RemoteMenu workflowAdmin = new RemoteMenu("/_workflow-admin").withLabel("Workflow");

    /** The form editor and the form definitions, served by the forms pod's second {@code @UI}. */
    @Menu
    RemoteMenu formsAdmin = new RemoteMenu("/_forms-admin").withLabel("Forms");

    /**
     * The hotels' integrations with Opera, served by integrations-service: registering one, its
     * onboarding gate by gate, and activating, pausing or decommissioning it. First of the
     * integration's menus, because it is where a hotel's integration starts.
     */
    @Menu
    RemoteMenu integrations = new RemoteMenu("/_integrations").withLabel("Integrations");

    /**
     * The CRS-PMS integration's control plane (PoC ACL): the causes blocking processes, the code
     * mapping with its proposals and approvals — and the button that asks the mapping agent — and
     * the partners' profiles in the PMS. A control-plane concern like the rest of this console:
     * approving an equivalence changes what every hotel's reservations become in the PMS.
     */
    @Menu
    RemoteMenu mapping = new RemoteMenu("/_mapping").withLabel("Mapping");

    /**
     * The customers — golden records — served by customer-mdm-service (HLA CRM-MDM): who each
     * reservation's passengers are, what Salesforce's cleaning merged, and whether the new codes
     * reached the PMS. Read-only: stewards merge in Salesforce.
     */
    @Menu
    RemoteMenu customers = new RemoteMenu("/_mdm").withLabel("Customers");

    /** What the integration told people, and who is told what. */
    @Menu
    RemoteMenu notifications = new RemoteMenu("/_communication").withLabel("Notifications");

    /** Who did what on the control plane: every auditable action, searchable (HLA F016). */
    @Menu
    RemoteMenu audit = new RemoteMenu("/_audit").withLabel("Audit");

    /**
     * The inbox, hidden from the bar: the badge in the widgets below is the way in, and it says how
     * much is waiting on the way. Still declared, so a deep link or a reload on /inbox/... resolves.
     */
    @Menu
    @Hidden
    RemoteMenu inbox = new RemoteMenu("/_inbox");

    /** The external APIs' usage, hidden from the bar like the inbox: its header widget is the way in. */
    @Menu
    @Hidden
    RemoteMenu apiUsage = new RemoteMenu("/_api-usage").withLabel("APIs externas");

    /**
     * Under the welcome banner: Salesforce's calls left of the org's daily allowance, whether they go
     * down, our pace, and Opera's calls today — what to look at before a meeting or a demo. Served
     * by integrations-service from numbers already held: a page view costs Salesforce nothing.
     */
    MicroFrontend apiUsageTiles = ApiUsageWidget.tiles();

    /**
     * How much of Salesforce and Opera is spent, the inbox badge and who is signed in — the widgets
     * every console shares (ui-commons). Nothing for an anonymous call: the bootstrap page is about
     * to redirect to Keycloak.
     */
    @Override
    public List<Component> widgets(HttpRequest httpRequest) {
        return ApiUsageWidget.withInboxBadge(httpRequest);
    }
}

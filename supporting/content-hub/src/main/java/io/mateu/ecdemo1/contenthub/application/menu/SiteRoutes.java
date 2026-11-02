package io.mateu.ecdemo1.contenthub.application.menu;

/**
 * A site's routes, as its portal serves them — so a PAGE or SECTION target becomes the route the
 * existing pages answer, and the menu never invents routes of its own. The intranet's are
 * /intranet/noticias/{id} and /intranet/secciones/{id}; another site (a newsletter) brings its own
 * implementation.
 */
public interface SiteRoutes {

    /** Where the site's routes hang (/intranet). */
    String base(String siteId);

    default String page(String siteId, String pageId) {
        return base(siteId) + "/noticias/" + pageId;
    }

    default String section(String siteId, String sectionId) {
        return base(siteId) + "/secciones/" + sectionId;
    }

    default String function(String siteId, String key) {
        return base(siteId) + "/" + key;
    }

    /** The portal's page that opens an external link in a new tab (Mateu's menus cannot). */
    default String externalLink(String siteId, String entryId) {
        return base(siteId) + "/enlace/" + siteId + "/" + entryId;
    }

    /** The portal's page that hosts a UI that is not federated (a web component, a frame). */
    default String hostedUi(String siteId, String entryId) {
        return base(siteId) + "/ui/" + siteId + "/" + entryId;
    }
}

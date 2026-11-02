package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The other UIs a menu entry may open (a REMOTE target): the registry of external UI sources the
 * control plane configures. This is only the seam — the registry itself is another piece; until it is
 * plugged in there are no sources (MenuConfig's default bean) and REMOTE entries are not shown.
 */
public interface UiSources {

    Optional<UiSource> find(String sourceId);

    List<UiSource> all();

    /** How a UI source is drawn. */
    enum Kind {
        /** Another Mateu app behind the gateway: federated, as the shells federate the services. */
        MATEU_REMOTE,
        /** A web component: hosted in a page of the portal. */
        WEB_COMPONENT,
        /** Any page: hosted in a page of the portal, in a frame. */
        IFRAME
    }

    /**
     * @param basePath       where the gateway serves it (/_users, /_crs…)
     * @param audience       who may use it (null: everyone) — the same model as the content's
     * @param requiredScopes scopes the reader must hold (the gateway's X-User-Scopes); empty: none
     */
    record UiSource(String id, String name, Kind kind, String basePath, Audience audience, Set<String> requiredScopes) {
        public UiSource {
            requiredScopes = requiredScopes == null ? Set.of() : Set.copyOf(requiredScopes);
        }
    }
}

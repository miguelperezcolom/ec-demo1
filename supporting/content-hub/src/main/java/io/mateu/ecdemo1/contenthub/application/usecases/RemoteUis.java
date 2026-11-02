package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who may use which registered UI — the read side of the registry, for the portal (remote pages and
 * blocks), the visual editor and the menus. A reader may use a source when it is registered and active,
 * they hold every scope it requires, and its audience admits their attributes (the same policy as
 * content; the projection already ANDs it into whatever uses the source, this is the check at the door).
 * The remote UI then enforces its own permissions with the reader's token: nothing here widens them.
 */
@Service
public class RemoteUis {

    final UiSources sources;
    final NamedAudiences namedAudiences;
    final Set<String> iframeOrigins;

    public RemoteUis(UiSources sources, NamedAudiences namedAudiences,
                     @Value("${content-hub.ui-sources.iframe-origins:}") String iframeOrigins) {
        this.sources = sources;
        this.namedAudiences = namedAudiences;
        this.iframeOrigins = origins(iframeOrigins);
    }

    /** The source, if this reader may use it. */
    public Optional<UiSource> usable(Reader reader, String sourceId) {
        if (reader == null || sourceId == null) {
            return Optional.empty();
        }
        return sources.find(sourceId)
                .filter(s -> s.usableWith(reader.scopes()))
                .filter(s -> namedAudiences.policy().admits(s.audience(), reader.attributes()))
                .filter(this::originStillAllowed);
    }

    /** The source a use names, if this reader may use it and the use fits it. */
    public Optional<UiSource> usable(Reader reader, RemoteUi use) {
        if (use == null) {
            return Optional.empty();
        }
        return usable(reader, use.sourceId()).filter(s -> {
            try {
                s.check(use);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        });
    }

    /**
     * For editors: the use names a registered source and fits it — what saving a remote page or block
     * requires (active or not: deactivating a source hides its uses, it does not break their drafts).
     */
    public UiSource requireRegistered(RemoteUi use) {
        var source = sources.find(use.sourceId())
                .orElseThrow(() -> new IllegalArgumentException("No hay ninguna UI externa registrada con el id «"
                        + use.sourceId() + "»"));
        source.check(use);
        return source;
    }

    /** The deployment's allow-list for iframe sources ({@code content-hub.ui-sources.iframe-origins}). */
    public Set<String> iframeOrigins() {
        return iframeOrigins;
    }

    /** An iframe registered while its origin was allowed is not drawn once the deployment removes it. */
    boolean originStillAllowed(UiSource s) {
        try {
            s.requireAllowedOrigin(iframeOrigins);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static Set<String> origins(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
                .map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
    }
}

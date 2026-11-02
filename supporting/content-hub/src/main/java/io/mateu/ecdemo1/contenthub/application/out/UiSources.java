package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.uisource.UiSource;

import java.util.List;
import java.util.Optional;

/**
 * The registry of external UIs, read-only: what remote pages, «Componente remoto» blocks and the menus'
 * REMOTE entries resolve a source id against. Whether a reader may use one is
 * {@code RemoteUis.usable(reader, sourceId)}.
 */
public interface UiSources {

    /** No sources at all — for wiring that does not use them. */
    UiSources NONE = new UiSources() {
        @Override
        public Optional<UiSource> find(String id) {
            return Optional.empty();
        }

        @Override
        public List<UiSource> all() {
            return List.of();
        }
    };

    Optional<UiSource> find(String id);

    /** Every registered source, active or not, by name. */
    List<UiSource> all();
}

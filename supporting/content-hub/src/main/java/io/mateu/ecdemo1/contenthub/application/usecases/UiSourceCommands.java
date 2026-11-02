package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.UiSourceRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.shared.ContentEvents;
import io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The registry of external UIs, written — configuration, {@code content:admin} only. A source's audience
 * is a named audience to the visibility policy ({@code ui-source:<id>}), so registering, changing or
 * removing one recalculates the projection: whatever uses it is shown to whom it now admits (an inactive
 * or removed source, to nobody).
 */
@Service
@RequiredArgsConstructor
public class UiSourceCommands {

    final UiSourceRepository sources;
    final RemoteUis remoteUis;
    final ItemRepository items;
    final DomainEvents events;

    @Transactional
    public void register(Reader reader, UiSource source) {
        reader.require(EditorialPermission.ADMIN);
        if (sources.find(source.id()).isPresent()) {
            throw new IllegalArgumentException("Ya hay una UI externa " + source.id());
        }
        save(source);
    }

    @Transactional
    public void update(Reader reader, UiSource source) {
        reader.require(EditorialPermission.ADMIN);
        sources.find(source.id()).orElseThrow(() -> new IllegalArgumentException("No existe la UI externa " + source.id()));
        save(source);
    }

    /** Removing one hides what uses it (pages, blocks, menu entries) until an editor points them elsewhere. */
    @Transactional
    public void delete(Reader reader, String id) {
        reader.require(EditorialPermission.ADMIN);
        sources.delete(id);
        events.publish(new ContentEvents.AudienceRedefined(UiSource.AUDIENCE_PREFIX + id));
    }

    void save(UiSource source) {
        source.requireAllowedOrigin(remoteUis.iframeOrigins());
        sources.save(source);
        events.publish(new ContentEvents.AudienceRedefined(UiSource.AUDIENCE_PREFIX + source.id()));
    }

    /** The items whose draft or latest version uses a source — what the registry lists as its uses. */
    public List<String> uses(String sourceId) {
        var out = new ArrayList<String>();
        for (Item item : items.findAll()) {
            var contents = new ArrayList<ItemContent>();
            contents.add(item.draft());
            item.latestVersion().ifPresent(v -> contents.add(v.content()));
            var ids = new LinkedHashSet<String>();
            for (var c : contents) {
                RemoteUi.parse(c.url()).filter(r -> r.sourceId().equals(sourceId)).ifPresent(r -> ids.add(item.id()));
                c.blocks().stream().filter(b -> b.remote() != null && b.remote().sourceId().equals(sourceId))
                        .findAny().ifPresent(b -> ids.add(item.id()));
            }
            ids.forEach(id -> out.add(id + " · " + item.draft().in(item.defaultLanguage(), item.defaultLanguage()).title()));
        }
        return out;
    }
}

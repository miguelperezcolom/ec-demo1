package io.mateu.ecdemo1.contenthub.application.visibility;

import io.mateu.ecdemo1.contenthub.application.out.NamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSource;
import io.mateu.ecdemo1.contenthub.domain.visibility.VisibilityPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The named audiences as the visibility policy resolves them — the editors' ones, and each registered UI
 * source's ({@code ui-source:<id>}, see {@link UiSource#reference}): a remote page or component is
 * projected with its source's audience ANDed in, so redefining or deactivating a source is redefining a
 * named audience.
 */
@Service
public class NamedAudiences {

    final NamedAudienceRepository repository;
    final UiSources uiSources;

    @Autowired
    public NamedAudiences(NamedAudienceRepository repository, UiSources uiSources) {
        this.repository = repository;
        this.uiSources = uiSources;
    }

    /** Without UI sources: a reference to one admits nobody. */
    public NamedAudiences(NamedAudienceRepository repository) {
        this(repository, UiSources.NONE);
    }

    public Map<String, Audience> snapshot() {
        var out = new HashMap<String, Audience>();
        repository.findAll().forEach(a -> out.put(a.id(), a.expression()));
        uiSources.all().forEach(s -> out.put(UiSource.AUDIENCE_PREFIX + s.id(), s.visibilityAudience()));
        return out;
    }

    public Function<String, String> labels() {
        var names = new HashMap<String, String>();
        repository.findAll().forEach(a -> names.put(a.id(), a.name()));
        uiSources.all().forEach(s -> names.put(UiSource.AUDIENCE_PREFIX + s.id(), "quien puede usar " + s.name()));
        return id -> names.getOrDefault(id, UiSource.isReference(id)
                ? "UI externa " + UiSource.sourceIdOf(id) + " (borrada)" : id + " (borrada)");
    }

    /** A policy over the named audiences as they are now. */
    public VisibilityPolicy policy() {
        return new VisibilityPolicy(snapshot()::get);
    }
}

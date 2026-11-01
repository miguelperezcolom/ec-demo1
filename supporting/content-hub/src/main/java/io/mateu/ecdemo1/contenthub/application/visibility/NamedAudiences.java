package io.mateu.ecdemo1.contenthub.application.visibility;

import io.mateu.ecdemo1.contenthub.application.out.NamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.audience.NamedAudience;
import io.mateu.ecdemo1.contenthub.domain.visibility.VisibilityPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The named audiences as the policy resolves them: read once per evaluation pass, not per leaf. */
@Service
@RequiredArgsConstructor
public class NamedAudiences {

    final NamedAudienceRepository repository;

    public Map<String, Audience> snapshot() {
        return repository.findAll().stream().collect(Collectors.toMap(NamedAudience::id, NamedAudience::expression));
    }

    public Function<String, String> labels() {
        var names = repository.findAll().stream().collect(Collectors.toMap(NamedAudience::id, NamedAudience::name));
        return id -> names.getOrDefault(id, id + " (borrada)");
    }

    /** A policy over the named audiences as they are now. */
    public VisibilityPolicy policy() {
        return new VisibilityPolicy(snapshot()::get);
    }
}

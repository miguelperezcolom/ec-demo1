package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.audience.NamedAudience;

import java.util.List;
import java.util.Optional;

public interface NamedAudienceRepository {

    Optional<NamedAudience> find(String id);

    void save(NamedAudience audience);

    void delete(String id);

    List<NamedAudience> findAll();
}
